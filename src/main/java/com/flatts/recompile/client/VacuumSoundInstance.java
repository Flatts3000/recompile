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
 * still held, so releasing, going flat, swapping hotbar slot, opening a screen and dying all end it
 * without any of those sites having to remember to say so. A sustain still playing after the vacuum is
 * put away is the failure this shape exists to prevent, and a call at each stop site is how you get one.
 *
 * <p>The rev-down is played from here rather than from the item for exactly that reason: this is the
 * one place that knows the loop has ended, whichever way it ended.
 */
public final class VacuumSoundInstance extends AbstractTickableSoundInstance {

    /**
     * Who currently has a loop running. Keyed by player so two people vacuuming side by side get one
     * each, and pruned by asking the instances themselves rather than by trusting a removal call: a
     * world unload stops every sound without telling us, and a map that only shrinks on a clean stop
     * would then refuse to start the sound again.
     */
    private static final Map<UUID, VacuumSoundInstance> ACTIVE = new HashMap<>();

    private final Player player;
    private final VacuumTier tier;

    private VacuumSoundInstance(Player player, VacuumTier tier) {
        super(RCSounds.vacuum(tier, "sustain"), SoundSource.PLAYERS, player.getRandom());
        this.player = player;
        this.tier = tier;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.8F;
        this.pitch = 1.0F;
        follow();
    }

    /** Start one for anybody who has just begun vacuuming, and forget anyone whose loop has ended. */
    public static void tickAll(Level level) {
        for (Iterator<Map.Entry<UUID, VacuumSoundInstance>> it = ACTIVE.entrySet().iterator(); it.hasNext();) {
            if (it.next().getValue().isStopped()) {
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

    @Override
    public void tick() {
        if (usingTier(player) != tier) {
            // Whatever ended it - released, flat, swapped, screen, death - this is where it is noticed,
            // so this is where the machine winds down.
            Minecraft.getInstance().level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                RCSounds.vacuum(tier, "rev_down"), SoundSource.PLAYERS, 0.8F, 1.0F, false);
            stop();
            return;
        }
        follow();
    }

    /** Ride along with the player, so the sound comes from the machine rather than from where it started. */
    private void follow() {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}
