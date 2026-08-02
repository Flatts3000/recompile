package com.flatts.recompile.network;

import com.flatts.recompile.Recompile;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: fill the Scrap Crafting Table's grid with these items (#95).
 *
 * <p><b>Why this exists at all.</b> JEI's stock transfer handler moves items out of the container's own
 * slots, and this table's materials mostly are not in them - they are in the Scrap Barrel and the Scrap
 * Bins wired to it, which is the entire point of the connected-storage panel. A player looking at a
 * recipe whose ingredients are all sitting in the barrel next door was told "Missing Items".
 *
 * <p><b>One item id per grid slot, nine of them, {@code -1} for empty.</b> Not a recipe id: the client
 * has already decided what goes where, and a recipe id would make the server redo that work with a
 * different notion of which ingredient satisfies a tag. Sending placements keeps one decision in one
 * place, and the server still validates that it can actually find each item before moving anything.
 *
 * <p>Registry ids over the wire rather than {@code ItemStack}s, matching what the panel's own
 * withdraw button already packs into a menu-button id.
 */
public record FillGridPayload(List<Integer> items) implements CustomPacketPayload {

    /** A 3x3 grid, always sent in full so slot index is position. */
    public static final int SLOTS = 9;
    /** No item for this slot. */
    public static final int EMPTY = -1;

    public static final CustomPacketPayload.Type<FillGridPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "fill_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FillGridPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(SLOTS)), FillGridPayload::items,
            FillGridPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
