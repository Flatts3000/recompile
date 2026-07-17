package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.compat.TeardownData;
import com.flatts.recompile.content.block.RecompileWorkbenchBlock;
import com.flatts.recompile.content.block.entity.RecompileWorkbenchBlockEntity;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: make the Recompile Workbench legible to a player who has never used one. The bench has
 * no GUI, so its whole state (what is racked, what it wants) is otherwise invisible. This reads
 * the {@code has_knife}/{@code has_prybar} blockstate and the looking player's held item and
 * spells out the next step - rack a tool, or hold a found item, or "no salvage value".
 *
 * <p>Teardown recipes are not client-synced in 26.1, so which tool a find needs comes from the
 * bundled JSON via {@link TeardownData} (mattress today), same trade-off as the JEI category.
 */
public enum WorkbenchHintProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "workbench_hint");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        boolean knife = state.getValue(RecompileWorkbenchBlock.HAS_KNIFE);
        boolean prybar = state.getValue(RecompileWorkbenchBlock.HAS_PRYBAR);

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
