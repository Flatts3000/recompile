package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Hauler Depot (#376, spec {@code docs/scrap_hauler_spec.md}): where a Scrap Hauler lives, what
 * deploys it, and what it brings the garbage back to.
 *
 * <p><b>The eleventh custom screen, and a recorded reversal.</b> The rule is no new machine screen
 * without one, and the two nearest neighbours each solve half of what this needs: the Scrap Barrel
 * reuses vanilla {@code ChestMenu} because it is only storage, and the Charging Station has no screen
 * at all because it holds one item. This is both at once plus power - a dedicated Hauler slot, a
 * large hold, an FE gauge and a Deploy/Recall button - and vanilla has no screen shaped like that.
 *
 * <p><b>In the Scrap Network</b> (ruling 10), which the Charging Station deliberately is not: that
 * tag routes items, and this block has items to route. Membership is the tag, and the block's ticker
 * pushes the hold into whatever bin or barrel shares a face.
 *
 * <p>A full cube, so no {@code noOcclusion}. The block itself is ordinary baked geometry; the Hauler
 * it deploys is an entity with a model of its own, which is what keeps the one-BlockEntityRenderer
 * rule intact.
 */
public class HaulerDepotBlock extends BaseEntityBlock {
    public static final MapCodec<HaulerDepotBlock> CODEC = simpleCodec(HaulerDepotBlock::new);

    public HaulerDepotBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<HaulerDepotBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HaulerDepotBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, RCBlockEntities.HAULER_DEPOT.get(), HaulerDepotBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HaulerDepotBlockEntity depot) {
            player.openMenu(depot);
        }
        return InteractionResult.SUCCESS;
    }

    // The hold and the Hauler drop from HaulerDepotBlockEntity.preRemoveSideEffects on every removal
    // cause, after the Hauler is recalled; the block itself drops via its loot table.
}
