package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.content.block.multiblock.MultiblockSkinnedBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;

/**
 * The guidebook's multiblock render pages, held against {@code Multiblock.java} (#37).
 *
 * <p><b>Why this test exists.</b> A machine's shape now lives in two places: the {@link Multiblock}
 * blueprint, which is what actually validates and assembles, and a Modonomicon dense pattern under
 * {@code data/recompile/modonomicon/multiblocks/}, which is what the player is shown and what the
 * in-world projection places. Nothing connects them. Add a cell to a machine and the guide keeps
 * confidently drawing the old shape, and the projection keeps telling the player to build something
 * that will not form - a wrong diagram is worse than no diagram, because the player trusts it and
 * blames themselves. The spec called for either sourcing the pattern from Java or locking it with a
 * test; Modonomicon reads its patterns from data files, so this is the lock.
 *
 * <p><b>What is compared.</b> The pattern's components, offset from its {@code '0'} centre, against
 * the blueprint's cells, offset from the core. Both directions: a cell in one and not the other
 * fails either way round.
 *
 * <p><b>The page shows the loose components, not the formed machine.</b> A formed cell becomes the
 * machine's bespoke block (a Water Tank becomes a Tree Nursery Tank), and the page's job is to teach
 * the build, so it draws what you place. The consequence is that projecting onto an already-formed
 * machine reads as unsatisfied. That is the correct trade: the projection is a build aid.
 *
 * <p><b>The one thing this cannot check is layer order.</b> Modonomicon's dense pattern lists layers
 * top-first ({@code DenseMultiblock} stores {@code stateMatchers[x][height - 1 - y][z]}), and this
 * test decodes them the same way, so an upside-down reading would agree with itself and pass. It was
 * confirmed twice by hand instead: against that line, and against Modonomicon's own demo patterns,
 * whose decorative floor is always the <em>last</em> layer. A {@code runClient} pass is still what
 * proves the render.
 *
 * <p>Note the directory is {@code multiblocks}, plural - Modonomicon's own folder, not one of the
 * vanilla data dirs 26.1 singularised. The same trap as {@code loot_modifiers}: rename it to match
 * the neighbours and the pattern silently stops loading.
 */
final class GuidebookMultiblockTests {

    private static final String ROOT = "/data/" + Recompile.MOD_ID + "/modonomicon/multiblocks/";

    /** {@code '_'} is Modonomicon's built-in "any block here" - padding, not a component. */
    private static final char ANY = '_';
    /** The anchor cell, which is always the core. */
    private static final char CENTRE = '0';

