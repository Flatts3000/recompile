package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flatts.recompile.content.ScrapBinContents;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ScrapBinContents} data component's codec (design P2.9). This is what
 * carries a full bin's {material, count} onto its dropped item, so a break-then-replace must survive
 * a serialize round trip exactly - a silent drift here loses or corrupts a player's stockpile.
 *
 * <p>Runs under moddev's {@code unitTest} integration (item registry populated, no server).
 */
class ScrapBinContentsCodecTest {

    @Test
    void roundTripsThroughNbtExactly() {
        ScrapBinContents original = new ScrapBinContents(RCItems.SCRAP_METAL.get(), 1234);

        Tag encoded = ScrapBinContents.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        ScrapBinContents decoded = ScrapBinContents.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(original, decoded);
        assertEquals(RCItems.SCRAP_METAL.get(), decoded.material());
        assertEquals(1234, decoded.count());
    }

    @Test
    void carriesLargeAmountsBeyondAStack() {
        // The bin holds thousands, far past a 64 stack - the codec must not clamp the count.
        ScrapBinContents original = new ScrapBinContents(RCItems.PLASTIC_SCRAP.get(), 4096);

        Tag encoded = ScrapBinContents.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        ScrapBinContents decoded = ScrapBinContents.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(4096, decoded.count());
    }
}
