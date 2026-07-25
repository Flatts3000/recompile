package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A footprint preview while the player holds <b>any multiblock core</b> (Rain Collector, Grass
 * Spreader, and any future one). A multiblock is a set of blocks the player must place at the right
 * offsets, and which offsets is not obvious from the single core in hand - so this dusts each cell of
 * the blueprint at the position the core would land, <b>green</b> where the cell is clear, <b>red</b>
 * where a block is in the way.
 *
 * <p>Client-only and purely visual: it reads {@link Minecraft#hitResult} and the core's blueprint,
 * spawns particles, and touches no world state. The blueprint is the multiblock system's single
 * source of truth, so the preview costs no new data. It renders at {@code Rotation.NONE} (the shipped
 * cores are rotation-invariant vertical columns); it is particles, not a render-pipeline overlay,
 * because 26.1's {@code RenderLevelStageEvent} lost the camera / partial-tick hooks a world-space
 * outline needs.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class MultiblockPlacementPreview {

    /** Emit every few ticks - dust lingers, so a sparse refresh still reads as a steady outline. */
    private static final int EMIT_INTERVAL_TICKS = 4;
    private static final DustParticleOptions CLEAR = new DustParticleOptions(0x66BB6A, 1.0f);   // green
    private static final DustParticleOptions BLOCKED = new DustParticleOptions(0xE53935, 1.0f);  // red

    private MultiblockPlacementPreview() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            return;
        }
        Multiblock blueprint = heldCoreBlueprint(player);
        if (blueprint == null || player.tickCount % EMIT_INTERVAL_TICKS != 0) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        // Where the core would land: the block face the player is aiming at.
        BlockPos corePos = hit.getBlockPos().relative(hit.getDirection());
        marker(level, corePos);
        for (Multiblock.Cell cell : blueprint.cells()) {
            marker(level, cell.at(corePos));
        }
    }

    /** The blueprint of the multiblock core the player is holding, or null. */
    private static Multiblock heldCoreBlueprint(Player player) {
        Multiblock fromMain = coreBlueprint(player.getMainHandItem());
        return fromMain != null ? fromMain : coreBlueprint(player.getOffhandItem());
    }

    private static Multiblock coreBlueprint(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item
            && item.getBlock() instanceof MultiblockCoreBlock core ? core.blueprint() : null;
    }

    /** Dust the cell: green if a block could be placed there, red if something is already in the way. */
    private static void marker(Level level, BlockPos pos) {
        DustParticleOptions color = level.getBlockState(pos).canBeReplaced() ? CLEAR : BLOCKED;
        level.addParticle(color, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
    }
}
