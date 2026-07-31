package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.AnimalBaitBlock;
import com.flatts.recompile.content.block.AnimalBaitBlock.Diet;
import com.flatts.recompile.content.block.AnimalBaitBlock.Outcome;
import com.flatts.recompile.content.block.AnimalBaitBlock.Terrain;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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
            // Discard the mock player before finishing: gametest plots sit within one player-radius of
            // each other, and a lingering alive player would trip PLAYER_NEAR on a neighbouring plot's
            // bait (the spacing test in particular). discard() flips isAlive() off synchronously, so the
            // next test body in plot order no longer sees it.
            player.discard();
            helper.succeed();
        });

        // Two baits too close resolve one at a time (no deadlock): the earlier-sorted bait wins and
        // settles; the later one yields. BAIT (2,2,2) sorts before (4,2,2), so BAIT is the winner.
        RCGameTests.test("animal_bait_spacing_resolves_a_cluster", 40, helper -> {
            placeBait(helper, BAIT, Diet.HERBIVORE, false);
            placeBait(helper, BAIT.offset(2, 0, 0), Diet.HERBIVORE, false);
            ServerLevel level = helper.getLevel();
            Outcome later = AnimalBaitBlock.settleOnce(level, helper.absolutePos(BAIT.offset(2, 0, 0)));
            helper.assertTrue(later == Outcome.CROWDED, "the later bait must yield, got " + later);
            Outcome earlier = AnimalBaitBlock.settleOnce(level, helper.absolutePos(BAIT));
            helper.assertTrue(earlier == Outcome.SETTLING,
                "the earlier bait must still settle (no deadlock), got " + earlier);
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

        // The weights are data now, so the thing worth proving is that the JSON actually reaches the draw.
        // A hardcoded fallback would satisfy "cow is heavier than sniffer" just as well, so these assert the
        // shipped file's exact numbers - they fail if the data map stops loading rather than silently
        // reverting to DEFAULT_WEIGHT for everything.
        // Probed on SAND, which neither mob is affine to, so these are base weights with no bonus in play.
        // (Probing on NONE would not isolate anything: an entry-less mob also reads as NONE, so the two
        // cases would coincide and the assertion would hold whether or not the file loaded.)
        RCGameTests.test("bait_weights_come_from_the_data_map", 1, helper -> {
            int cow = AnimalBaitBlock.weightOf(holderOf(EntityType.COW), Terrain.SAND);
            int sniffer = AnimalBaitBlock.weightOf(holderOf(EntityType.SNIFFER), Terrain.SAND);
            helper.assertTrue(cow == 10, "cow's data-map weight must be 10, got " + cow);
            helper.assertTrue(sniffer == 1, "sniffer's data-map weight must be 1, got " + sniffer);
            helper.succeed();
        });

        // Terrain affinity rides the same entry: the bonus lands only on the terrain the mob is keyed to.
        RCGameTests.test("bait_weight_terrain_bonus_is_terrain_specific", 1, helper -> {
            int onGrass = AnimalBaitBlock.weightOf(holderOf(EntityType.COW), Terrain.GRASS);
            int onSand = AnimalBaitBlock.weightOf(holderOf(EntityType.COW), Terrain.SAND);
            helper.assertTrue(onGrass == 15, "a grass-affine cow on grass must be 10+5, got " + onGrass);
            helper.assertTrue(onSand == 10, "a grass-affine cow on sand must take no bonus, got " + onSand);
            helper.succeed();
        });

        // A mob with no entry stays reachable rather than dropping to zero - what lets a pack make a mob
        // spawnable with a diet tag alone. Wolf is deliberately absent from the shipped file.
        RCGameTests.test("bait_weight_falls_back_for_unlisted_mobs", 1, helper -> {
            int wolf = AnimalBaitBlock.weightOf(holderOf(EntityType.WOLF), Terrain.GRASS);
            helper.assertTrue(wolf == AnimalBaitBlock.DEFAULT_WEIGHT,
                "an untuned mob must ride DEFAULT_WEIGHT with no affinity, got " + wolf);
            helper.succeed();
        });
    }

    private static Holder<EntityType<?>> holderOf(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type);
    }
}
