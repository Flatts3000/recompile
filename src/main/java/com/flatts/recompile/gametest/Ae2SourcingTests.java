package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * AE2 has no certus, no fluix and no sky stone in this world, and #276 routes all three out of waste.
 *
 * <p><b>Everything here asserts the WITHOUT-AE2 case</b>, which is the state every run of this suite is
 * in and the one that breaks silently. The with-AE2 half cannot be tested here - AE2 is not a
 * dependency and will not become one for a stopgap - so it is verified by dropping the jar into
 * {@code run/mods} and running against it, the way #270's press pool was.
 */
final class Ae2SourcingTests {

    private Ae2SourcingTests() {
    }

    /** Every recipe this feature ships, and the guard each one must carry. */
    private static final List<String> RECIPES = List.of(
        "separating_silicon", "separating_certus_quartz", "separating_fluix",
        "sky_stone_block_from_shards");

    static void register() {

        // THE SHARD MUST NOT DROP INTO A WORLD THAT CANNOT USE IT (#276).
        //
        // <p>It is OUR item, so it exists in every install, while the recipe turning four of them into
        // an ae2 block is guarded. Without AE2 a player would pick these out of the depths forever
        // with nothing to do with them, and CLAUDE.md is explicit that a find which is neither useful
        // nor wanted is clutter.
        //
        // <p><b>Four of the five obvious ways to gate a drop do not work, and all four were tried
        // here.</b> A condition on a loot POOL or an ENTRY is not read at all. A condition on a TAG
        // FILE is silently ignored in 26.1 - measured: the tag kept its member with AE2 absent. A
        // condition on a loot table FILE works, but gating the target of a reference is not the same
        // as gating the reference: review of #277 caught the first version doing exactly that, and
        // <b>the dud it left is what the drop count below now pins.</b> The entry sat in
        // slag_rubble_pulls at weight 15, kept winning 15 rolls in 405 with AE2 absent, and yielded
        // nothing - 291 items from 300 rolls, a silent 1-in-27 empty pull in the DEFAULT install.
        //
        // <p>What works is a <b>loot_modifiers file</b>, which honours neoforge:conditions - measured
        // by putting a mod_loaded guard on no_saplings.json, at which point the sapling lockout
        // stopped applying and saplings dropped. So the shard sits in the pull stream unconditionally
        // (it is our own item; the id always resolves) in a pool of its OWN, and no_sky_stone.json
        // strips it back out when AE2 is absent. Nothing dangles, and the seven terrain weights are
        // never touched.
        //
        // <p><b>The obvious inverse - a modifier that ADDS the drop - was built first and cannot
        // aim.</b> neoforge:add_table does fire on this mod's pull streams (measured at 3.6% against
        // an intended 3.7%), but restricting it to one table needs neoforge:loot_table_id, which
        // compares getQueriedLootTableId() - never set on a table rolled programmatically, which is
        // how all five of this mod's roll sites work. With the condition it dropped nothing at all;
        // without it, it fired on every table in the game.
        RCGameTests.test("sky_stone_shard_is_inert_without_ae2", 60, helper -> {
            var level = helper.getLevel();
            if (net.neoforged.fml.ModList.get().isLoaded("ae2")) {
                // THE OTHER HALF, and it only ever runs by hand. CI has no AE2, so everything below
                // measures the absent case; this branch is what a maintainer gets by dropping the AE2
                // and guideme jars into run/mods and re-running the suite. It is here rather than in
                // a throwaway probe so that the with-mod verification is repeatable instead of being
                // something someone once eyeballed.
                assertSkyStoneDrops(helper);
                return;
            }

            // THE PULL STREAM ITSELF, which is where the whole thing is observable. The shard is
            // named in it unconditionally, so a strip modifier that stopped loading would show up
            // in `foreign` below rather than passing quietly.
            var key = net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/slag_rubble_pulls"));
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            helper.assertTrue(table != LootTable.EMPTY,
                "slag_rubble_pulls did not load at all, so this measured nothing.");

            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN,
                    Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1))))
                .create(LootContextParamSets.CHEST);
            int drops = 0;
            var foreign = new TreeSet<String>();
            for (int i = 0; i < 300; i++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    drops++;
                    if (stack.is(RCItems.SKY_STONE_SHARD.get())) {
                        foreign.add("recompile:sky_stone_shard");
                    }
                    var sid = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (!"minecraft".equals(sid.getNamespace())
                        && !Recompile.MOD_ID.equals(sid.getNamespace())) {
                        foreign.add(sid.toString());
                    }
                }
            }
            // EXACTLY ONE ITEM PER ROLL, which is what makes the gating correct rather than merely
            // quiet, and it is the assertion carrying the review finding. Pool 0 is rolls:1 over
            // seven weighted item entries and must always yield exactly one; pool 1 is the sky stone
            // pool and must yield nothing at all here, its 3.7% entry stripped and its filler empty.
            //
            // <p>It is deliberately an equality rather than a floor. The first version asserted
            // drops > 200, which a 3.7% dud rate clears comfortably - and that is exactly how the
            // dud shipped: 291 items from 300 rolls, passing a test written to catch it.
            helper.assertTrue(drops == 300,
                drops + " items came out of 300 rolls of Slag Rubble rather than 300. A pull must "
                    + "always hand back its terrain shard: a roll that wins and yields nothing is a "
                    + "pull the player pays for and gets nothing back from - no log line, no message, "
                    + "and the block can crumble on it. Above 300 means the strip modifier is not "
                    + "loading and sky stone is dropping into a world with no AE2 to use it.");
            helper.assertTrue(foreign.isEmpty(),
                "Slag Rubble dropped something a world without AE2 cannot use: " + foreign);

            // AND THE GUARD IS PRESENT, not merely inferred from the silence above. Every assertion
            // so far passes if the feature were deleted outright, so they cannot tell "correctly
            // gated" from "gone". This reads the modifier file and checks the gate itself, the same
            // shape as the_ae2_sourcing_recipes_are_inert_without_ae2 below.
            String modifier = read("/data/recompile/loot_modifiers/no_sky_stone.json");
            helper.assertTrue(modifier != null,
                "loot_modifiers/no_sky_stone.json is missing, so nothing above measured a gate - the "
                    + "shard is named unconditionally in slag_rubble_pulls, and that file is the only "
                    + "thing keeping it out of a world without AE2");
            var mod = com.google.gson.JsonParser.parseString(modifier).getAsJsonObject();
            boolean inverted = false;
            if (mod.has("neoforge:conditions")) {
                for (var raw : mod.getAsJsonArray("neoforge:conditions")) {
                    var cond = raw.getAsJsonObject();
                    if (!cond.has("type") || !"neoforge:not".equals(cond.get("type").getAsString())) {
                        continue;
                    }
                    var inner = cond.getAsJsonObject("value");
                    inverted |= inner != null && inner.has("type") && inner.has("modid")
                        && "neoforge:mod_loaded".equals(inner.get("type").getAsString())
                        && "ae2".equals(inner.get("modid").getAsString());
                }
            }
            helper.assertTrue(inverted,
                "no_sky_stone.json must be guarded by neoforge:not(neoforge:mod_loaded ae2) - the "
                    + "INVERSE of every other guard in this feature, because it is the thing that "
                    + "runs when AE2 is ABSENT. A plain mod_loaded here would strip the shard exactly "
                    + "when AE2 is installed, which is the one case it must not.");
            String type = mod.has("type") ? mod.get("type").getAsString() : null;
            String stripped = mod.has("item") ? mod.get("item").getAsString() : null;
            helper.assertTrue("recompile:strip_item".equals(type)
                    && "recompile:sky_stone_shard".equals(stripped),
                "no_sky_stone.json no longer strips recompile:sky_stone_shard (type=" + type
                    + ", item=" + stripped + "), so the guard checked above is guarding something "
                    + "other than the drop this test is about");
            helper.succeed();
        });

        // THE RECIPES ARE ABSENT, AND FOR THE RIGHT REASON.
        //
        // <p>Same shape as a_guarded_override_is_inert_without_its_mod, and for the same reason: "no
        // ae2 recipe is loaded" passes in BOTH the good and the bad state. With the guard the recipe is
        // skipped; WITHOUT it the file fails to parse on its own result id and is equally absent. A
        // broken file and a correct one are indistinguishable from the recipe map, so absence is
        // asserted together with the reason for it.
        RCGameTests.test("the_ae2_sourcing_recipes_are_inert_without_ae2", 40, helper -> {
            if (net.neoforged.fml.ModList.get().isLoaded("ae2")) {
                helper.succeed();
                return;
            }

            List<String> unguarded = new ArrayList<>();
            int present = 0;
            for (String name : RECIPES) {
                String body = read("/data/recompile/recipe/" + name + ".json");
                if (body == null) {
                    continue;
                }
                present++;
                boolean guarded = false;
                var root = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                if (root.has("neoforge:conditions")) {
                    for (var raw : root.getAsJsonArray("neoforge:conditions")) {
                        var cond = raw.getAsJsonObject();
                        guarded |= cond.has("type") && cond.has("modid")
                            && "neoforge:mod_loaded".equals(cond.get("type").getAsString())
                            && "ae2".equals(cond.get("modid").getAsString());
                    }
                }
                if (!guarded) {
                    unguarded.add(name);
                }
            }

            List<String> loaded = new ArrayList<>();
            for (var holder : helper.getLevel().recipeAccess().recipeMap().values()) {
                String id = holder.id().identifier().getPath();
                if (RECIPES.contains(id)) {
                    loaded.add(id);
                }
            }

            helper.assertTrue(unguarded.isEmpty(),
                "these produce an ae2 item with no neoforge:mod_loaded guard, so without AE2 they do "
                    + "not merely fail to apply - they FAIL TO PARSE, which is one ERROR line in an "
                    + "otherwise green run: " + unguarded);
            helper.assertTrue(loaded.isEmpty(),
                "recipes producing ae2 items loaded without AE2 present: " + loaded);
            helper.assertTrue(present == 0 || present == RECIPES.size(),
                "found " + present + " of the " + RECIPES.size() + " sourcing recipes. Partial is the "
                    + "bad state: three of four routes leaves AE2 still unplayable, and removing this "
                    + "stopgap means removing all of them.");
            helper.succeed();
        });
    }

    /**
     * With AE2 installed the shard must actually turn up, at roughly the rate the pool declares.
     *
     * <p>Asserts the two things the absent-mod case cannot: that the strip modifier is gone (so the
     * shard drops at all) and that pool 0 still hands back exactly one terrain shard every roll (so
     * the sky stone pool is riding along rather than displacing anything).
     */
    private static void assertSkyStoneDrops(net.minecraft.gametest.framework.GameTestHelper helper) {
        var level = helper.getLevel();
        var key = net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/slag_rubble_pulls"));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN,
                Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1))))
            .create(LootContextParamSets.CHEST);

        int rolls = 4000;
        int shards = 0;
        int terrain = 0;
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.is(RCItems.SKY_STONE_SHARD.get())) {
                    shards++;
                } else {
                    terrain++;
                }
            }
        }

        // The declared rate is 15 of 405, or 3.70%. The band is wide enough that 4000 rolls will not
        // flake on it and narrow enough to catch the rate being wrong by a factor, which is the way
        // a weight edit actually goes wrong.
        double pct = 100.0 * shards / rolls;
        helper.assertTrue(pct > 2.0 && pct < 6.0,
            "sky stone came out of Slag Rubble at " + String.format("%.2f", pct) + "% over " + rolls
                + " rolls, against a declared 3.70% (weight 15 against the stream's own 390). Zero "
                + "means the drop is not reaching players who have AE2 installed at all.");
        helper.assertTrue(terrain == rolls,
            terrain + " terrain shards from " + rolls + " rolls rather than " + rolls + ". The sky "
                + "stone pool must ride ALONG with the terrain pool, never displace it - that is the "
                + "whole reason it is a second pool instead of an eighth entry.");
        helper.succeed();
    }

    /** One bundled JSON as text, or null. */
    private static String read(String path) {
        try (java.io.InputStream in = Ae2SourcingTests.class.getResourceAsStream(path)) {
            return in == null ? null
                : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failed) {
            return null;
        }
    }
}
