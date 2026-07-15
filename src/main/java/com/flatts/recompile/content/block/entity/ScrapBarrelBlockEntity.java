package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.block.ScrapBarrelBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Scrap Barrel's inventory: 27 slots, vanilla barrel behaviour throughout.
 *
 * <p>Mirrors {@code BarrelBlockEntity}. It reuses vanilla's {@link ChestMenu} rather than
 * minting a menu and screen of its own, which also means no client screen registration -
 * the same reuse the Scrap Crafting Table makes of vanilla's crafting menu.
 *
 * <p>Contents drop on break for free: {@code BlockEntity.preRemoveSideEffects} drops any
 * block entity that is a {@link Container}, so there is nothing to do here for that.
 *
 * <p>The one deviation from vanilla is the sound position. A barrel plays its open/close
 * sound offset along its facing; this block has no facing (it is always top-up), so the
 * sound comes off the lid.
 */
public class ScrapBarrelBlockEntity extends RandomizableContainerBlockEntity {

    private static final Component DEFAULT_NAME = Component.translatable("container.recompile.scrap_barrel");
    private static final int SLOTS = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        {
            Objects.requireNonNull(ScrapBarrelBlockEntity.this);
        }

        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            ScrapBarrelBlockEntity.this.playSound(SoundEvents.BARREL_OPEN);
            ScrapBarrelBlockEntity.this.updateBlockState(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            ScrapBarrelBlockEntity.this.playSound(SoundEvents.BARREL_CLOSE);
            ScrapBarrelBlockEntity.this.updateBlockState(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
        }

        @Override
        public boolean isOwnContainer(Player player) {
            if (player.containerMenu instanceof ChestMenu chestMenu) {
                Container container = chestMenu.getContainer();
                return container == ScrapBarrelBlockEntity.this;
            }
            return false;
        }
    };

    public ScrapBarrelBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.SCRAP_BARREL.get(), worldPosition, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public void startOpen(ContainerUser containerUser) {
        if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
            this.openersCounter.incrementOpeners(containerUser.getLivingEntity(), this.getLevel(),
                this.getBlockPos(), this.getBlockState(), containerUser.getContainerInteractionRange());
        }
    }

    @Override
    public void stopOpen(ContainerUser containerUser) {
        if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
            this.openersCounter.decrementOpeners(containerUser.getLivingEntity(), this.getLevel(),
                this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public List<ContainerUser> getEntitiesWithContainerOpen() {
        return this.openersCounter.getEntitiesWithContainerOpen(this.getLevel(), this.getBlockPos());
    }

    /** Driven by the block's scheduled tick - {@code ContainerOpenersCounter} books it. */
    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    private void updateBlockState(BlockState state, boolean open) {
        this.level.setBlock(this.getBlockPos(), state.setValue(ScrapBarrelBlock.OPEN, open), 3);
    }

    /** Off the lid: the barrel is always top-up, so the offset is a fixed half-block of Y. */
    private void playSound(SoundEvent event) {
        double x = this.worldPosition.getX() + 0.5;
        double y = this.worldPosition.getY() + 1.0;
        double z = this.worldPosition.getZ() + 0.5;
        this.level.playSound(null, x, y, z, event, SoundSource.BLOCKS, 0.5F,
            this.level.getRandom().nextFloat() * 0.1F + 0.9F);
    }
}
