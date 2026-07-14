package com.flatts.recompile.content.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared "pick-through" behaviour for the sortable garbage variants (design P0.4 /
 * P1.1): right-click a placed block to pull one drop from its region table; after a
 * few pulls the block crumbles. Sort progress lives in a blockstate {@code sorted}
 * property (a palette flyweight - the garbage blocks are the mod's bulk block, so no
 * per-instance BlockEntity).
 *
 * <p>Each concrete variant supplies its own pull table, crumble range, and the tool
 * it takes to open (null = bare hand). Subclasses provide their own {@code sorted}
 * property so the persisted range matches how many pulls that variant allows.
 */
public abstract class SortableBlock extends Block {

    protected SortableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(sortedProperty(), 0));
    }

    /** The {@code sorted} progress property (0 .. maxPulls-1), defined per variant. */
    protected abstract IntegerProperty sortedProperty();

    /** The region pull table this variant draws from. */
    protected abstract ResourceKey<LootTable> pullTable();

    /** Crumble window: never before minPulls, certain at maxPulls, rising chance between. */
    protected abstract int minPulls();

    protected abstract int maxPulls();

    /** The item required to sort this variant, or null to sort with an empty hand. */
    @Nullable
    protected abstract Item requiredTool();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(sortedProperty());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (requiredTool() == null) {
            if (level instanceof ServerLevel serverLevel) {
                sort(serverLevel, pos);
            }
            return InteractionResult.SUCCESS;
        }
        // Needs a tool: nudge the player, don't consume the block.
        if (!level.isClientSide()) {
            player.sendSystemMessage(
                Component.translatable("message.recompile.needs_tool",
                    Component.translatable(requiredTool().getDescriptionId())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        Item tool = requiredTool();
        if (tool != null && stack.is(tool)) {
            if (level instanceof ServerLevel serverLevel) {
                sort(serverLevel, pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Pull once: roll this variant's table, drop it, advance progress, crumble if spent. */
    public boolean sort(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SortableBlock)) {
            return false;
        }
        SoundType sound = state.getSoundType();

        LootTable table = level.getServer().reloadableRegistries().getLootTable(pullTable());
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .create(LootContextParamSets.CHEST);
        List<ItemStack> pulled = table.getRandomItems(params);
        for (ItemStack drop : pulled) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.6F, 0.9F);

        int pulls = state.getValue(sortedProperty()) + 1;
        if (shouldCrumble(pulls, level.getRandom())) {
            level.destroyBlock(pos, false);
            level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
            return true;
        }
        level.setBlock(pos, state.setValue(sortedProperty(), pulls), Block.UPDATE_ALL);
        return false;
    }

    private boolean shouldCrumble(int pulls, RandomSource random) {
        if (pulls >= maxPulls()) {
            return true;
        }
        if (pulls < minPulls()) {
            return false;
        }
        float chance = (float) (pulls - (minPulls() - 1)) / (maxPulls() - (minPulls() - 1));
        return random.nextFloat() < chance;
    }

    /** Single entry point for interactions and gametests: sort the sortable block at pos. */
    public static boolean sortOnce(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof SortableBlock block) {
            return block.sort(level, pos);
        }
        return false;
    }
}
