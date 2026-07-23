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
    // The three sides of the contested frontier. All three are tags rather than hardcoded
    // block lists so a pack can retune what counts as junk, what shelters, and what covers,
    // without a mod release - the same data-driven-first rule the pull tables follow.

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
