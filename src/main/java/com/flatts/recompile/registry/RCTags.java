package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
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
}
