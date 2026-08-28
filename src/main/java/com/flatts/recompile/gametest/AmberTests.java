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
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
            int checked = 0;
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
                            String species = components.get("recompile:species").getAsString();
                            checked++;
                            if (BuiltInRegistries.ENTITY_TYPE
                                    .getOptional(Identifier.parse(species)).isEmpty()) {
                                unknown.add(species);
                            }
                        }
                    }
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

    /** One bundled JSON as text, or null. */
    private static String read(String path) {
        try (InputStream in = AmberTests.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failed) {
            return null;
        }
    }
}
