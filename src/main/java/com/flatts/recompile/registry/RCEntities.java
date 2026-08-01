package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.entity.RoachEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's entities. There was no entity layer at all before #78 - everything Recompile ships is
 * blocks, items and data - so this is the whole of it.
 *
 * <p><b>26.1 note:</b> {@code EntityType.Builder.build} takes a {@link ResourceKey}, not a string id.
 * Every 1.21-era snippet passes a name and will not compile.
 */
public final class RCEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, Recompile.MOD_ID);

    private static final ResourceKey<EntityType<?>> ROACH_KEY = ResourceKey.create(
        Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "roach"));

    /**
     * The Roach: {@link MobCategory#MONSTER} so it obeys the usual hostile despawn rules, but it is
     * never in a biome's spawner list - it only ever arrives by being disturbed out of a garbage block.
     * Sized like a silverfish, because it wears a silverfish's model.
     */
    public static final Supplier<EntityType<RoachEntity>> ROACH = ENTITIES.register(
        "roach",
        () -> EntityType.Builder.of(RoachEntity::new, MobCategory.MONSTER)
            .sized(0.4F, 0.3F)
            .eyeHeight(0.13F)
            .clientTrackingRange(8)
            .build(ROACH_KEY));

    private RCEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        modEventBus.addListener(RCEntities::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ROACH.get(), RoachEntity.createAttributes().build());
    }
}
