package com.flatts.recompile.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * What a filled Scrap Bin carries on its dropped item: the material it is bound to and how much.
 *
 * <p>An item data component, so a bin broken with contents keeps them - the same break-survives
 * mechanism the Rain Collector uses for its water. Written only when the bin holds something; a bin
 * broken empty drops with no component and so comes back blank and unbound.
 *
 * @param material the bound salvage item
 * @param count    how many are stored (may far exceed a stack; the bin holds thousands)
 */
public record ScrapBinContents(Item material, int count) {

    public static final Codec<ScrapBinContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("material").forGetter(ScrapBinContents::material),
        Codec.INT.fieldOf("count").forGetter(ScrapBinContents::count)
    ).apply(instance, ScrapBinContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScrapBinContents> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.registry(Registries.ITEM), ScrapBinContents::material,
        ByteBufCodecs.VAR_INT, ScrapBinContents::count,
        ScrapBinContents::new);
}
