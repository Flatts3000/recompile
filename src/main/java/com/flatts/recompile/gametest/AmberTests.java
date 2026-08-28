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
