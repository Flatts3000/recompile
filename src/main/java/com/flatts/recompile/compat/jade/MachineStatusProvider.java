package com.flatts.recompile.compat.jade;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.GrassSpreaderCoreBlock;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: whether a machine is assembled, and if not, what it is still missing.
 *
 * <p>Formation is otherwise a silent yes/no - a tower that looks finished but is not doing anything
 * gives the player nothing to act on. Naming the missing part is the whole point; "it needs a Solar
 * Panel above" is a fix, "not formed" is a shrug.
 *
 * <p>It also reports the Grass Spreader's radius, because that number is the machine's entire
 * statement about how much land it can hold and there is no other way to see it in world.
 */
public enum MachineStatusProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "machine_status");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlock() instanceof MultiblockCoreBlock core)) {
            return;
        }
        boolean formed = MultiblockCoreBlock.isFormed(accessor.getBlockState());
        tooltip.add(Component.translatable(
            formed ? "jade.recompile.machine_running" : "jade.recompile.machine_incomplete"));

        if (!formed) {
            appendMissing(tooltip, core, accessor);
        } else if (core instanceof GrassSpreaderCoreBlock) {
            // Known limitation: this reads the CLIENT's copy of a COMMON config, which is per-side
            // and not synced. On a server that has retuned the radius the number shown here is the
            // client's, not the server's. Fixing it properly means shipping the value over a Jade
            // block-data provider, the way WorkbenchDataProvider sends tool durability - worth doing
            // if anyone actually retunes it, not worth the plumbing while it is a shipped default.
            tooltip.add(Component.translatable("jade.recompile.spreader_radius",
                RCConfig.GRASS_SPREADER_RADIUS.get()));
        }
    }

    /** Name the first cell that is not holding its component - the one thing the player can act on. */
    private static void appendMissing(ITooltip tooltip, MultiblockCoreBlock core, BlockAccessor accessor) {
        BlockPos pos = accessor.getPosition();
        for (Multiblock.Cell cell : core.blueprint().cells()) {
            if (!accessor.getLevel().getBlockState(cell.at(pos)).is(cell.component())) {
                tooltip.add(Component.translatable("jade.recompile.machine_needs",
                    cell.component().getName()));
                return;
            }
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
