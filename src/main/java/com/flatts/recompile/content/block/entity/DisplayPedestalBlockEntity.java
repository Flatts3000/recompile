package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Display Pedestal's state (Collectibles, design I-2): a single displayed item - a finished
 * collectible trophy set out on the stand.
 *
 * <p>Like {@link RecompileWorkbenchBlockEntity} it is deliberately <b>not</b> a
 * {@link net.minecraft.world.Container} and exposes no item-handler capability, so it can never be
 * hopper-fed - it is a display, not storage. It holds one stack so the trophy survives save/load,
 * drops it on any removal, and syncs it to the client so the {@link
 * com.flatts.recompile.client.DisplayPedestalRenderer} can draw it.
 *
 * <p><b>This is the block that needs the mod's one BlockEntityRenderer</b> - the recorded, scoped
 * reversal of P1.11.6. A pedestal shows an <em>arbitrary</em> collectible, so its look cannot be a
 * baked model; the held item is rendered live. The no-BER rule was written for dump-scale finds
 * (thousands in view); a handful of trophy stands is nowhere near that, so the reason does not apply
 * here. Every other block still bakes its model.
 */
public class DisplayPedestalBlockEntity extends BlockEntity {

    private NonNullList<ItemStack> item = NonNullList.withSize(1, ItemStack.EMPTY);

    public DisplayPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.DISPLAY_PEDESTAL.get(), pos, state);
    }

    /** The displayed trophy (may be empty). Read by the renderer and gametests. */
    public ItemStack getDisplayed() {
        return item.get(0);
    }

    public boolean isEmpty() {
        return item.get(0).isEmpty();
    }

    /** Set the displayed trophy (a single item) and sync to the client. */
    public void setDisplayed(ItemStack stack) {
        item.set(0, stack);
        changed();
    }

    /** Take the displayed trophy back off the stand, clearing it. */
    public ItemStack removeDisplayed() {
        ItemStack out = item.get(0);
        item.set(0, ItemStack.EMPTY);
        changed();
        return out;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            // Push the new item to clients so the renderer redraws - a display BE has no blockstate
            // to ride on, unlike the workbench's baked has_knife/has_prybar.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Drop the trophy on any removal (player break, explosion, piston, /setblock, mod replace). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
        super.preRemoveSideEffects(pos, oldState);
        if (level != null && !level.isClientSide() && !isEmpty()) {
            Block.popResource(level, pos, item.get(0));
            item.set(0, ItemStack.EMPTY);
        }
    }

    // ---- persistence + client sync ----------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.item);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.item = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.item);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }
}
