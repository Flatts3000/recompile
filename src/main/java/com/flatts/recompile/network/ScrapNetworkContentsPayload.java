package com.flatts.recompile.network;

import com.flatts.recompile.Recompile;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The connected scrap network's contents, computed server-side and pushed to the client so the Scrap
 * Crafting Table's panel can show them (design P2.10 flow 4). This is the <b>single source of truth</b>
 * for the panel: the server owns the real bins + barrel, aggregates what is available, and syncs it -
 * so the client never floods block entities itself, and the panel can never disagree with reality.
 *
 * @param binCount  how many Scrap Bins are wired in (empty or not) - drives the "N bins" summary
 * @param hasBarrel whether a Scrap Barrel is wired in
 * @param materials every item available in the network (bins + barrel), merged by item with its total
 */
public record ScrapNetworkContentsPayload(int binCount, boolean hasBarrel, List<Material> materials)
        implements CustomPacketPayload {

    public static final Type<ScrapNetworkContentsPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "scrap_network_contents"));

    public static final ScrapNetworkContentsPayload EMPTY =
        new ScrapNetworkContentsPayload(0, false, List.of());

    /** One available material and how much of it the whole network holds. */
    public record Material(Item item, int count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Material> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), Material::item,
            ByteBufCodecs.VAR_INT, Material::count,
            Material::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ScrapNetworkContentsPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScrapNetworkContentsPayload::binCount,
            ByteBufCodecs.BOOL, ScrapNetworkContentsPayload::hasBarrel,
            Material.STREAM_CODEC.apply(ByteBufCodecs.list()), ScrapNetworkContentsPayload::materials,
            ScrapNetworkContentsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
