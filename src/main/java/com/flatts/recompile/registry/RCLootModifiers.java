package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.loot.StripSaplingsModifier;
import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Global loot modifiers. One so far: the sapling lockout (P2.4-R2), which makes saplings
 * unobtainable as items so the tree planter is the only way one ever enters the world.
 *
 * <p>Registering the serializer here is only half the wiring - a modifier does nothing until it is
 * <em>instanced</em> by JSON and listed in the index. Both live under a directory named
 * <b>{@code loot_modifiers}, plural</b>: it is NeoForge's folder, not one of the vanilla data
 * directories 26.1 singularised, so the instinct to match {@code loot_table/} and {@code recipe/}
 * would silently stop the modifier loading with no error anywhere.
 */
public final class RCLootModifiers {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Recompile.MOD_ID);

    /** Strips {@code #minecraft:saplings} out of every loot roll. */
    public static final Supplier<MapCodec<? extends IGlobalLootModifier>> STRIP_SAPLINGS =
        LOOT_MODIFIERS.register("strip_saplings", () -> StripSaplingsModifier.CODEC);

    private RCLootModifiers() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        LOOT_MODIFIERS.register(modEventBus);
    }
}
