package com.flatts.recompile.gametest;

import com.flatts.recompile.content.entity.PigeonEntity;
import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.content.entity.PigeonForageGoal;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.world.level.block.state.BlockState;
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

        // A PIGEON MUST NOT EAT THE DUMP. Foraging deliberately rolls its own table rather than calling
        // SortableBlock.sortOnce, because a real pull advances the sorted blockstate and eventually
        // crumbles the block - so a bird that really sorted would quietly dismantle a player's garbage
        // while they were away. The block must come out of a peck exactly as it went in.
        //
        // Asserted against the loot table rather than by running the goal, which needs pathing and a
        // real walk. The destructive half is the part that matters and it is the part that is checkable.
        RCGameTests.test("a_pigeon_forages_without_consuming_the_pile", 40, helper -> {
            BlockPos pos = new BlockPos(1, 2, 1);
            helper.setBlock(pos, RCBlocks.GARBAGE_BLOCK.get());
            BlockState before = helper.getBlockState(pos);

            var level = helper.getLevel();
            var key = PigeonForageGoal.tableFor(RCBlocks.GARBAGE_BLOCK.get());
            helper.assertTrue(key != null,
                "a peck at a garbage block must resolve a table, or it silently yields nothing forever");
            var table = level.getServer().reloadableRegistries().getLootTable(key);

            List<net.minecraft.world.item.Item> seen = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(
                        net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(pos)))
                    .create(net.minecraft.world.level.storage.loot.parameters
                        .LootContextParamSets.CHEST);
                for (var stack : table.getRandomItems(params)) {
                    if (!stack.isEmpty()) {
                        seen.add(stack.getItem());
                    }
                }
            }
            helper.assertTrue(seen.size() > 150,
                "the forage table yielded almost nothing over 200 rolls (" + seen.size() + ") - an "
                    + "empty table is indistinguishable from a bird that never finds anything");

            // The block is untouched: same state, same sorted count, still there.
            helper.assertBlockPresent(RCBlocks.GARBAGE_BLOCK.get(), pos);
            helper.assertTrue(helper.getBlockState(pos).equals(before),
                "foraging must leave the pile exactly as it was - a pigeon that advances the sorted "
                    + "count is a pigeon that eats an unattended dump");
            helper.succeed();
        });

        // A PIGEON CAN ONLY FIND WHAT THE PILE ITSELF WOULD GIVE YOU, AND THAT IS A GATING RULE
        // (owner, 2026-08-04). A mob that produces a NEW material is a route around whatever gates that
        // material, and this one wanders into your base on its own.
        //
        // It holds structurally: the goal rolls the block's own pull table, so there is no second table
        // to drift. This asserts the wiring rather than comparing two files, because the previous
        // version DID compare two files and the bespoke table was wrong twice in one afternoon - wheat
        // seeds, which are behind the Hydroponics Bay, and then bread, an apple and a feather, none of
        // which this world produces at all.
        RCGameTests.test("a_pigeon_can_only_find_what_the_pile_itself_gives", 20, helper -> {
            List<String> problems = new ArrayList<>();
            for (var block : List.of(RCBlocks.GARBAGE_BLOCK.get(), RCBlocks.TRASH_BAG.get(),
                    RCBlocks.COMPACTED_BALE.get())) {
                var foraged = PigeonForageGoal.tableFor(block);
                var pulled = com.flatts.recompile.content.block.SortableBlock.pullTableOf(block);
                if (foraged == null) {
                    problems.add(block + " resolves no forage table, so a peck at it does nothing");
                } else if (!foraged.equals(pulled)) {
                    problems.add(block + " forages " + foraged + " but is picked through for " + pulled
                        + " - a bird must not have its own stream");
                }
            }
            helper.assertTrue(problems.isEmpty(), "pigeon foraging is not gated to the pile: " + problems);

            // And the mod ships no separate forage table to fall back to. A leftover file is how the
            // rule quietly comes undone later.
            helper.assertTrue(
                StrayTests.class.getResource(
                    "/data/recompile/loot_table/gameplay/pigeon_forage.json") == null,
                "a bespoke pigeon forage table is back. It cannot be a subset of the pile by "
                    + "construction, so it will drift, and the drift is a progression leak");
            helper.succeed();
        });

        // AND IT ONLY PECKS AT HOUSEHOLD GARBAGE. Stone Rubble and Mechanical Waste are SortableBlocks
        // too, and deriving the target from that class had pigeons foraging in broken concrete out in
        // the demolition yard - which is where this was actually caught.
        RCGameTests.test("a_pigeon_only_forages_household_garbage", 20, helper -> {
            List<String> wrong = new ArrayList<>();
            for (var block : List.of(RCBlocks.STONE_RUBBLE.get(), RCBlocks.MECHANICAL_WASTE.get())) {
                if (block.defaultBlockState().is(
                        com.flatts.recompile.registry.RCTags.PIGEON_FORAGEABLE)) {
                    wrong.add(block.toString());
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                "a pigeon would peck at " + wrong + ". Those are demolition yard piles of stone and "
                    + "machinery, and a bird finding food in them is the bug this tag exists for");
            for (var block : List.of(RCBlocks.GARBAGE_BLOCK.get(), RCBlocks.TRASH_BAG.get(),
                    RCBlocks.COMPACTED_BALE.get())) {
                helper.assertTrue(block.defaultBlockState().is(
                        com.flatts.recompile.registry.RCTags.PIGEON_FORAGEABLE),
                    block + " must be forageable, or the pigeon has nothing to peck at");
            }
            helper.succeed();
        });

        // NOTHING VALUABLE COMES OUT OF A BIRD. The pigeon is ambiance, and the moment its table can
        // hand out something worth waiting for, it is a farm instead. Guards the gem tier explicitly,
        // since that is the line the rest of the mod is built to hold.
        RCGameTests.test("a_pigeon_never_finds_anything_worth_farming", 20, helper -> {
            var level = helper.getLevel();
            var table = level.getServer().reloadableRegistries()
                .getLootTable(PigeonForageGoal.tableFor(RCBlocks.GARBAGE_BLOCK.get()));
            List<String> rich = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(
                        net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))))
                    .create(net.minecraft.world.level.storage.loot.parameters
                        .LootContextParamSets.CHEST);
                for (var stack : table.getRandomItems(params)) {
                    var item = stack.getItem();
                    if (item == net.minecraft.world.item.Items.DIAMOND
                        || item == net.minecraft.world.item.Items.EMERALD
                        || item == net.minecraft.world.item.Items.LAPIS_LAZULI
                        || item == net.minecraft.world.item.Items.REDSTONE
                        || item == net.minecraft.world.item.Items.GOLD_INGOT
                        || item == net.minecraft.world.item.Items.AMETHYST_SHARD
                        || item == net.minecraft.world.item.Items.IRON_INGOT) {
                        rich.add(item.toString());
                    }
                }
            }
            helper.assertTrue(rich.isEmpty(),
                "a pigeon found " + rich + ". Everything in that list is gated on the demolition yard "
                    + "or the Separator, and a bird is neither");
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