    /** A page's {@code "multiblock_id"}, which is the only place the book names a pattern. */
    private static final java.util.regex.Pattern MULTIBLOCK_ID = java.util.regex.Pattern.compile(
        "\"multiblock_id\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"");

    /** A machine's guide pattern and the core block that defines its real shape. */
    private record Machine(String pattern, java.util.function.Supplier<? extends MultiblockCoreBlock> core) {
    }

    private static final List<Machine> MACHINES = List.of(
        new Machine("rain_collector", com.flatts.recompile.registry.RCBlocks.RAIN_COLLECTOR),
        new Machine("grass_spreader", com.flatts.recompile.registry.RCBlocks.GRASS_SPREADER),
        new Machine("compost_heap", com.flatts.recompile.registry.RCBlocks.COMPOST_HEAP),
        new Machine("tree_nursery", com.flatts.recompile.registry.RCBlocks.TREE_NURSERY),
        new Machine("separator", com.flatts.recompile.registry.RCBlocks.SEPARATOR));

    private GuidebookMultiblockTests() {
    }

    static void register() {
        registerJeiPartRule();
        registerSkinIndexRule();
        RCGameTests.test("every_guidebook_multiblock_matches_its_blueprint", 20, helper -> {
            List<String> problems = new ArrayList<>();
            for (Machine machine : MACHINES) {
                JsonObject json = read(helper, machine.pattern());
                if (json == null) {
                    problems.add(machine.pattern() + ": no pattern file at " + ROOT);
                    continue;
                }
                MultiblockCoreBlock core = machine.core().get();
                Map<Vec3i, String> drawn = decode(machine.pattern(), json, problems);
                Map<Vec3i, String> built = blueprintOf(core);
                for (Map.Entry<Vec3i, String> cell : built.entrySet()) {
                    String shown = drawn.remove(cell.getKey());
                    if (shown == null) {
                        problems.add(machine.pattern() + ": the blueprint has " + cell.getValue()
                            + " at " + show(cell.getKey()) + " and the guide draws nothing there");
                    } else if (!shown.equals(cell.getValue())) {
                        problems.add(machine.pattern() + ": at " + show(cell.getKey())
                            + " the blueprint wants " + cell.getValue() + ", the guide draws " + shown);
                    }
                }
                for (Map.Entry<Vec3i, String> extra : drawn.entrySet()) {
                    problems.add(machine.pattern() + ": the guide draws " + extra.getValue() + " at "
                        + show(extra.getKey()) + ", which is not part of the machine");
                }
            }
            helper.assertTrue(problems.isEmpty(),
                "guide multiblocks that disagree with their blueprint (" + problems.size() + "): "
                    + problems);
            helper.succeed();
        });

        // This guards the test above as much as the data. That one reads every mapping's "block"
        // field, which only a block matcher has - point a cell at a tag or a blockstate and it throws
        // instead of reporting, and a blockstate matcher would in any case promise something stricter
        // than Multiblock.matches, which compares block identity and ignores properties. The
        // unknown-id half overlaps with the comparison above; it survives because it names the id.
        RCGameTests.test("every_guidebook_multiblock_matches_on_block_identity", 20, helper -> {
            List<String> unknown = new ArrayList<>();
            for (Machine machine : MACHINES) {
                JsonObject json = read(helper, machine.pattern());
                if (json == null) {
                    continue;
                }
                for (Map.Entry<String, com.google.gson.JsonElement> entry
                        : json.getAsJsonObject("mapping").entrySet()) {
                    JsonObject matcher = entry.getValue().getAsJsonObject();
                    // Anything other than a plain block matcher would loosen what the page promises
                    // relative to what Multiblock.matches actually accepts, which is block identity.
                    String type = matcher.get("type").getAsString();
                    if (!"modonomicon:block".equals(type)) {
                        unknown.add(machine.pattern() + ": '" + entry.getKey() + "' is a " + type
                            + ", but the blueprint matches on block identity alone");
                        continue;
                    }
                    String id = matcher.get("block").getAsString();
                    var parsed = net.minecraft.resources.Identifier.tryParse(id);
                    if (parsed == null || !BuiltInRegistries.BLOCK.containsKey(parsed)) {
                        unknown.add(machine.pattern() + ": " + id);
                    }
                }
            }
            helper.assertTrue(unknown.isEmpty(),
                "guide multiblock mappings that will not load (" + unknown.size() + "): " + unknown);
            helper.succeed();
        });

        registerCoverage();
    }

    /**
     * A three-way check that {@link #MACHINES} has not become the stale copy.
     *
     * <p>That list is a second inventory of which machines have a page, and the copy nobody
     * remembers to update is always the one that is read. So compare it against both of the places
     * the real answer lives: the pattern files on disk, and the pages that draw them. A fifth
     * machine, an orphaned pattern, or a page pointed at a file that was never written all fail
     * here rather than passing quietly.
     */
    private static void registerCoverage() {
        RCGameTests.test("every_guidebook_multiblock_is_covered_by_this_test", 20, helper -> {
            Set<String> checked = new LinkedHashSet<>();
            for (Machine machine : MACHINES) {
                checked.add(Recompile.MOD_ID + ":" + machine.pattern());
            }

            Set<String> onDisk = patternFiles(helper);
            helper.assertTrue(!onDisk.isEmpty(),
                "no multiblock pattern files were found at all - discovery is broken, so this test "
                    + "would pass against a mod that ships none of them");

            Set<String> referenced = new LinkedHashSet<>();
            for (String json : GuidebookTests.bookFiles(helper)) {
                java.util.regex.Matcher m = MULTIBLOCK_ID.matcher(json);
                while (m.find()) {
                    referenced.add(m.group(1));
                }
            }
            helper.assertTrue(!referenced.isEmpty(),
                "no guidebook page references a multiblock at all - discovery is broken, so this "
                    + "test would pass against a book with every render page deleted");

            List<String> problems = new ArrayList<>();
            for (String id : referenced) {
                if (!onDisk.contains(id)) {
                    problems.add("a page draws " + id + ", which has no pattern file");
                }
            }
            for (String id : onDisk) {
                if (!referenced.contains(id)) {
                    problems.add(id + " is a pattern file that no page draws");
                }
                if (!checked.contains(id)) {
                    problems.add(id + " is a pattern this test does not check against a blueprint");
                }
            }
            for (String id : checked) {
                if (!onDisk.contains(id)) {
                    problems.add(id + " is checked by this test but has no pattern file");
                }
            }
            helper.assertTrue(problems.isEmpty(),
                "guide multiblock coverage gaps (" + problems.size() + "): " + problems);
            helper.succeed();
        });
    }

    /**
     * Every pattern file that ships, by id.
     *
     * <p>Anchored on a known file rather than the directory, for the reason {@code GuidebookTests}
     * gives: asking a classloader for a directory does not reliably return a URL.
     */
    private static Set<String> patternFiles(GameTestHelper helper) {
        Set<String> out = new LinkedHashSet<>();
        java.net.URL anchor = GuidebookMultiblockTests.class.getResource(ROOT + "rain_collector.json");
        if (anchor == null) {
            helper.fail("no multiblock patterns on the classpath at " + ROOT);
            return out;
        }
        try (java.util.stream.Stream<java.nio.file.Path> walk =
                java.nio.file.Files.walk(java.nio.file.Path.of(anchor.toURI()).getParent())) {
            for (java.nio.file.Path path : walk.filter(java.nio.file.Files::isRegularFile).toList()) {
                String file = path.getFileName().toString();
                if (file.endsWith(".json")) {
                    out.add(Recompile.MOD_ID + ":" + file.substring(0, file.length() - ".json".length()));
                }
            }
        } catch (IOException | java.net.URISyntaxException e) {
            helper.fail("could not walk the multiblock patterns: " + e);
        }
        return out;
    }

    /** Blueprint cells as offset -> component block id, plus the core at the origin. */
    private static Map<Vec3i, String> blueprintOf(MultiblockCoreBlock core) {
        Map<Vec3i, String> out = new LinkedHashMap<>();
        out.put(Vec3i.ZERO, idOf(core));
        for (Multiblock.Cell cell : core.blueprint().cells()) {
            out.put(cell.offset(), idOf(cell.component()));
        }
        return out;
    }

    /**
     * A dense pattern as offset-from-centre -> block id.
     *
     * <p>Layers run top-first, so the world Y of layer {@code i} of {@code n} is {@code n - 1 - i}.
     * Within a layer each string is one X and each character in it is one Z, which is Patchouli's
     * convention and the one {@code DenseMultiblock} keeps.
     */
    private static Map<Vec3i, String> decode(String name, JsonObject json, List<String> problems) {
        JsonArray layers = json.getAsJsonArray("pattern");
        JsonObject mapping = json.getAsJsonObject("mapping");
        int height = layers.size();

        Map<Vec3i, String> cells = new LinkedHashMap<>();
        Vec3i centre = null;
        for (int layer = 0; layer < height; layer++) {
            JsonArray rows = layers.get(layer).getAsJsonArray();
            int y = height - 1 - layer;
            for (int x = 0; x < rows.size(); x++) {
                String row = rows.get(x).getAsString();
                for (int z = 0; z < row.length(); z++) {
                    char c = row.charAt(z);
                    if (c == ANY) {
                        continue;
                    }
                    Vec3i at = new Vec3i(x, y, z);
                    if (c == CENTRE) {
                        centre = at;
                    }
                    if (!mapping.has(String.valueOf(c))) {
                        problems.add(name + ": '" + c + "' at " + show(at) + " is not mapped");
                        continue;
                    }
                    cells.put(at, mapping.getAsJsonObject(String.valueOf(c)).get("block").getAsString());
                }
            }
        }
        if (centre == null) {
            problems.add(name + ": the pattern has no '0' centre, so it cannot be anchored");
            return new LinkedHashMap<>();
        }

        Map<Vec3i, String> relative = new LinkedHashMap<>();
        for (Map.Entry<Vec3i, String> cell : cells.entrySet()) {
            relative.put(cell.getKey().subtract(centre), cell.getValue());
        }
        return relative;
    }

    private static String idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static String show(Vec3i offset) {
        return "(" + offset.getX() + ", " + offset.getY() + ", " + offset.getZ() + ")";
    }

    private static JsonObject read(GameTestHelper helper, String name) {
        try (InputStream in = GuidebookMultiblockTests.class.getResourceAsStream(ROOT + name + ".json")) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            helper.fail("could not read the guide multiblock " + name + ": " + e);
            return null;
        }
    }
    /**
     * The JEI rule, kept honest from both ends (owner, 2026-08-03): a viewer must not list a multiblock
     * part the player can never hold.
     *
     * <p>{@code MultiblockParts} decides that structurally - a cell whose formed block differs from its
     * component is a transformation, so the formed half is unobtainable. This asserts the structure
     * actually lines up with reality, in both directions:
     *
     * <ul>
     *   <li>nothing on the hide list has a recipe, so the rule never hides something craftable;</li>
     *   <li>the list is not empty, so a broken derivation cannot pass as "nothing to hide".</li>
     * </ul>
     *
     * <p>The first half is the one that bites. Give a formed cell a recipe one day - a perfectly
     * reasonable thing to do - and the machine it belongs to keeps working while JEI quietly stops
     * admitting the part exists.
     */
    private static void registerJeiPartRule() {
        RCGameTests.test("jei_hides_only_multiblock_parts_that_cannot_be_crafted", 20, helper -> {
            var hidden = com.flatts.recompile.compat.MultiblockParts.formedOnly();
            helper.assertTrue(!hidden.isEmpty(),
                "no formed-only multiblock parts were derived at all - the derivation is broken, so "
                    + "this would pass against a JEI list full of uncraftable parts");

            // Read the bundled recipe FILES rather than the loaded recipes. A Recipe cannot be asked
            // what it makes without an input in 26.1 (assemble takes one and throws on null), and only
            // this mod could ever add a recipe for its own formed block, so its own recipe folder is
            // the complete answer. Same trick SeparatingData uses, for the same reason.
            Set<String> results = new LinkedHashSet<>();
            for (JsonObject recipe : com.flatts.recompile.compat.RecipeFiles.all()) {
                collectResultIds(recipe.get("result"), results);
            }
            List<String> craftable = new ArrayList<>();
            for (var block : hidden) {
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                if (results.contains(id)) {
                    craftable.add(id);
                }
            }
            helper.assertTrue(craftable.isEmpty(),
                "these are on the JEI hide list but ARE craftable (" + craftable.size() + "): "
                    + craftable + ". A part with a recipe is real content and must stay visible");
            helper.succeed();
        });
    }

