package com.flatts.recompile.gametest;

import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.event.RCCauldronInteractions;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Dried Bouquet route (#331, #335): a household find that rehydrates in a water cauldron into one
 * of the two-block plants nothing else here provides, or pulls apart for fibre.
 *
 * <p>Two of these assert the ROUTE rather than the mechanism. The mechanism is the clay chain's
 * cauldron interaction with a table behind it, and {@code ClayChainTests} already proves that shape
 * works; what was actually broken was that four items sat in {@code #recompile:compostable} with no
 * source in the world, and a mechanism test would pass with the table pointing at dandelions.
 */
public final class DriedBouquetTests {

    private DriedBouquetTests() {
    }

    /** The bouquet's whole point: every member of its table is a two-block plant. */
    private static Set<Item> bouquetPool() {
        return SortingData.outputs(SortingData.BOUQUET).stream()
            .map(w -> w.stack().getItem())
            .collect(Collectors.toSet());
    }

    public static void register() {
        // IN THE WORLD: a water cauldron turns the bouquet into a plant from its table and loses a
        // level. Driven through the real interaction, since there is no recipe to look up - and the
        // product is checked against the table rather than against one flower, because which one
        // comes out is the roll and not the mechanism.
        RCGameTests.test("a_water_cauldron_rehydrates_a_bouquet_into_a_tall_plant", 40, helper -> {
            BlockPos cauldron = new BlockPos(1, 1, 1);
            helper.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));

            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.DRIED_BOUQUET.get(), 1));

            BlockPos abs = helper.absolutePos(cauldron);
            var state = helper.getLevel().getBlockState(abs);
            var result = state.useItemOn(player.getItemInHand(InteractionHand.MAIN_HAND),
                helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            helper.assertTrue(result.consumesAction(),
                "using a Dried Bouquet on a water cauldron did nothing - the interaction is not "
                    + "registered, and nothing else in the game turns it into a plant");

            Set<Item> pool = bouquetPool();
            List<Item> got = new ArrayList<>();
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (!stack.isEmpty() && pool.contains(stack.getItem())) {
                    got.add(stack.getItem());
                }
            }
            helper.assertTrue(got.size() == 1,
                "rehydrating must yield exactly one plant from the bouquet table, got " + got);
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "the bouquet must be consumed");
            var after = helper.getLevel().getBlockState(abs);
            helper.assertTrue(after.is(Blocks.WATER_CAULDRON)
                    && after.getValue(LayeredCauldronBlock.LEVEL) == 2,
                "rehydrating must cost a level of water, got " + after);
            helper.succeed();
        });

        // THE TABLE IS THE GAP AND NOTHING ELSE. Every entry is a two-block plant (the shape of what
        // the trader never sells), and the four the compostable tag named without a source are all in
        // it - that tag entry was the concrete defect #331 filed, so this is where it is pinned.
        RCGameTests.test("the_bouquet_table_is_exactly_the_missing_tall_plants", 20, helper -> {
            Set<Item> pool = bouquetPool();
            helper.assertTrue(pool.size() >= 5,
                "the bouquet table should hold the four tall flowers and the large fern, got " + pool);

            List<Item> notTall = new ArrayList<>();
            for (Item item : pool) {
                Block block = Block.byItem(item);
                if (!(block instanceof DoublePlantBlock)) {
                    notTall.add(item);
                }
            }
            helper.assertTrue(notTall.isEmpty(),
                "the bouquet exists to source TWO-BLOCK plants, which nothing else here can; these "
                    + "entries are something else: " + notTall);

            List<Item> missing = new ArrayList<>();
            for (Item flower : List.of(Items.SUNFLOWER, Items.LILAC, Items.PEONY, Items.ROSE_BUSH,
                    Items.LARGE_FERN)) {
                if (!pool.contains(flower)) {
                    missing.add(flower);
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "#recompile:compostable names these and nothing else sources them: " + missing);
            helper.succeed();
        });

        // Both halves of the source are declared in data, so both are asserted from data: the
        // bouquet is a household pull, and the bouquet composts (it is dead plant matter, and the tag
        // that names its products should name it).
        RCGameTests.test("a_dried_bouquet_is_a_household_find_and_composts", 20, helper -> {
            boolean found = SortingData.outputs(SortingData.HOUSEHOLD).stream()
                .anyMatch(w -> w.stack().is(RCItems.DRIED_BOUQUET.get()));
            helper.assertTrue(found,
                "the Dried Bouquet must be a household_pulls entry, or the whole route has no start");
            helper.assertTrue(new ItemStack(RCItems.DRIED_BOUQUET.get()).is(RCTags.COMPOSTABLE),
                "a Dried Bouquet is dead plant matter and should be in #recompile:compostable");
            helper.assertTrue(helper.getLevel().getServer().reloadableRegistries()
                    .getLootTable(RCCauldronInteractions.BOUQUET_TABLE) != LootTable.EMPTY,
                "the bouquet table did not load - the interaction would refuse every bouquet");
            helper.succeed();
        });

        // THE OTHER EXIT: a bouquet a player does not want to gamble on pulls apart for fibre, and it
        // must not hand a flower back that way or the cauldron is pointless.
        RCGameTests.test("a_dried_bouquet_tears_down_into_fibre_and_never_a_flower", 20, helper -> {
            RecipeHolder<TeardownRecipe> bouquet = null;
            for (RecipeHolder<TeardownRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.TEARDOWN.get())) {
                if (holder.value().input().test(new ItemStack(RCItems.DRIED_BOUQUET.get()))) {
                    bouquet = holder;
                }
            }
            helper.assertTrue(bouquet != null, "no teardown recipe accepts a Dried Bouquet");

            Set<Item> pool = bouquetPool();
            List<Item> outputs = new ArrayList<>();
            bouquet.value().results().forEach(r -> outputs.add(r.item()));
            bouquet.value().pools().forEach(p -> p.entries().forEach(e -> e.item().ifPresent(outputs::add)));
            bouquet.value().extras().forEach(e -> outputs.add(e.item()));
            helper.assertTrue(outputs.contains(RCItems.FIBER_SCRAP.get()),
                "tearing a bouquet down must yield Fiber Scrap, got " + outputs);
            List<Item> flowers = outputs.stream().filter(pool::contains).toList();
            helper.assertTrue(flowers.isEmpty(),
                "teardown must not be a second route to the plants, got " + flowers);
            helper.assertTrue(bouquet.value().tool().isEmpty(),
                "a bouquet comes apart by hand; a tool gate here is theatre");
            helper.succeed();
        });
    }
}
