package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Solar Panel: a shared machine component, and <b>completely inert</b>.
 *
 * <p>It does <em>not</em> detect light, emit redstone, or generate power. Saying so explicitly
 * because the name invites exactly that, and P3.5 locks "no RF before the Nether" - there is no
 * energy system in this mod and this block is not the start of one. It is a part that has to be in
 * the right cell of a blueprint, nothing more. Same rule the Machine Frame and the Motor follow.
 *
 * <p>Visually it is a recoloured vanilla daylight detector, because vanilla already ships a block
 * that <em>is</em> a solar panel - reusing it costs no new art, which is the constraint that
 * actually governs this mod.
 *
 * <p>It is both the component you place and the machine's formed cell: its appearance does not
 * change on forming, so it needs no separate formed twin. Extending {@link MultiblockDummyBlock}
 * costs nothing standalone - {@code findCore} returns null and every override falls through - but
 * inside a formed machine it redirects break and use to the core, which is what keeps the machine
 * behaving as one object.
 */
public class SolarPanelBlock extends MultiblockDummyBlock {

    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    /** A slab, matching the daylight-detector model it borrows. */
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 6, 16);

    public SolarPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SolarPanelBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
