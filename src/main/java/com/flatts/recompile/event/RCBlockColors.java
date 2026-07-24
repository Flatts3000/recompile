package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.ScrapBinBlock;
import com.flatts.recompile.registry.RCBlocks;
import java.util.List;
import java.util.Set;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Client-side color for the Scrap Bin (P2.9): the bin's body is tinted its bound material's color at
 * render, via {@code tintindex 0}, so a wall of bins reads by hue. A {@link BlockTintSource}, not a
 * BlockEntityRenderer - the same mechanism vanilla grass and water use - so it stays inside the mod's
 * no-BER rule. The color lives on {@link com.flatts.recompile.content.block.ScrapBinContent}, keyed on
 * the {@code content} blockstate; {@code EMPTY} and {@code GENERIC} return white, leaving the neutral
 * base texture untinted (the empty bin and the modded-scrap fallback).
 *
 * <p>The item (inventory / hand) tint is deliberately not wired: a crafted bin is empty and so neutral
 * anyway, and 26.1's item tint is a separate data-driven system. A loaded bin in the inventory reading
 * neutral rather than colored is an accepted, minor cosmetic gap.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class RCBlockColors {

    /** One tint source per material, chosen by the {@code content} blockstate. */
    private static final BlockTintSource SCRAP_BIN_TINT = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return state.getValue(ScrapBinBlock.CONTENT).color();
        }

        @Override
        public Set<Property<?>> relevantProperties() {
            return Set.of(ScrapBinBlock.CONTENT);
        }
    };

    private RCBlockColors() {
    }

    @SubscribeEvent
    static void registerBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        // List index is the model's tintindex; the bin's body faces use tintindex 0.
        event.getBlockColors().register(List.of(SCRAP_BIN_TINT), RCBlocks.SCRAP_BIN.get());
    }
}
