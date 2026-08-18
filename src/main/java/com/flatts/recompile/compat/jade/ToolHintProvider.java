package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.BulkyWasteBlock;
import com.flatts.recompile.content.block.ManholeBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.SteelBeamBlock;
import com.flatts.recompile.event.RCHarvestGate;
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
        if (block instanceof ManholeBlock) {
            // OPEN, not salvage. "Salvage with a Prybar" is true of Bulky Waste, where prying drops the
            // find - a manhole drops nothing at all and is deliberately unbreakable, so the salvage
            // wording promises loot that is not there. Same gate, different promise.
            tooltip.add(Component.translatable("jade.recompile.open_with",
                Component.translatable(RCItems.PRYBAR.get().getDescriptionId())));
        } else if (tool != null) {
            tooltip.add(Component.translatable("jade.recompile.salvage_with",
                Component.translatable(tool.getDescriptionId())));
        } else if (block instanceof SortableBlock) {
            tooltip.add(Component.translatable("jade.recompile.sort_by_hand"));
        }

        // The DIGGING tool as well, because they are different questions and the answers differ: a
        // Block of Garbage sorts bare-handed and needs a shovel to carry off. Saying only "Sort by
        // hand" on a block a bare hand cannot pick up is a half-truth, and the half it leaves out is
        // the one that costs the player a block.
        if (block.defaultBlockState().requiresCorrectToolForDrops() && block instanceof SortableBlock) {
            tooltip.add(Component.translatable("jade.recompile.dig_with",
                Component.translatable(RCHarvestGate.toolKey(block.defaultBlockState()))));
        }
    }

    private static @Nullable Item salvageTool(Block block) {
        if (block instanceof SortableBlock sortable) {
            return sortable.sortTool();
        }
        if (block instanceof BulkyWasteBlock || block instanceof ManholeBlock) {
            return RCItems.PRYBAR.get();
        }
        if (block instanceof SteelBeamBlock) {
            return RCItems.CUTTING_TORCH.get();
        }
        return null;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
