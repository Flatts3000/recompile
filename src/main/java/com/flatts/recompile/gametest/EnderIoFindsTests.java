package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
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
 * Grains of Infinity are found in Mechanical Waste when Ender IO is installed (owner, 2026-08-21).
 *
 * <p><b>This asserts the WITHOUT-Ender-IO case</b>, which is the state every run of this suite is in
 * and the one that breaks silently. The with-mod half runs by hand, by dropping the Ender IO jar into
 * {@code run/mods} and re-running - the same way #270's press pool and #277's sky stone were checked.
 */
final class EnderIoFindsTests {

    private EnderIoFindsTests() {
    }

    /** The stream the entry lives in. */
    private static final String PULLS = "gameplay/mechanical_pulls";

    static void register() {

        // A FIND THAT NEEDS ANOTHER MOD MUST COST THIS TABLE NOTHING WHEN THAT MOD IS ABSENT.
        //
        // <p>Mechanical Waste is engine content and the only source of the Motor and Magnet Scrap, so
        // an Ender IO entry inside it has two ways to do damage and both are silent:
        //
        // <p><b>1. Killing the table at parse.</b> An item id resolves against the registry when the
        // file is read, so naming enderio:grains_of_infinity without Ender IO gives "Unknown registry
        // key" and takes the WHOLE table down - the Motor and Magnet Scrap with it, reading in-game as
        // a pile that gives nothing. A TagKey does not resolve at parse time, which is why this is a
        // tag entry.
        //
        // <p><b>2. Winning a roll and handing back nothing.</b> This is the one that shipped: #276 put
        // a mod-gated drop in Slag Rubble as an entry that kept its weight when its target was absent,
        // so one pull in twenty-seven was silently empty. Here the guard is {@code expand: true}, which
        // makes a tag contribute ONE ENTRY PER MEMBER - none at all when the tag is empty. With
        // {@code expand: false} it would contribute a single entry holding the whole weight, win one
        // roll in twelve, and yield nothing.
        //
        // <p>The drop count below is what separates those two, and it is an equality rather than a
        // floor on purpose: #276's test asserted {@code drops > 200} out of 300, which a 3.7% dud rate
        // passes comfortably, and that is exactly how the dud reached a release.
        RCGameTests.test("grains_of_infinity_is_inert_without_enderio", 60, helper -> {
            var level = helper.getLevel();
            if (net.neoforged.fml.ModList.get().isLoaded("enderio")) {
                // THE OTHER HALF, and it only ever runs by hand: CI has no Ender IO. Drop its jar into
                // run/mods and re-run to exercise this. Note that ~58 unrelated tests fail in that
                // configuration - Ender IO registers a payload the headless GameTest harness refuses
                // ("Payload enderio:powered_spawner_soul may not be sent to the client"), which breaks
                // every test using a mock player. This one does not use one, so it still measures.
                assertGrainsDrop(helper);
                return;
            }

            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, PULLS));
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            helper.assertTrue(table != LootTable.EMPTY,
                "mechanical_pulls did not load at all, so this measured nothing. An unresolvable item "
                    + "id in it would do exactly that, which is the first failure this test exists to "
                    + "catch.");

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
                drops + " items came out of " + rolls + " rolls of Mechanical Waste rather than "
                    + rolls + ". The pool is rolls:1 over weighted entries, so every roll must hand "
                    + "back exactly one item. A shortfall means an entry won its roll and produced "
                    + "nothing - a pull the player pays for and gets nothing back from, with no log "
                    + "line and no message. Check that the grains entry still carries expand:true.");
            helper.assertTrue(foreign.isEmpty(),
                "Mechanical Waste dropped something from a mod that is not installed: " + foreign);

            // AND THE MECHANISM, read off the file rather than inferred from the silence above. Every
            // assertion so far passes if the entry were simply deleted, so they cannot tell "correctly
            // inert" from "gone" - the same reason the AE2 guards are asserted rather than assumed.
            String body = read("/data/recompile/loot_table/" + PULLS + ".json");
            helper.assertTrue(body != null, "could not read mechanical_pulls.json off the classpath");
            var pool = com.google.gson.JsonParser.parseString(body).getAsJsonObject()
                .getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
            boolean found = false;
            for (var raw : pool) {
                var entry = raw.getAsJsonObject();
                if (!entry.has("name")
                    || !"c:dusts/grains_of_infinity".equals(entry.get("name").getAsString())) {
                    continue;
                }
                found = true;
                helper.assertTrue("minecraft:tag".equals(entry.get("type").getAsString()),
                    "the grains entry is no longer a minecraft:tag entry. Naming the item directly "
                        + "kills this whole table at parse without Ender IO, taking the Motor and "
                        + "Magnet Scrap with it.");
                helper.assertTrue(entry.has("expand") && entry.get("expand").getAsBoolean(),
                    "the grains entry lost expand:true. Without it the tag contributes ONE entry "
                        + "holding the full weight instead of one per member, so with Ender IO absent "
                        + "it wins one roll in twelve and hands back nothing - the silent dud pull "
                        + "that shipped in #276.");
            }
            helper.assertTrue(found,
                "the c:dusts/grains_of_infinity entry is gone from mechanical_pulls, so everything "
                    + "above passed by measuring a table that no longer has the feature in it");
            helper.succeed();
        });
    }

    /**
     * With Ender IO installed the tag has a member, so the entry must actually produce it.
     *
     * <p>Asserts the drop appears at roughly its declared share AND that the pool still yields exactly
     * one item per roll - the second half matters because expand:true is what keeps the weight out of
     * the denominator, and a change there would show up as a rate shift rather than as an error.
     */
    private static void assertGrainsDrop(net.minecraft.gametest.framework.GameTestHelper helper) {
        var level = helper.getLevel();
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, PULLS));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
            .create(LootContextParamSets.CHEST);

        int rolls = 4000;
        int grains = 0;
        int drops = 0;
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.isEmpty()) {
                    continue;
                }
                drops++;
                if ("enderio".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
                    grains++;
                }
            }
        }

        double pct = 100.0 * grains / rolls;
        helper.assertTrue(pct > 5.0 && pct < 12.0,
            "Grains of Infinity came out of Mechanical Waste at " + String.format("%.2f", pct)
                + "% over " + rolls + " rolls, against a declared 8.1% (weight 20 of 247). Zero means "
                + "the tag entry is not resolving even with Ender IO installed.");
        helper.assertTrue(drops == rolls,
            drops + " items from " + rolls + " rolls rather than " + rolls
                + "; the pool must still yield exactly one item per roll with the mod present");
        helper.succeed();
    }

    /** One bundled JSON as text, or null. */
    private static String read(String path) {
        try (java.io.InputStream in = EnderIoFindsTests.class.getResourceAsStream(path)) {
            return in == null ? null
                : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failed) {
            return null;
        }
    }
}
