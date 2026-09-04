package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge data attachments: per-entity state that is not an item and not a block.
 *
 * <p><b>The market's scrip balance is the first one this mod has ever registered</b> (spec
 * {@code docs/market_spec.md}, #311). Everything else here that remembers something does it as an
 * item component, a blockstate flyweight or a block entity, and the balance can be none of those: it
 * belongs to a PLAYER, it is deliberately not an item so it cannot be dropped, stolen or hoppered,
 * and a scoreboard would leave it editable from chat. An attachment is exactly "a number the game
 * keeps about a player", which is what company scrip is - ledger credit at the company store.
 */
public final class RCAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Recompile.MOD_ID);

    /**
     * A player's company scrip balance.
     *
     * <p><b>{@code copyOnDeath} is set explicitly, and it is load-bearing.</b> NeoForge copies
     * attachments onto the respawned player only for types that opt in; without it the account empties
     * on death, which nobody would report as a bug - it reads as the shop being broken.
     * {@code a_scrip_balance_survives_death} pins it.
     *
     * <p><b>Not synced.</b> The two terminal screens carry the balance through a menu data slot, the
     * way every other screen here moves a number, so the client never needs the attachment itself.
     */
    public static final Supplier<AttachmentType<Integer>> SCRIP = ATTACHMENTS.register("scrip",
        () -> AttachmentType.builder(() -> 0)
            .serialize(Codec.INT.fieldOf("scrip"))
            .copyOnDeath()
            .build());

    private RCAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
