package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
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
        new Machine("tree_nursery", com.flatts.recompile.registry.RCBlocks.TREE_NURSERY));

    private GuidebookMultiblockTests() {
    }

    static void register() {
        RCGameTests.test("every_guidebook_multiblock_matches_its_blueprint", 20, helper -> {
            List<String> problems = new ArrayList<>();
            for (Machine machine : MACHINES) {
                JsonObject json = read(helper, machine.pattern());
                if (json == null) {
                    problems.add(machine.pattern() + ": no pattern file at " + ROOT);
                    continue;
                }
                MultiblockCoreBlock core = machine.core().get();
                Map<Vec3i, String> drawn = decode(helper, machine.pattern(), json, problems);
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

        // A pattern naming a block that no longer exists throws inside Modonomicon at datapack load,
        // long after this mod has decided everything is fine. Cheaper to say so here, by name.
        RCGameTests.test("every_guidebook_multiblock_names_real_blocks", 20, helper -> {
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
     * The list above is a second inventory of which machines have a page, and the copy nobody
     * remembers to update is always the one that is read. This is what makes forgetting it fail: a
     * fifth machine's page, or a fifth pattern file, and the comparison above is silently not
     * covering it.
     */
    private static void registerCoverage() {
        RCGameTests.test("every_guidebook_multiblock_is_covered_by_this_test", 20, helper -> {
            Set<String> known = new LinkedHashSet<>();
            for (Machine machine : MACHINES) {
                known.add(Recompile.MOD_ID + ":" + machine.pattern());
            }

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
                if (!known.contains(id)) {
                    problems.add("a page draws " + id + ", which this test does not check");
                }
            }
            for (String id : known) {
                if (!referenced.contains(id)) {
                    problems.add(id + " has a pattern file that no page draws");
                }
            }
            helper.assertTrue(problems.isEmpty(),
                "guide multiblock coverage gaps (" + problems.size() + "): " + problems);
            helper.succeed();
        });
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
    private static Map<Vec3i, String> decode(GameTestHelper helper, String name, JsonObject json,
            List<String> problems) {
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
}
