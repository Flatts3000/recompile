package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * GameTests for the Cupola Furnace (#50): the machine that makes iron reachable, and the machine that
 * finally automates. Both are things the Burn Barrel deliberately withholds, so both are asserted here -
 * and the automation test is the exact inverse of {@code burn_barrel_blocks_a_hopper}.
 */
final class CupolaFurnaceTests {

    private CupolaFurnaceTests() {
    }

    private static CupolaFurnaceBlockEntity place(net.minecraft.gametest.framework.GameTestHelper helper,
            BlockPos pos) {
        helper.setBlock(pos, RCBlocks.CUPOLA_FURNACE.get());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof CupolaFurnaceBlockEntity cupola) {
            return cupola;
        }
        helper.fail("the cupola furnace has no BlockEntity");
        return null;
    }

    static void register() {
        // The payoff: a Steel Offcut cut off a beam becomes iron here, and ONLY here. Nothing else in
        // this world runs BLASTING - a vanilla blast furnace costs 5 iron ingots, which is what this gates.
        RCGameTests.test("cupola_remelts_offcuts_into_iron", 250, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(1, 1, 1));
            if (cupola == null) {
                return;
            }
            cupola.setItem(0, new ItemStack(RCItems.STEEL_OFFCUT.get()));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.succeedWhen(() ->
                helper.assertTrue(cupola.getItem(2).is(Items.IRON_INGOT),
                    "the cupola must remelt a steel offcut into iron, output was " + cupola.getItem(2)));
        });

        // Rebar is the trickle: a nugget, not an ingot, so nine rebar per ingot.
        RCGameTests.test("cupola_remelts_rebar_into_a_nugget", 250, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(3, 1, 1));
            if (cupola == null) {
                return;
            }
            cupola.setItem(0, new ItemStack(RCItems.REBAR.get()));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.succeedWhen(() ->
                helper.assertTrue(cupola.getItem(2).is(Items.IRON_NUGGET),
                    "rebar must yield an iron NUGGET, not an ingot - output was " + cupola.getItem(2)));
        });

        // A TRUE upgrade: it also does everything the Burn Barrel does, so you stop needing the barrel
        // rather than keeping both for different jobs. Cooking food is the barrel's other job.
        RCGameTests.test("cupola_does_everything_the_barrel_does", 250, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(3, 1, 3));
            if (cupola == null) {
                return;
            }
            cupola.setItem(0, new ItemStack(Items.BEEF));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.succeedWhen(() ->
                helper.assertTrue(cupola.getItem(2).is(Items.COOKED_BEEF),
                    "the cupola must cook food too, output was " + cupola.getItem(2)));
        });

        // ...and it is NOT restricted the way the barrel is. The barrel refuses ore, sand, stone and logs;
        // this one is an ordinary furnace, which is the whole point of upgrading.
        RCGameTests.test("cupola_is_not_restricted_like_the_barrel", 250, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(5, 1, 1));
            if (cupola == null) {
                return;
            }
            cupola.setItem(0, new ItemStack(Items.SAND));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.succeedWhen(() ->
                helper.assertTrue(cupola.getItem(2).is(Items.GLASS),
                    "the cupola must smelt what the barrel refuses, output was " + cupola.getItem(2)));
        });

        // The other half of the upgrade: this one automates. The Burn Barrel exposes no slots to any face
        // on purpose and a hopper under it pulls nothing; a hopper under this one must pull, or the
        // machine tier is only a metal tier.
        RCGameTests.test("cupola_allows_a_hopper", 60, helper -> {
            BlockPos pos = new BlockPos(1, 2, 3);
            CupolaFurnaceBlockEntity cupola = place(helper, pos);
            if (cupola == null) {
                return;
            }
            helper.setBlock(pos.below(), Blocks.HOPPER);
            cupola.setItem(2, new ItemStack(Items.IRON_INGOT));
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(cupola.getItem(2).isEmpty(),
                    "a hopper must pull from the cupola - automation is the upgrade's other reward, got "
                        + cupola.getItem(2));
                helper.succeed();
            });
        });
    }
}
