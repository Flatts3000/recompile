package com.flatts.recompile.compat;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Reads what a pull / find loot table can yield, for the JEI categories. Server-safe (no
 * client or JEI types), so it can be unit-tested by a GameTest.
 *
 * <p><b>Why parse the bundled JSON</b> instead of sampling the live table: loot tables
 * are not synced to clients, so a client-side JEI plugin cannot read the loaded table on a
 * dedicated server. The bundled default is deterministic, works everywhere, and needs no
 * server. The trade-off is that a datapack that retunes a pull table is not reflected in
 * JEI - acceptable for the mod's own tables; revisit if pack-tuned pulls need to show.
 *
 * <p>Chance is the entry weight over the pool total, times the pool's own chance condition, times
 * whatever share a parent table passed down. {@code set_count} functions (e.g. scrap metal 1-2) are
 * ignored for display - the item and its odds are the point.
 *
 * <p><b>Three entry shapes are understood, and the last two were added because the alternative was a
 * silent lie.</b> A {@code minecraft:item} is itself. A {@code minecraft:loot_table} entry is followed
 * into the table it names, with the parent's share multiplied in - Bulky Waste is two nested tiers now,
 * and skipping those entries would have shown a Prying category containing nothing but paintings. A
 * {@code minecraft:tag} entry reports every member of the tag at the entry's own share - see
 * {@link #expandTag} for why that is the same number for both forms of the entry.
 *
 * <p>Anything else is skipped rather than guessed at, and a table that references itself is cut off at
 * the first repeat.
 */
public final class SortingData {

    /**
     * Whether a nested loot table's own {@code neoforge:conditions} are satisfied here.
     *
     * <p>This class reads bundled FILES, so it sees tables the game did not load. The evaluation
     * itself is {@code RecipeFiles.conditionsHold} rather than a copy of it: review of #277 found
     * this method duplicated there byte for byte, and two evaluators of a condition that fails
     * silently drift apart without either one saying so.
     */
    private static boolean conditionsHold(String path) {
        JsonObject obj = json(path);
        return obj == null || RecipeFiles.conditionsHold(obj);
    }

    /** One bundled JSON off the classpath, or null if it cannot be read or parsed. */
    private static @Nullable JsonObject json(String path) {
        try (InputStream in = SortingData.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (Exception unreadable) {
            return null;
        }
    }

    /**
     * Items that an ACTIVE global loot modifier removes from every roll, so no viewer offers them.
     *
     * <p><b>Why a viewer has to know about loot modifiers at all.</b> This class models the drops a
     * player will actually see, and a {@code recompile:strip_item} modifier deletes an item from every
     * loot roll in the game after the table has produced it. A table that lists the item is telling
     * the truth about itself and a lie about the world.
     *
     * <p>That is not hypothetical: the Sky Stone Shard is named unconditionally in
     * {@code slag_rubble_pulls} and stripped again when AE2 is absent, so without this the census
     * predicted it at 1 in 27 while the game gave 0 - caught by
     * {@code pull_rates_match_what_the_mod_claims}, which exists for exactly this disagreement.
     *
     * <p>Read from the folder rather than a list of known modifiers, on the {@code TeardownData}
     * precedent: a hardcoded inventory of the same facts is a second copy, and the copy nobody
     * remembers to update is always the one that gets read.
     */
    private static java.util.Set<Item> strippedItems() {
        java.util.Set<Item> stripped = new java.util.HashSet<>();
        for (JsonObject modifier : RecipeFiles.folder(MODIFIER_ANCHOR)) {
            if (!isType(modifier, "recompile:strip_item") || !modifier.has("item")) {
                continue;
            }
            if (!RecipeFiles.conditionsHold(modifier)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getValue(
                Identifier.parse(modifier.get("item").getAsString()));
            if (item != Items.AIR) {
                stripped.add(item);
            }
        }
        return stripped;
    }

    /**
     * Any modifier file, used to find the folder the rest live in.
     *
     * <p>{@code loot_modifiers} is NeoForge's directory and is PLURAL - not one of the vanilla data
     * directories 26.1 singularised. Correcting it to match {@code loot_table/} would silently read
     * nothing here and silently stop the modifiers loading in the game.
     */
    private static final String MODIFIER_ANCHOR =
        "/data/" + Recompile.MOD_ID + "/loot_modifiers/no_saplings.json";


    /** The household pull stream: garbage blocks and compacted bales draw from it. */
    public static final String HOUSEHOLD = "/data/recompile/loot_table/gameplay/household_pulls.json";
    /** The trash-bag pull stream. */
    public static final String BAG = "/data/recompile/loot_table/gameplay/bag_pulls.json";

    /** Mechanical Waste's stream: industrial scrap, and the only source of the Motor. */
    public static final String MECHANICAL = "/data/recompile/loot_table/gameplay/mechanical_pulls.json";

    /** The demolition yard's stream: stone shards rather than household scrap. */
    public static final String RUBBLE = "/data/recompile/loot_table/gameplay/rubble_pulls.json";
    /** Bulky Waste's find table (broken open with the prybar). */
    public static final String BULKY = "/data/recompile/loot_table/blocks/bulky_waste.json";

    /** What a Cutting Torch gets out of a Steel I-Beam. */
    public static final String STEEL_BEAM = "/data/recompile/loot_table/blocks/steel_i_beam.json";

    /** The Hydroponics Bay's seedling lottery: which plant an Unknown Seedling turns out to be. */
    public static final String SEEDLING = "/data/recompile/loot_table/gameplay/hydroponics_seedling.json";

    /**
     * Every species the pull streams can stamp onto a piece of Amber (#294), in table order.
     *
     * <p>Read from the bundled loot JSON for the same reason everything else in this class is: loot
     * tables are not client-synced, and JEI needs this on the client to build one sequencing page and
     * one spawn-egg page per creature. Listing them in Java instead would be a second source of truth
     * for the one thing the tables already say.
     */
    public static List<Identifier> amberSpecies() {
        List<Identifier> species = new ArrayList<>();
        java.util.Set<Item> stripped = strippedItems();
        for (String table : List.of(HOUSEHOLD, BAG)) {
            // The same three checks every other reader in this class makes, and it shipped without
            // them. A table whose conditions do not hold is not loaded by the game, so listing its
            // species puts creatures in JEI that no player can find; a strip modifier deleting amber
            // does the same thing one layer down; and reading a `recompile:species` component off an
            // entry that is not amber, or off a function that is not set_components, would invent one.
            // None of those is hypothetical here - #276 and #277 are exactly this shape.
            if (!conditionsHold(table)) {
                continue;
            }
            JsonObject root = json(table);
            if (root == null || !root.has("pools")) {
                continue;
            }
            for (JsonElement rawPool : root.getAsJsonArray("pools")) {
                JsonObject pool = rawPool.getAsJsonObject();
                if (!pool.has("entries")) {
                    continue;
                }
                for (JsonElement rawEntry : pool.getAsJsonArray("entries")) {
                    JsonObject entry = rawEntry.getAsJsonObject();
                    if (!entry.has("name") || !entry.has("functions")) {
                        continue;
                    }
                    Item item = BuiltInRegistries.ITEM.getValue(
                        Identifier.parse(entry.get("name").getAsString()));
                    if (item != RCItems.AMBER.get() || stripped.contains(item)) {
                        continue;
                    }
                    for (JsonElement rawFn : entry.getAsJsonArray("functions")) {
                        JsonObject fn = rawFn.getAsJsonObject();
                        if (!fn.has("function")
                                || !"minecraft:set_components".equals(fn.get("function").getAsString())
                                || !fn.has("components")) {
                            continue;
                        }
                        JsonObject components = fn.getAsJsonObject("components");
                        if (!components.has("recompile:species")) {
                            continue;
                        }
                        Identifier id =
                            Identifier.tryParse(components.get("recompile:species").getAsString());
                        if (id != null && !species.contains(id)) {
                            species.add(id);
                        }
                    }
                }
            }
        }
        return List.copyOf(species);
    }

    /**
     * One possible output and how likely it is (0..1).
     *
     * <p>{@code variants} is normally just {@code [stack]}. It is longer only where
     * {@link #visibleOutputs} has collapsed a run of entries that are the same object wearing
     * different data - the 29 species of Amber - into a single slot for JEI to cycle, the way a tag
     * input already cycles its accepted items. {@code stack} stays a single representative so every
     * existing {@code .stack().is(...)} check reads the same as before.
     */
    public record Weighted(ItemStack stack, float chance, List<ItemStack> variants) {
        public Weighted(ItemStack stack, float chance) {
            this(stack, chance, List.of(stack));
        }
    }

    private SortingData() {
    }

    /** Weighted item outputs of a bundled loot table, or empty if it cannot be read. */
    /**
     * Every block a player can pick through, paired with the stream it draws from.
     *
     * <p><b>Derived from the block registry, deliberately.</b> The JEI Sorting category used to hold
     * a hand-written list of four, and Mechanical Waste was never added to it - so clicking Magnet
     * Scrap in JEI showed <i>nothing at all</i>. Block drops are invisible to JEI and no recipe makes
     * the stuff, so the panel was simply empty, which reads as a broken item rather than a missing
     * entry. Quartz Grit, Spent Abrasive and the Motor were in the same state.
     *
     * <p>Deriving it means a new pile variant is covered the day it is registered, which is precisely
     * what a list maintained by hand failed to do.
     */
    public static List<SortingSource> sortingSources() {
        List<SortingSource> sources = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof SortableBlock)) {
                continue;
            }
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!Recompile.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            ResourceKey<LootTable> table = SortableBlock.pullTableOf(block);
            if (table != null) {
                sources.add(new SortingSource(block, pathFor(table)));
            }
        }
        return List.copyOf(sources);
    }

    /** A sortable block and the classpath path of the pull stream it draws from. */
    public record SortingSource(Block block, String path) {}

    /** {@code recompile:gameplay/household_pulls} to the bundled JSON this class reads. */
    public static String pathFor(ResourceKey<LootTable> table) {
        Identifier id = table.identifier();
        return "/data/" + id.getNamespace() + "/loot_table/" + id.getPath() + ".json";
    }

    public static List<Weighted> outputs(String resourcePath) {
        return outputs(resourcePath, null);
    }

    /**
     * What a <b>viewer</b> is allowed to show: {@link #outputs} minus {@code #recompile:undiscoverable}.
     *
     * <p><b>Finding a Puzzle Cube piece in a bag of rubbish should be a surprise</b> (owner,
     * 2026-08-04), and a JEI category that lists every collectible and all six recovered paintings
     * spends that surprise before the player has broken a single block. The odds are the point of these
     * categories for ordinary materials and the opposite of the point for treasure.
     *
     * <p>Hidden from the <b>salvage categories only</b>, not from JEI's item list. A player who has
     * found a piece still needs to look up that nine of them make the cube; hiding the item outright
     * would take that away to protect a surprise they have already had.
     *
     * <p>This is deliberately NOT done inside {@link #outputs}. That method answers "what can this
     * table produce", which the GameTests use to prove things are reachable - a reachability check that
     * silently skipped treasure would be worse than no check. Presentation filters; the data does not
     * lie.
     */
    public static List<Weighted> visibleOutputs(String resourcePath,
            HolderLookup.@Nullable Provider registries) {
        List<Weighted> out = new ArrayList<>();
        for (Weighted weighted : outputs(resourcePath, registries)) {
            if (!weighted.stack().is(RCTags.UNDISCOVERABLE)) {
                out.add(weighted);
            }
        }
        return collapseStamped(out);
    }

    /**
     * Merge entries that are the same ITEM differing only by which creature is stamped on them.
     *
     * <p><b>29 ambers is not 29 things to find.</b> Every species is its own loot entry, so a sorting
     * page drew 29 identical orange slots that filled the category and buried the twelve materials
     * above them. What the player is deciding from is one number - how often amber turns up at all -
     * and that number was the one thing the page did not show, because it was split 29 ways.
     *
     * <p>So they collapse to one slot carrying the summed chance, with every species kept as a cycling
     * variant. Nothing is hidden and no tooltip lies: the slot rotates through the real stamped stacks,
     * each naming its own creature. Stripping the component instead would have been the smaller change
     * and a false one - an Amber with no species reads "Clear, with nothing inside", and no amber the
     * pull streams produce is empty.
     *
     * <p>Keyed on the component rather than on the item, so this is not a rule about Amber: anything
     * stamped the same way collapses the same way the day it is added.
     *
     * <p>Presentation only. {@link #outputs} still reports every entry, which is what the reachability
     * GameTests measure and what {@link #species} reads to build the Sequencer's list.
     */
    private static List<Weighted> collapseStamped(List<Weighted> entries) {
        List<Weighted> out = new ArrayList<>();
        Map<Item, Integer> firstSeen = new HashMap<>();
        for (Weighted weighted : entries) {
            if (weighted.stack().get(RCDataComponents.SPECIES.get()) == null) {
                out.add(weighted);
                continue;
            }
            Integer at = firstSeen.get(weighted.stack().getItem());
            if (at == null) {
                firstSeen.put(weighted.stack().getItem(), out.size());
                List<ItemStack> variants = new ArrayList<>();
                variants.add(weighted.stack());
                out.add(new Weighted(weighted.stack(), weighted.chance(), variants));
                continue;
            }
            Weighted merged = out.get(at);
            // The list is the live one built above, so appending here grows that slot's cycle.
            merged.variants().add(weighted.stack());
            out.set(at, new Weighted(merged.stack(), merged.chance() + weighted.chance(),
                merged.variants()));
        }
        return out;
    }

    /** As {@link #visibleOutputs(String, HolderLookup.Provider)}, without registry access. */
    public static List<Weighted> visibleOutputs(String resourcePath) {
        return visibleOutputs(resourcePath, null);
    }

    /**
     * As {@link #outputs(String)}, but able to resolve datapack-registry components.
     *
     * <p>Pass a registry when one is available (JEI has the client level's) and painting entries show
     * the actual artwork. Pass null and they show a plain painting at the same odds.
     */
    public static List<Weighted> outputs(String resourcePath,
            HolderLookup.@Nullable Provider registries) {
        List<Weighted> out = new ArrayList<>();
        collect(resourcePath, 1.0F, registries, out, new java.util.HashSet<>());

        // A table lists what it can produce; a global loot modifier decides what survives the roll.
        // Applied here rather than inside collect so it covers nested tables and tag expansions too -
        // stripping is global, and a viewer that honoured it at only one depth would be a subtler
        // version of not honouring it at all.
        java.util.Set<Item> stripped = strippedItems();
        if (!stripped.isEmpty()) {
            out.removeIf(weighted -> stripped.contains(weighted.stack().getItem()));
        }
        return out;
    }

    /**
     * Walk one table, adding every item it can produce at its true odds.
     *
     * @param share      the probability of reaching this table at all, from its parents
     * @param visited    resource paths already on this branch, so a table that references itself
     *                   stops rather than recursing until the stack gives out
     */
    private static void collect(String resourcePath, float share,
            HolderLookup.@Nullable Provider registries, List<Weighted> out,
            java.util.Set<String> visited) {
        if (!visited.add(resourcePath)) {
            return;
        }
        try (InputStream in = SortingData.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return;
            }
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement poolEl : root.getAsJsonArray("pools")) {
                JsonObject pool = poolEl.getAsJsonObject();
                // A pool can be gated behind a chance condition, and ignoring it overstates every entry
                // inside. Bulky Waste's painting pool is 7%, so its six paintings are ~1.2% each - JEI
                // would have shown 16.7%, a 14x lie, and the same shape of wrong as telling players the
                // Burn Barrel smelts iron. The odds ARE the information in these categories.
                float poolChance = poolChance(pool);
                JsonArray entries = pool.getAsJsonArray("entries");
                int total = 0;
                for (JsonElement e : entries) {
                    // Count EVERY entry's weight, including minecraft:empty. A rare-bonus pool (a big
                    // empty weight + one rare item) has to divide by the full weight, or the item reads
                    // as 100% of its pool instead of its true 1-in-N odds.
                    total += effectiveWeight(e.getAsJsonObject());
                }
                if (total == 0) {
                    continue;
                }
                for (JsonElement e : entries) {
                    JsonObject o = e.getAsJsonObject();
                    float entryShare = share * poolChance * weight(o) / total;
                    if (isItem(o)) {
                        Item item = BuiltInRegistries.ITEM.getValue(
                            Identifier.parse(o.get("name").getAsString()));
                        if (item != Items.AIR) {
                            out.add(new Weighted(
                                withComponents(registries, new ItemStack(item), o), entryShare));
                        }
                    } else if (isType(o, "minecraft:loot_table") && o.has("value")) {
                        // A nested table can be CONDITIONAL, and a condition on a whole loot table
                        // file is honoured by the game (unlike one on a pool, an entry, or a tag).
                        // Following one whose condition fails models drops that cannot happen:
                        // pull_rates_match_what_the_mod_claims caught exactly that when the AE2 sky
                        // stone drop landed (#276) - predicted 1 in 27, the game gave 0.
                        String nested = pathOf(o.get("value").getAsString());
                        if (conditionsHold(nested)) {
                            collect(nested, entryShare, registries, out, visited);
                        }
                    } else if (isType(o, "minecraft:tag") && o.has("name")) {
                        expandTag(o.get("name").getAsString(), entryShare, out);
                    } else if (isType(o, "minecraft:alternatives") && o.has("children")) {
                        alternatives(o.getAsJsonArray("children"), entryShare, registries, out);
                    }
                }
            }
        } catch (Exception ex) {
            // A malformed table costs a category, not a crash.
        } finally {
            visited.remove(resourcePath);
        }
    }

    /** {@code recompile:gameplay/bulky_spine} to the classpath path its JSON lives at. */
    private static String pathOf(String id) {
        Identifier parsed = Identifier.parse(id);
        return "/data/" + parsed.getNamespace() + "/loot_table/" + parsed.getPath() + ".json";
    }

    /**
     * Split a tag entry's share evenly across the tag's members.
     *
     * <p><b>Every member gets the entry's FULL share, and that is true of both forms of the entry -
     * for opposite reasons.</b> Review of #279 caught this method dividing the share by the member
     * count, on the belief that {@code expand: false} rolls once and then picks a member. It does not.
     * From {@code net.minecraft.world.level.storage.loot.entries.TagEntry} in 26.1.2:
     *
     * <pre>public void createItemStack(Consumer&lt;ItemStack&gt; output, LootContext context) {
     *     BuiltInRegistries.ITEM.getTagOrEmpty(this.tag).forEach(item -&gt; output.accept(new ItemStack(item)));
     * }</pre>
     *
     * <p>So {@code expand: false} is ONE entry that emits ALL members together when it wins, and each
     * member's chance of appearing is the entry's chance: {@code weight / total}. {@code expand: true}
     * instead emits one entry PER MEMBER, each at the entry's weight - mutually exclusive, but the
     * denominator grew to match (see {@link #effectiveWeight}), so each member is again
     * {@code weight / total}. Same figure, different mechanism.
     *
     * <p>The mod's own data said so all along: {@code chests/sump.json} records that
     * {@code expand: false} "yields EVERY item in the tag at once rather than picking one", measured
     * at 16 of 16 in #268. The code contradicted its own measurement.
     *
     * <p><b>Nothing shipped exercises this today</b>, which is why no test caught it: the only tag
     * entry in the mod is that sump pool, and this class reads pull streams rather than chest tables.
     * {@code no_unexercised_tag_entry_reaches_the_viewer} fails the build if that stops being true, so
     * the untested path cannot be relied on without coverage arriving with it.
     */
    private static void expandTag(String id, float share, List<Weighted> out) {
        Identifier parsed = Identifier.tryParse(id.startsWith("#") ? id.substring(1) : id);
        if (parsed == null) {
            return;
        }
        var tag = BuiltInRegistries.ITEM.get(net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, parsed));
        if (tag.isEmpty()) {
            return;
        }
        var members = tag.get().stream().toList();
        if (members.isEmpty()) {
            return;
        }
        for (var holder : members) {
            out.add(new Weighted(new ItemStack(holder.value()), share));
        }
    }

    /**
     * What an entry contributes to its pool's total weight, which is not always its {@code weight}.
     *
     * <p><b>A {@code minecraft:tag} entry with {@code expand: true} contributes one entry PER MEMBER</b>,
     * so its real contribution is weight times member count - and <b>nothing at all when the tag is
     * empty</b>, which is what makes an absent mod's tag cost the pool nothing. Modelling it as a flat
     * {@code weight} inflates the denominator and quietly understates every other entry in the pool:
     * caught by {@code pull_rates_match_what_the_mod_claims} when the Ender IO grains entry landed,
     * which put all seven of Mechanical Waste's real drops out by 1.1x (247 against the game's 227).
     *
     * <p>{@code expand: false} always contributes exactly one entry whether the tag has members or not,
     * so its weight counts in full - and an empty tag then wins rolls and yields nothing, which is why
     * the grains entry uses {@code expand: true}.
     */
    private static int effectiveWeight(JsonObject entry) {
        if (!isType(entry, "minecraft:tag") || !entry.has("name")
            || !entry.has("expand") || !entry.get("expand").getAsBoolean()) {
            return weight(entry);
        }
        return weight(entry) * tagSize(entry.get("name").getAsString());
    }

    /** How many items a tag holds right now; 0 if it does not exist in this install. */
    private static int tagSize(String id) {
        Identifier parsed = Identifier.tryParse(id.startsWith("#") ? id.substring(1) : id);
        if (parsed == null) {
            return 0;
        }
        var tag = BuiltInRegistries.ITEM.get(net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, parsed));
        return tag.map(named -> named.size()).orElse(0);
    }

    /**
     * The interesting outcomes of an {@code alternatives} entry: its <b>conditioned</b> children.
     *
     * <p>Alternatives are tried in order and the first whose conditions pass wins, so they are not a
     * probability split - each child is the whole outcome under its own condition, and gets the parent's
     * full share rather than a fraction of it.
     *
     * <p>The unconditioned child is skipped on purpose. In a block table that is the ordinary self-drop
     * - the Steel I-Beam handing itself back to a pickaxe - and "you get the block you broke" is not a
     * salvage outcome; showing it in a Cutting Torch category would say the torch returns beams, which
     * it never does. If a table ever needs that fallback surfaced, this is the line to revisit.
     */
    private static void alternatives(JsonArray children, float share,
            HolderLookup.@Nullable Provider registries, List<Weighted> out) {
        for (JsonElement childEl : children) {
            JsonObject child = childEl.getAsJsonObject();
            if (!child.has("conditions")) {
                continue;
            }
            if (isItem(child)) {
                Item item = BuiltInRegistries.ITEM.getValue(
                    Identifier.parse(child.get("name").getAsString()));
                if (item != Items.AIR) {
                    out.add(new Weighted(withComponents(registries, new ItemStack(item), child), share));
                }
            }
        }
    }

    private static boolean isType(JsonObject entry, String type) {
        return entry.has("type") && type.equals(entry.get("type").getAsString());
    }

    /**
     * A pool's own probability of rolling at all, from a {@code minecraft:random_chance} condition.
     *
     * <p>Returns 1 when the pool is unconditional. Only this one condition is understood on purpose:
     * anything else (a tool check, an enchantment) is not a probability and cannot honestly be folded
     * into a percentage, so a pool carrying one is reported at its raw weight rather than guessed at.
     */
    private static float poolChance(JsonObject pool) {
        if (!pool.has("conditions")) {
            return 1.0F;
        }
        float chance = 1.0F;
        for (JsonElement c : pool.getAsJsonArray("conditions")) {
            JsonObject o = c.getAsJsonObject();
            if (o.has("condition") && "minecraft:random_chance".equals(o.get("condition").getAsString())
                    && o.has("chance")) {
                chance *= o.get("chance").getAsFloat();
            }
        }
        return chance;
    }

    /**
     * Apply a {@code minecraft:set_components} function, so the shown stack is the thing you actually
     * find rather than a generic one.
     *
     * <p>Six recovered paintings are six entries of {@code minecraft:painting} distinguished only by
     * their components. Ignoring those made JEI's Prying category show six identical blank canvases -
     * technically the right item, and useless: a player looking up where the Mona Lisa comes from would
     * see a row of anonymous paintings.
     *
     * <p>Only {@code set_components} is understood, and only for components that can be read back out
     * of JSON without a registry. Anything else is skipped rather than guessed at, and the entry still
     * shows with its correct odds.
     */
    private static ItemStack withComponents(HolderLookup.@Nullable Provider registries,
            ItemStack stack, JsonObject entry) {
        if (!entry.has("functions")) {
            return stack;
        }
        for (JsonElement fnEl : entry.getAsJsonArray("functions")) {
            JsonObject fn = fnEl.getAsJsonObject();
            if (!fn.has("function")
                    || !"minecraft:set_components".equals(fn.get("function").getAsString())
                    || !fn.has("components")) {
                continue;
            }
            JsonObject components = fn.getAsJsonObject("components");
            if (components.has("minecraft:painting/variant")) {
                paintingVariant(registries, components.get("minecraft:painting/variant").getAsString())
                    .ifPresent(v -> stack.set(DataComponents.PAINTING_VARIANT, v));
            }
            // Amber (#294) is the same shape as the paintings above and arrived four years' worth of
            // entries deeper: 29 pieces in household_pulls distinguished ONLY by which creature is in
            // them. Skipping the component made the Sorting category show 29 anonymous Amber rows at
            // 0.014% each, which is technically the right item and tells a player nothing about the
            // one thing they came to look up. A plain Identifier, so it reads straight back out of
            // JSON with no registry.
            if (components.has("recompile:species")) {
                Identifier species =
                    Identifier.tryParse(components.get("recompile:species").getAsString());
                if (species != null) {
                    stack.set(RCDataComponents.SPECIES.get(), species);
                }
            }
        }
        return stack;
    }

    /**
     * Look a painting variant up by id, if a registry is available.
     *
     * <p>Painting variants are a <b>datapack registry</b>, not a built-in one, so unlike an item they
     * cannot be resolved from a static registry - there is no {@code BuiltInRegistries.PAINTING_VARIANT}.
     * This class is deliberately level-free (it parses bundled JSON so the JEI categories work in
     * singleplayer and on a server alike, and so a GameTest can cover it), so the lookup is optional:
     * with a registry the category shows the actual artwork, without one it shows a plain painting at
     * the correct odds. Degrading is better than either crashing or dragging a level in here.
     */
    private static Optional<Holder<PaintingVariant>> paintingVariant(
            HolderLookup.@Nullable Provider registries, String id) {
        if (registries == null) {
            return Optional.empty();
        }
        return registries.lookup(Registries.PAINTING_VARIANT)
            .flatMap(lookup -> lookup.get(ResourceKey.create(
                Registries.PAINTING_VARIANT, Identifier.parse(id))))
            .map(h -> (Holder<PaintingVariant>) h);
    }

    private static boolean isItem(JsonObject entry) {
        return entry.has("type") && "minecraft:item".equals(entry.get("type").getAsString());
    }

    private static int weight(JsonObject entry) {
        return entry.has("weight") ? entry.get("weight").getAsInt() : 1;
    }
}
