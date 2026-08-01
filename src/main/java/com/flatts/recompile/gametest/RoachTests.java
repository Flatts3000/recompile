package com.flatts.recompile.gametest;

import com.flatts.recompile.content.entity.RoachEntity;
import com.flatts.recompile.registry.RCEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

/**
 * GameTests for the Roach (#78, spec {@code docs/roach_spec.md}) - phase 1, the entity itself.
 *
 * <p>The behaviour worth pinning here is a <b>negative</b>: a roach must never summon others. That is
 * the one thing separating it from the silverfish it borrows its model from, and it is invisible until
 * a player hits one in a dump and the floor comes alive. Extending {@code Monster} rather than
 * {@code Silverfish} is what buys it, so this test guards the class choice, not a line of logic.
 */
final class RoachTests {

    private static final BlockPos SPOT = new BlockPos(2, 2, 2);

    private RoachTests() {
    }

    private static int roachesNear(net.minecraft.gametest.framework.GameTestHelper helper, BlockPos abs) {
        return helper.getLevel()
            .getEntitiesOfClass(RoachEntity.class, new AABB(abs).inflate(8)).size();
    }

    static void register() {
        // It exists, spawns, and is the type we registered.
        RCGameTests.test("roach_spawns_from_its_type", 20, helper -> {
            BlockPos abs = helper.absolutePos(SPOT);
            Entity roach = RCEntities.ROACH.get().spawn(helper.getLevel(), abs, EntitySpawnReason.COMMAND);
            helper.assertTrue(roach instanceof RoachEntity, "the roach type must spawn a RoachEntity");
            helper.assertTrue(roachesNear(helper, abs) == 1, "exactly one roach");
            helper.succeed();
        });

        // Attributes are registered. Without EntityAttributeCreationEvent the entity crashes on spawn,
        // and a missing attribute reads as a broken mob rather than a missing registration.
        RCGameTests.test("roach_has_its_attributes", 20, helper -> {
            BlockPos abs = helper.absolutePos(SPOT);
            Entity spawned = RCEntities.ROACH.get().spawn(helper.getLevel(), abs, EntitySpawnReason.COMMAND);
            if (!(spawned instanceof RoachEntity roach)) {
                helper.fail("the roach did not spawn");
                return;
            }
            helper.assertTrue(roach.getAttributeValue(Attributes.MAX_HEALTH) == 6.0,
                "max health must be 6, got " + roach.getAttributeValue(Attributes.MAX_HEALTH));
            helper.assertTrue(roach.getAttributeValue(Attributes.ATTACK_DAMAGE) == 1.0,
                "attack damage must be 1, got " + roach.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.succeed();
        });

        // THE invariant for this entity: it must not BE a Silverfish.
        //
        // Asserted structurally, because the behavioural version does not work and I shipped it before
        // checking. "Hurt one and see if others appear" passes trivially in a bare test plot: vanilla's
        // summon searches for INFESTED BLOCKS nearby, and there are none, so the roach passed that test
        // while literally extending Silverfish. The class choice is what buys the no-swarm rule, so the
        // class choice is what gets pinned.
        RCGameTests.test("roach_is_not_a_silverfish", 20, helper -> {
            BlockPos abs = helper.absolutePos(SPOT);
            Entity spawned = RCEntities.ROACH.get().spawn(helper.getLevel(), abs, EntitySpawnReason.COMMAND);
            helper.assertFalse(spawned instanceof net.minecraft.world.entity.monster.Silverfish,
                "the roach must not extend Silverfish - that inherits the summon-friends behaviour, "
                    + "which in a dump means one bad pull becomes a swarm in the starting biome");
            helper.assertTrue(spawned instanceof RoachEntity, "and it must still be our entity");
            helper.succeed();
        });

        // Hurting one still must not multiply it. Weaker than the check above - it cannot see a summon
        // that needs blocks this plot does not have - but it catches a future goal added by hand.
        RCGameTests.test("hurting_a_roach_summons_nothing", 60, helper -> {
            BlockPos abs = helper.absolutePos(SPOT);
            Entity spawned = RCEntities.ROACH.get().spawn(helper.getLevel(), abs, EntitySpawnReason.COMMAND);
            if (!(spawned instanceof RoachEntity roach)) {
                helper.fail("the roach did not spawn");
                return;
            }
            roach.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0F);
            helper.runAfterDelay(20, () -> {
                int count = roachesNear(helper, abs);
                helper.assertTrue(count <= 1, "hurting a roach must not call others, found " + count);
                helper.succeed();
            });
        });

        // It is not in any biome's spawner list - the starting biome stays creature-free, and a roach
        // only ever arrives by being disturbed out of a block (phase 3).
        RCGameTests.test("roach_is_in_no_spawner_list", 20, helper -> {
            java.util.List<String> found = new java.util.ArrayList<>();
            helper.getLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .listElements()
                .forEach(holder -> {
                    // getMobs takes a category in 26.1, so every category has to be asked in turn.
                    for (net.minecraft.world.entity.MobCategory category
                            : net.minecraft.world.entity.MobCategory.values()) {
                        holder.value().getMobSettings().getMobs(category).unwrap().forEach(data -> {
                            // unwrap() yields Weighted<SpawnerData>, so the entry is behind value().
                            if (data.value().type() == RCEntities.ROACH.get()) {
                                found.add(holder.key().identifier() + "/" + category.getName());
                            }
                        });
                    }
                });
            helper.assertTrue(found.isEmpty(),
                "the roach must not be in a spawner list, found in: " + found);
            helper.succeed();
        });
    }
}
