package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ancient Sculk (#266): the only deep dark in this world.
 *
 * <p>This world places no deep dark biome and therefore no ancient city, so nine vanilla items had no
 * source at all. One block in the compacted depths, broken with a diamond sledgehammer or better,
 * yields sculk powder, and the powder crafts the family.
 */
final class AncientSculkTests {

    private AncientSculkTests() {
    }

    static void register() {

        // THE TIER GATE, all four hammers, and the reason all four are checked rather than the one the
        // ruling names: this is the first block in the mod to gate on tool TIER rather than tool TYPE,
        // so the interesting failure is not "diamond does not work" but "iron does".
        //
        // The gate is entirely vanilla tags - #recompile:mineable/sledgehammer for the type and
        // #minecraft:needs_diamond_tool for the tier - and it hinges on a detail that is easy to
        // change by accident: RCItems.COPPER_TIER is built on INCORRECT_FOR_STONE_TOOL, and vanilla's
        // incorrect-for-stone and incorrect-for-iron tags both contain needs-diamond. Retier the copper
        // sledgehammer and this silently opens.
        RCGameTests.test("ancient_sculk_needs_a_diamond_sledgehammer_or_better", 20, helper -> {
            BlockState state = RCBlocks.ANCIENT_SCULK.get().defaultBlockState();
            helper.assertTrue(state.requiresCorrectToolForDrops(),
                "Ancient Sculk must require the correct tool, or every gate below is decoration");

            for (var good : List.of(RCItems.DIAMOND_SLEDGEHAMMER, RCItems.NETHERITE_SLEDGEHAMMER)) {
                helper.assertTrue(new ItemStack(good.get()).isCorrectToolForDrops(state),
                    BuiltInRegistries.ITEM.getKey(good.get()) + " must break Ancient Sculk for drops");
            }
            for (var weak : List.of(RCItems.COPPER_SLEDGEHAMMER, RCItems.IRON_SLEDGEHAMMER)) {
                helper.assertFalse(new ItemStack(weak.get()).isCorrectToolForDrops(state),
                    BuiltInRegistries.ITEM.getKey(weak.get()) + " must NOT break Ancient Sculk for "
                        + "drops - the ruling was diamond or better");
            }
            // And no other tool type at all, however good it is.
            helper.assertFalse(new ItemStack(Items.NETHERITE_PICKAXE).isCorrectToolForDrops(state),
                "a netherite pickaxe must not work: the type gate is a sledgehammer, and tier is only "
                    + "the second half of it");
            helper.assertFalse(ItemStack.EMPTY.isCorrectToolForDrops(state),
                "a bare hand must not work");
            helper.succeed();
        });

        // IT DROPS POWDER AND NOT ITSELF. Handing the block back would let a player carry the deep dark
        // around as a building block, and the whole design is that it becomes powder first.
        RCGameTests.test("ancient_sculk_breaks_into_sculk_powder", 40, helper -> {
            BlockPos pos = new BlockPos(1, 2, 1);
            helper.setBlock(pos, RCBlocks.ANCIENT_SCULK.get());
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(RCItems.DIAMOND_SLEDGEHAMMER.get()));
            helper.getLevel().destroyBlock(helper.absolutePos(pos), true, player);
            helper.succeedWhen(() -> {
                helper.assertItemEntityPresent(RCItems.SCULK_POWDER.get());
                helper.assertItemEntityNotPresent(RCItems.ANCIENT_SCULK.get());
            });
        });

        // THE FAMILY THE POWDER OPENS, and the two the owner ruled OUT, in one sweep - because those
        // are the same decision seen from either side and splitting them lets one drift.
        RCGameTests.test("sculk_powder_opens_the_deep_dark_and_only_the_deep_dark", 20, helper -> {
            List<String> missing = new ArrayList<>();
            for (Item want : List.of(Items.SCULK, Items.SCULK_VEIN, Items.SCULK_SENSOR,
                    Items.SCULK_SHRIEKER, Items.SCULK_CATALYST)) {
                if (!craftedFrom(helper, want, RCItems.SCULK_POWDER.get())) {
                    missing.add(BuiltInRegistries.ITEM.getKey(want).toString());
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "these have no recipe consuming sculk powder, so Ancient Sculk opens less than #266 "
                    + "says it does: " + missing);

            // The calibrated sensor is vanilla's own recipe off the sensor plus amethyst, and amethyst
            // comes off the Separator - so it follows for free and is worth asserting, because "it
            // follows" is exactly the kind of claim that stops being true quietly.
            helper.assertTrue(hasRecipeFor(helper, Items.CALIBRATED_SCULK_SENSOR),
                "nothing makes a calibrated sculk sensor, so the family is one short");

            // AND THE RULING THAT SAYS NO (owner, 2026-08-20). These have no chain in vanilla at all,
            // so a route here would be invention rather than recovery. Recorded in
            // ../trashlands/docs/material_economy.md under the deliberately-absent register.
            //
            // Asserted on the two that have no vanilla recipe at all. MUSIC_DISC_5 is deliberately not
            // in this list: vanilla ships a recipe for it - nine fragments to a disc - so "nothing
            // crafts it" is simply false, and asserting that would fail against a world where it is
            // still perfectly unreachable. What keeps the disc out is that its INGREDIENT has no
            // source, which is the fragment below. Recipe existence is not reachability, and this test
            // said otherwise until it was run.
            List<String> shouldNot = new ArrayList<>();
            for (Item out : List.of(Items.REINFORCED_DEEPSLATE, Items.DISC_FRAGMENT_5)) {
                if (hasRecipeFor(helper, out)) {
                    shouldNot.add(BuiltInRegistries.ITEM.getKey(out).toString());
                }
            }
            helper.assertTrue(shouldNot.isEmpty(),
                "these are deliberately absent from this world and something now crafts them: "
                    + shouldNot + ". If that is a new decision it belongs in the register in "
                    + "material_economy.md before it belongs in a recipe file.");

            // The disc follows its fragment out, and that is worth pinning rather than assuming: if a
            // fragment ever gains a source, this stops being true silently and the register in
            // material_economy.md goes stale with it.
            helper.assertFalse(craftedFrom(helper, Items.MUSIC_DISC_5, Items.DISC_FRAGMENT_5)
                    && hasRecipeFor(helper, Items.DISC_FRAGMENT_5),
                "Music Disc 5 is reachable: something now produces disc fragments, so the disc follows "
                    + "and both are out of step with the deliberately-absent register");
            helper.succeed();
        });

        // THE VEIN GENERATES, and it targets the block this dimension is actually made of.
        //
        // The second half is the one that fails silently: an ore feature names the block it converts,
        // and naming one the compacted depths do not contain - netherrack, the obvious guess for a
        // Nether biome, of which this dimension generates none - places nothing, throws nothing and
        // logs nothing.
        RCGameTests.test("ancient_sculk_generates_in_the_depths", 20, helper -> {
            var biome = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(
                    Recompile.MOD_ID, "compacted_depths"))).value();

            boolean listed = false;
            for (var step : biome.getGenerationSettings().features()) {
                for (var holder : step) {
                    if (holder.unwrapKey().map(k -> k.identifier().getPath())
                            .orElse("").equals("ancient_sculk_vein")) {
                        listed = true;
                    }
                }
            }
            helper.assertTrue(listed,
                "ancient_sculk_vein is not in the compacted depths' feature list, so it generates "
                    + "nowhere at all and the block is creative-only");

            String json = readResource(
                "/data/recompile/worldgen/configured_feature/ancient_sculk_vein.json");
            helper.assertTrue(json != null, "cannot read ancient_sculk_vein.json off the classpath");
            helper.assertTrue(json.contains("recompile:techno_organic_waste"),
                "the vein does not target recompile:techno_organic_waste. The compacted depths are "
                    + "made of that and nothing else, so a feature aimed at any other block replaces "
                    + "nothing, silently.");
            helper.assertTrue(json.contains("recompile:ancient_sculk"),
                "the vein does not place ancient sculk");
            helper.succeed();
        });
    }

    /** Whether any recipe produces {@code result} while consuming {@code input}. */
    private static boolean craftedFrom(net.minecraft.gametest.framework.GameTestHelper helper,
            Item result, Item input) {
        for (RecipeHolder<?> holder : helper.getLevel().recipeAccess().recipeMap().values()) {
            boolean makes = false;
            for (var display : holder.value().display()) {
                if (RecipeResults.produces(display.result(), result)) {
                    makes = true;
                }
            }
            if (!makes) {
                continue;
            }
            for (var ingredient : holder.value().placementInfo().ingredients()) {
                if (ingredient.test(new ItemStack(input))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether anything at all produces an item. */
    private static boolean hasRecipeFor(net.minecraft.gametest.framework.GameTestHelper helper,
            Item result) {
        for (RecipeHolder<?> holder : helper.getLevel().recipeAccess().recipeMap().values()) {
            for (var display : holder.value().display()) {
                if (RecipeResults.produces(display.result(), result)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One bundled JSON as text, or null. Classpath, because a dev run has data/ on it. */
    private static String readResource(String path) {
        try (java.io.InputStream in = AncientSculkTests.class.getResourceAsStream(path)) {
            return in == null ? null
                : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failed) {
            return null;
        }
    }
}
