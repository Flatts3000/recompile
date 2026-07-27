package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.AnimalBaitBlock;
import com.flatts.recompile.content.block.AnimalBaitBlock.Diet;
import com.flatts.recompile.content.block.AnimalBaitBlock.Outcome;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * GameTests for animal bait (reclamation rung 5). Driven through the block's static {@code settleOnce}
 * entry point (the {@code sortOnce} convention): each call is one settle step, so 8 undisturbed steps
 * fire the bait. The gate reasons are asserted via the returned {@link Outcome}.
 */
final class AnimalBaitTests {

    private static final BlockPos BAIT = new BlockPos(2, 2, 2);

    private AnimalBaitTests() {
    }

    private static void placeBait(GameTestHelper helper, BlockPos pos, Diet diet, boolean rich) {
        helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(pos, RCBlocks.ANIMAL_BAIT.get().defaultBlockState()
            .setValue(AnimalBaitBlock.DIET, diet)
            .setValue(AnimalBaitBlock.RICH, rich));
    }

    /** Drive settling to the firing step (8 undisturbed calls). */
    private static void settleToFire(ServerLevel level, BlockPos abs) {
        for (int i = 0; i <= AnimalBaitBlock.SETTLE_MAX; i++) {
            AnimalBaitBlock.settleOnce(level, abs);
        }
    }

    private static int countTag(GameTestHelper helper, BlockPos abs, TagKey<EntityType<?>> tag) {
        int n = 0;
        for (Entity e : helper.getLevel().getEntitiesOfClass(Entity.class, new AABB(abs).inflate(6))) {
            if (e.getType().builtInRegistryHolder().is(tag)) {
                n++;
            }
        }
        return n;
    }

    private static boolean baitStillThere(GameTestHelper helper, BlockPos abs) {
        return helper.getLevel().getBlockState(abs).getBlock() instanceof AnimalBaitBlock;
    }

    static void register() {
        // Undisturbed on grass, past the settle stages: fires, spawns one herbivore-tag mob, bait gone.
        RCGameTests.test("animal_bait_settles_and_spawns", 40, helper -> {
            placeBait(helper, BAIT, Diet.HERBIVORE, false);
            BlockPos abs = helper.absolutePos(BAIT);
            settleToFire(helper.getLevel(), abs);
            helper.assertFalse(baitStillThere(helper, abs), "the bait must be consumed once it fires");
            helper.assertTrue(countTag(helper, abs, Diet.HERBIVORE.tag()) == 1,
                "firing must spawn exactly one herbivore-tag mob, got "
                    + countTag(helper, abs, Diet.HERBIVORE.tag()));
            helper.succeed();
        });

        // Tag-respecting: each diet spawns only from its own allowlist (this also proves no excluded mob
        // can appear - the spawn is tag-gated, so the tag is the only source).
        RCGameTests.test("animal_bait_carnivore_stays_in_tag", 40, helper -> {
            placeBait(helper, BAIT, Diet.CARNIVORE, false);
            BlockPos abs = helper.absolutePos(BAIT);
            settleToFire(helper.getLevel(), abs);
            helper.assertTrue(countTag(helper, abs, Diet.CARNIVORE.tag()) == 1
                    && countTag(helper, abs, Diet.HERBIVORE.tag()) == 0,
                "carnivore bait must spawn a carnivore-tag mob only");
            helper.succeed();
        });

        // A player in range holds settling (resets it) - wildlife will not come while watched.
        RCGameTests.test("animal_bait_player_holds_settling", 40, helper -> {
            placeBait(helper, BAIT, Diet.HERBIVORE, false);
            BlockPos abs = helper.absolutePos(BAIT);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
            Outcome outcome = AnimalBaitBlock.settleOnce(helper.getLevel(), abs);
            helper.assertTrue(outcome == Outcome.PLAYER_NEAR, "a nearby player must hold settling, got " + outcome);
            helper.assertTrue(baitStillThere(helper, abs), "the bait must not fire with a player near");
            helper.succeed();
        });

        // Two baits too close: the second cannot settle.
        RCGameTests.test("animal_bait_spacing_blocks_settling", 40, helper -> {
            placeBait(helper, BAIT, Diet.HERBIVORE, false);
            placeBait(helper, BAIT.offset(2, 0, 0), Diet.HERBIVORE, false);
            Outcome outcome = AnimalBaitBlock.settleOnce(helper.getLevel(), helper.absolutePos(BAIT));
            helper.assertTrue(outcome == Outcome.CROWDED, "a nearby bait must block settling, got " + outcome);
            helper.succeed();
        });

        // Off grass: inert.
        RCGameTests.test("animal_bait_needs_grass", 40, helper -> {
            helper.setBlock(BAIT.below(), Blocks.STONE);
            helper.setBlock(BAIT, RCBlocks.ANIMAL_BAIT.get().defaultBlockState());
            Outcome outcome = AnimalBaitBlock.settleOnce(helper.getLevel(), helper.absolutePos(BAIT));
            helper.assertTrue(outcome == Outcome.NO_GRASS, "off grass the bait is inert, got " + outcome);
            helper.succeed();
        });

        // Rich bait seeds a bonded pair - two mobs, one of them a baby.
        RCGameTests.test("animal_bait_rich_seeds_a_pair", 40, helper -> {
            placeBait(helper, BAIT, Diet.HERBIVORE, true);
            BlockPos abs = helper.absolutePos(BAIT);
            settleToFire(helper.getLevel(), abs);
            helper.assertTrue(countTag(helper, abs, Diet.HERBIVORE.tag()) == 2,
                "Rich bait must seed two mobs, got " + countTag(helper, abs, Diet.HERBIVORE.tag()));
            int babies = 0;
            for (Entity e : helper.getLevel().getEntitiesOfClass(Entity.class, new AABB(abs).inflate(6))) {
                if (e instanceof AgeableMob ageable && ageable.isBaby()) {
                    babies++;
                }
            }
            helper.assertTrue(babies == 1, "Rich bait's pair must include one baby, got " + babies);
            helper.succeed();
        });
    }
}
