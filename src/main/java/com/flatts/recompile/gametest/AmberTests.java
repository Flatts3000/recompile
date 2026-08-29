package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.item.AmberItem;
import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCItems;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Amber and the Broken Spawner (#294): the two halves of the route to a working spawner.
 *
 * <p>Both guards here exist because their failure is silent. A species id that resolves to nothing
 * produces amber a player can hold, sort and never use, and a rate set carelessly produces a feature
 * nobody reaches - and in neither case does anything log, throw, or look wrong in the file.
 */
final class AmberTests {

    private static final List<String> POOLS = List.of(
        "/data/recompile/loot_table/gameplay/household_pulls.json",
        "/data/recompile/loot_table/gameplay/bag_pulls.json");

    private AmberTests() {
    }

    static void register() {

        // EVERY SPECIES AN AMBER CAN CARRY MUST BE A REAL ENTITY.
        //
        // <p>The species is an Identifier rather than a Holder on purpose - a datapack must be able to
        // name a creature from a mod that is not installed without taking the loot table down at parse
        // - and the price of that is that a TYPO is indistinguishable from a deliberate cross-mod
        // reference. This asserts the ids THIS MOD ships, which are the ones that have no excuse.
        //
        // <p>What it would catch: `minecraft:mooshrom`. The amber drops, stamps, stacks, and reads
        // "Something unrecognisable" forever. Nothing else in the build has an opinion about it.
        RCGameTests.test("every_amber_species_is_a_real_entity", 20, helper -> {
            var unknown = new TreeSet<String>();
            var found = speciesInPullStreams(helper);
            int checked = found.size();
            for (String species : found) {
                if (BuiltInRegistries.ENTITY_TYPE
                        .getOptional(Identifier.parse(species)).isEmpty()) {
                    unknown.add(species);
                }
            }
            helper.assertTrue(checked > 0,
                "no amber entries were found in either pull stream, so this measured nothing - the "
                    + "loot tables no longer carry amber, or the entry shape changed");
            helper.assertTrue(unknown.isEmpty(),
                "these amber entries name an entity that does not exist, so the amber they produce "
                    + "can never be sequenced into anything: " + unknown);
            helper.succeed();
        });

        // THE RATE HAS TO LAND IN A BAND, AND THE BAND IS NARROW FOR A REASON.
        //
        // <p>A spawn egg costs four fragments OF ONE SPECIES, so the real cost is four-of-a-kind: a
        // median of about 16 ambers against a weighted table, or 63 against a flat one. That
        // multiplier is invisible in the loot file, and it is what makes the difference between a
        // feature and a rumour - at the collectibles' 1-in-480,000 the first egg is several hundred
        // hours.
        //
        // <p>Asserted as a band rather than a number so #36 can tune inside it without this going red.
        RCGameTests.test("amber_is_findable_but_not_common", 60, helper -> {
            var level = helper.getLevel();
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/household_pulls"));
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            helper.assertTrue(table != LootTable.EMPTY, "household_pulls did not load");

            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN,
                    Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .create(LootContextParamSets.CHEST);

            int rolls = 60000;
            int amber = 0;
            int stamped = 0;
            var species = new TreeSet<String>();
            for (int i = 0; i < rolls; i++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (!stack.is(RCItems.AMBER.get())) {
                        continue;
                    }
                    amber++;
                    // EVERY PIECE CARRIES ONE. An unstamped amber is inert, and the loot function is
                    // the only thing that puts a species on it - so a pool that lost its functions
                    // block would still drop amber and still look fine here without this.
                    if (AmberItem.isStamped(stack)) {
                        stamped++;
                        species.add(String.valueOf(stack.get(RCDataComponents.SPECIES.get())));
                    }
                }
            }

            helper.assertTrue(amber > 0,
                "no amber in " + rolls + " rolls of household_pulls, so it is either absent or so "
                    + "rare that the chain behind it cannot be reached");
            helper.assertTrue(stamped == amber,
                (amber - stamped) + " of " + amber + " ambers came out with no species on them. An "
                    + "unstamped piece can never be sequenced; check the set_components function.");

            // One in 700 over 60,000 rolls is about 86 expected. The band is wide enough for tuning
            // and narrow enough to catch a decimal place.
            int oneIn = rolls / amber;
            helper.assertTrue(oneIn >= 200 && oneIn <= 3000,
                "amber came out at about 1 in " + oneIn + " pulls, outside the intended 1-in-200 to "
                    + "1-in-3000 band. Below that it is not a find; above it, remember a spawn egg "
                    + "needs FOUR of one species, so 16 ambers is the real cost of the first one.");
            helper.assertTrue(species.size() > 5,
                "only " + species.size() + " distinct species appeared in " + amber + " ambers, so "
                    + "the table is far less varied than it declares: " + species);
            helper.succeed();
        });

        // THE SPAWNER RECIPE HAS TO PRODUCE THE ORDINARY SPAWNER, AND A PLACEABLE ONE.
        //
        // <p>A spawner is a BLOCK; it has a block item only because `Items.SPAWNER =
        // registerBlock(Blocks.SPAWNER)`, which is what lets a recipe name it at all.
        //
        // <p><b>This is NOT guarding against a typo.</b> That was the first justification written here
        // and it is wrong, measured: pointing the result at `minecraft:spawnr` fails the recipe codec
        // at load with "Unknown registry key", loudly, before anything ships. What it guards is a swap
        // to an id that is VALID and wrong - and `minecraft:trial_spawner` is exactly that trap. It
        // parses, it is a spawner to look at, and `TrialSpawnerBlockEntity` also implements
        // {@code Spawner}, so a spawn egg retypes it just the same and the chain appears to work. It
        // is a completely different machine: one-shot, ejects its loot, resets itself, and cannot be
        // taken away again. A player would earn the blueprint over four Broken Spawners and get
        // something that behaves nothing like what the guidebook describes.
        //
        // <p>The BlockItem assertion covers the other half: everything past this point is "place it,
        // then retype it", so a result that could not be placed would break two steps later and a
        // long way from here.
        RCGameTests.test("the_spawner_blueprint_yields_a_placeable_spawner", 20, helper -> {
            var found = new java.util.ArrayList<
                com.flatts.recompile.content.recipe.BlueprintCraftingRecipe>();
            for (var holder : helper.getLevel().recipeAccess().recipeMap()
                    .byType(com.flatts.recompile.registry.RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                if (holder.value().blueprint().equals(
                        com.flatts.recompile.content.item.BlueprintItem.SPAWNER)) {
                    found.add(holder.value());
                }
            }
            helper.assertTrue(found.size() == 1,
                "expected exactly one recipe for the Spawner Cage blueprint, found " + found.size());

            var result = found.get(0).result().item();
            helper.assertTrue(result != net.minecraft.world.item.Items.AIR,
                "the Spawner Cage recipe produces AIR, which is what an unresolvable item id becomes - "
                    + "the blueprint would be earned and craft nothing");
            helper.assertTrue(result == net.minecraft.world.item.Items.SPAWNER,
                "the Spawner Cage recipe produces " + result + " rather than minecraft:spawner");
            helper.assertTrue(result instanceof net.minecraft.world.item.BlockItem block
                    && block.getBlock() == net.minecraft.world.level.block.Blocks.SPAWNER,
                "the result is not the spawner's block item, so it cannot be placed - and placing it "
                    + "is the whole point, since a spawn egg retypes it afterwards");
            helper.succeed();
        });

        // EVERY TOOLTIP LINE THIS CHAIN CAN PRINT IS ACTUALLY TRANSLATED.
        //
        // <p>A missing key renders as the key itself, which reads to a player as a typo rather than as
        // a bug, and it only shows in a client that neither test layer runs. AmberItem.tooltipKeys()
        // existed with a javadoc promising exactly this test and nothing called it, so the javadoc was
        // describing coverage that did not exist - which is worse than no javadoc, because it stops
        // the next person looking.
        //
        // <p>The blueprint key is here rather than in BlueprintTests because it is the one the spawn
        // egg family needs and it takes a %s: the set is one PER ENTITY TYPE, so its name is computed
        // from the mob rather than written out, and a key that lost its placeholder would silently
        // drop the species from the sheet.
        RCGameTests.test("every_amber_tooltip_line_is_translated", 20, helper -> {
            List<String> missing = new ArrayList<>();
            List<String> keys = new ArrayList<>(
                com.flatts.recompile.content.item.AmberItem.tooltipKeys());
            keys.add("blueprint.recompile.spawn_egg");
            keys.add("container.recompile.sequencer");
            for (String key : keys) {
                if (Component.translatable(key).getString().equals(key)) {
                    missing.add(key);
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "these keys render as their own name, so they are missing from en_us.json: " + missing);

            // The computed name has to actually name the creature. A key without its %s renders
            // "Spawn Egg" for every mob in the game and the sheets become indistinguishable, which is
            // the paintings bug again one layer up.
            String cow = Component.translatable("blueprint.recompile.spawn_egg",
                Component.translatable("entity.minecraft.cow")).getString();
            helper.assertTrue(cow.contains("Cow"),
                "the spawn-egg blueprint name rendered as \"" + cow + "\", which does not name the "
                    + "creature - every species' sheet would read alike");
            helper.succeed();
        });

        // THE EGG RECIPE READS THE SHEET IN THE GRID, and only a sheet that names a real creature.
        //
        // <p>Driven through the recipe rather than through a menu because the recipe is where the
        // decision lives; the menu's job (putting the sheet back) is proven separately below.
        RCGameTests.test("a_spawn_egg_blueprint_makes_that_species_egg", 20, helper -> {
            var level = helper.getLevel();
            var recipes = new ArrayList<
                com.flatts.recompile.content.recipe.SpawnEggCraftingRecipe>();
            for (var holder : level.recipeAccess().recipeMap()
                    .byType(com.flatts.recompile.registry.RCRecipeTypes.SPAWN_EGG_CRAFTING.get())) {
                recipes.add(holder.value());
            }
            helper.assertTrue(recipes.size() == 1,
                "expected exactly one spawn_egg_crafting recipe, found " + recipes.size()
                    + " - the whole point of the type is that one recipe covers every creature");
            var recipe = recipes.get(0);

            helper.assertTrue(
                recipe.assemble(gridWith(sheetFor("minecraft:cow"))).is(
                    net.minecraft.world.item.Items.COW_SPAWN_EGG),
                "a cow sheet must make a cow spawn egg");
            helper.assertTrue(
                recipe.assemble(gridWith(sheetFor("minecraft:bee"))).is(
                    net.minecraft.world.item.Items.BEE_SPAWN_EGG),
                "and a bee sheet a bee one - the species comes from the sheet, not the recipe");

            // EVERY SPECIES THE AMBER CAN NAME HAS TO WORK. A pool entry whose creature has no spawn
            // egg is a sheet a player earns over four ambers and can never spend, and nothing else in
            // this file would notice: the loot test only asks that the entity exists.
            List<String> unmakeable = new ArrayList<>();
            for (String id : speciesInPullStreams(helper)) {
                if (recipe.assemble(gridWith(sheetFor(id))).isEmpty()) {
                    unmakeable.add(id);
                }
            }
            helper.assertTrue(unmakeable.isEmpty(),
                "these species can be found in amber but have no spawn egg to craft, so their sheets "
                    + "are dead ends: " + unmakeable);
            helper.succeed();
        });

        // THE THINGS THAT MUST NOT MAKE AN EGG. Each of these would otherwise show a result the player
        // cannot explain, and the two-sheet case is the exact non-determinism this recipe type exists
        // to avoid - the reason it is not 29 blueprint_crafting recipes.
        RCGameTests.test("only_one_real_spawn_egg_sheet_makes_an_egg", 20, helper -> {
            var level = helper.getLevel();
            var recipe = level.recipeAccess().recipeMap()
                .byType(com.flatts.recompile.registry.RCRecipeTypes.SPAWN_EGG_CRAFTING.get())
                .iterator().next().value();

            helper.assertTrue(recipe.assemble(gridWith(
                    new ItemStack(RCItems.BLUEPRINT.get()))).isEmpty(),
                "a BLANK blueprint must make nothing");
            helper.assertTrue(recipe.assemble(gridWith(com.flatts.recompile.content.item.BlueprintItem
                    .of(RCItems.BLUEPRINT.get(),
                        com.flatts.recompile.content.item.BlueprintItem.CLEAN_MATTRESS))).isEmpty(),
                "and so must a sheet for something that is not a creature at all");
            helper.assertTrue(recipe.assemble(gridWith(sheetFor("minecraft:not_a_mob"))).isEmpty(),
                "a species that does not exist must make nothing rather than an empty stack the table "
                    + "would show as a blank result");
            helper.assertTrue(recipe.assemble(gridWith(sheetFor("minecraft:arrow"))).isEmpty(),
                "and an entity that exists but has no spawn egg must make nothing either");

            helper.assertTrue(recipe.assemble(twoSheets()).isEmpty(),
                "two different sheets is an ambiguous request, and picking one would be exactly the "
                    + "coin-flip this recipe type was written to avoid");
            helper.succeed();
        });

        // THE SHEET SURVIVES MAKING THE EGG, which is the whole reason it is allowed in the grid.
        //
        // <p>This is the one place in the mod where a Blueprint is an INPUT, and vanilla's ResultSlot
        // decrements every occupied grid slot on take. Without the table's own result slot the sheet
        // would be eaten by the first egg it made and four ambers of one species would buy exactly
        // one, which is not a gate, it is a tax. Driven through the real menu because the bug would
        // live in the menu: the recipe is perfectly correct either way.
        RCGameTests.test("the_spawn_egg_sheet_is_not_consumed_by_the_egg", 40, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.SCRAP_CRAFTING_TABLE.get());
            var menu = new com.flatts.recompile.content.menu.ScrapCraftingStationMenu(
                1, player.getInventory(), helper.getLevel(), helper.absolutePos(pos));

            // The shipped pattern: " G ", "GBG", " R ".
            ItemStack sheet = sheetFor("minecraft:cow");
            menu.getSlot(2).set(new ItemStack(RCItems.GLASS_SHARDS.get()));
            menu.getSlot(4).set(new ItemStack(RCItems.GLASS_SHARDS.get()));
            menu.getSlot(5).set(sheet);
            menu.getSlot(6).set(new ItemStack(RCItems.GLASS_SHARDS.get()));
            menu.getSlot(8).set(new ItemStack(RCItems.RENDERED_ORGANICS.get()));
            menu.slotsChanged(null);

            ItemStack result = menu.getSlot(0).getItem();
            helper.assertTrue(result.is(net.minecraft.world.item.Items.COW_SPAWN_EGG),
                "the table showed " + result + " rather than a cow spawn egg, so the grid or the "
                    + "recipe pattern has moved out from under this test");

            menu.getSlot(0).onTake(player, result.copy());

            // THE SHEET IS STILL THERE.
            ItemStack after = menu.getSlot(5).getItem();
            helper.assertTrue(after.is(RCItems.BLUEPRINT.get()),
                "the sheet was consumed making the egg - a blueprint is knowledge, and this is the "
                    + "only recipe that could ever spend one");
            helper.assertTrue(sheetFor("minecraft:cow").getComponents().equals(after.getComponents()),
                "the sheet came back BLANK, which is worse than losing it: the player still holds a "
                    + "blueprint and has silently lost the species they earned");

            // AND THE VESSEL IS SPENT. A rule that put everything back would be a duplicator, so the
            // other half is asserted in the same breath rather than assumed.
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty()
                    && menu.getSlot(8).getItem().isEmpty(),
                "the glass and the organics were not consumed, so the egg costs nothing but the sheet");
            player.discard();
            helper.succeed();
        });

        // NOTHING ELSE MAKES A SPAWN EGG. The mirror of a_blueprint_result_has_no_other_route, which
        // cannot cover these: it reads the gated set out of the blueprint_crafting recipes, and a
        // spawn egg comes from a type of its own. Vanilla ships no spawn-egg recipe, so the gate holds
        // today by luck rather than by design - which is exactly the kind of thing that stops being
        // true when a pack is added and nobody notices.
        RCGameTests.test("a_spawn_egg_has_no_route_that_is_not_the_sheet", 20, helper -> {
            var level = helper.getLevel();
            List<String> leaks = new ArrayList<>();
            int swept = 0;
            for (var holder : level.recipeAccess().recipeMap().values()) {
                swept++;
                if (holder.value().getType()
                        == com.flatts.recompile.registry.RCRecipeTypes.SPAWN_EGG_CRAFTING.get()) {
                    continue;   // the sanctioned route
                }
                for (var display : holder.value().display()) {
                    for (var stack : display.result().resolveForStacks(
                            net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level))) {
                        if (stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem) {
                            leaks.add(holder.id() + " -> " + stack);
                        }
                    }
                }
            }
            helper.assertTrue(swept > 100,
                "only " + swept + " recipes were swept - the sweep is broken, so this would pass "
                    + "against any recipe that made a spawn egg");
            helper.assertTrue(leaks.isEmpty(),
                "these make a spawn egg without the sheet, so the amber chain is not the only route: "
                    + leaks);
            helper.succeed();
        });

        // THE WHOLE CHAIN, END TO END, in the order a player would walk it. Each step is covered
        // separately above; this is the one that would catch two correct halves that do not join -
        // a fragment the assembly recipe cannot read, or a set id the egg recipe parses differently
        // from the one the machine stamps.
        RCGameTests.test("amber_becomes_a_spawn_egg", 60, helper -> {
            var level = helper.getLevel();
            var pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.SEQUENCER.get());
            var machine = (com.flatts.recompile.content.block.entity.SequencerBlockEntity)
                level.getBlockEntity(helper.absolutePos(pos));

            // 1. A stamped amber, read by the machine.
            ItemStack amber = new ItemStack(RCItems.AMBER.get());
            amber.set(RCDataComponents.SPECIES.get(),
                Identifier.fromNamespaceAndPath("minecraft", "cow"));
            ItemStack fragment = com.flatts.recompile.content.block.entity.SequencerBlockEntity
                .fragmentFor(amber);
            helper.assertTrue(!fragment.isEmpty(), "the sequencer produced no fragment for a cow");

            // 2. Four of them assemble into the sheet. Asked of the live recipe manager rather than
            // constructed by hand, because "does the generic assembly recipe accept a set the machine
            // invented" is precisely the join being tested.
            Identifier set = fragment.get(RCDataComponents.BLUEPRINT.get());
            helper.assertTrue(set != null, "the fragment names no blueprint set");
            int required = com.flatts.recompile.content.recipe.FragmentAssemblyRecipe
                .requiredFor(level, set);
            List<ItemStack> cells = new ArrayList<>();
            for (int i = 0; i < required; i++) {
                cells.add(fragment.copy());
            }
            var assembly = level.recipeAccess()
                .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                    net.minecraft.world.item.crafting.CraftingInput.of(required, 1, cells), level);
            helper.assertTrue(assembly.isPresent(),
                required + " fragments toward " + set + " assembled into nothing");
            ItemStack sheet = assembly.get().value().assemble(
                net.minecraft.world.item.crafting.CraftingInput.of(required, 1, cells));
            helper.assertTrue(sheet.is(RCItems.BLUEPRINT.get()),
                "the fragments made " + sheet + " rather than a Blueprint");
            helper.assertTrue(set.equals(sheet.get(RCDataComponents.BLUEPRINT.get())),
                "the sheet names " + sheet.get(RCDataComponents.BLUEPRINT.get()) + " rather than "
                    + set + ", so the machine and the assembly disagree about the set");

            // 3. And that sheet makes the egg for the creature the amber held.
            var recipe = level.recipeAccess().recipeMap()
                .byType(com.flatts.recompile.registry.RCRecipeTypes.SPAWN_EGG_CRAFTING.get())
                .iterator().next().value();
            ItemStack egg = recipe.assemble(gridWith(sheet));
            helper.assertTrue(egg.is(net.minecraft.world.item.Items.COW_SPAWN_EGG),
                "the chain ended in " + egg + " rather than a cow spawn egg - a cow went into the "
                    + "amber and something else came out the far end");
            helper.succeed();
        });

        // WHAT JEI WILL DRAW, checked here because the categories themselves are client-only.
        //
        // <p>Both halves of this chain are invisible to JEI on its own: sequencing has no recipe object
        // at all, and the egg has exactly one recipe whose result is computed from the sheet. So the
        // pages are built from bundled data, and this asserts that data is there and agrees with the
        // world - an empty category is not an error, it is a mechanic the player cannot look up with
        // nothing anywhere saying why.
        //
        // <p><b>What it does NOT catch, stated because it looks like it should.</b> The viewer's parser
        // and this test's parser read the same two files, so deleting an amber entry moves both and
        // this stays green - measured, not assumed. What it does catch is the two parsers disagreeing,
        // which is a real risk since they are independently written; truncating the viewer's list by
        // one drives it red. It also cannot tell whether the viewer reads BOTH pull streams, because
        // the two carry identical species sets: dropping bag_pulls from the viewer changes nothing
        // observable. If they ever diverge, this needs a case that only one table can satisfy.
        RCGameTests.test("the_amber_chain_can_be_drawn_by_a_viewer", 20, helper -> {
            var species = com.flatts.recompile.compat.SortingData.amberSpecies();
            var fromTables = speciesInPullStreams(helper);
            helper.assertTrue(species.size() == fromTables.size(),
                "the viewer sees " + species.size() + " species and the pull streams carry "
                    + fromTables.size() + " - JEI would list a different set of creatures from the "
                    + "ones a player can actually find");
            for (Identifier id : species) {
                helper.assertTrue(fromTables.contains(id.toString()),
                    "the viewer would show " + id + ", which no pull stream can produce");
            }

            var vessel = com.flatts.recompile.compat.BlueprintData.spawnEggPattern();
            helper.assertTrue(vessel.isPresent(),
                "the spawn-egg vessel could not be read out of the bundled recipe, so its JEI category "
                    + "would be empty and the only route to an egg would be undiscoverable");

            // The drawn grid must be the grid that works. Reading the file and matching are separate
            // code paths, and a page showing an arrangement the table refuses is worse than no page.
            var live = helper.getLevel().recipeAccess().recipeMap()
                .byType(com.flatts.recompile.registry.RCRecipeTypes.SPAWN_EGG_CRAFTING.get())
                .iterator().next().value();
            helper.assertTrue(
                vessel.get().width() == live.pattern().width()
                    && vessel.get().height() == live.pattern().height(),
                "the vessel JEI draws is " + vessel.get().width() + "x" + vessel.get().height()
                    + " and the one the table runs is " + live.pattern().width() + "x"
                    + live.pattern().height());
            helper.succeed();
        });

        // THE MACHINE ACTUALLY READS ONE, and refuses what it cannot.
        //
        // <p>Driven through the block entity's own tick rather than by placing a player at a screen,
        // because the screen is the one layer GameTest cannot see - and the thing worth proving here
        // is the state machine, not the pixels.
        RCGameTests.test("the_sequencer_reads_a_stamped_amber_and_refuses_a_blank_one", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.SEQUENCER.get());
            var machine = (com.flatts.recompile.content.block.entity.SequencerBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            helper.assertTrue(machine != null, "the sequencer has no block entity");

            // A BLANK PIECE IS REFUSED AT THE SLOT. That is the only place a player learns it before
            // spending two hundred ticks of power discovering the output is empty.
            ItemStack blank = new ItemStack(RCItems.AMBER.get());
            helper.assertTrue(!machine.canPlaceItem(
                    com.flatts.recompile.content.block.entity.SequencerBlockEntity.INPUT_SLOT, blank),
                "an unstamped amber was accepted; it can never produce anything, so the slot is the "
                    + "one place to say so");

            ItemStack stamped = new ItemStack(RCItems.AMBER.get());
            stamped.set(RCDataComponents.SPECIES.get(),
                Identifier.fromNamespaceAndPath("minecraft", "cow"));
            helper.assertTrue(machine.canPlaceItem(
                    com.flatts.recompile.content.block.entity.SequencerBlockEntity.INPUT_SLOT, stamped),
                "a stamped amber was refused by the input slot");

            // NO POWER, NO PROGRESS. A machine that read amber for free would make the whole FE tier
            // decorative, and nothing else in this file would notice.
            machine.setItem(
                com.flatts.recompile.content.block.entity.SequencerBlockEntity.INPUT_SLOT, stamped);
            // A FULL READ'S WORTH OF TICKS, not a handful. Twenty ticks against a two-hundred-tick
            // read cannot tell "no power" from "not enough time", so the assertion below passed
            // against a machine with the energy check deleted - found by mutation, not by review.
            for (int i = 0; i <= com.flatts.recompile.content.block.entity
                    .SequencerBlockEntity.TICKS_PER_READ; i++) {
                com.flatts.recompile.content.block.entity.SequencerBlockEntity.serverTick(
                    helper.getLevel(), helper.absolutePos(pos),
                    helper.getBlockState(pos), machine);
            }
            helper.assertTrue(machine.getItem(
                    com.flatts.recompile.content.block.entity.SequencerBlockEntity.OUTPUT_SLOT)
                    .isEmpty(),
                "the sequencer produced a fragment with an empty battery");

            // Fill it and run a whole read.
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                machine.battery().insert(
                    com.flatts.recompile.content.block.entity.SequencerBlockEntity.CAPACITY,
                    transaction);
                transaction.commit();
            }
            int ticks = com.flatts.recompile.content.block.entity.SequencerBlockEntity.TICKS_PER_READ;
            for (int i = 0; i <= ticks; i++) {
                com.flatts.recompile.content.block.entity.SequencerBlockEntity.serverTick(
                    helper.getLevel(), helper.absolutePos(pos),
                    helper.getBlockState(pos), machine);
            }

            ItemStack out = machine.getItem(
                com.flatts.recompile.content.block.entity.SequencerBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(!out.isEmpty(),
                "a full battery and " + ticks + " ticks produced no fragment");
            helper.assertTrue(out.is(RCItems.IDEA_FRAGMENT.get()),
                "the sequencer produced " + out + " rather than an Idea Fragment");
            Identifier set = out.get(RCDataComponents.BLUEPRINT.get());
            helper.assertTrue(set != null && set.getPath().endsWith("/cow"),
                "the fragment names " + set + ", which does not carry the cow that was in the amber - "
                    + "so four of these would assemble into the wrong blueprint, or none at all");
            helper.assertTrue(machine.getItem(
                    com.flatts.recompile.content.block.entity.SequencerBlockEntity.INPUT_SLOT)
                    .isEmpty(),
                "the amber survived being read, so one piece would produce fragments forever");
            helper.succeed();
        });
    }



    /**
     * Every species the two pull streams can stamp onto a piece of amber.
     *
     * <p>Read out of the bundled loot JSON rather than listed in Java, so the tables stay the single
     * source of truth. Two tests need it: one asks that each names a real entity, the other that each
     * has a spawn egg to craft - a species that fails either is a sheet a player earns over four
     * ambers and can never spend.
     */
    private static java.util.SortedSet<String> speciesInPullStreams(
            net.minecraft.gametest.framework.GameTestHelper helper) {
        var species = new TreeSet<String>();
        for (String path : POOLS) {
            String body = read(path);
            helper.assertTrue(body != null, "could not read " + path + " off the classpath");
            for (JsonElement rawPool : JsonParser.parseString(body).getAsJsonObject()
                    .getAsJsonArray("pools")) {
                for (JsonElement rawEntry : rawPool.getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject entry = rawEntry.getAsJsonObject();
                    if (!"recompile:amber".equals(
                            entry.has("name") ? entry.get("name").getAsString() : "")) {
                        continue;
                    }
                    for (JsonElement rawFunction : entry.getAsJsonArray("functions")) {
                        JsonObject components = rawFunction.getAsJsonObject()
                            .getAsJsonObject("components");
                        species.add(components.get("recompile:species").getAsString());
                    }
                }
            }
        }
        return species;
    }

    /** A Blueprint stack naming a species' spawn-egg set. */
    private static ItemStack sheetFor(String species) {
        return com.flatts.recompile.content.item.BlueprintItem.of(RCItems.BLUEPRINT.get(),
            Identifier.fromNamespaceAndPath("recompile",
                com.flatts.recompile.content.item.BlueprintItem.SPAWN_EGG_PREFIX
                    + species.replace(':', '/')));
    }

    /**
     * The shipped recipe's grid with one sheet in the middle.
     *
     * <p>Built from the recipe file's own pattern rather than typed out here, so changing the vessel
     * ingredients does not silently make these tests assert against a grid nothing matches.
     */
    private static net.minecraft.world.item.crafting.CraftingInput gridWith(ItemStack sheet) {
        List<ItemStack> cells = new ArrayList<>();
        cells.add(new ItemStack(RCItems.GLASS_SHARDS.get()));
        cells.add(sheet);
        cells.add(new ItemStack(RCItems.RENDERED_ORGANICS.get()));
        // 1x3 is enough: the recipe is asked for its RESULT here, and assemble() reads the sheet out
        // of whatever it is given rather than re-checking the arrangement. matches() is what cares
        // about shape, and the bench test below is where the real grid is exercised.
        return net.minecraft.world.item.crafting.CraftingInput.of(1, 3, cells);
    }

    /** Two different sheets in one grid. */
    private static net.minecraft.world.item.crafting.CraftingInput twoSheets() {
        return net.minecraft.world.item.crafting.CraftingInput.of(1, 2,
            List.of(sheetFor("minecraft:cow"), sheetFor("minecraft:pig")));
    }

    /** One bundled JSON as text, or null. */
    private static String read(String path) {
        try (InputStream in = AmberTests.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failed) {
            return null;
        }
    }
}
