package com.flatts.recompile.gametest;

import com.flatts.recompile.content.entity.PigeonEntity;
import com.flatts.recompile.registry.RCEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * The dump's strays (#133): cats, dogs and pigeons live here, rarely, as ambiance.
 *
 * <p>These assert the things that fail <b>silently</b>. A biome whose spawner list is subtly wrong does
 * not error - it just never produces an animal, and the dump stays exactly as empty as it was before
 * anyone worked on this.
 */
final class StrayTests {

    private static final ResourceKey<Biome> SPRAWL = ResourceKey.create(
        Registries.BIOME, Identifier.fromNamespaceAndPath("recompile", "household_sprawl"));

    private StrayTests() {
    }

    static void register() {
        // All three are in the biome, in the category their entity type demands. Cat and wolf are
        // MobCategory.CREATURE - fixed on the vanilla type, so they cannot be listed as ambient however
        // much they behave like it - and a mob filed under the wrong category is simply never spawned,
        // with nothing anywhere saying so.
        RCGameTests.test("the_dump_has_strays_in_it", 20, helper -> {
            Biome biome = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.BIOME).getValue(SPRAWL);
            helper.assertTrue(biome != null, "household_sprawl must exist");
            MobSpawnSettings spawns = biome.getMobSettings();

            List<String> missing = new ArrayList<>();
            if (!lists(spawns, MobCategory.CREATURE, EntityType.CAT)) {
                missing.add("cat (creature)");
            }
            if (!lists(spawns, MobCategory.CREATURE, EntityType.WOLF)) {
                missing.add("wolf (creature)");
            }
            if (!lists(spawns, MobCategory.AMBIENT, RCEntities.PIGEON.get())) {
                missing.add("pigeon (ambient)");
            }
            helper.assertTrue(missing.isEmpty(),
                "strays absent from household_sprawl: " + missing
                    + ". The biome was creature-free by design, so an entry that quietly fails to load "
                    + "leaves it exactly as it was and nothing complains");
            helper.succeed();
        });

        // RARE, and rare is the decision - "a moment, not a feature". Vanilla's common animals sit at
        // weight 8-12; anything in that range here would make the dump a farm.
        RCGameTests.test("strays_are_rare", 20, helper -> {
            Biome biome = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.BIOME).getValue(SPRAWL);
            MobSpawnSettings spawns = biome.getMobSettings();
            List<String> tooCommon = new ArrayList<>();
            for (MobCategory category : List.of(MobCategory.CREATURE, MobCategory.AMBIENT)) {
                // 26.1 moved the weight OUT of SpawnerData into the Weighted wrapper around it, so
                // the record now carries only the type and the group size.
                for (var entry : spawns.getMobs(category).unwrap()) {
                    MobSpawnSettings.SpawnerData data = entry.value();
                    if (entry.weight() > 4) {
                        tooCommon.add(data.type() + " at weight " + entry.weight());
                    }
                    if (data.maxCount() > 3) {
                        tooCommon.add(data.type() + " in groups of " + data.maxCount());
                    }
                }
            }
            helper.assertTrue(tooCommon.isEmpty(),
                "strays that are not rare: " + tooCommon + ". Vanilla common animals are weight 8-12; "
                    + "these are meant to be something you remember seeing");
            helper.succeed();
        });

        // A PIGEON CANNOT BE TAMED (owner, 2026-08-04). It falls out of extending Animal, but the bird
        // it borrows its model from is a ShoulderRidingEntity that sits on your shoulder - so the way
        // this breaks is somebody changing the base class for an unrelated reason and never noticing
        // they handed players a pet.
        RCGameTests.test("a_pigeon_cannot_be_tamed", 20, helper -> {
            helper.assertFalse(TamableAnimal.class.isAssignableFrom(PigeonEntity.class),
                "a pigeon must not be tameable - it is ambiance, not a pet");
            helper.succeed();
        });

        // It spawns and lives without crashing. Attributes registered through
        // EntityAttributeCreationEvent are the usual omission, and a missing one throws on spawn rather
        // than at load, so nothing catches it until something tries to exist.
        RCGameTests.test("a_pigeon_can_exist", 40, helper -> {
            BlockPos pos = new BlockPos(1, 2, 1);
            PigeonEntity pigeon = RCEntities.PIGEON.get().create(
                helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
            helper.assertTrue(pigeon != null, "the pigeon type must be able to create an entity");
            pigeon.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(pos)));
            helper.getLevel().addFreshEntity(pigeon);
            helper.assertTrue(pigeon.isAlive(), "a freshly spawned pigeon must be alive");
            helper.assertTrue(pigeon.getMaxHealth() > 0,
                "a pigeon with no attributes has no health - EntityAttributeCreationEvent is missing");
            helper.succeed();
        });
    }

    private static boolean lists(MobSpawnSettings spawns, MobCategory category, EntityType<?> type) {
        for (var entry : spawns.getMobs(category).unwrap()) {
            if (entry.value().type() == type) {
                return true;
            }
        }
        return false;
    }
}
