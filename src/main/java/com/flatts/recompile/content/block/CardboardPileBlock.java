package com.flatts.recompile.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pile of flattened boxes (#309): surface litter that breaks into cardboard.
 *
 * <p><b>Cardboard is found as a PILE rather than as a pull-stream entry</b> (owner, 2026-08-31).
 * The first version made {@code recompile:cardboard} a weighted entry in {@code household_pulls},
 * which works and is invisible: you would occasionally get cardboard out of a garbage block and
 * never see any in a dump that is supposed to be full of boxes. A dump full of boxes should look
 * like one.
 *
 * <p><b>And you just break it</b> (owner, 2026-08-31: "I think it should drop cardboard when broken
 * by hand", and separately that it should be neither a sortable nor a teardown). Two richer designs
 * were built and rejected in turn, and the reasons are worth keeping because both looked right:
 *
 * <ul>
 *   <li><b>Not a {@link SortableBlock}.</b> It shipped as one for an afternoon, and Jade named the
 *       problem out loud: the tooltip read <i>"Sort by hand"</i>. Sorting is the verb for an opaque
 *       block whose contents are a surprise you reveal one pull at a time. A stack of flattened
 *       boxes is not opaque and holds no surprise - you can see exactly what it is - so making the
 *       player right-click it three times is ceremony charging for nothing.
 *   <li><b>Not a teardown either.</b> A {@code recompile:teardown} recipe is the mod's signature
 *       mechanic and the obvious home for "take a thing apart", and it would have put cardboard
 *       behind the Recompile Workbench. The Workbench is cheap - three scrap metal and four rebar -
 *       but cheap is not free, and this family exists to be the one a player can use before they
 *       have built anything at all.
 * </ul>
 *
 * <p>What is left is the plainest thing in the mod, which is the point: walk up, break it, get
 * cardboard. No tool, no station, no state. That is also why this class is nearly empty - it is a
 * {@link FallingBlock} and nothing else, and everything interesting about it is in its loot table
 * and in {@code MoundFeature}.
 *
 * <p><b>Its drop is cardboard, not itself</b>, which makes the block unobtainable in survival - the
 * Bulky Waste arrangement exactly, and registered with an item form for the same reason: so a
 * builder can place one in creative.
 *
 * <p>Obeys gravity like the rest of the garbage (P0.3), so mounds still slump when quarried around
 * it.
 */
public class CardboardPileBlock extends FallingBlock {

    public static final MapCodec<CardboardPileBlock> CODEC = simpleCodec(CardboardPileBlock::new);

    public CardboardPileBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CardboardPileBlock> codec() {
        return CODEC;
    }

    /** The colour of the dust it trails while falling - its map colour, as Bulky Waste does. */
    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getMapColor(level, pos).col;
    }
}
