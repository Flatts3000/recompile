package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/** Tag keys this mod defines. Vanilla tags it merely contributes to live in JSON only. */
public final class RCTags {

    private RCTags() {
    }

    /**
     * Blocks the scrap knife cuts free quickly - the knife's answer to
     * {@code minecraft:mineable/shovel}.
     *
     * <p>Every garbage block has exactly one tool: garbage digs with the junk shovel, an
     * appliance is pried, and a bale is the knife's. The knife already <em>opens</em> a
     * bale in place, but a bale is also the Sorting Tarp's best input, and harvesting one
     * meant mining it with nothing that helps - 27 ticks, which made the richest block in
     * the game the slowest to actually cash in. Giving the knife a mining rule over this
     * tag lets it cut a bale loose as well as open it, without handing the shovel a job
     * that is not its own.
     */
    public static final TagKey<Block> MINEABLE_WITH_KNIFE = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "mineable/knife"));

    /**
     * Blocks the prybar levers apart quickly - the third leg of the one-tool-per-block
     * rule, after the junk shovel's garbage and the scrap knife's bales.
     *
     * <p>The Scrap Barrel is welded sheet steel. A vanilla barrel is wood and answers to
     * an axe; this one has no more business with an axe than with a shovel, and prying a
     * seam apart is exactly what the prybar is for.
     */
    public static final TagKey<Block> MINEABLE_WITH_PRYBAR = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "mineable/prybar"));

    /**
     * Blocks the Sledgehammer crushes (demolition yard): Reinforced Concrete. You crush concrete but you
     * cut steel, so this is the sledge's tag; steel gets its own ({@code mineable/cutting_torch}). Solid,
     * {@code requiresCorrectToolForDrops}, so bare hands and the wrong tool yield nothing.
     */
    public static final TagKey<Block> MINEABLE_WITH_SLEDGEHAMMER = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "mineable/sledgehammer"));

    /**
     * Blocks the Cutting Torch cuts (demolition yard): Steel I-Beams. You crush concrete but you <em>cut</em>
     * steel, so this is the torch's tag, separate from {@code mineable/sledgehammer}. Solid,
     * {@code requiresCorrectToolForDrops}, so bare hands, the sledgehammer, and the wrong tool yield nothing.
     */
    public static final TagKey<Block> MINEABLE_WITH_CUTTING_TORCH = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "mineable/cutting_torch"));

    // ---------------- Encroachment (P1.7-R): the junkyard fights back ----------------
    // Every side of the contested frontier is a tag rather than a hardcoded block list, and
    // each of those tags is itself built from *other* tags wherever one exists. That is what
    // makes the system survive contact with the wider mod ecosystem: a chisel-style mod that
    // adds forty dirt variants joins #minecraft:dirt and is covered without a mod release,
    // where a block-id list would silently ignore every one of them.

    /**
     * What unhealed ground can take: the dirt family, via {@code #minecraft:substrate_overworld}
     * (plus the convention tag where a pack supplies one). Explicitly an allowlist rather than
     * "anything that is not junk", so an unrecognised modded block is never eaten by default.
     *
     * <p><b>Not {@code #minecraft:dirt}</b> - 26.1 narrowed that tag to dirt, coarse dirt and
     * rooted dirt, so it does not contain grass. {@code substrate_overworld} is the union that
     * still means "overworld ground": {@code #dirt + #mud + #moss_blocks + #grass_blocks}. Mods
     * add their variants to those sub-tags, so the family stays correct without a mod release.
     *
     * <p>Note this covers plain {@code dirt} as well as grass, which closes a loophole: a
     * rung-1 spreader that leaves bare dirt at the frontier does not get a free pass.
     *
     * <p><b>Farmland is added on top of the vanilla tag</b> (vanilla does not count it as
     * substrate), but only dry farmland is ever taken - see {@code RCEncroachment.isMoist}.
     * <b>Dirt paths are deliberately left out</b> entirely.
     */
    public static final TagKey<Block> ENCROACHABLE = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "encroachable"));

    /**
     * Carved back out of {@link #ENCROACHABLE}, because tags can union but cannot subtract.
     * Ships with two entries and both are load-bearing:
     *
     * <ul>
     *   <li><b>coarse dirt</b> - the revert target itself. Without this the sweep would churn
     *       bare ground into bare ground forever.</li>
     *   <li><b>mycelium</b> - the substrate {@code MyceliumPatchFeature} places and dump
     *       mushrooms grow on. It is the forage half of the P1.9 food tier, so letting the
     *       junkyard eat it would quietly erode the only renewable food in the world.</li>
     * </ul>
     */
    public static final TagKey<Block> ENCROACHMENT_IMMUNE = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "encroachment_immune"));

    /**
     * Unhealed ground: what a healed block has to be touching to be on the frontier at all.
     * Coarse dirt (the world's universal surface) plus the garbage blocks, so a mound you
     * have not finished quarrying presses on your grass exactly like bare earth does.
     */
    public static final TagKey<Block> HOSTILE_GROUND = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "hostile_ground"));

    /**
     * Rung 3, the thing that ends the fight: logs and leaves. Grass with one of these in
     * range is permanent, which is what finally makes wood-as-treasure (P2.4 item 4)
     * load-bearing - the first forest is what locks a border, not just a trophy.
     */
    public static final TagKey<Block> FRONTIER_ANCHOR = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "frontier_anchor"));

    /**
     * Rung 2, the thing that buys a turn: flowers, grasses, ferns, saplings. Cover does not
     * lower a probability, it <em>absorbs the hit</em> - the encroachment strips the plant and
     * leaves the soil. So the border visibly goes bare before it goes brown, which is a warning
     * the player can read instead of a dice roll they cannot.
     */
    public static final TagKey<Block> FRONTIER_COVER = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "frontier_cover"));

    /**
     * Where the junkyard fights back at all. Gating on a biome tag keeps the sweep inert in a
     * vanilla overworld (where coarse dirt occurs naturally and would otherwise make badlands
     * grass rot), and lets a pack extend encroachment to new garbage regions in Phase 4 by
     * adding a line of JSON.
     */
    public static final TagKey<Biome> ENCROACHES = TagKey.create(
        Registries.BIOME, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "encroaches"));

    /**
     * What the Grass Spreader can turn into grass (P2.4-R3). Ships as coarse dirt and plain dirt.
     *
     * <p><b>Plain dirt is in here deliberately.</b> Vanilla grass cannot spread onto coarse dirt but
     * <em>can</em> spread onto dirt, so if the spreader left dirt as an intermediate, vanilla would
     * finish the job for free and break P2.4-R item 3's "nothing renews on its own". For the same
     * reason the conversion goes straight to grass and never stops at dirt.
     *
     * <p>Mycelium is excluded via {@link #ENCROACHMENT_IMMUNE} rather than by omission here - it is
     * inside the vanilla dirt family, and it is the dump-mushroom substrate, so paving it would eat
     * the P1.9 forage economy. Same carve-out encroachment makes, for the same reason.
     */
    public static final TagKey<Block> SPREADABLE = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "spreadable"));

    /**
     * Carved back out of {@link #SPREADABLE}: mycelium, the dump-mushroom substrate, so a spreader
     * never paves over the P1.9 forage economy.
     *
     * <p><b>Deliberately not {@link #ENCROACHMENT_IMMUNE}</b>, which looks like the same idea and is
     * not: that tag contains <em>coarse dirt</em>, because coarse dirt is what encroachment reverts
     * <em>to</em>. Here coarse dirt is the primary target, so reusing that tag would make the machine
     * refuse the one block it exists to convert. Two tags because the two systems mean opposite
     * things by "immune".
     */
    public static final TagKey<Block> SPREAD_IMMUNE = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "spread_immune"));

    /**
     * What a Scrap Bin accepts (P2.9): the raw materials pulled from garbage. The default membership
     * (in JSON) is exactly the seven-material vocabulary - scrap metal, plastic, glass shards, muck,
     * fiber, e-scrap, junk - and excludes the pull stream's non-raw outputs (rebar, tin cans, glass
     * bottles), crafted intermediates, finds, food, and tools. Open by design: a pack adds modded
     * scrap to this tag without a mod release. An item tag, not a block tag, because it gates what an
     * item can become - the bin's contents.
     *
     * <p>Since #68 it also includes {@link #STONE_SHARDS} by tag reference, so the demolition yard's
     * base materials store like the household ones. The rule is "base material of a pull stream", not
     * "household material" - a shard is what sifting rubble yields, exactly as scrap metal is what
     * sifting garbage yields. Cut products and crafted intermediates (Steel Offcut, ingots, rebar) stay
     * out, same as they always were on the household side.
     */
    public static final TagKey<Item> BINNABLE = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "binnable"));

    /**
     * The demolition yard's seven stone shards - what Sifting Rubble yields, and the crafting input for
     * the vanilla stone family. Already shipped as JSON; declared here so Java can assert against it.
     */
    public static final TagKey<Item> STONE_SHARDS = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "stone_shards"));

    /**
     * Finished goods: things a person would throw away, which the dump gives you and no recipe makes
     * (design P2.11, issue #161). Owner ruling 2026-08-08 on the case that decided it: <b>players should
     * find buckets, not craft them.</b>
     *
     * <p>The test is <i>would a person throw this away?</i> A bucket, a rug, a bottle, a door, a sign:
     * yes. An iron ingot, a stone block, a dye, a plank: no - nobody discards stock. Building blocks are
     * materials and stay craftable, which is consistent rather than an exception: the stone came from
     * shards, so the dump already gave it to you.
     *
     * <p><b>A tag rather than a hardcoded list</b>, so a pack can extend the rule without a mod release -
     * the same standard the teardown schema is held to. {@code FoundNotCraftedTests} walks every recipe
     * the game has loaded and fails if anything in here can be crafted, which is what makes the rule a
     * property of the build rather than a paragraph in a document.
     *
     * <p><b>Membership is not enough on its own</b>, and that is the whole reason the sweep exists. The
     * original plan leaned on material scarcity - the idea being that vanilla's glass bottle is already
     * unobtainable because this world has no {@code minecraft:glass}. That was measured and it is false:
     * stone shards craft {@code minecraft:stone}, which is in {@code #minecraft:stone_crafting_materials},
     * which crafts a vanilla furnace, which smelts the sand that Reinforced Concrete drops. Scarcity
     * arguments die the moment anything adds the material, exactly as the iron gate's first design did
     * (#91), and neither failure announces itself. Recipes get disabled explicitly here.
     */
    public static final TagKey<Item> FOUND_ONLY = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "found_only"));

    /**
     * What the Compost Heap will take as feed (Mod Jam - the fertilizer tier). Ships with the two organics
     * the dump yields ({@code organic_muck}, {@code fiber_scrap}) plus the obvious vanilla compostables -
     * leaves, saplings, flowers, grasses and ferns, the small mushrooms, and crop matter (crops, seeds,
     * berries), pulled in through vanilla item tags where they exist so modded variants ride along. Any one
     * alone forms a layer; there is no greens/browns puzzle. Still an allowlist rather than "anything
     * organic", so an unrecognised modded item is never silently compostable by default.
     *
     * <p>Open by design and the whole reason this is a tag and not a hardcoded list: a pack adds its own
     * compostables to this tag without a mod release, exactly as a real heap would take them. An item tag,
     * not a block tag - it gates what an item in hand can do.
     */
    /**
     * What the Burn Barrel will burn, beyond food (which it takes by the FOOD component, so every vanilla
     * and modded edible works without being listed). This tag is the rest: the mod's scrap smelting, plus
     * inputs whose product is edible although they are not - kelp, notably.
     *
     * <p>The barrel is an ALLOWLIST on purpose. A denylist would need a new entry every time vanilla or a
     * pack adds a recipe, and a missed entry leaks silently - which is exactly how iron ended up
     * ungated. This one fails closed.
     */
    public static final TagKey<Item> BURN_BARREL_SMELTABLE = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "burn_barrel_smeltable"));

    /**
     * What the Hydroponics Bay can grow (#43).
     *
     * <p>A tag rather than a recipe type because the mechanic is always "this item makes more of
     * itself", so per-plant recipes would be ten copies of one sentence. It is also the extension point
     * a pack actually wants: add an item and the bay grows it.
     */
    /** Every colour of Clean Mattress, so a recipe can take any of them (#95). */
    public static final TagKey<Item> CLEAN_MATTRESSES = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "clean_mattresses"));

    public static final TagKey<Item> HYDROPONIC = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "hydroponic"));

    public static final TagKey<Item> COMPOSTABLE = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "compostable"));

    /**
     * The scrap blocks that join into one network by adjacency (P2.10): bins, barrel, sorter,
     * workbench, burn barrel and the scrap crafting table. Placed sharing a face they become one
     * connected cluster; {@code ScrapNetwork} floods this tag to route junk between them, with no
     * controller and no saved structure. A block tag, and open by design - a pack adds a modded
     * scrap block to the network without a mod release.
     *
     * <p>Membership means "conducts the network", not "is storage": only two of the six are routing
     * sinks (a Scrap Bin, and the Scrap Barrel matched by block id). The Burn Barrel is in the tag so
     * a smelter wired into the cluster still conducts, but it is deliberately never a sink - it is a
     * furnace {@code WorldlyContainer}, and routing must not land in its smelt slots.
     */
    public static final TagKey<Block> SCRAP_CONNECTABLE = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "scrap_connectable"));

    /**
     * The piles a pigeon will peck at: household garbage, bags and bales.
     *
     * <p><b>A tag, because {@code instanceof SortableBlock} was wrong</b> (playtest, 2026-08-04). Stone
     * Rubble and Mechanical Waste are sortable too, so deriving the target from the class had pigeons
     * pulling rotten flesh out of a pile of broken concrete in the demolition yard. The shared behaviour
     * is "you can pick through it"; what a bird is interested in is a different and smaller question, and
     * only a list can answer it.
     */
    public static final TagKey<Block> PIGEON_FORAGEABLE = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "pigeon_forageable"));

    /**
     * Treasure a viewer must not spoil: the collectibles and the recovered paintings.
     *
     * <p>Finding a Puzzle Cube piece in a bag of rubbish should be a surprise, and a JEI category
     * listing every collectible and all six paintings spends that before the player breaks a block.
     * The odds are the point of those categories for ordinary materials and the opposite of the point
     * for treasure.
     *
     * <p>Read only by {@code SortingData.visibleOutputs}, which is the salvage categories. It does not
     * hide the items from JEI's item list - somebody holding a piece still needs to look up that nine
     * of them make the cube - and it does not touch the loot tables, so nothing about what the world
     * actually drops changes.
     */
    public static final TagKey<Item> UNDISCOVERABLE = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "undiscoverable"));
}
