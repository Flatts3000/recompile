package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * GameTests for the sapling lockout (design P2.4-R2): no loot roll yields a sapling, so the Tree
 * Nursery is how trees enter the world and no <i>found</i> sapling can shortcut the reclamation ladder
 * onto raw coarse dirt.
 *
 * <p><b>Scope, because it narrowed.</b> This said "a player can never obtain a sapling" until
 * 2026-08-20, when villagers arrived (#227) and a wandering trader began selling them. Trades are not
 * loot rolls and these tests do not cover them, deliberately - #263 closed not-planned, so buying a
 * sapling is accepted behaviour rather than a hole. What these still guarantee is that one cannot be
 * <b>found</b>, which is the half the reclamation ladder actually depends on: the failure P2.4-R
 * guards against is a sapling in the first hour, not one bought after a cured villager.
 *
 * <p>The failure mode these are shaped around is <b>silence</b>. A global loot modifier that fails
 * to load - wrong directory, bad codec, missing index entry - throws nothing and simply leaves the
 * game behaving exactly like vanilla. So every test here is written to fail loudly in that case
 * rather than to describe the happy path.
 */
final class SaplingLockoutTests {

    private static final BlockPos TARGET = new BlockPos(2, 2, 2);

    private SaplingLockoutTests() {
    }

    static void register() {
        // The load-bearing test. A sapling block drops itself DETERMINISTICALLY in vanilla, so
        // this can only pass when the modifier is actually live - which makes it the guard on the
        // whole silent-failure class, not just on this one drop.
        RCGameTests.test("saplings_never_drop_from_a_broken_sapling", 20, helper -> {
            helper.setBlock(TARGET, Blocks.OAK_SAPLING);
            // helper.destroyBlock passes dropBlock=false and would assert nothing at all here.
            helper.getLevel().destroyBlock(helper.absolutePos(TARGET), true);

            helper.assertItemEntityNotPresent(Items.OAK_SAPLING);
            helper.succeed();
        });

        // The path a player would actually take: chop a tree, farm the leaves. Vanilla drops a
        // sapling from leaves 5% of the time, so 200 breaks without the modifier would yield ~10.
        // A false pass here is 0.95^200, roughly 3 in 100,000.
        RCGameTests.test("saplings_never_drop_from_leaves", 100, helper -> {
            for (int i = 0; i < 200; i++) {
                helper.setBlock(TARGET, Blocks.OAK_LEAVES);
                helper.getLevel().destroyBlock(helper.absolutePos(TARGET), true);
            }

            helper.assertItemEntityNotPresent(Items.OAK_SAPLING);
            helper.succeed();
        });

        // A filter this broad is dangerous in the other direction: an over-matching predicate would
        // quietly delete loot pack-wide, and nobody would notice until items stopped appearing.
        // Prove the modifier removes saplings and nothing else.
        RCGameTests.test("sapling_lockout_does_not_strip_other_drops", 20, helper -> {
            helper.setBlock(TARGET, RCBlocks.GARBAGE_BLOCK.get());
            helper.getLevel().destroyBlock(helper.absolutePos(TARGET), true);

            helper.assertItemEntityPresent(RCBlocks.GARBAGE_BLOCK.get().asItem(), TARGET, 2.0D);
            helper.succeed();
        });
    }
}
