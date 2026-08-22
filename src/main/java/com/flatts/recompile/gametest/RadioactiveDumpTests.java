package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The radioactive dump (#285): the second frontier region, and the only uranium in the world.
 *
 * <p>Spec: {@code docs/radioactive_dump_spec.md}. V1 ships no radiation - that is deferred to
 * Mekanism, which already has a complete system - so what is tested here is the region's placement,
 * its tool gate, and that its Powah-gated drop is inert without Powah.
 */
final class RadioactiveDumpTests {

    private RadioactiveDumpTests() {
    }

    static void register() {

        // THE TOOL GATE, AND IT IS A FAMILY RATHER THAN AN ITEM.
        //
        // <p>SortableBlock's gate names a single Item, which is right for the Prybar and the Scrap
        // Knife - there is one of each. There are FOUR sledgehammers, so naming one would tell a
        // player holding a diamond sledgehammer to go and fetch a copper one. #285 added
        // requiredToolFamily() for that, and this is what pins it.
        //
        // <p>Asserted over the TAG rather than over a list of four, so a fifth tier is covered the
        // day it is registered - the same reasoning the tag itself exists for.
        RCGameTests.test("mill_tailings_takes_any_sledgehammer", 20, helper -> {
            SortableBlock tailings = (SortableBlock) RCBlocks.MILL_TAILINGS.get();

            List<String> rejected = new ArrayList<>();
            int checked = 0;
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(RCTags.SLEDGEHAMMER)) {
                checked++;
                if (!tailings.acceptsTool(new ItemStack(holder.value()))) {
                    rejected.add(String.valueOf(BuiltInRegistries.ITEM.getKey(holder.value())));
                }
            }

            helper.assertTrue(checked >= 4,
                "expected at least the four sledgehammer tiers in #recompile:sledgehammer, found "
                    + checked + " - an empty or missing tag makes every assertion below vacuous");
            helper.assertTrue(rejected.isEmpty(),
                "these sledgehammers cannot open Mill Tailings, so a player holding one is told to "
                    + "fetch a different tier: " + rejected + ". The gate is a FAMILY; if it has gone "
                    + "back to naming a single Item, three of the four stop working.");

            // And it still refuses everything else. Without this the test passes against a block
            // that lost its gate entirely and became bare-hand sortable - which is silent, because
            // nothing about a pile looks different when its tool stops being required.
            helper.assertTrue(!tailings.acceptsTool(new ItemStack(Items.STICK)),
                "a stick opens Mill Tailings, so the tool gate is not gating anything");
            helper.assertTrue(!tailings.acceptsTool(new ItemStack(RCItems.PRYBAR.get())),
                "a prybar opens Mill Tailings; the prybar is the DRUM's tool, not the heap's");
            helper.assertTrue(tailings.sortToolFamily() == RCTags.SLEDGEHAMMER,
                "Mill Tailings no longer declares the sledgehammer family");
            helper.assertTrue(tailings.sortTool() != null,
                "Mill Tailings has no representative tool, so Jade's hint renders as 'sort by hand' - "
                    + "wrong in the one direction that matters");
            helper.succeed();
        });

        // THE POWAH DROP IS INERT WITHOUT POWAH, AND COSTS THE POOL NOTHING.
        //
        // <p>Uraninite arrives as a minecraft:tag entry over #c:raw_materials/uraninite, and
        // expand:true is load-bearing: it makes a tag contribute one entry PER MEMBER, so an absent
        // tag contributes NONE. expand:false would leave a single entry holding weight 55 that wins
        // one roll in five and hands back nothing - the silent dud pull that shipped in #276.
        RCGameTests.test("tailings_yield_nothing_foreign_without_powah", 60, helper -> {
            var level = helper.getLevel();
            boolean withPowah = net.neoforged.fml.ModList.get().isLoaded("powah");

            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/tailings_pulls"));
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            helper.assertTrue(table != LootTable.EMPTY, "tailings_pulls did not load at all");

            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN,
                    Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .create(LootContextParamSets.CHEST);

            int rolls = 400;
            int drops = 0;
            var foreign = new TreeSet<String>();
            for (int i = 0; i < rolls; i++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    drops++;
                    Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (!"minecraft".equals(id.getNamespace())
                        && !Recompile.MOD_ID.equals(id.getNamespace())) {
                        foreign.add(id.toString());
                    }
                }
            }

            helper.assertTrue(drops == rolls,
                drops + " items came out of " + rolls + " rolls of Mill Tailings rather than " + rolls
                    + ". Every roll must hand back exactly one item; a shortfall means the uraninite "
                    + "tag entry is winning rolls and yielding nothing, which is a pull the player "
                    + "pays for and gets nothing back from. Check it still carries expand:true.");
            if (withPowah) {
                // THE OTHER HALF, and it only ever runs by hand: CI has no Powah. Drop its jar into
                // run/mods and re-run. Uraninite is the entire reason this region exists, so "the
                // tag entry resolves" is the one thing worth proving with the mod present.
                helper.assertTrue(foreign.contains("powah:uraninite_raw"),
                    "Powah is installed and Mill Tailings produced no uraninite in " + rolls
                        + " rolls. The tag entry over #c:raw_materials/uraninite is not resolving, so "
                        + "the region has no uranium and Powah is still unstartable. Saw: " + foreign);
                helper.succeed();
                return;
            }

            helper.assertTrue(foreign.isEmpty(),
                "Mill Tailings dropped something from a mod that is not installed: " + foreign);
            helper.succeed();
        });

        // THE REGION IS PLACED, AND BEYOND THE YARD.
        //
        // <p>Read off the preset rather than from the biome source, because the ONSET is the
        // decision: 1024 is double the yard's 512, so a player finds the dump while working the
        // yard. A region whose onset slipped below the yard's would still generate, and would
        // quietly stop being a second frontier.
        RCGameTests.test("the_radioactive_dump_sits_beyond_the_demolition_yard", 20, helper -> {
            String body = read("/data/recompile/worldgen/world_preset/garbage.json");
            helper.assertTrue(body != null, "could not read the world preset off the classpath");

            var frontier = com.google.gson.JsonParser.parseString(body).getAsJsonObject()
                .getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
                .getAsJsonObject("generator").getAsJsonObject("biome_source")
                .getAsJsonArray("frontier");

            int yard = -1;
            int dump = -1;
            for (var raw : frontier) {
                var entry = raw.getAsJsonObject();
                String biome = entry.get("biome").getAsString();
                int onset = entry.has("onset") ? entry.get("onset").getAsInt() : 0;
                if ("recompile:demolition_yard".equals(biome)) {
                    yard = onset;
                } else if ("recompile:radioactive_dump".equals(biome)) {
                    dump = onset;
                }
            }

            helper.assertTrue(yard > 0,
                "the demolition yard is not in the frontier list, so this measured nothing");
            helper.assertTrue(dump > 0,
                "recompile:radioactive_dump is not in the frontier list - its blocks and finds all "
                    + "exist, and no world will ever generate one");
            helper.assertTrue(dump > yard,
                "the radioactive dump's onset (" + dump + ") is not beyond the demolition yard's ("
                    + yard + "), so it is not a second frontier - it would compete with the yard for "
                    + "the same ring rather than sitting past it");
            helper.succeed();
        });
    }

    /** One bundled JSON as text, or null. */
    private static String read(String path) {
        try (java.io.InputStream in = RadioactiveDumpTests.class.getResourceAsStream(path)) {
            return in == null ? null
                : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failed) {
            return null;
        }
    }
}
