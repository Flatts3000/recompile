package com.flatts.recompile.content.freight;

import com.flatts.recompile.content.recipe.FreightPhaseRecipe;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * What the screen needs to draw a phase, sent once through the menu's open buffer.
 *
 * <p><b>The manifest travels in the open buffer, not in data slots</b>, which is the Buy Terminal's
 * pattern (#370) rather than a new one. Requirements do not change while a screen is open, so paying
 * for them every tick in the 16-bit data channel would be waste on top of a hazard: a count may run
 * to 100,000 and a data slot carries 32,767. Only the DELIVERED numbers move, and those are the ones
 * the menu syncs.
 *
 * <p><b>An empty manifest is a real state, not an error.</b> It means the ladder is finished, or a
 * pack shipped no phases at all. The screen says so rather than drawing an empty table.
 */
public record FreightManifest(String name, int tier, int ladderLength, List<Line> lines) {

    /** How many lines a screen will draw. A pack with more gets the first six and a note. */
    public static final int MAX_LINES = 6;

    public record Line(Item item, int required) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM), Line::item,
                ByteBufCodecs.VAR_INT, Line::required,
                Line::new);
    }

    public static final FreightManifest NONE = new FreightManifest("", 0, 0, List.of());

    public static final StreamCodec<RegistryFriendlyByteBuf, FreightManifest> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FreightManifest::name,
            ByteBufCodecs.VAR_INT, FreightManifest::tier,
            ByteBufCodecs.VAR_INT, FreightManifest::ladderLength,
            Line.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), FreightManifest::lines,
            FreightManifest::new);

    /** The manifest for whatever phase {@code tier} is working toward. */
    public static FreightManifest of(FreightPhaseRecipe phase, int tier, int ladderLength) {
        List<Line> lines = phase.requires().stream()
            .limit(MAX_LINES)
            .map(requirement -> new Line(requirement.item(), requirement.count()))
            .toList();
        return new FreightManifest(phase.name(), tier, ladderLength, lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
