package com.flatts.recompile.client;

import com.flatts.recompile.content.item.GarbageVacuumItem;
import com.flatts.recompile.content.item.VacuumTier;
import com.flatts.recompile.registry.RCSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Garbage Vacuum's running sound, held open for as long as somebody is holding the trigger (#378).
 *
 * <p><b>A loop is a client-side instance, not a server one-shot every N ticks.</b> What shipped before
 * was the latter - one vanilla puff every twelve ticks - and it could not be anything else while the
 * server owned it: repeated one-shots cannot butt together without a seam, they drift out of phase
 * between listeners, and they carry no notion of starting or stopping, so a tap was silent and a
 * release just left a gap. Vanilla runs elytra flight and the minecart the same way this does.
 *
 * <p><b>Nothing is sent over the network for it.</b> Whether a player is using an item is already
 * synced entity state, so every client can see every nearby player's vacuum start and stop by looking.
 * That is also what makes the stop path reliable: the instance asks each tick whether the trigger is
 * still held, so releasing, going flat, swapping hotbar slot and dying all end it without any of those
 * sites having to remember to say so. A sustain still playing after the vacuum is put away is the
 * failure this shape exists to prevent, and a call at each stop site is how you get one.
 *
 * <p>Opening a SCREEN is deliberately not on that list, and this said it was.
 * {@code Minecraft.tick} only calls {@code handleKeybinds} while no screen and no overlay is up, and
 * the release lives inside it - so opening the inventory mid-suck leaves the trigger held and the
 * vacuum still working on the server. The sound keeping up with that is correct; claiming it stops was
 * the error.
 *
 * <p>The rev-down is played from here rather than from the item for exactly that reason: this is the
 * one place that knows the loop has ended, whichever way it ended.
 */
public final class VacuumSoundInstance extends AbstractTickableSoundInstance {

    /**
     * Who currently has a loop running. Keyed by player, so two people vacuuming side by side get one
     * each. See {@link #tickAll} for why it is pruned on two conditions rather than one.
     */
    private static final Map<UUID, VacuumSoundInstance> ACTIVE = new HashMap<>();

    /** What the loop settles at once the spin-up has handed over. */
    private static final float FULL_VOLUME = 0.8F;

    private final Player player;
    private final VacuumTier tier;

    private VacuumSoundInstance(Player player, VacuumTier tier) {
        super(RCSounds.vacuum(tier, "sustain"), SoundSource.PLAYERS, player.getRandom());
        this.player = player;
        this.tier = tier;
        this.looping = true;
        // delay STAYS ZERO. The obvious way to sit the loop behind the spin-up is to delay it, and it
        // is the wrong one: the sound engine only loops a channel seamlessly when the delay is zero,
        // and a delayed instance falls onto the manual re-queue path - which puts back exactly the seam
        // this whole change removes. So it starts immediately and fades IN across the spin-up instead,
        // which is what vanilla's elytra instance does for the same reason.
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 1.0F;
        follow();
    }

    /** Start one for anybody who has just begun vacuuming, and forget anyone whose loop has ended. */
    public static void tickAll(Level level) {
        // Pruned on TWO conditions, and the second is the one that matters. An instance that stopped
        // itself reports it; an instance the sound engine dropped underneath us does not - leaving the
        // world, or the sound system being reloaded, stops every sound without telling the instance.
        // A map that only shrank on a clean stop would then hold a live-looking entry forever and
        // refuse to start that player's vacuum ever again. Asking whether they are still vacuuming
        // needs no cooperation from anybody.
        for (Iterator<Map.Entry<UUID, VacuumSoundInstance>> it = ACTIVE.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, VacuumSoundInstance> entry = it.next();
            Player owner = level.getPlayerByUUID(entry.getKey());
            if (entry.getValue().isStopped() || owner == null || usingTier(owner) == null) {
                it.remove();
            }
        }
        for (Player player : level.players()) {
            VacuumTier tier = usingTier(player);
            if (tier == null || ACTIVE.containsKey(player.getUUID())) {
                continue;
            }
            VacuumSoundInstance instance = new VacuumSoundInstance(player, tier);
            ACTIVE.put(player.getUUID(), instance);
            Minecraft.getInstance().getSoundManager().queueTickingSound(instance);
        }
    }

    /** The tier of the vacuum this player is holding the trigger on, or null if they are not. */
    private static VacuumTier usingTier(Player player) {
        if (!player.isAlive() || player.isRemoved() || !player.isUsingItem()) {
            return null;
        }
        return player.getUseItem().getItem() instanceof GarbageVacuumItem vacuum ? vacuum.tier() : null;
    }

    /** How long the spin-up runs, in client ticks: 0.45 s of audio at 20 ticks a second. */
    private static final int FADE_IN_TICKS = 9;

    private int age;

    @Override
    public void tick() {
        if (usingTier(player) != tier) {
            // Whatever ended it - released, flat, swapped, screen, death - this is where it is noticed,
            // so this is where the machine winds down. The level can be gone by now (the world unloaded
            // between the sound engine's tick and this one), and a wind-down nobody is left to hear is
            // not worth an NPE on the render thread.
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                level.playLocalSound(
                    player.getX(), player.getY(), player.getZ(),
                    RCSounds.vacuum(tier, "rev_down"), SoundSource.PLAYERS, 0.8F, 1.0F, false);
            }
            stop();
            return;
        }
        age++;
        this.volume = FULL_VOLUME * Math.min(1.0F, (float) age / FADE_IN_TICKS);
        follow();
    }

    /** Ride along with the player, so the sound comes from the machine rather than from where it started. */
    private void follow() {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}
