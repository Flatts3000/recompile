package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.RainCollectorBlockEntity;
import com.flatts.recompile.content.block.entity.ScrapBarrelBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block-entity registry.
 *
 * <p>The mod ran with none of these on purpose for a long while - the Sorting Tarp is
 * stateless so it can never become an automation surface. Storage is the honest exception:
 * a container has to hold items. That is not a reversal of the tarp's design, but it is the
 * line where "no block stores anything" stops being true, so keep it to blocks whose whole
 * job is holding items.
 */
public final class RCBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Recompile.MOD_ID);

    /** The Scrap Barrel's 27-slot inventory (design: storage without wood). */
    public static final Supplier<BlockEntityType<ScrapBarrelBlockEntity>> SCRAP_BARREL =
        BLOCK_ENTITIES.register(
            "scrap_barrel",
            () -> new BlockEntityType<>(ScrapBarrelBlockEntity::new, RCBlocks.SCRAP_BARREL.get()));

    /** The Rain Collector's water tank (design P1.10) - the second holding block. */
    public static final Supplier<BlockEntityType<RainCollectorBlockEntity>> RAIN_COLLECTOR =
        BLOCK_ENTITIES.register(
            "rain_collector",
            () -> new BlockEntityType<>(RainCollectorBlockEntity::new, RCBlocks.RAIN_COLLECTOR.get()));

    private RCBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(RCBlockEntities::onRegisterCapabilities);
    }

    /** Expose the collector's water tank so pipes, pumps, and buckets see it as any tank. */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            RAIN_COLLECTOR.get(),
            (be, side) -> be.fluidHandler());
    }
}
