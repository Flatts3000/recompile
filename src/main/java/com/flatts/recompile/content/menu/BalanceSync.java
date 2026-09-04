package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.market.Market;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;

/**
 * Carries a player's scrip balance from the server to an open market screen.
 *
 * <p><b>A menu data slot is 16 bits on the wire, whatever its Java type says.</b>
 * {@code ClientboundContainerSetDataPacket} holds an {@code int} in its field and writes it with
 * {@code writeShort}, reading it back with {@code readShort} - so a value over 32,767 arrives
 * wrapped negative and one over 65,535 arrives truncated. Nothing logs it and the server stays
 * right, so the only symptom is a screen quoting a number the player knows is wrong.
 *
 * <p>The mod had never met this: every other synced value here is a tank or a buffer capped at
 * 20,000 or less. A balance is capped at {@link Market#MAX_BALANCE}, which is a billion, and
 * selling a few hundred solar panels clears a short - so this is the first number in the mod that
 * genuinely needs two slots, one per half, put back together on the client.
 *
 * <p><b>One implementation for both terminals</b>, because two copies of a bit-shift is exactly the
 * kind of thing that drifts in one screen and not the other. The arithmetic itself lives on
 * {@link Market} so a unit test can drive it without a menu.
 */
final class BalanceSync {

    private final Player player;
    private int low;
    private int high;

    BalanceSync(Player player) {
        this.player = player;
    }

    /** The low half. Add it to the menu FIRST; {@link #balance()} does not care about order. */
    DataSlot lowSlot() {
        return new DataSlot() {
            @Override
            public int get() {
                return Market.syncLow(Market.balance(player));
            }

            @Override
            public void set(int value) {
                BalanceSync.this.low = value;
            }
        };
    }

    /** The high half. */
    DataSlot highSlot() {
        return new DataSlot() {
            @Override
            public int get() {
                return Market.syncHigh(Market.balance(player));
            }

            @Override
            public void set(int value) {
                BalanceSync.this.high = value;
            }
        };
    }

    /**
     * What the screen should show.
     *
     * <p>The server answers from the attachment, which is the truth; the client answers from the two
     * halves it has been sent. Deliberately NOT written back into the client player's own attachment:
     * a mirror that is only fresh while a screen is open would be a trap for anything that later
     * reads {@code Market.balance} on the client and believes it.
     */
    int balance() {
        return player.level().isClientSide()
            ? Market.fromSync(low, high)
            : Market.balance(player);
    }
}
