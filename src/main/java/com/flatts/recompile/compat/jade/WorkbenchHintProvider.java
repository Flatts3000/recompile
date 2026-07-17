package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.compat.TeardownData;
import com.flatts.recompile.content.block.RecompileWorkbenchBlock;
import com.flatts.recompile.content.block.entity.RecompileWorkbenchBlockEntity;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: make the Recompile Workbench legible to a player who has never used one. The bench has
 * no GUI, so its whole state (what is racked, what it wants) is otherwise invisible. This reads
 * the {@code has_knife}/{@code has_prybar} blockstate and the looking player's held item and
 * spells out the next step - rack a tool, or hold a found item, or "no salvage value" - and the
 * remaining durability of each racked tool.
 *
 * <p>The racked tools' durability lives in the server-side BlockEntity (not synced), so it comes
 * across via Jade's server-data channel ({@link #appendServerData} on the server writes it,
 * {@link BlockAccessor#getServerData()} reads it on the client). Which tool a find needs comes
 * from the bundled recipe JSON via {@link TeardownData} (recipes are not client-synced in 26.1).
 */
public enum WorkbenchHintProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "workbench_hint");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof RecompileWorkbenchBlockEntity workbench) {
            writeTool(data, "knife", workbench.getTool(RecompileWorkbenchBlockEntity.KNIFE_SLOT));
            writeTool(data, "prybar", workbench.getTool(RecompileWorkbenchBlockEntity.PRYBAR_SLOT));
        }
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return true;
    }

    private static void writeTool(CompoundTag data, String key, ItemStack tool) {
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            data.putInt(key + "_rem", tool.getMaxDamage() - tool.getDamageValue());
            data.putInt(key + "_max", tool.getMaxDamage());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        boolean knife = state.getValue(RecompileWorkbenchBlock.HAS_KNIFE);
        boolean prybar = state.getValue(RecompileWorkbenchBlock.HAS_PRYBAR);

        CompoundTag data = accessor.getServerData();
        appendDurability(tooltip, data, "knife", RCItems.SCRAP_KNIFE.get());
        appendDurability(tooltip, data, "prybar", RCItems.PRYBAR.get());

        if (!knife && !prybar) {
            tooltip.add(Component.translatable("jade.recompile.workbench_empty"));
        }

        Player player = accessor.getPlayer();
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }

        if (RecompileWorkbenchBlockEntity.isRackTool(held)) {
            tooltip.add(Component.translatable("jade.recompile.workbench_rack_tool",
                Component.translatable(held.getItem().getDescriptionId())));
            return;
        }

        TeardownData.Entry entry = TeardownData.forInput(held.getItem());
        if (entry == null) {
            tooltip.add(Component.translatable("jade.recompile.workbench_no_value"));
        } else if (entry.tool() == null || toolRacked(entry.tool(), knife, prybar)) {
            tooltip.add(Component.translatable("jade.recompile.workbench_ready"));
        } else {
            tooltip.add(Component.translatable("jade.recompile.workbench_needs_tool",
                Component.translatable(entry.tool().getDescriptionId())));
        }
    }

    private static void appendDurability(ITooltip tooltip, CompoundTag data, String key, Item tool) {
        if (data != null && data.contains(key + "_max")) {
            tooltip.add(Component.translatable("jade.recompile.workbench_durability",
                Component.translatable(tool.getDescriptionId()),
                data.getIntOr(key + "_rem", 0), data.getIntOr(key + "_max", 0)));
        }
    }

    private static boolean toolRacked(Item tool, boolean knife, boolean prybar) {
        if (tool == RCItems.SCRAP_KNIFE.get()) {
            return knife;
        }
        if (tool == RCItems.PRYBAR.get()) {
            return prybar;
        }
        return false;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
