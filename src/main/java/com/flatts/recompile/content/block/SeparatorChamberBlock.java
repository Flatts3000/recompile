package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One cell of the Separator's 2x2 grinding bay ({@code docs/separator_model_spec.md}).
 *
 * <p><b>Four blocks that have to look like one opening.</b> Each carries the quarter of the grinder it
 * shows, stamped by the core at assembly, and the four textures are quarters of a single image - so the
 * teeth run continuously across the seams instead of the comb restarting at every block edge. Without
 * this the bay reads as four small grinders in a square, which is the thing it must not look like.
 *
 * <p><b>The rim is geometry, so each quadrant model draws only its own OUTER edges.</b> The first build
 * gave every cell a full raised rim on all four sides, which drew a complete box around each block - so
 * the bay read as four separate wells no matter how the floor texture was sliced. Slicing a texture
 * cannot fix a border that is in the model.
 *
 * <p>{@link #FACING} is the machine's facing, not the cell's. It is applied as a model rotation, which
 * turns the whole 2x2 image together and is why the quadrant can be taken from the unrotated offset.
 *
 * <p>{@link #ACTIVE} is mirrored here from the core rather than read from it. A dummy cell has no
 * BlockEntity and no cheap way to find its master at render time, and this is the same one-property
 * mirror the {@code LIT} state uses on a vanilla furnace. Without it the animated texture is unreachable:
 * the running models shipped and nothing referenced them, so the grinder never appeared to turn.
 */
public class SeparatorChamberBlock extends MultiblockDummyBlock {

    public static final MapCodec<SeparatorChamberBlock> CODEC = simpleCodec(SeparatorChamberBlock::new);

    /** Which quarter of the bay this is, in unrotated space: x + z * 2. */
    public static final IntegerProperty QUADRANT = IntegerProperty.create("quadrant", 0, 3);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Mirrors the core's ACTIVE, so the bay animates while the machine grinds. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public SeparatorChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(QUADRANT, 0)
            .setValue(ACTIVE, false)
            .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends SeparatorChamberBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(QUADRANT, FACING, ACTIVE);
    }
}
