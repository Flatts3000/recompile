package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.WorkstationCoreBlock;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A ground-level footprint preview while the player holds a Workstation Core (design P2.10). The
 * bench is 18 blocks and builds relative to the player's facing, so "where will it land, and will it
 * fit" is not obvious from the single block in hand. This dusts each cell of the projected footprint
 * with a marker - <b>green</b> where the cell is clear, <b>red</b> where a block is in the way - at
 * the position the core would be placed, rotated to the player's look direction.
 *
 * <p>Client-only and purely visual: it reads {@link Minecraft#hitResult} and the blueprint, spawns
 * particles, and touches no world state. It mirrors the reactor-outline affordance the multiblock
 * spec calls for, done with particles rather than a render-pipeline overlay (26.1's
 * {@code RenderLevelStageEvent} lost the camera/partial-tick hooks a world-space outline needs).
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class WorkstationPlacementPreview {

    /** Emit every few ticks - dust lingers, so a sparse refresh still reads as a steady outline. */
    private static final int EMIT_INTERVAL_TICKS = 4;
    private static final DustParticleOptions CLEAR = new DustParticleOptions(0x66BB6A, 1.0f);   // green
    private static final DustParticleOptions BLOCKED = new DustParticleOptions(0xE53935, 1.0f);  // red

    private WorkstationPlacementPreview() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || !holdingCore(player)) {
            return;
        }
        if (player.tickCount % EMIT_INTERVAL_TICKS != 0) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        // Where the core would land, and how the blueprint is turned - both exactly as
        // WorkstationCoreBlock.getStateForPlacement resolves them (facing = opposite of look, via the
        // shared facingForPlacement), so the preview cannot lie.
        BlockPos corePos = hit.getBlockPos().relative(hit.getDirection());
        Rotation rotation = WorkstationCoreBlock.rotationFromFacing(
            WorkstationCoreBlock.facingForPlacement(player.getDirection()));
        Multiblock blueprint = RCBlocks.WORKSTATION_CORE.get().blueprint();

        marker(level, corePos);
        for (Multiblock.Cell cell : blueprint.cells()) {
            marker(level, cell.at(corePos, rotation));
        }
    }

    private static boolean holdingCore(Player player) {
        return isCore(player.getMainHandItem()) || isCore(player.getOffhandItem());
    }

    private static boolean isCore(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() == RCBlocks.WORKSTATION_CORE.get();
    }

    /** Dust the cell: green if a block could be placed there, red if something is already in the way. */
    private static void marker(Level level, BlockPos pos) {
        DustParticleOptions color = level.getBlockState(pos).canBeReplaced() ? CLEAR : BLOCKED;
        level.addParticle(color, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
    }
}
