package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.SolarPanelBlockEntity;
import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Solar Panel: a shared machine component, and <b>a real generator</b> as of #72.
 *
 * <p>It was inert for the mod's whole life because P3.5 locked "no RF before the Nether". That lock is
 * reversed ({@code trashlands/docs/design_decisions.md} P3.5) and this block is now the half of the power
 * tier that needed no new art - it was already named and shaped like the thing it became. Behaviour lives
 * in {@link SolarPanelBlockEntity}; it still does not emit redstone.
 *
 * <p><b>The Machine Frame and the Pump are still inert.</b> The Pump in particular stays that way under
 * P2.3 ("Recompile converts, Create moves"), so this is not a precedent for animating the rest of the
 * component set.
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
public class SolarPanelBlock extends MultiblockDummyBlock implements EntityBlock {

    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    /** A slab, matching the daylight-detector model it borrows. */
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 6, 16);

    public SolarPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }

    /**
     * Server-side only, and unconditional - a panel generates wherever it stands, including inside a
     * formed Grass Spreader or Tree Nursery. Those machines simply do not consume; see
     * {@link SolarPanelBlockEntity} for why that is the only version that does not break saves.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != RCBlockEntities.SOLAR_PANEL.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<SolarPanelBlockEntity>)
            SolarPanelBlockEntity::serverTick;
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
