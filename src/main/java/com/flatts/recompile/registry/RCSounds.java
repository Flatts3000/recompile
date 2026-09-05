package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's sound events. <b>There were none before the Scrap Hauler</b> (#376): everything else here
 * plays a vanilla {@code SoundEvents} constant, so this registry, the {@code sounds.json} beside it and
 * the {@code .ogg} files it names are all firsts.
 *
 * <p><b>The audio is SOURCED, not generated</b> (owner, 2026-09-05), which is the opposite of the
 * texture rule and is stated here so nobody reaches for a generator by analogy. Until sourced files
 * land, {@code sounds.json} points each event at a vanilla sound so the events resolve and the hooks
 * are exercised; swapping in the real audio is a change to that JSON and nothing here.
 *
 * <p>Four, deliberately, per the spec's core set: everything else the machine does falls back to a
 * vanilla sound at the call site, the way the rest of the mod already works.
 */
public final class RCSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, Recompile.MOD_ID);

    /** Looped while it works: the whir a player hears before they see it. */
    public static final Supplier<SoundEvent> HAULER_IDLE = register("entity.scrap_hauler.idle");

    /** A block leaving the world into its hold. */
    public static final Supplier<SoundEvent> HAULER_PICKUP = register("entity.scrap_hauler.pickup");

    /** The Depot letting it out. */
    public static final Supplier<SoundEvent> HAULER_DEPLOY = register("entity.scrap_hauler.deploy");

    /** The Depot taking it back. */
    public static final Supplier<SoundEvent> HAULER_RECALL = register("entity.scrap_hauler.recall");

    private RCSounds() {
    }

    private static Supplier<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
