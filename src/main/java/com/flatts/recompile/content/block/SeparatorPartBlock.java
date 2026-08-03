package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A formed cell of the Separator: chamber, housing or chute
 * ({@code docs/separator_model_spec.md}).
 *
 * <p>One class for all three, because they differ only in their model. A formed cell is a <b>bespoke
 * per-machine block</b> rather than the component's model restacked, which is what lets the machine be
 * a designed object; but bespoke <i>art</i> does not mean bespoke <i>behaviour</i>, and all three do
 * the same thing here: redirect use and break to the master.
 *
 * <p><b>They carry the machine's facing, not their own.</b> The chute's mouth is cut into one side of
 * its model, so without this it pointed north whatever direction the machine was built in - correct by
 * accident on a north-facing Separator and wrong on the other three. A formed cell has no idea which
 * machine it belongs to, so the core stamps this when the machine assembles.
 *
 * <p>The housing carries it too even though its model is a plain cube. It costs three extra blockstate
 * lines, and it means the day the housing gets a vent or a hatch on its front face, the state it needs
 * is already there and already correct.
 */
public class SeparatorPartBlock extends MultiblockDummyBlock {

    public static final MapCodec<SeparatorPartBlock> CODEC = simpleCodec(SeparatorPartBlock::new);

    /** The machine's facing, stamped by the core at assembly. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SeparatorPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    protected MapCodec<? extends SeparatorPartBlock> codec() {
        return CODEC;
    }
}
