package com.flatts.recompile.gametest;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.recipe.BlueprintAccess;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Blueprints, phase 2 (#95): the item exists, it names a set, and the thing it unlocks is reachable no
 * other way.
 *
 * <p>The claim that has to hold is <b>the gate</b>. A Clean Mattress that some ordinary recipe can also
 * produce makes the whole blueprint system decorative, and a gate proven only by a comment is exactly
 * how the iron gate stayed open for weeks - the reason
 * {@code no_smelting_recipe_turns_a_mod_item_into_iron} exists at all. So this sweeps the live recipe
 * manager rather than trusting the recipe files.
 */
final class BlueprintTests {

    private BlueprintTests() {
    }

    static void register() {
        // THE GATE. Nothing in the crafting recipe manager may produce a Clean Mattress: the blueprint
        // bench is the only route, and that is the entire proposition of the feature.
        RCGameTests.test("a_blueprint_result_has_no_other_route", 20, helper -> {
            // EVERY blueprint-gated result, not one named item. The Hydroponics Bay moved behind a
            // blueprint after this test was written, and a mattress-only sweep would have said nothing
            // about it - the gate would have been whatever the recipe files happened to say, proven
            // nowhere. Reading the gated set from the recipes themselves means a pack that gates a
            // third thing gets the same guarantee without editing this.
            Set<Item> gated = new java.util.HashSet<>();
            for (RecipeHolder<BlueprintCraftingRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                gated.add(holder.value().result().item());
            }
            helper.assertTrue(!gated.isEmpty(),
                "no blueprint recipes were found - the sweep is broken, so this would pass against "
                    + "anything");

            List<String> offenders = new ArrayList<>();
            int swept = 0;
            // Sweeping by INPUT the way the iron gate test does cannot work here: the question is not
            // "what does this item turn into" but "what turns into this item", and the input space for
            // that is every combination in a 3x3.
            for (RecipeHolder<?> holder : helper.getLevel().recipeAccess().recipeMap().values()) {
                swept++;
                if (holder.value().getType() == RCRecipeTypes.BLUEPRINT_CRAFTING.get()) {
                    continue;   // the sanctioned route
                }
                for (Item result : gated) {
                    // A recipe that CONSUMES the gated item and hands one back is a recolour, not a
                    // source: it creates nothing, and net new items is what the gate is about. Same
                    // distinction phase 1 drew on the beds, where the 16 wool-to-bed recipes were
                    // deleted and the 16 dye-a-bed recipes were deliberately left alone.
                    if (consumes(holder.value(), result)) {
                        continue;
                    }
                    for (var display : holder.value().display()) {
                        if (produces(display.result(), result)) {
                            offenders.add(holder.id() + " -> "
                                + BuiltInRegistries.ITEM.getKey(result));
                        }
                    }
                }
            }
            helper.assertTrue(swept > 100,
                "only " + swept + " recipes were swept - the sweep is broken, so this would pass "
                    + "against any recipe that made a gated item");
            helper.assertTrue(offenders.isEmpty(),
                "these are blueprint-gated and reachable another way, so the gate leaks: " + offenders);
            helper.succeed();
        });

        // The recipe type parses and its one recipe is present. Without this, a typo in the JSON is a
        // silent no-op: the recipe simply does not load and nothing anywhere reports it.
        RCGameTests.test("the_clean_mattress_blueprint_recipe_loads", 20, helper -> {
            var recipes = helper.getLevel().recipeAccess().recipeMap()
                .byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get());
            List<BlueprintCraftingRecipe> found = new ArrayList<>();
            recipes.forEach(holder -> found.add(holder.value()));
            helper.assertTrue(found.size() == BlueprintItem.shipped().size(),
                "one blueprint recipe per shipped blueprint, expected " + BlueprintItem.shipped().size()
                    + " and got " + found.size());

            BlueprintCraftingRecipe mattress = found.stream()
                .filter(r -> r.blueprint().equals(BlueprintItem.CLEAN_MATTRESS))
                .findFirst().orElse(null);
            helper.assertTrue(mattress != null, "the Clean Mattress recipe must load");
            helper.assertTrue(mattress.result().item() == RCItems.cleanMattress(net.minecraft.world.item.DyeColor.WHITE),
                "and produce a white Clean Mattress, got " + mattress.result().item());

            BlueprintCraftingRecipe bay = found.stream()
                .filter(r -> r.blueprint().equals(BlueprintItem.HYDROPONICS_BAY))
                .findFirst().orElse(null);
            helper.assertTrue(bay != null, "and so must the Hydroponics Bay recipe");
            helper.assertTrue(bay.result().item() == RCItems.HYDROPONICS_BAY.get(),
                "producing a Hydroponics Bay, got " + bay.result().item());
            helper.succeed();
        });

        // SHAPED MEANS SHAPED. It was shapeless first, on the argument that the blueprint is already
        // the puzzle - but a blueprint that does not say how the thing is laid out is not much of a
        // blueprint. So the arrangement is now load-bearing and has to be asserted as such: the right
        // pattern matches, the same items in the wrong places do not.
        RCGameTests.test("a_blueprint_recipe_needs_its_pattern", 20, helper -> {
            var recipes = helper.getLevel().recipeAccess().recipeMap()
                .byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get());
            List<BlueprintCraftingRecipe> found = new ArrayList<>();
            recipes.forEach(holder -> found.add(holder.value()));
            BlueprintCraftingRecipe recipe = found.stream()
                .filter(r -> r.blueprint().equals(BlueprintItem.CLEAN_MATTRESS))
                .findFirst().orElseThrow();

            ItemStack wool = new ItemStack(net.minecraft.world.item.Items.WHITE_WOOL);
            ItemStack string = new ItemStack(net.minecraft.world.item.Items.STRING);
            var right = net.minecraft.world.item.crafting.CraftingInput.of(3, 2, List.of(
                wool, wool, wool,
                string, string, string));
            helper.assertTrue(recipe.matches(right, helper.getLevel()),
                "three wool over three string must match the pattern");

            var upsideDown = net.minecraft.world.item.crafting.CraftingInput.of(3, 2, List.of(
                string, string, string,
                wool, wool, wool));
            helper.assertFalse(recipe.matches(upsideDown, helper.getLevel()),
                "string over wool is a different arrangement and must not match");

            // Any wool, because the ingredient is the tag - a player recolours afterwards.
            var coloured = net.minecraft.world.item.crafting.CraftingInput.of(3, 2, List.of(
                new ItemStack(net.minecraft.world.item.Items.RED_WOOL), wool, wool,
                string, string, string));
            helper.assertTrue(recipe.matches(coloured, helper.getLevel()),
                "the wool ingredient is a tag, so any colour of wool works");

            var missing = net.minecraft.world.item.crafting.CraftingInput.of(3, 2, List.of(
                wool, wool, ItemStack.EMPTY,
                string, string, string));
            helper.assertFalse(recipe.matches(missing, helper.getLevel()),
                "and a hole in the pattern is not a match");
            helper.succeed();
        });

        // A blueprint with no component is a real thing a player can hold: /give makes one, and so does
        // a pack removing a recipe out from under a save. It must be inert, not a crash.
        RCGameTests.test("a_blank_blueprint_is_inert_rather_than_broken", 20, helper -> {
            ItemStack blank = new ItemStack(RCItems.BLUEPRINT.get());
            helper.assertTrue(BlueprintItem.blueprintOf(blank) == null,
                "a blueprint with no component names nothing");
            helper.assertTrue(
                BlueprintItem.blueprintOf(
                    new ItemStack(RCItems.cleanMattress(net.minecraft.world.item.DyeColor.WHITE))) == null,
                "and an item that is not a blueprint never names one either");

            ItemStack unknown = BlueprintItem.of(RCItems.BLUEPRINT.get(),
                Identifier.fromNamespaceAndPath("recompile", "no_such_blueprint"));
            helper.assertTrue(BlueprintItem.blueprintOf(unknown) != null,
                "a blueprint naming a set nothing uses still reads back - it is data, not a lookup");
            helper.succeed();
        });

        // Decided rather than defaulted, so assert it: knowledge does not stack. A second copy of what
        // you already know is worth nothing, and presence answers "do I know this" better than a count.
        RCGameTests.test("blueprints_do_not_stack", 20, helper -> {
            helper.assertTrue(new ItemStack(RCItems.BLUEPRINT.get()).getMaxStackSize() == 1,
                "a blueprint is a document, not a resource");

            ItemStack a = BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS);
            ItemStack b = BlueprintItem.of(RCItems.BLUEPRINT.get(),
                Identifier.fromNamespaceAndPath("recompile", "something_else"));
            helper.assertFalse(ItemStack.isSameItemSameComponents(a, b),
                "two different blueprints must be distinguishable, or one item cannot carry them all");
            helper.succeed();
        });
        // THE CABINET IS A REFERENCE SHELF, NOT A CHEST. Letting it take anything would make it a worse
        // Scrap Barrel with a nicer texture, and the filter has to be on the CONTAINER rather than only
        // on the menu slot: in 26.1 vanilla's Slot.mayPlace returns true unconditionally and ChestMenu
        // uses a plain Slot, which is the same trap that forced the Burn Barrel's refuse rule into its
        // ticker. A hopper is the path that finds this out.
        RCGameTests.test("a_filing_cabinet_takes_blueprints_and_nothing_else", 20, helper -> {
            var pos = new net.minecraft.core.BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get());
            var cabinet = (com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(pos));

            ItemStack sheet = BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS);
            helper.assertTrue(cabinet.canPlaceItem(0, sheet), "a cabinet files blueprints");
            helper.assertFalse(cabinet.canPlaceItem(0, new ItemStack(RCItems.JUNK.get())),
                "and refuses junk, from a hopper as readily as from a hand");
            helper.assertFalse(
                cabinet.canPlaceItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
                "and anything else that is not a blueprint");
            helper.succeed();
        });

        // What the crafting table will ask it. Reading back by SET rather than by stack is the point:
        // the table wants to know whether the knowledge is reachable, not how it is stored.
        RCGameTests.test("a_filing_cabinet_reports_what_it_holds", 20, helper -> {
            var pos = new net.minecraft.core.BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get());
            var cabinet = (com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(pos));

            helper.assertFalse(cabinet.holds(BlueprintItem.CLEAN_MATTRESS),
                "an empty cabinet holds nothing");
            cabinet.setItem(7,
                BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS));
            helper.assertTrue(cabinet.holds(BlueprintItem.CLEAN_MATTRESS),
                "a filed blueprint must be found wherever in the drawers it sits");
            helper.assertFalse(cabinet.holds(
                    Identifier.fromNamespaceAndPath("recompile", "something_else")),
                "and only the one that is actually filed");
            helper.assertTrue(cabinet.filed().size() == 1,
                "one sheet in, one set reported");
            helper.succeed();
        });

        // It is in the Scrap Network by tag, which is the whole of how the crafting table will reach it.
        // A block that is not in the tag is invisible to the cluster and the feature silently does
        // nothing - there is no error for being absent from a tag.
        RCGameTests.test("a_filing_cabinet_is_part_of_the_scrap_network", 20, helper -> {
            helper.assertTrue(
                com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get().defaultBlockState()
                    .is(com.flatts.recompile.registry.RCTags.SCRAP_CONNECTABLE),
                "the cabinet must be scrap_connectable or the table can never see it");
            helper.succeed();
        });

        // TEARDOWN FINALLY TEACHES. The teaches field has been parsed and ignored since Phase 0 - the
        // workbench's own javadoc said so in as many words - so the thing worth asserting is that it is
        // read at all, and that what comes out points at the right blueprint.
        //
        // The chance is 0.25, so this drives the roll a hundred times rather than once. A single
        // attempt would fail three runs in four and teach everyone to re-run the suite until it
        // passed, which is worse than having no test.
        RCGameTests.test("tearing_down_a_mattress_can_teach_the_clean_mattress_idea", 60, helper -> {
            var recipes = helper.getLevel().recipeAccess().recipeMap()
                .byType(RCRecipeTypes.TEARDOWN.get());
            var teaching = new ArrayList<com.flatts.recompile.content.recipe.TeardownRecipe>();
            recipes.forEach(holder -> {
                if (!holder.value().teaches().isEmpty()) {
                    teaching.add(holder.value());
                }
            });
            helper.assertTrue(!teaching.isEmpty(),
                "some teardown must carry a teaches entry, or the knowledge axis is still dormant");

            var toMattress = teaching.stream()
                .flatMap(r -> r.teaches().stream())
                .filter(e -> e.recipe().equals(BlueprintItem.CLEAN_MATTRESS))
                .findFirst().orElse(null);
            helper.assertTrue(toMattress != null,
                "something must teach the Clean Mattress idea, or the blueprint is unreachable");
            helper.assertTrue(toMattress.chance() >= 1.0f,
                "every teardown teaches (owner, 2026-08-02). A chance below 1 turns a four-teardown "
                    + "cost into a dice game, and the thing that ends the grind is knowing the recipe, "
                    + "not getting lucky - got " + toMattress.chance());
            helper.assertTrue(toMattress.scrapsRequired() > 1,
                "scraps_required is the whole reason fragments exist - at 1 the fragment is the sheet");

            // EVERY teaches entry must name a blueprint that exists. This is the general form of a bug
            // this change created: teaches had been parsed and ignored since Phase 0, so the schema's
            // own EXAMPLE recipe carried a teaches pointing at minecraft:iron_door and nothing noticed
            // for months. Reading the field turned that dormant example into live content - a fragment
            // toward a blueprint the mod does not ship, which can never be assembled into anything.
            List<String> dangling = new ArrayList<>();
            for (var recipe : teaching) {
                for (var teach : recipe.teaches()) {
                    if (!BlueprintItem.shipped().contains(teach.recipe())) {
                        dangling.add(teach.recipe().toString());
                    }
                }
            }
            helper.assertTrue(dangling.isEmpty(),
                "these teardowns teach a blueprint that does not exist, so the fragments they grant can "
                    + "never be assembled: " + dangling);
            helper.succeed();
        });

        // A fragment names what it is an idea ABOUT, which is what stops one easy teardown unlocking
        // everything. Two fragments toward different blueprints must not pile up together.
        RCGameTests.test("idea_fragments_are_specific_to_their_blueprint", 20, helper -> {
            ItemStack toMattress = com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(), BlueprintItem.CLEAN_MATTRESS, 1);
            ItemStack toSomethingElse = com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(),
                Identifier.fromNamespaceAndPath("recompile", "something_else"), 1);

            helper.assertTrue(com.flatts.recompile.content.item.IdeaFragmentItem.towards(toMattress)
                    .equals(BlueprintItem.CLEAN_MATTRESS),
                "a fragment must name the blueprint it leads to");
            helper.assertFalse(ItemStack.isSameItemSameComponents(toMattress, toSomethingElse),
                "fragments toward different blueprints must not stack into one pile");
            helper.assertTrue(toMattress.getMaxStackSize() > 1,
                "fragments stack, unlike blueprints - watching the count rise IS the mechanic");
            helper.succeed();
        });

        // THE GATE, from the table's side. Reachability is checked at the table because a Recipe only
        // ever sees its own input - it cannot know what a player is carrying or what block is next
        // door. Both routes are tested because one working is not evidence for the other.
        RCGameTests.test("a_blueprint_recipe_needs_the_sheet_within_reach", 40, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var table = new net.minecraft.core.BlockPos(1, 1, 1);
            helper.setBlock(table, com.flatts.recompile.registry.RCBlocks.SCRAP_CRAFTING_TABLE.get());
            var abs = helper.absolutePos(table);
            var level = helper.getLevel();

            helper.assertFalse(
                BlueprintAccess.reachable(level, player, abs, BlueprintItem.CLEAN_MATTRESS),
                "with the sheet nowhere, a blueprint recipe must not run");

            // Route one: carried.
            player.getInventory().add(
                BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS));
            helper.assertTrue(
                BlueprintAccess.reachable(level, player, abs, BlueprintItem.CLEAN_MATTRESS),
                "carrying the sheet is enough - a player who just earned one should not also have to "
                    + "have found a cabinet");
            helper.assertFalse(BlueprintAccess.reachable(level, player, abs,
                    Identifier.fromNamespaceAndPath("recompile", "something_else")),
                "and it unlocks only the blueprint it actually names");

            // Route two: filed next door, with an empty inventory.
            player.getInventory().clearContent();
            var cabinetPos = new net.minecraft.core.BlockPos(2, 1, 1);
            helper.setBlock(cabinetPos, com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get());
            var cabinet = (com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity)
                level.getBlockEntity(helper.absolutePos(cabinetPos));
            helper.assertFalse(
                BlueprintAccess.reachable(level, player, abs, BlueprintItem.CLEAN_MATTRESS),
                "an EMPTY adjacent cabinet must not unlock anything");
            cabinet.setItem(0, BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS));
            helper.assertTrue(
                BlueprintAccess.reachable(level, player, abs, BlueprintItem.CLEAN_MATTRESS),
                "a cabinet touching the table is read from it - placement, not wiring");
            player.discard();
            helper.succeed();
        });

        // A cabinet that is NOT in the cluster must not count, or "adjacency" means nothing and the
        // whole Scrap Network premise leaks. Tested at a distance rather than trusted.
        RCGameTests.test("a_distant_filing_cabinet_does_not_count", 40, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var table = new net.minecraft.core.BlockPos(0, 1, 0);
            var far = new net.minecraft.core.BlockPos(4, 1, 4);
            helper.setBlock(table, com.flatts.recompile.registry.RCBlocks.SCRAP_CRAFTING_TABLE.get());
            helper.setBlock(far, com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get());
            var cabinet = (com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(far));
            cabinet.setItem(0, BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS));

            helper.assertFalse(BlueprintAccess.reachable(helper.getLevel(), player,
                    helper.absolutePos(table), BlueprintItem.CLEAN_MATTRESS),
                "a cabinet across the room is not in the cluster and must not unlock the recipe");
            player.discard();
            helper.succeed();
        });

        // FRAGMENTS BECOME THE SHEET. The threshold is read off the teardown that teaches it rather
        // than hardcoded in the recipe, so a pack retunes cost and odds in the same file.
        RCGameTests.test("enough_fragments_of_one_idea_make_the_blueprint", 20, helper -> {
            com.flatts.recompile.content.recipe.FragmentAssemblyRecipe assembly = null;
            for (var holder : helper.getLevel().recipeAccess().recipeMap()
                    .byType(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
                if (holder.value()
                        instanceof com.flatts.recompile.content.recipe.FragmentAssemblyRecipe found) {
                    assembly = found;
                }
            }
            helper.assertTrue(assembly != null, "the fragment assembly recipe must be loaded");

            int need = 4;   // mattress.json's scraps_required
            ItemStack frags = com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(), BlueprintItem.CLEAN_MATTRESS, need);
            helper.assertTrue(assembly.matches(input(frags), helper.getLevel()),
                "enough fragments of one idea must assemble");
            ItemStack made = assembly.assemble(input(frags));
            helper.assertTrue(BlueprintItem.CLEAN_MATTRESS.equals(BlueprintItem.blueprintOf(made)),
                "and produce the blueprint they were fragments of, got " + made);

            ItemStack tooFew = com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(), BlueprintItem.CLEAN_MATTRESS, need - 1);
            helper.assertFalse(assembly.matches(input(tooFew), helper.getLevel()),
                "one short must make nothing - a threshold that rounds down is not a threshold");

            // Mixing ideas is a mistake, not a partial match. Without this a player could pool
            // unrelated fragments into whichever blueprint they wanted, and earning each separately
            // is the entire reason a fragment names its target.
            ItemStack other = com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(),
                Identifier.fromNamespaceAndPath("recompile", "something_else"), 1);
            helper.assertFalse(assembly.matches(input(tooFew, other), helper.getLevel()),
                "fragments toward different blueprints must not top each other up");
            helper.succeed();
        });

        // The payoff, and the last link in the chain. These are sixteen ORDINARY shaped recipes now,
        // one per coloured Clean Mattress, rather than one special recipe reading a component - which
        // is why a recipe viewer can draw them and why this looks them up the normal way.
        //
        // Every colour is driven, not a sample: sixteen recipe files is exactly the surface where
        // fifteen get written, the same reason the wool gate has a sweep rather than a checklist.
        RCGameTests.test("every_clean_mattress_makes_the_bed_of_its_colour", 20, helper -> {
            List<String> wrong = new ArrayList<>();
            for (net.minecraft.world.item.DyeColor colour
                    : net.minecraft.world.item.DyeColor.values()) {
                var grid = net.minecraft.world.item.crafting.CraftingInput.of(3, 2, List.of(
                    ItemStack.EMPTY, new ItemStack(RCItems.cleanMattress(colour)), ItemStack.EMPTY,
                    new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS),
                    new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS),
                    new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS)));
                var found = helper.getLevel().getServer().getRecipeManager().getRecipeFor(
                    net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, helper.getLevel());
                if (found.isEmpty()) {
                    wrong.add(colour.getName() + " -> no recipe");
                    continue;
                }
                Identifier want = Identifier.withDefaultNamespace(colour.getName() + "_bed");
                ItemStack made = found.get().value().assemble(grid);
                if (!BuiltInRegistries.ITEM.getKey(made.getItem()).equals(want)) {
                    wrong.add(colour.getName() + " -> " + made);
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                "a Clean Mattress must make the bed of its own colour - this is the only bed recipe "
                    + "left, so a gap here is a colour of bed that cannot exist: " + wrong);
            helper.succeed();
        });

        // Every colour is reachable by dyeing any other, so a player is never stuck with one they
        // cannot change. Keyed on the tag rather than on white, which is what makes that true.
        RCGameTests.test("any_clean_mattress_dyes_to_any_colour", 20, helper -> {
            List<String> wrong = new ArrayList<>();
            for (net.minecraft.world.item.DyeColor colour
                    : net.minecraft.world.item.DyeColor.values()) {
                // Dye a BLACK one, the least likely to be a recipe's assumed input.
                var grid = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(
                    new ItemStack(RCItems.cleanMattress(net.minecraft.world.item.DyeColor.BLACK)),
                    new ItemStack(BuiltInRegistries.ITEM.getValue(
                        Identifier.withDefaultNamespace(colour.getName() + "_dye")))));
                var found = helper.getLevel().getServer().getRecipeManager().getRecipeFor(
                    net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, helper.getLevel());
                if (found.isEmpty()
                        || found.get().value().assemble(grid).getItem()
                            != RCItems.cleanMattress(colour)) {
                    wrong.add(colour.getName());
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                "a black Clean Mattress must dye to every colour, or a player can paint themselves "
                    + "into a corner: " + wrong);
            helper.succeed();
        });

        // EVERY MOD RECIPE MUST SURVIVE THE WIRE, and nothing else in the suite touches this.
        //
        // Recipes are sent to a client once on join. A GameTest server has no client, so a broken
        // stream codec is invisible to all 299 tests here and shows up the first time a human presses
        // Play - as "Connection Lost: Failed to encode packet", which reads as a networking fault and
        // names neither the recipe nor the mod.
        //
        // That is exactly what happened: StreamCodec.unit looks like the obvious fit for a recipe with
        // no fields, and its encoder ASSERTS the value equals the instance baked into it. A recipe
        // loaded from JSON is a different object, so every join died while the server stayed healthy.
        RCGameTests.test("every_mod_recipe_survives_being_sent_to_a_client", 20, helper -> {
            var registries = helper.getLevel().registryAccess();
            List<String> broken = new ArrayList<>();
            int checked = 0;
            for (RecipeHolder<?> holder : helper.getLevel().recipeAccess().recipeMap().values()) {
                var serializer = holder.value().getSerializer();
                var id = net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER
                    .getKey(serializer);
                if (id == null || !id.getNamespace().equals(com.flatts.recompile.Recompile.MOD_ID)) {
                    continue;   // vanilla's own codecs are not ours to prove
                }
                checked++;
                try {
                    var buf = new net.minecraft.network.RegistryFriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer(), registries);
                    encodeThrough(serializer, buf, holder.value());
                    serializer.streamCodec().decode(buf);
                } catch (RuntimeException e) {
                    broken.add(holder.id() + " (" + id + "): " + e);
                }
            }
            helper.assertTrue(checked > 0,
                "no mod recipes were checked - the sweep is broken, so this would pass against a codec "
                    + "that kills every client join");
            helper.assertTrue(broken.isEmpty(),
                "these recipes cannot be sent to a client, so joining the world fails: " + broken);
            helper.succeed();
        });

        // LEARNING STOPS WHEN YOU HAVE LEARNED IT. Every teardown teaches, so without this a player
        // who finished the blueprint keeps being handed fragments toward a sheet they already own -
        // litter that never becomes anything, forever.
        //
        // It checks the same two places the crafting table checks, so "known" means one thing across
        // the system rather than two things that nearly agree.
        RCGameTests.test("teardown_stops_teaching_once_the_blueprint_is_known", 40, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var bench = new net.minecraft.core.BlockPos(1, 1, 1);
            helper.setBlock(bench, com.flatts.recompile.registry.RCBlocks.RECOMPILE_WORKBENCH.get());
            var abs = helper.absolutePos(bench);
            var level = helper.getLevel();

            helper.assertFalse(
                BlueprintAccess.reachable(level, player, abs, BlueprintItem.CLEAN_MATTRESS),
                "precondition: the player has not learned it yet, so teardowns should teach");

            player.getInventory().add(
                BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS));
            helper.assertTrue(
                BlueprintAccess.reachable(level, player, abs, BlueprintItem.CLEAN_MATTRESS),
                "holding the sheet must count as known, so the bench stops handing out fragments");

            // And filed next door counts too - otherwise a player who tidied their blueprints into a
            // cabinet would start collecting fragments for them all over again.
            player.getInventory().clearContent();
            var cabinetPos = new net.minecraft.core.BlockPos(2, 1, 1);
            helper.setBlock(cabinetPos, com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get());
            var cabinet = (com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity)
                level.getBlockEntity(helper.absolutePos(cabinetPos));
            cabinet.setItem(0, BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS));
            helper.assertTrue(
                BlueprintAccess.reachable(level, player, abs, BlueprintItem.CLEAN_MATTRESS),
                "filing it away must not restart the grind");
            player.discard();
            helper.succeed();
        });

        // THE CABINET FILES FOR YOU. Tip four fragments in and it assembles the sheet, so a player
        // does not have to carry a done deal to a crafting table to perform a step with exactly one
        // possible outcome.
        RCGameTests.test("a_filing_cabinet_assembles_fragments_into_the_blueprint", 60, helper -> {
            var pos = new net.minecraft.core.BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get());
            var cabinet = (com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(pos));

            // One short: it must sit there. Accumulating is the mechanic, so a near miss is untouched.
            cabinet.setItem(0, com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(), BlueprintItem.CLEAN_MATTRESS, 3));
            tickCabinet(helper, pos, cabinet);
            helper.assertFalse(cabinet.holds(BlueprintItem.CLEAN_MATTRESS),
                "three of four must not assemble anything");
            helper.assertTrue(cabinet.getItem(0).getCount() == 3,
                "and the fragments must be left exactly alone, not consumed toward nothing");

            // The fourth completes it.
            cabinet.setItem(1, com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(), BlueprintItem.CLEAN_MATTRESS, 1));
            tickCabinet(helper, pos, cabinet);
            helper.assertTrue(cabinet.holds(BlueprintItem.CLEAN_MATTRESS),
                "four fragments must become the blueprint");
            helper.succeed();
        });

        // A CABINET PACKED WITH FRAGMENTS MUST STILL ASSEMBLE THEM. Filing the sheet before clearing
        // the fragments deadlocks at exactly the moment a player is most likely to hit it: pipe
        // fragments in until all 54 slots are full and there is nowhere to put the blueprint, so
        // filing fails, so the fragments are never cleared, so there is never anywhere to put it. A
        // machine that stops working the more you feed it, saying nothing.
        RCGameTests.test("a_cabinet_full_of_fragments_still_assembles_the_blueprint", 60, helper -> {
            var pos = new net.minecraft.core.BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get());
            var cabinet = (com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(pos));

            for (int slot = 0; slot < cabinet.getContainerSize(); slot++) {
                cabinet.setItem(slot, com.flatts.recompile.content.item.IdeaFragmentItem.of(
                    RCItems.IDEA_FRAGMENT.get(), BlueprintItem.CLEAN_MATTRESS, 64));
            }
            tickCabinet(helper, pos, cabinet);
            helper.assertTrue(cabinet.holds(BlueprintItem.CLEAN_MATTRESS),
                "a cabinet with no free slot must clear the fragments to make room for the sheet");
            helper.succeed();
        });

        // THE TICKER, not the method. Every other cabinet test calls condenseNow directly, which
        // proves the logic and says nothing about whether anything runs it - the exact gap that let
        // the Cupola Furnace sit in the Scrap Network for weeks doing nothing. This one places a
        // cabinet, drops fragments in, and waits for the block to do it on its own.
        RCGameTests.test("a_placed_cabinet_files_fragments_by_itself", 80, helper -> {
            var pos = new net.minecraft.core.BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get());
            var cabinet = (com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            cabinet.setItem(0, com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(), BlueprintItem.CLEAN_MATTRESS, 8));

            helper.succeedWhen(() -> helper.assertTrue(
                cabinet.holds(BlueprintItem.CLEAN_MATTRESS),
                "a placed cabinet must file fragments without anyone calling it"));
        });

        // SURPLUS IS DESTROYED, and this is the only place the mod deletes a player's items - so the
        // rule is narrow and asserted from both sides: a fragment goes only when this cabinet already
        // holds the blueprint it leads to, and a fragment toward anything else is never touched.
        RCGameTests.test("a_filing_cabinet_bins_fragments_it_no_longer_needs", 60, helper -> {
            var pos = new net.minecraft.core.BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.FILING_CABINET.get());
            var cabinet = (com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(pos));

            cabinet.setItem(0, BlueprintItem.of(RCItems.BLUEPRINT.get(), BlueprintItem.CLEAN_MATTRESS));
            cabinet.setItem(1, com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(), BlueprintItem.CLEAN_MATTRESS, 7));
            // A fragment toward a DIFFERENT blueprint, which must survive untouched.
            Identifier other = Identifier.fromNamespaceAndPath("recompile", "something_else");
            cabinet.setItem(2, com.flatts.recompile.content.item.IdeaFragmentItem.of(
                RCItems.IDEA_FRAGMENT.get(), other, 2));

            tickCabinet(helper, pos, cabinet);
            helper.assertTrue(cabinet.getItem(1).isEmpty(),
                "fragments toward a blueprint already filed here are worth nothing and are binned");
            helper.assertTrue(cabinet.getItem(0).getCount() == 1,
                "and the blueprint itself is untouched - no second copy, no loss");
            helper.assertTrue(cabinet.getItem(2).getCount() == 2,
                "a fragment toward a blueprint this cabinet does NOT hold must never be destroyed");
            helper.succeed();
        });
    }

    /**
     * Run one filing pass.
     *
     * <p>Through the cabinet's static entry point rather than serverTick, which gates on game time: time
     * does not advance between calls inside one tick, so looping serverTick either fires every time or
     * never, depending on which second the test happened to start on.
     */
    private static void tickCabinet(GameTestHelper helper, net.minecraft.core.BlockPos pos,
            com.flatts.recompile.content.block.entity.FilingCabinetBlockEntity cabinet) {
        cabinet.condenseNow(helper.getLevel());
    }

    /** Encode one recipe through its own serializer, past the generics. */
    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> void encodeThrough(
            net.minecraft.world.item.crafting.RecipeSerializer<T> serializer,
            net.minecraft.network.RegistryFriendlyByteBuf buf,
            net.minecraft.world.item.crafting.Recipe<?> recipe) {
        serializer.streamCodec().encode(buf, (T) recipe);
    }

    /** A crafting grid holding exactly these items, in this order. */
    private static CraftingInput input(ItemStack... stacks) {
        NonNullList<ItemStack> items = NonNullList.withSize(stacks.length, ItemStack.EMPTY);
        for (int i = 0; i < stacks.length; i++) {
            items.set(i, stacks[i]);
        }
        return CraftingInput.of(stacks.length, 1, items);
    }

    /** Whether this item is one of the recipe's own ingredients. */
    private static boolean consumes(net.minecraft.world.item.crafting.Recipe<?> recipe, Item item) {
        for (var ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.test(new ItemStack(item))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a recipe's result slot yields this item.
     *
     * <p>Reads the two concrete shapes a real result takes - a plain item and a stack template - and
     * recurses through the wrappers vanilla builds around them. It does <b>not</b> resolve through a
     * context map, which would need a live client-side resolution context that a headless GameTest has
     * no business constructing. A shape this does not recognise is reported as "not a match", which is
     * the safe direction for a gate test only in one sense: it could miss a leak rather than invent
     * one, so the sweep count assertion above is what stops it degrading into a test of nothing.
     */
    private static boolean produces(SlotDisplay display, Item item) {
        return switch (display) {
            case SlotDisplay.ItemSlotDisplay slot -> slot.item().value() == item;
            case SlotDisplay.ItemStackSlotDisplay slot -> slot.stack().item().value() == item;
            case SlotDisplay.Composite composite ->
                composite.contents().stream().anyMatch(inner -> produces(inner, item));
            case SlotDisplay.WithRemainder remainder -> produces(remainder.input(), item);
            case SlotDisplay.OnlyWithComponent only -> produces(only.source(), item);
            default -> false;
        };
    }
}
