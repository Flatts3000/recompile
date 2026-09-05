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
 * <p><b>The audio is SYNTHESISED, and that reverses a ruling made earlier the same day</b> (owner,
 * 2026-09-05). This javadoc said "SOURCED, not generated ... so nobody reaches for a generator by
 * analogy", which held for about as long as it took to price sourcing. Three things decided it, and
 * the first is the one that binds hardest:
 *
 * <ul>
 *   <li><b>Licensing.</b> This repo is MIT and a mod ships loose {@code .ogg} files inside a jar
 *       anyone can unzip, so its licence hands every downstream user the right to redistribute them.
 *       The obvious AI tool forbids exactly that - ElevenLabs' prohibited use policy 9(c) bars
 *       distributing sound-effect output "on a standalone basis ... including as isolated files,
 *       audio samples ... or other collections of sounds" - so the licence would promise something
 *       the assets could not back. Same shape as the Create split: MIT code, reserved assets.</li>
 *   <li><b>Loops.</b> A machine loop plays for as long as a player holds a button. A seam is not a
 *       blemish, it is a click once a cycle forever. Synthesis is seamless by construction; a clip
 *       has to be crossfaded by hand and still drifts.</li>
 *   <li><b>Families.</b> Every sound this mod needs is a machine, so one declared voice gives the
 *       Hauler and the Garbage Vacuum a shared character that cannot drift apart, and a tier ladder
 *       becomes a scalar rather than four more sourcing jobs.</li>
 * </ul>
 *
 * <p>The generator is {@code sfxgen} in {@code ../mc-pack-toolkit}, texgen's audio sibling, driven by
 * {@code sfxgen.toml} in this repo. Only the finished mono 44.1 kHz Ogg files are committed.
 *
 * <p>Four, deliberately, per the spec's core set: everything else the machine does falls back to a
 * vanilla sound at the call site, the way the rest of the mod already works.
 */
public final class RCSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, Recompile.MOD_ID);

    /**
     * Looped while it works: the whir a player hears before they see it.
     *
     * <p>Played on a fixed cadence from {@link com.flatts.recompile.content.entity.ScrapHaulerEntity},
     * NOT through {@code getAmbientSound}, whose spacing vanilla randomises - a machine that hums
     * irregularly reads as a creature. The cadence is the clip's own length, so consecutive plays
     * butt together into one continuous hum.
     */
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
