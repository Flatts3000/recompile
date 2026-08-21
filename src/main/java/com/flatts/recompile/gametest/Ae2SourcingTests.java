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
        // <p><b>Three of the four obvious ways to gate a drop do not work, and all three were tried
        // here.</b> A condition on a loot POOL or an ENTRY is not read at all. A condition on a TAG
        // FILE is silently ignored in 26.1 - measured: the tag kept its member with AE2 absent, which
        // is what sent this design to a nested table. What IS honoured is a condition on a whole loot
        // table FILE, so the drop lives in one and slag_rubble_pulls reaches it through a
        // minecraft:loot_table entry.
        RCGameTests.test("sky_stone_shard_is_inert_without_ae2", 60, helper -> {
            var level = helper.getLevel();
            if (net.neoforged.fml.ModList.get().isLoaded("ae2")) {
                helper.succeed();
                return;
            }

            // THE MECHANISM, asserted directly. A conditional TABLE FILE does not load when its
            // condition fails, which is what makes the drop inert - and it is the only one of the
            // three obvious mechanisms that works. Measured while building this: a condition on a
            // POOL or an ENTRY is not read at all (#270), and a condition on a TAG FILE is ignored in
            // 26.1 - the tag kept its member with AE2 absent, which is what sent the drop through a
            // nested table instead.
            var findsKey = net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/sky_stone_finds"));
            helper.assertTrue(
                level.getServer().reloadableRegistries().getLootTable(findsKey) == LootTable.EMPTY,
                "recompile:gameplay/sky_stone_finds LOADED without AE2 present, so its "
                    + "neoforge:conditions is not being honoured and the Sky Stone Shard will drop "
                    + "into worlds that cannot use it. A condition on a whole loot table file is the "
                    + "only one of pool, entry, tag and file that works - if that has changed, the "
                    + "drop needs a different mechanism, not a looser test.");

            // And the drop itself, through the table rather than through the tag, because the tag being
            // empty is only half of it - a tag entry over an empty tag must also yield nothing rather
            // than erroring or falling back to some default.
            var key = net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/slag_rubble_pulls"));
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            helper.assertTrue(table != LootTable.EMPTY,
                "slag_rubble_pulls did not load at all, so this measured nothing. A tag entry naming a "
                    + "tag that does not exist would do exactly that.");

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
            helper.assertTrue(drops > 200,
                "only " + drops + " items came out of 300 rolls of Slag Rubble, so this would pass "
                    + "against a table that had quietly stopped working");
            helper.assertTrue(foreign.isEmpty(),
                "Slag Rubble dropped something a world without AE2 cannot use: " + foreign);
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
