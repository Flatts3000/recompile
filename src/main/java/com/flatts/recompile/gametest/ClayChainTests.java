package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;

/**
 * The clay chain (#115): sherds to grog to a dry body to clay.
 *
 * <p>Clay is <b>recycled out of the dump</b> rather than dug, which is the whole point - this world has
 * no clay in the ground and 43 vanilla items behind it, the largest single gap in the game.
 *
 * <p><b>The chemistry is what shapes the chain, and it is why the obvious version is wrong.</b> Firing
 * is irreversible: above roughly 550-600 C kaolinite dehydroxylates and the bound hydroxyls leave the
 * lattice for good, so a crushed sherd cannot be rehydrated into clay. Grog is a non-plastic temper and
 * its real job is the opposite of what a clay body needs. The plasticity has to come from an actual clay
 * mineral, which is what the bentonite in cat litter is - so the two halves are useless apart and
 * correct together.
 */
final class ClayChainTests {

    private ClayChainTests() {
    }

    static void register() {
        // THE CHAIN CONNECTS, stage by stage, by id.
        //
        // Each link is asserted against the LIVE recipe manager rather than against the files, because
        // what matters is that one stage's output is the next stage's input - a chain whose halves both
        // exist and do not meet is the failure a per-recipe test cannot see.
        RCGameTests.test("the_clay_chain_runs_from_sherds_to_a_dry_body", 20, helper -> {
            // STAGE ONE: any sherd crushes to grog. Asserted through the TAG, so a sherd added by a
            // later version or a pack is covered - and if the tag were ever emptied this fails rather
            // than passing against nothing.
            List<Item> sherds = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (new ItemStack(item).is(ItemTags.DECORATED_POT_SHERDS)) {
                    sherds.add(item);
                }
            }
            helper.assertTrue(sherds.size() >= 20,
                "only " + sherds.size() + " pottery sherds found in #minecraft:decorated_pot_sherds - "
                    + "discovery is broken, so this would pass by checking nothing");

            var pulverizing = helper.getLevel().recipeAccess().recipeMap()
                .byType(com.flatts.recompile.registry.RCRecipeTypes.PULVERIZING.get());
            List<String> uncrushable = new ArrayList<>();
            for (Item sherd : sherds) {
                boolean covered = false;
                for (var holder : pulverizing) {
                    if (holder.value().input().test(new ItemStack(sherd))
                            && holder.value().result().toStack().is(RCItems.GROG.get())) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    uncrushable.add(BuiltInRegistries.ITEM.getKey(sherd).toString());
                }
            }
            helper.assertTrue(uncrushable.isEmpty(),
                "these sherds do not crush into Grog, so part of the vanilla set stays the dead item it "
                    + "was before this chain existed: " + uncrushable);

            // STAGE TWO: grog plus bentonite makes the dry body. Three grog and one litter, in a grid.
            var input = CraftingInput.of(2, 2, List.of(
                new ItemStack(RCItems.GROG.get()), new ItemStack(RCItems.GROG.get()),
                new ItemStack(RCItems.GROG.get()), new ItemStack(RCItems.KITTY_LITTER.get())));
            var crafted = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
            helper.assertTrue(crafted.isPresent(),
                "grog plus kitty litter must craft something - the dry blend is the step that makes the "
                    + "cauldron possible, because three inputs will not fit in a cauldron interaction");
            helper.assertTrue(crafted.get().value().assemble(input).is(RCItems.DRY_CLAY_BODY.get()),
                "the blend must make a Dry Clay Body");
            helper.succeed();
        });

        // STAGE THREE, IN THE WORLD: a water cauldron turns the dry body into clay and loses a level.
        //
        // Driven through the real interaction rather than a recipe lookup, because there is no recipe -
        // it is a CauldronInteraction, registered in Java, and the only thing that proves it is wired is
        // using it. The water cost is asserted too: a chain that drained nothing would be quietly free,
        // and water is the scarce input of the whole P1.10 economy.
        RCGameTests.test("a_water_cauldron_hydrates_the_dry_body_into_clay", 40, helper -> {
            BlockPos cauldron = new BlockPos(1, 1, 1);
            helper.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));

            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(RCItems.DRY_CLAY_BODY.get(), 1));

            BlockPos abs = helper.absolutePos(cauldron);
            var state = helper.getLevel().getBlockState(abs);
            var result = state.useItemOn(
                player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND),
                helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(abs),
                    net.minecraft.core.Direction.UP, abs, false));
            helper.assertTrue(result.consumesAction(),
                "using a Dry Clay Body on a water cauldron did nothing - the interaction is not "
                    + "registered, and nothing else in the game turns the blend into clay");

            helper.assertTrue(player.getInventory().contains(new ItemStack(Items.CLAY_BALL)),
                "hydrating must yield a clay ball");
            var after = helper.getLevel().getBlockState(abs);
            helper.assertTrue(after.is(Blocks.WATER_CAULDRON)
                    && after.getValue(LayeredCauldronBlock.LEVEL) == 2,
                "hydrating must cost a level of water, got " + after);
            helper.succeed();
        });

        // NO FREE LOOP. Sherds make clay; clay makes pots; pots are crafted FROM sherds. If a pot could
        // be ground back into more clay than it cost, the chain would print material.
        //
        // The bentonite is what closes it - it is consumed every time and has no route back - but that
        // is an argument, and this is the assertion. It checks the shape rather than the arithmetic:
        // nothing in the mill turns a decorated pot, terracotta or bricks back into grog, so there is no
        // cycle to run at all.
        RCGameTests.test("no_fired_clay_grinds_back_into_the_chain", 20, helper -> {
            List<String> loops = new ArrayList<>();
            for (Item item : List.of(Items.DECORATED_POT, Items.TERRACOTTA, Items.BRICKS,
                    Items.BRICK, Items.CLAY, Items.CLAY_BALL, Items.FLOWER_POT)) {
                for (var holder : helper.getLevel().recipeAccess().recipeMap()
                        .byType(com.flatts.recompile.registry.RCRecipeTypes.PULVERIZING.get())) {
                    if (holder.value().input().test(new ItemStack(item))) {
                        loops.add(BuiltInRegistries.ITEM.getKey(item)
                            + " -> " + holder.value().result().toStack());
                    }
                }
            }
            helper.assertTrue(loops.isEmpty(),
                "these grind back into the clay chain, which makes the whole thing a loop that prints "
                    + "material: " + loops);
            helper.succeed();
        });
    }
}
