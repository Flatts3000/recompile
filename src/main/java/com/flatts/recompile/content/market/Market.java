package com.flatts.recompile.content.market;

import com.flatts.recompile.registry.RCAttachments;
import com.flatts.recompile.registry.RCDataMaps;
import com.flatts.recompile.registry.RCTags;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The market's arithmetic, in one place (spec {@code docs/market_spec.md}, #311).
 *
 * <p>Two blocks share this: the Sell Terminal credits a player for products and the Buy Terminal
 * debits them for Blueprints. Both screens draw the same balance and both menus read the same price
 * tables, so the rules live here rather than in either block.
 *
 * <p><b>The balance is a data attachment on the player and is never an item.</b> It cannot be
 * dropped, stored, stolen or lost on death, and selling cannot be automated because a hopper has no
 * account. None of that is enforced by a rule; it falls out of the balance not existing as a stack.
 *
 * <p><b>What sells is a tag and what it pays is a data map</b>, deliberately two surfaces: the tag is
 * the ruling and is what a pack already knows how to extend, and a tag cannot carry a number. A tag
 * member with no price is a build failure ({@code every_sellable_item_has_a_price}) and, at runtime,
 * is refused rather than bought for nothing - the door fails closed in both places.
 */
public final class Market {

    /** A ceiling well below overflow, so a credit can never wrap negative. */
    public static final int MAX_BALANCE = 1_000_000_000;

    private Market() {
    }

    // ---------------- the balance ----------------

    public static int balance(Player player) {
        return player.getData(RCAttachments.SCRIP);
    }

    public static void setBalance(Player player, int balance) {
        player.setData(RCAttachments.SCRIP, Math.max(0, Math.min(MAX_BALANCE, balance)));
    }

    /** Add to the balance. Negative and zero amounts are ignored rather than trusted. */
    public static void credit(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(player, (int) Math.min(MAX_BALANCE, (long) balance(player) + amount));
    }

    /**
     * Take from the balance if it is there.
     *
     * @return whether the full amount was taken. Nothing is taken on a refusal, so a failed purchase
     *         costs nothing - "it produced no sheet" passes just as well on a terminal that never
     *         works, which is why the paired test checks the balance too.
     */
    public static boolean debit(Player player, int amount) {
        if (amount <= 0 || balance(player) < amount) {
            return false;
        }
        setBalance(player, balance(player) - amount);
        return true;
    }

    // ---------------- moving a balance to a screen ----------------

    /**
     * The low 16 bits of a balance, for a menu data slot.
     *
     * <p><b>A data slot is a short on the wire.</b> {@code ClientboundContainerSetDataPacket} declares
     * its value an {@code int} and then writes it with {@code writeShort}, so anything over 32,767
     * arrives wrapped and anything over 65,535 arrives truncated, silently. A balance runs to
     * {@link #MAX_BALANCE}, so it travels as two slots and is put back together with
     * {@link #fromSync}. See {@code BalanceSync}, which is the only caller.
     */
    public static int syncLow(int balance) {
        return balance & 0xFFFF;
    }

    /** The high 16 bits of a balance. */
    public static int syncHigh(int balance) {
        return (balance >>> 16) & 0xFFFF;
    }

    /**
     * The two halves put back together.
     *
     * <p>Both are masked because {@code readShort} sign-extends: a low half of 59,392 arrives as
     * -6,144, and adding that to a shifted high half would come out short by 65,536.
     */
    public static int fromSync(int low, int high) {
        return ((high & 0xFFFF) << 16) | (low & 0xFFFF);
    }

    // ---------------- selling ----------------

    /** What one of this item pays, or 0 for anything unpriced. */
    public static int priceOf(Item item) {
        Integer price = item.builtInRegistryHolder().getData(RCDataMaps.SCRIP_VALUE);
        return price == null ? 0 : price;
    }

    /**
     * Whether the terminal will take this.
     *
     * <p>BOTH the tag and a price, so an item tagged without a price is refused rather than bought for
     * zero. The build already fails on that state; this is the runtime half of failing closed.
     */
    public static boolean isSellable(ItemStack stack) {
        return !stack.isEmpty() && stack.is(RCTags.SELLABLE) && priceOf(stack.getItem()) > 0;
    }

    /** What everything in a container would pay, so the screen can show it before the sale. */
    public static int quote(Container goods) {
        long total = 0;
        for (int slot = 0; slot < goods.getContainerSize(); slot++) {
            ItemStack stack = goods.getItem(slot);
            if (isSellable(stack)) {
                total += (long) priceOf(stack.getItem()) * stack.getCount();
            }
        }
        return (int) Math.min(MAX_BALANCE, total);
    }

    // ---------------- buying ----------------

    /**
     * One thing the Buy Terminal stocks: a Blueprint set and its price in scrip.
     *
     * <p>Declared by a {@code recompile:market_offer} recipe and carried to the client in the menu's
     * open buffer, so the screen draws exactly the list the server will sell from and no second sync
     * path exists.
     */
    public record Offer(ItemStack stack, int price) {

        /**
         * The stack itself travels, components and all.
         *
         * <p>That is what lets one row type sell both knowledge and things: a blueprint line is a
         * Blueprint stack carrying its set component, an item line is the item. The screen renders
         * whatever it is handed and the purchase hands back a copy, so neither side needs to know
         * which kind of offer it is looking at - except to NAME it, since a Blueprint's item name is
         * "Blueprint" for every set and the set name lives in its component.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> STREAM_CODEC =
            StreamCodec.composite(
                ItemStack.STREAM_CODEC, Offer::stack,
                ByteBufCodecs.VAR_INT, Offer::price,
                Offer::new);

        public static final StreamCodec<RegistryFriendlyByteBuf, java.util.List<Offer>>
            LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list());

        /** What to call this row: the Blueprint's set name, or the item's own name. */
        public net.minecraft.network.chat.Component displayName() {
            Identifier set = com.flatts.recompile.content.item.BlueprintItem.blueprintOf(stack);
            return set != null
                ? com.flatts.recompile.content.item.BlueprintItem.setName(set)
                : stack.getHoverName();
        }

        /** The Blueprint set this row sells, or null if it sells a thing rather than knowledge. */
        public @org.jspecify.annotations.Nullable Identifier blueprint() {
            return com.flatts.recompile.content.item.BlueprintItem.blueprintOf(stack);
        }
    }
}
