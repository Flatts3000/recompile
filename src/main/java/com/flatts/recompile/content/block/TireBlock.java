package com.flatts.recompile.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A tire (spec {@code docs/tire_piles_spec.md}, #155). Slab-shaped so two make a metre and a heap is a
 * column of them, circular in model, and found in clustered dumps across the household sprawl.
 *
 * <p><b>The model is a real torus, and it took three passes to get there</b> (owner, 2026-09-04, from
 * screenshots). It was first four bars around a hollow centre and only four pixels tall against an
 * eight-pixel slab collision box, so it read as a thin frame with daylight through it that did not fill
 * its own space. It was then a plain full half block, which fixed the height and lost the circle. What
 * ships is an octagonal ring: four straight segments plus <b>the same four boxes rotated 45 degrees
 * about Y</b>, which is the one rotation a model element may carry that yields an octagon's diagonals.
 * Each segment is 6 long and 3 thick with its outer face 7 from centre, putting its corners at
 * {@code sqrt(3^2 + 7^2) = 7.62}, and the rotated copies land theirs at 7.62 too - which is what stops
 * the diagonals bulging past the flats. The hole is 8 across the flats.
 *
 * <p><b>So this block DOES ask for {@code noOcclusion}</b>, because the model has a hole through it -
 * see the note in {@code RCBlocks}. Collision is still the plain slab box, which is what lets a player
 * walk up a pile.
 *
 * <p><b>It is a plain block, not a {@code SortableBlock}</b> (owner, 2026-09-04: "a tire is not a
 * sortable block, that wouldn't make any sense"). There is no {@code sorted} progress, no crumble
 * window and no pull table. What it gives depends on what breaks it, and that lives in the loot table
 * rather than here: a hand yields the tire, a Scrap Knife yields rubber, which is a
 * {@code minecraft:match_tool} condition and no Java at all. Vanilla does the same thing to make dead
 * coral drop only to silk touch.
 *
 * <p><b>Not being a {@code SortableBlock} decides two other things for free.</b> That class extends
 * {@code FallingBlock}, so a tire would have inherited gravity behind an {@code obeysGravity()} hook;
 * a plain block brings none and a stack stays where it was tipped. And {@code MoundGroundBlock.isMound}
 * counts only {@code SortableBlock} and {@code BulkyWasteBlock}, so a tire standing on Mound Ground is
 * never mistaken for part of a mound - which retires the Phase 5 hazard #155 identified. Piles still
 * keep off mound footprints, but as a design call rather than a technical necessity.
 *
 * <p><b>Fire behaves exactly as it does on netherrack</b> (owner, 2026-09-04, nonnegotiable). That is
 * {@link #isFireSource} below rather than the {@code #minecraft:infiniburn_overworld} tag: NeoForge
 * asks the block directly from {@code FireBlock.tick}, and answering here is block-scoped and cannot be
 * broken by a datapack editing a vanilla-namespace tag for unrelated reasons. Four consequences follow
 * and all are intended - it never self-extinguishes even in rain, the fire does not consume the tire
 * (this block is deliberately absent from every flammability registration), a player can still put it
 * out, and it spreads by ordinary rules. On the raw dump there is nothing to spread to: the surface is
 * coarse dirt and this mod registers no flammable blocks at all.
 */
public class TireBlock extends SlabBlock {

    public TireBlock(Properties properties) {
        super(properties);
    }

    /**
     * Fire on a tire never goes out.
     *
     * <p>{@code FireBlock.tick} reads this through {@code belowState.isFireSource(...)} and skips both
     * its burn-out branch and its rain-extinguish branch when it answers true. The rain half is the one
     * worth knowing here: this world rains, the Rain Collector depends on it, and a downpour will not
     * clear a tire dump.
     */
    @Override
    public boolean isFireSource(BlockState state, net.minecraft.world.level.LevelReader level,
            BlockPos pos, Direction direction) {
        return true;
    }

    // NO isPathfindable OR getShadeBrightness OVERRIDE, even though the model is mostly hole again.
    // Both used to force values that described the MODEL, and neither question is about the model:
    // pathfinding and support read the COLLISION shape, which is the plain slab box either way. So a
    // tire paths and shades as any vanilla slab does, which is what a block you can walk up should do.
}
