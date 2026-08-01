package com.flatts.recompile.gametest;

import com.flatts.recompile.content.entity.RoachEntity;
import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCEntities;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * Spawn a roach and guarantee it is gone when the test ends.
     *
     * <p>Not housekeeping: gametest plots sit within a few blocks of each other, this entity walks, and
     * it targets players. A roach left alive is a hostile wandering into whatever test runs next - the
     * same trap {@code AnimalBaitTests} hit with a lingering mock player, which tripped a neighbouring
     * plot's assertions.
     */
    private static RoachEntity spawnRoach(net.minecraft.gametest.framework.GameTestHelper helper,
            BlockPos abs) {
        Entity spawned = RCEntities.ROACH.get().spawn(helper.getLevel(), abs, EntitySpawnReason.COMMAND);
        return spawned instanceof RoachEntity roach ? roach : null;
    }

    private static void clearRoaches(net.minecraft.gametest.framework.GameTestHelper helper, BlockPos abs) {
        helper.getLevel().getEntitiesOfClass(RoachEntity.class, new AABB(abs).inflate(12))
            .forEach(Entity::discard);
    }

    /** A food item's nutrition, for the progression assertions below. */
    private static int nutrition(net.minecraft.world.item.Item item) {
        net.minecraft.world.food.FoodProperties food =
            new net.minecraft.world.item.ItemStack(item).get(net.minecraft.core.component.DataComponents.FOOD);
        return food == null ? -1 : food.nutrition();
    }

    private static int roachesNear(net.minecraft.gametest.framework.GameTestHelper helper, BlockPos abs) {
        return helper.getLevel()
            .getEntitiesOfClass(RoachEntity.class, new AABB(abs).inflate(8)).size();
    }

    static void register() {
        // It exists, spawns, and is the type we registered.
        RCGameTests.test("roach_spawns_from_its_type", 20, helper -> {
            BlockPos abs = helper.absolutePos(SPOT);
            Entity roach = spawnRoach(helper, abs);
            helper.assertTrue(roach instanceof RoachEntity, "the roach type must spawn a RoachEntity");
            helper.assertTrue(roachesNear(helper, abs) == 1, "exactly one roach");
            clearRoaches(helper, abs);
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
            clearRoaches(helper, abs);
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
            clearRoaches(helper, abs);
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
                clearRoaches(helper, abs);
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
        // The entity skin is named from RENDERER CODE, not from a model, so
        // every_texture_a_model_names_exists cannot see it - rename or drop the file and the roach is
        // the missing texture with a green build. Entity textures need their own check for exactly the
        // reason the model sweep exists.
        RCGameTests.test("roach_skin_exists", 20, helper -> {
            helper.assertTrue(
                RoachTests.class.getResource("/assets/recompile/textures/entity/roach.png") != null,
                "assets/recompile/textures/entity/roach.png is missing, so the roach renders as the "
                    + "missing texture - and no model references it, so nothing else would notice");
            helper.succeed();
        });

        // The claim the whole drop choice rests on: the Burn Barrel cooks Raw Roach WITHOUT any tag
        // change, because its rule matches the FOOD component rather than a list. If the barrel's rule
        // ever narrows to an allowlist, this fails - which is the point, since the alternative was
        // making roaches drop organic muck and compete with the Compost Heap.
        RCGameTests.test("burn_barrel_cooks_raw_roach_with_no_tag", 20, helper -> {
            helper.assertTrue(
                com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity.burns(
                    new net.minecraft.world.item.ItemStack(RCItems.RAW_ROACH.get())),
                "the barrel must accept Raw Roach through the FOOD component, with no allowlist entry");
            helper.assertFalse(
                new net.minecraft.world.item.ItemStack(RCItems.RAW_ROACH.get())
                    .is(com.flatts.recompile.registry.RCTags.BURN_BARREL_SMELTABLE),
                "...and it must NOT be in the allowlist, or this proves nothing about the component");
            helper.succeed();
        });

        // Raw smelts to cooked, and cooked is worth more than raw - otherwise the barrel step is a
        // ritual rather than an upgrade.
        RCGameTests.test("raw_roach_smelts_into_cooked_roach", 20, helper -> {
            ServerLevel level = helper.getLevel();
            net.minecraft.world.item.crafting.SingleRecipeInput input =
                new net.minecraft.world.item.crafting.SingleRecipeInput(
                    new net.minecraft.world.item.ItemStack(RCItems.RAW_ROACH.get()));
            boolean smelts = level.getServer().getRecipeManager().recipeMap()
                .getRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, level)
                .findAny().isPresent();
            helper.assertTrue(smelts, "Raw Roach must have a smelting recipe");

            int raw = nutrition(RCItems.RAW_ROACH.get());
            int cooked = nutrition(RCItems.COOKED_ROACH.get());
            helper.assertTrue(cooked > raw,
                "cooking must be worth doing: raw " + raw + " -> cooked " + cooked);
            helper.succeed();
        });

        // The progression guard. Roaches arrive at tier 0, so cooked roach must not beat the tin can -
        // the earliest renewable food outclassing the found food would invert the whole early economy.
        RCGameTests.test("cooked_roach_does_not_beat_the_tin_can", 20, helper -> {
            int roach = nutrition(RCItems.COOKED_ROACH.get());
            int can = nutrition(RCItems.TIN_CAN_OPEN.get());
            helper.assertTrue(roach <= can,
                "cooked roach (" + roach + ") must not out-feed an opened tin can (" + can
                    + ") - it is renewable from the first garbage block, and the can is not");
            helper.succeed();
        });

        // The phase's actual payload, and nothing else covers it: entity loot tables are outside
        // every_block_has_a_loot_table, which sweeps blocks only. A roach with no table drops nothing
        // and the whole food line is unreachable in play while every other test stays green.
        RCGameTests.test("a_killed_roach_drops_raw_roach", 60, helper -> {
            BlockPos abs = helper.absolutePos(SPOT);
            RoachEntity roach = spawnRoach(helper, abs);
            if (roach == null) {
                helper.fail("the roach did not spawn");
                return;
            }
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);

            // Killed BY THE PLAYER on purpose - the table requires it, see below.
            roach.hurtServer(helper.getLevel(),
                helper.getLevel().damageSources().playerAttack(player), 100.0F);

            helper.runAfterDelay(5, () -> {
                boolean dropped = helper.getLevel()
                    .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new AABB(abs).inflate(6))
                    .stream()
                    .anyMatch(item -> item.getItem().is(RCItems.RAW_ROACH.get()));
                helper.assertTrue(dropped, "a roach killed by a player must drop Raw Roach");
                clearRoaches(helper, abs);
                player.discard();
                helper.succeed();
            });
        });

        // ...and it drops NOTHING otherwise. That is deliberate: this is the earliest renewable food in
        // the game, so a roach dying to fall damage or a mob grinder must not feed anyone. The condition
        // is the anti-farm measure, and without this test it would look like an accident and be
        // "cleaned up" by the next person reading the loot table.
        RCGameTests.test("a_roach_that_dies_alone_drops_nothing", 60, helper -> {
            BlockPos abs = helper.absolutePos(SPOT);
            RoachEntity roach = spawnRoach(helper, abs);
            if (roach == null) {
                helper.fail("the roach did not spawn");
                return;
            }
            roach.hurtServer(helper.getLevel(),
                helper.getLevel().damageSources().fellOutOfWorld(), 100.0F);

            helper.runAfterDelay(5, () -> {
                boolean dropped = helper.getLevel()
                    .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new AABB(abs).inflate(6))
                    .stream()
                    .anyMatch(item -> item.getItem().is(RCItems.RAW_ROACH.get()));
                helper.assertFalse(dropped,
                    "a roach not killed by a player must drop nothing - the condition is what stops "
                        + "the earliest renewable food in the game from being farmable");
                clearRoaches(helper, abs);
                helper.succeed();
            });
        });

        // Phase 3: a disturbed garbage block releases exactly one roach. Driven through spawnRoach
        // rather than by rolling the 1-in-N until it fires - the sortOnce convention, so the test is
        // about what happens when it fires rather than about how often.
        RCGameTests.test("a_disturbed_garbage_block_releases_one_roach", 40, helper -> {
            BlockPos block = new BlockPos(2, 1, 2);
            helper.setBlock(block, RCBlocks.GARBAGE_BLOCK.get());
            BlockPos abs = helper.absolutePos(block);

            boolean released = SortableBlock.spawnRoach(helper.getLevel(), abs);
            helper.assertTrue(released, "disturbing garbage must be able to release a roach");
            helper.assertTrue(roachesNear(helper, abs) == 1,
                "exactly one, never a swarm - got " + roachesNear(helper, abs));
            clearRoaches(helper, abs);
            helper.succeed();
        });

        // Only household garbage hides them. The yard already has four hostile spawns, and this mechanic
        // is about the STARTING biome having one thing that reacts to being disturbed - so a new
        // sortable must opt in rather than inherit it.
        RCGameTests.test("only_garbage_harbours_roaches", 20, helper -> {
            List<String> wrong = new ArrayList<>();
            record Variant(String name, net.minecraft.world.level.block.Block block, boolean expected) { }
            for (Variant v : List.of(
                    new Variant("garbage_block", RCBlocks.GARBAGE_BLOCK.get(), true),
                    new Variant("trash_bag", RCBlocks.TRASH_BAG.get(), false),
                    new Variant("compacted_bale", RCBlocks.COMPACTED_BALE.get(), false),
                    new Variant("stone_rubble", RCBlocks.STONE_RUBBLE.get(), false))) {
                BlockPos pos = new BlockPos(1, 1, 1);
                helper.setBlock(pos, v.block());
                if (!(helper.getLevel().getBlockState(helper.absolutePos(pos)).getBlock()
                        instanceof SortableBlock sortable)) {
                    wrong.add(v.name() + " is not a SortableBlock");
                    continue;
                }
                if (sortable.harboursRoaches() != v.expected()) {
                    wrong.add(v.name() + " harboursRoaches=" + sortable.harboursRoaches()
                        + ", expected " + v.expected());
                }
                helper.setBlock(pos, Blocks.AIR);
            }
            helper.assertTrue(wrong.isEmpty(), "wrong roach hosts: " + wrong);
            helper.succeed();
        });

        // Config off means off. Everything ships config-gated in this mod, and a gate nobody tests is a
        // gate that quietly stops working.
        RCGameTests.test("roaches_can_be_turned_off", 20, helper -> {
            BlockPos block = new BlockPos(2, 1, 2);
            helper.setBlock(block, RCBlocks.GARBAGE_BLOCK.get());
            BlockPos abs = helper.absolutePos(block);
            if (!(helper.getLevel().getBlockState(abs).getBlock() instanceof SortableBlock sortable)) {
                helper.fail("the garbage block is not a SortableBlock");
                return;
            }
            boolean was = RCConfig.ROACHES_ENABLED.get();
            try {
                RCConfig.ROACHES_ENABLED.set(false);
                for (int i = 0; i < 50; i++) {
                    helper.assertFalse(sortable.releaseRoach(helper.getLevel(), abs),
                        "no roach may be released while the config is off");
                }
            } finally {
                RCConfig.ROACHES_ENABLED.set(was);
            }
            clearRoaches(helper, abs);
            helper.succeed();
        });

    }
}
