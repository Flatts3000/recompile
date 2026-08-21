package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.worldgen.sewer.SewerPalette;
import com.flatts.recompile.content.worldgen.sewer.SewerPieces;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * What a sewer pays out, and what that costs the economy (#90).
 *
 * <p><b>The palette is part of the payout.</b> A structure that places a block also hands the player
 * that block, so what a sewer is built from is a question about gates and not only about looks -
 * {@code the_sewer_palette_opens_no_gate} walks every block the structure can place rather than reading
 * the palette, because the block somebody adds next year is the one that opens the furnace route.
 *
 * <p><b>Exclusivity is the hard half.</b> "There is a source" needs one table; "this is the ONLY source"
 * needs every table, every recipe of every type, and proof that the sweep could actually see them - which
 * is why {@link LootSearch} reports what it could not read and why the echo shard test asserts that list
 * is empty before it believes its own result.
 *
 * <p>Split out of {@code SewerTests} at #223; see {@link SewerShapeTests} for the seam.
 */
final class SewerLootTests {

    private SewerLootTests() {
    }

    /**
     * Whether a recipe's OUTPUT positions mention an item id.
     *
     * <p><b>Outputs only, and the distinction is the whole point.</b> This pass used to search the raw
     * file, which cannot tell a result from an ingredient - so a recipe that SPENDS an echo shard was
     * reported as one that makes them. {@code sculk_catalyst_from_powder} (#266) is exactly that: eight
     * sculk powder around one shard, consuming the rarest thing in a sewer to buy the deep dark's
     * living heart. It creates no shards at all.
     *
     * <p>The repo already draws this line elsewhere and in the same words:
     * {@code a_blueprint_result_has_no_other_route} skips a recipe that consumes the gated item and
     * hands one back, because "net new items is what the gate is about".
     *
     * <p><b>Raw text within those subtrees rather than a typed read</b>, because that is the property
     * the second pass exists for: this mod's own recipe types return an empty {@code display()}, so a
     * custom-type recipe producing a shard is invisible to the typed sweep above. Every output shape
     * this mod ships is covered - {@code result} for the cooking and crafting types, {@code results}
     * and {@code extras} for teardown and separating, {@code pools} for the weighted draws.
     */
    private static boolean outputsMention(String body, String id) {
        com.google.gson.JsonObject root =
            com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        for (String key : List.of("result", "results", "extras", "pools", "byproducts")) {
            if (root.has(key) && root.get(key).toString().contains(id)) {
                return true;
            }
        }
        return false;
    }

    static void register() {
        // THE PALETTE OPENS NO GATE. Phase 2's acceptance criterion, and it says explicitly that it must
        // be asserted by walking every block the structure can place rather than by reading the palette -
        // which is why SewerPalette.ALL exists as a list at all.
        //
        // The gate: anything in #minecraft:stone_crafting_materials crafts a vanilla furnace, and a
        // vanilla furnace skips the Cupola. Brick was checked by hand when the spec was written; this
        // checks it on every build, and covers the block somebody adds to the palette next year.
        RCGameTests.test("the_sewer_palette_opens_no_gate", 20, helper -> {
            List<String> offenders = new ArrayList<>();
            for (BlockState state : SewerPalette.ALL) {
                ItemStack item = new ItemStack(state.getBlock());
                if (!item.isEmpty() && item.is(ItemTags.STONE_CRAFTING_MATERIALS)) {
                    offenders.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                }
            }
            helper.assertTrue(SewerPalette.ALL.size() >= 4,
                "the sewer palette has only " + SewerPalette.ALL.size() + " entries - discovery is "
                    + "broken, so this would pass by checking almost nothing");
            helper.assertTrue(offenders.isEmpty(),
                "these sewer blocks craft a vanilla furnace, which skips the Cupola and opens the iron "
                    + "gate: " + offenders);
            helper.succeed();
        });


        // AND EVERY DEPOSIT HAS SOMETHING IN IT.
        //
        // The silt is suspicious sand and suspicious gravel (owner, 2026-08-18), which makes a sewer the
        // one place in this world with archaeology in it. The block is only half of that: a brushable
        // with no loot table on its block entity brushes away into ordinary sand or gravel and drops
        // NOTHING, with no error logged and nothing in the world to see - a dig site that pays out
        // exactly as much as the gravel it replaced, minus the gravel.
        //
        // So this walks the pieces that place silt and checks the contents rather than the block, which
        // is the half that fails silently. It counts what it found first, because a sweep that walks
        // zero deposits passes every assertion after it.
        RCGameTests.test("the_silt_has_something_buried_in_it", 40, helper -> {
            var level = helper.getLevel();
            var gen = level.getChunkSource().getGenerator();
            var mgr = level.structureManager();

            var silt = ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "archaeology/sewer_silt"));
            helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(silt)
                    != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
                "recompile:archaeology/sewer_silt resolves to nothing, so every deposit in every sewer "
                    + "brushes out empty - which looks to a player exactly like bad luck, forever");

            // THE SUMP AND A ROW OF CORRIDORS. The sump beds its whole pool floor deterministically and
            // a corridor silts its corners on a seed, so one proves the bulk case and the other proves
            // the path that actually rolls - and they are separate call sites, which is how one of them
            // would get missed.
            var pieces = new ArrayList<StructurePiece>();
            BlockPos at = helper.absolutePos(new BlockPos(0, 120, 0));
            pieces.add(new SewerPieces.SewerSump(3, SewerPieces.SewerPiece.box(
                at.getX(), at.getY(), at.getZ(), Direction.SOUTH, 9, 7, 9), Direction.SOUTH));
            // ON A GRID, NOT A LINE, for the reason the_dressing_blocks_are_all_reachable already
            // records. silt() seeds on minX * 31 + minZ * 17, so six corridors sharing an X and stepping
            // Z by 9 shift the seed by 153 each time - which is 0 mod 3, and the gate is mod 3. Every
            // corridor in a column lands in the SAME residue class and places an identical count, so a
            // row of six samples one case six times.
            for (int step = 0; step < 6; step++) {
                BlockPos runAt = at.offset((step % 3) * 11, 0, 12 + (step / 3) * 9);
                pieces.add(new SewerPieces.SewerCorridor(1, SewerPieces.SewerPiece.box(
                    runAt.getX(), runAt.getY(), runAt.getZ(), Direction.SOUTH, 5, 5, 7), Direction.SOUTH));
            }

            // A COURSE UNDER EVERY PIECE, for the reason the den test needs one: SILT IS GRAVEL AND SAND,
            // AND BOTH FALL. Worldgen gets away with it because a WorldGenRegion writes into a
            // ProtoChunk, so BrushableBlock.onPlace never runs and no fall is ever scheduled - but a
            // GameTest runs against a live ServerLevel, where fourteen unsupported deposits become
            // fourteen falling block entities dropping out of this test's airspace and into whatever is
            // below. Which, at these heights, is the den test's open platform with live animals on it: a
            // block landing on a frog puts it inside one, and the den test would report a suffocation
            // that this test caused.
            for (var piece : pieces) {
                BoundingBox box = piece.getBoundingBox();
                for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
                    for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                        level.setBlock(new BlockPos(x, box.minY() - 1, z), SewerPalette.WALL,
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }
                }
            }

            int sumpDeposits = 0;
            int runDeposits = 0;
            List<String> hollow = new ArrayList<>();
            List<String> uniform = new ArrayList<>();
            for (var piece : pieces) {
                var mine = new java.util.ArrayList<net.minecraft.world.level.block.Block>();
                BoundingBox box = piece.getBoundingBox();
                BoundingBox limit = new BoundingBox(box.minX() - 8, box.minY() - 8, box.minZ() - 8,
                    box.maxX() + 8, box.maxY() + 8, box.maxZ() + 8);
                piece.postProcess(level, mgr, gen, RandomSource.create(9L), limit,
                    new net.minecraft.world.level.ChunkPos(box.minX() >> 4, box.minZ() >> 4), at);
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            var here = new BlockPos(x, y, z);
                            if (!(level.getBlockState(here).getBlock()
                                    instanceof net.minecraft.world.level.block.BrushableBlock)) {
                                continue;
                            }
                            if (piece instanceof SewerPieces.SewerSump) {
                                sumpDeposits++;
                            } else {
                                runDeposits++;
                                mine.add(level.getBlockState(here).getBlock());
                            }
                            var be = level.getBlockEntity(here);
                            String carried = be == null ? "no block entity at all"
                                : be.saveCustomOnly(level.registryAccess()).getStringOr("LootTable", "");
                            if (!silt.identifier().toString().equals(carried)) {
                                hollow.add(here.toShortString() + " carries [" + carried + "]");
                            }
                        }
                    }
                }
                // AND A CORRIDOR THAT SILTS TWICE SILTS BOTH WAYS. The type used to come off the key's
                // parity, and every offset a key can take is even, so every firing cell in one corridor
                // shared the seed's parity: half the corridors in the world were all gravel and half all
                // sand, never mixed, while the palette called one "the finer half of the same deposit".
                // Both blocks stayed reachable across corridors, so the reachability sweep could not see
                // it - only a per-corridor look can.
                if (mine.size() > 1 && new HashSet<>(mine).size() == 1) {
                    uniform.add(box.getCenter().toShortString() + " is all " + mine.getFirst());
                }
            }
            helper.assertTrue(uniform.isEmpty(),
                "these corridors bedded more than one deposit and made them all the same block, so the "
                    + "coarse and fine halves of a silt bed never appear together: " + uniform);

            // EXACTLY FOURTEEN AND AT LEAST ONE EACH, rather than a total with a round number under it.
            // The first version asserted 20 across the lot, which was precisely the achievable minimum -
            // fourteen from the sump plus one from each of six correlated corridors - so it had no
            // headroom in either direction and would have gone red on any tuning at all while still
            // being unable to tell WHICH half moved.
            //
            // The sump's count is exact because nothing about it is rolled: a 5x3 patch inset from the
            // pool walls, less the cell the crate sits in. A corridor's is a floor because its four
            // candidate cells collapse to one residue class, which always yields one and sometimes two.
            helper.assertTrue(sumpDeposits == 14,
                "the sump bedded " + sumpDeposits + " deposits rather than 14 (a 5x3 patch, less the "
                    + "crate's own cell), so either the bed moved or the crate is eating one of them");
            // EIGHT ACROSS SIX CORRIDORS, and that is arithmetic rather than a seed. silt() offers four
            // cells whose keys differ by {6, 8, 34, 36} - {0, 2, 1, 0} mod 3 - so a corridor places two
            // when its seed is 0 mod 3 and one otherwise. The grid above steps X by 11, which moves the
            // seed by 341 (2 mod 3), so the three columns cover all three residues whatever the plot
            // lands on: one column of two corridors places two each, the other four place one.
            helper.assertTrue(runDeposits == 8,
                runDeposits + " deposits across " + (pieces.size() - 1) + " corridors rather than 8 - "
                    + "recompute it from silt()'s gate before relaxing this, because the number is "
                    + "derived and a change to it means the corner arithmetic moved");
            // AND THE SUMP IS TAKEN BACK DOWN. It ships a drowned spawner and a stocked barrel, and this
            // test builds one outside its own plot, where nothing clears it - so leaving it standing
            // meant a live spawner running in shared world space for the rest of the suite.
            for (var piece : pieces) {
                BoundingBox box = piece.getBoundingBox();
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            level.setBlock(new BlockPos(x, y, z),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                        }
                    }
                }
                level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                        new net.minecraft.world.phys.AABB(box.minX() - 4, box.minY() - 4, box.minZ() - 4,
                            box.maxX() + 5, box.maxY() + 5, box.maxZ() + 5),
                        e -> !(e instanceof net.minecraft.world.entity.player.Player))
                    .forEach(net.minecraft.world.entity.Entity::discard);
            }
            helper.assertTrue(hollow.isEmpty(),
                "these deposits have no loot table on them, so they brush away to nothing and the silt "
                    + "is scenery wearing an archaeology texture: " + hollow);
            helper.succeed();
        });


        // THE AE2 PRESSES COST NOTHING WHEN AE2 IS ABSENT (#268).
        //
        // The sump carries the four Inscriber presses, because AE2 is otherwise dead content in this
        // world: its tree hangs off Sky Stone, Sky Stone comes from meteorites, and meteorites gate on
        // a biome tag this mod deliberately ships no entry for. Its own recipes for the presses take
        // the same press as the stamp, so they duplicate one rather than make one.
        //
        // <p>THIS TEST RUNS IN A WORLD WITHOUT AE2, which is the state that needs proving. Two ways it
        // could go wrong and neither announces itself:
        //
        // <p>The table could fail to PARSE. An item id in a loot table resolves against the registry
        // when the file is read, so naming ae2:silicon_press directly would break the whole sump for
        // everyone not running AE2 - and the symptom is an empty crate at the bottom of every sewer,
        // which reads as bad luck. The entry is a TAG for exactly that reason: a TagKey does not
        // resolve at parse time. Asserting the table still loads is asserting that.
        //
        // <p>Or it could parse and silently CARRY something. An absent tag should roll to nothing.
        //
        // <p><b>It deliberately does not assert the pool is there.</b> This is a stopgap that leaves
        // when KubeJS is fixed (Flatts3000/trashlands#46), and a test pinning the entry would make
        // that removal a code change instead of deleting a pool. Everything below stays true after
        // the pool is gone.
        RCGameTests.test("the_sump_is_unchanged_without_ae2", 60, helper -> {
            var level = helper.getLevel();
            helper.assertFalse(
                net.neoforged.fml.ModList.get().isLoaded("ae2"),
                "AE2 is loaded, so this test cannot prove what it is for - it measures the WITHOUT "
                    + "case, and with AE2 present the presses are supposed to appear");

            var key = net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "chests/sump"));
            var table = level.getServer().reloadableRegistries().getLootTable(key);
            helper.assertTrue(table != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
                "recompile:chests/sump did not load. An unresolvable id in a loot table takes the "
                    + "whole file with it, so the AE2 entry has to be a tag rather than four item "
                    + "entries - this is what says it still is.");

            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams
                    .ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(
                        helper.absolutePos(new BlockPos(1, 1, 1))))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);

            java.util.Set<String> foreign = new java.util.TreeSet<>();
            int shards = 0;
            int drops = 0;
            for (int i = 0; i < 200; i++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    drops++;
                    var sid = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                        stack.getItem());
                    if (!"minecraft".equals(sid.getNamespace())
                        && !Recompile.MOD_ID.equals(sid.getNamespace())) {
                        foreign.add(sid.toString());
                    }
                    if (stack.is(net.minecraft.world.item.Items.ECHO_SHARD)) {
                        shards++;
                    }
                }
            }
            helper.assertTrue(drops > 200,
                "only " + drops + " items came out of 200 rolls of the sump, so this measured very "
                    + "little and would pass against a table that had quietly stopped working");
            helper.assertTrue(foreign.isEmpty(),
                "the sump produced items from a mod that is not loaded: " + foreign);
            helper.assertTrue(shards == 200,
                "the sump gave " + shards + " echo shards in 200 rolls rather than one per roll, so "
                    + "adding the press pool changed what was already there");
            helper.succeed();
        });

        // THE ONE THING ONLY A SEWER GIVES (#90 improvements, phase 4).
        //
        // This is the twin check the found_only rule already uses, because the failure has two halves
        // and fixing one without the other is worse than neither: an item with no source is not "rare",
        // it is a bug nobody can see, and an item with a second source is not exclusive however loudly
        // the docs say it is. So one half asserts the sump crate really carries it, and the other walks
        // everything the mod ships looking for a second way to get one.
        RCGameTests.test("the_echo_shard_comes_from_the_sump_and_from_nowhere_else", 40, helper -> {
            var level = helper.getLevel();
            var key = net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "chests/sump"));
            helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(key)
                    != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
                "recompile:chests/sump resolves to nothing, so the crate at the bottom of every sewer is "
                    + "empty and the phase ships an item with no source at all");

            BlockPos at = helper.absolutePos(new BlockPos(0, 34, 0));
            BoundingBox box = SewerPieces.SewerPiece.box(at.getX(), at.getY(), at.getZ(),
                Direction.SOUTH, 9, 7, 9);
            BoundingBox limit = new BoundingBox(box.minX() - 8, box.minY() - 8, box.minZ() - 8,
                box.maxX() + 8, box.maxY() + 8, box.maxZ() + 8);
            new SewerPieces.SewerSump(3, box, Direction.SOUTH).postProcess(
                level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(9L), limit,
                new net.minecraft.world.level.ChunkPos(box.minX() >> 4, box.minZ() >> 4), at);
            int crates = 0;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        if (level.getBlockEntity(new BlockPos(x, y, z))
                                instanceof net.minecraft.world.RandomizableContainer c
                                && key.equals(c.getLootTable())) {
                            crates++;
                        }
                    }
                }
            }
            helper.assertTrue(crates == 1,
                "the sump holds " + crates + " stocked crates rather than exactly 1 - the reward is "
                    + "supposed to be one thing at the bottom of one room");

            // AND NO SECOND ROUTE, through the registry rather than through the filesystem.
            //
            // The first version of this walked /data/recompile/loot_table on the classpath and grepped.
            // It found ZERO files - NeoForge's dev classpath resolves an individual resource but returns
            // null for a directory URL - so the emptiness assertion beneath it held against anything,
            // and adding an echo shard to the sewer barrel table did not move it. LootSearch already
            // solved this for the found_only rule by listing table IDS from the loaded registry and
            // reading each one by its known path.
            //
            // A DATAPACK'S TABLE IS NOT COVERED, and the first version of this comment claimed it was.
            // LootSearch takes only the ID LIST from the registry and reads contents off the classpath,
            // so a table a pack adds or overrides is exactly the case it cannot see. That is why the
            // skipped-table assertion below exists rather than being belt-and-braces: it turns the blind
            // spot into a failure instead of a clean result.
            var shard = net.minecraft.world.item.Items.ECHO_SHARD;
            var tables = new TreeSet<>(LootSearch.tablesThatCanDrop(level, shard));
            var unread = LootSearch.tablesNotRead(level);
            helper.assertTrue(unread.isEmpty(),
                "these loot tables are in the registry but were not read, so a second source could be "
                    + "sitting in one of them and this sweep would still come back clean: " + unread);
            helper.assertTrue(tables.remove("recompile:chests/sump"),
                "recompile:chests/sump cannot produce an echo shard. The tables that can are " + tables
                    + " - if that is empty the phase ships an item that exists in the registry and "
                    + "nowhere in the world, which is not rarity but a bug no player can tell from one; "
                    + "if it is not, the reward simply moved and the sump is a dry hole");
            helper.assertTrue(tables.isEmpty(),
                "the echo shard has a second loot source, so it is not the one thing only a sewer "
                    + "gives: " + tables);

            // AND NOTHING CRAFTS ONE EITHER. A loot-only sweep would miss a recipe, which is the other
            // half of the found_only twin check and the half that costs nothing to run.
            //
            // TWICE, because neither pass sees everything. display() is empty for this mod's OWN recipe
            // types - separating and pulverizing both return List.of(), and teardown never overrides it -
            // so a display-only sweep is blind to precisely the schemas a pack extends. It was written
            // that way first, and counting recipes did not save it: that counts recipes, not readable
            // ones. The second pass reads every recompile: recipe as JSON, by id from the loaded registry
            // and path by convention, which is the same trick LootSearch uses and for the same reason.
            List<String> crafted = new ArrayList<>();
            List<String> unreadable = new ArrayList<>();
            int readable = 0;
            for (var holder : level.recipeAccess().recipeMap().values()) {
                for (var display : holder.value().display()) {
                    readable++;
                    if (RecipeResults.produces(display.result(), shard)) {
                        crafted.add(String.valueOf(holder.id().identifier()));
                    }
                }
                var rid = holder.id().identifier();
                if (!Recompile.MOD_ID.equals(rid.getNamespace())) {
                    continue;
                }
                String body = null;
                try (var in = SewerLootTests.class.getResourceAsStream(
                        "/data/" + rid.getNamespace() + "/recipe/" + rid.getPath() + ".json")) {
                    if (in != null) {
                        body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } catch (java.io.IOException e) {
                    helper.fail("could not read recipe " + rid + ": " + e);
                    return;
                }
                if (body == null) {
                    unreadable.add(rid.toString());
                } else if (outputsMention(body, "minecraft:echo_shard")) {
                    crafted.add(rid.toString());
                }
            }
            helper.assertTrue(readable > 100,
                "only " + readable + " recipes had a readable result, so the display half of this sweep "
                    + "is measuring an empty set");
            helper.assertTrue(unreadable.isEmpty(),
                "these mod recipes could not be read, so a custom-type recipe making an echo shard would "
                    + "be invisible to both halves of this sweep: " + unreadable);
            helper.assertTrue(crafted.isEmpty(),
                "the echo shard can be crafted, so it is found in name only: " + crafted);

            // AND VANILLA'S OWN SOURCE IS OUT OF REACH. An echo shard is Ancient City loot, which needs
            // a deep dark - so the exclusivity claim rests on this world containing none.
            //
            // OVER THE WORLD PRESET, not over our own biomes. The first version asked whether any
            // recompile: biome was in #minecraft:has_structure/ancient_city, which is near-tautological:
            // a mod biome only enters that vanilla tag if this mod puts it there. The regression that
            // matters is a VANILLA biome entering the region source, and adding minecraft:deep_dark to
            // the preset would have kept that version green.
            String preset;
            try (var in = SewerLootTests.class.getResourceAsStream(
                    "/data/recompile/worldgen/world_preset/garbage.json")) {
                helper.assertTrue(in != null, "the garbage world preset is not on the classpath");
                preset = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                helper.fail("could not read the garbage world preset: " + e);
                return;
            }
            var cities = TagKey.create(Registries.BIOME,
                Identifier.withDefaultNamespace("has_structure/ancient_city"));
            List<String> placeable = new ArrayList<>();
            List<String> deep = new ArrayList<>();
            level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().forEach(holder -> {
                String id = holder.key().identifier().toString();
                if (!preset.contains("\"" + id + "\"")) {
                    return;
                }
                placeable.add(id);
                if (holder.is(cities)) {
                    deep.add(id);
                }
            });
            helper.assertTrue(!placeable.isEmpty(),
                "the world preset names no biome this test could resolve, so the Ancient City check is "
                    + "measuring an empty set");
            helper.assertTrue(deep.isEmpty(),
                "the garbage world can place " + deep + ", which hosts Ancient Cities - vanilla's own "
                    + "echo shard source - so the sewer is no longer the only one");
            helper.succeed();
        });
    }

}
