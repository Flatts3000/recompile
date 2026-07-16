package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.BulkyWasteBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: name the tool a salvage block wants, on hover - so the prybar/knife gate reads
 * before you swing at it, not only as a chat nudge after. Bare-hand sortables say so too.
 */
public enum ToolHintProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "tool_hint");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Block block = accessor.getBlock();
        Item tool = salvageTool(block);
        if (tool != null) {
            tooltip.add(Component.translatable("jade.recompile.salvage_with",
                Component.translatable(tool.getDescriptionId())));
        } else if (block instanceof SortableBlock) {
            tooltip.add(Component.translatable("jade.recompile.sort_by_hand"));
        }
    }

    private static @Nullable Item salvageTool(Block block) {
        if (block instanceof SortableBlock sortable) {
            return sortable.sortTool();
        }
        if (block instanceof BulkyWasteBlock) {
            return RCItems.PRYBAR.get();
        }
        return null;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