    /** Item ids named by a recipe's {@code result}, in any of the shapes 26.1 accepts. */
    private static void collectResultIds(com.google.gson.JsonElement result, Set<String> into) {
        if (result == null) {
            return;
        }
        if (result.isJsonPrimitive()) {
            into.add(result.getAsString());
            return;
        }
        if (!result.isJsonObject()) {
            return;
        }
        JsonObject object = result.getAsJsonObject();
        for (String key : List.of("id", "item")) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                into.add(object.get(key).getAsString());
            }
        }
    }

    /**
     * The whole-machine skin's arithmetic, for every multiblock in the game.
     *
     * <p>A skinned cell shows the tile belonging to its position, and that position is a blockstate
     * value with a hard ceiling. Two ways that breaks quietly:
     *
     * <ul>
     *   <li>a machine grows past {@code MAX_CELLS} and the cells past the ceiling all fall back to
     *       tile 0, so one corner of the machine wears the wrong face;</li>
     *   <li>two cells collide on one index, so two positions draw the same tile and a third tile is
     *       never drawn at all.</li>
     * </ul>
     *
     * <p>Both look like bad art rather than a broken index, which is exactly why they need a test.
     * Checked for every machine, not only the skinned ones - a machine that adopts the skin later
     * should find out it does not fit before someone spends an afternoon on its art.
     */
    private static void registerSkinIndexRule() {
        RCGameTests.test("every_multiblock_fits_the_whole_machine_skin", 20, helper -> {
            List<String> problems = new ArrayList<>();
            int checked = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                if (!(block instanceof MultiblockCoreBlock core)) {
                    continue;
                }
                checked++;
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                Multiblock blueprint = core.blueprint();
                Map<Integer, String> seen = new LinkedHashMap<>();

                // The core sits at the origin and is part of the machine's surface, so it takes an
                // index too. Leave it out and every cell is numbered as if the core were not there.
                List<Vec3i> offsets = new ArrayList<>();
                offsets.add(Vec3i.ZERO);
                for (Multiblock.Cell cell : blueprint.cells()) {
                    offsets.add(cell.offset());
                }

                for (Vec3i offset : offsets) {
                    int index = blueprint.cellIndex(offset);
                    if (index < 0) {
                        problems.add(id + " cell " + offset + " is outside its own bounds");
                        continue;
                    }
                    if (index >= MultiblockSkinnedBlock.MAX_CELLS) {
                        problems.add(id + " cell " + offset + " indexes " + index + ", past the "
                            + MultiblockSkinnedBlock.MAX_CELLS + "-cell ceiling");
                    }
                    String previous = seen.put(index, offset.toString());
                    if (previous != null && !previous.equals(offset.toString())) {
                        problems.add(id + " indexes " + previous + " and " + offset + " both to "
                            + index);
                    }
                }
            }
            helper.assertTrue(checked > 0,
                "no multiblock cores were found - discovery is broken, so this would pass against a "
                    + "machine that does not fit at all");
            helper.assertTrue(problems.isEmpty(),
                "machines that do not fit the skin index (" + problems.size() + "): " + problems);

            // The Separator's exact numbering, pinned. tools/skin_machine.py computes this ordering
            // independently in Python to cut the art, and the two agreeing is the whole contract: if
            // they drift, every cell wears some other cell's tile and the machine's skin scrambles.
            // That reads as bad art, not as an off-by-one, so nothing would point at either side.
            Multiblock separator = RCBlocks.SEPARATOR.get().blueprint();
            List<String> order = new ArrayList<>();
            for (Vec3i offset : separator.skinOrder()) {
                order.add(offset.getX() + "," + offset.getY() + "," + offset.getZ());
            }
            helper.assertTrue(order.equals(List.of(
                    "0,0,0", "1,0,0", "2,0,0", "0,0,1", "1,0,1", "2,0,1",
                    "0,1,0", "1,1,0", "2,1,0", "0,1,1", "1,1,1", "2,1,1")),
                "the Separator's skin order changed to " + order + ". tools/skin_machine.py cuts its "
                    + "art against the old one, so the machine's skin is now scrambled - re-run it");
            helper.succeed();
        });
    }

}
