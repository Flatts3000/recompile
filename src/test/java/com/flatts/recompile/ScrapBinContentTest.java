package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.block.ScrapBinContent;
import com.flatts.recompile.registry.RCItems;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Scrap Bin's content-to-color mapping (design P2.9). The first JUnit tests in the
 * mod - they run under moddev's {@code unitTest} integration, which loads a real mod context so the
 * item registry is populated (the mapping is by registry id) without booting a server.
 *
 * <p>Behaviour that needs a world (deposit, withdraw, break-survives, the interaction) lives in
 * {@code ScrapBinTests} GameTests; this covers the pure, world-free logic.
 */
class ScrapBinContentTest {

    @Test
    void knownMaterialsMapToTheirOwnValue() {
        assertEquals(ScrapBinContent.SCRAP_METAL, ScrapBinContent.forItem(RCItems.SCRAP_METAL.get()));
        assertEquals(ScrapBinContent.PLASTIC_SCRAP, ScrapBinContent.forItem(RCItems.PLASTIC_SCRAP.get()));
        assertEquals(ScrapBinContent.GLASS_SHARDS, ScrapBinContent.forItem(RCItems.GLASS_SHARDS.get()));
        assertEquals(ScrapBinContent.ORGANIC_MUCK, ScrapBinContent.forItem(RCItems.ORGANIC_MUCK.get()));
        assertEquals(ScrapBinContent.FIBER_SCRAP, ScrapBinContent.forItem(RCItems.FIBER_SCRAP.get()));
        assertEquals(ScrapBinContent.E_SCRAP, ScrapBinContent.forItem(RCItems.E_SCRAP.get()));
        assertEquals(ScrapBinContent.JUNK, ScrapBinContent.forItem(RCItems.JUNK.get()));
    }

    @Test
    void unmappedItemsFallBackToGeneric() {
        // Anything binnable-but-uncolored (a modded material) or simply not ours lands on GENERIC.
        assertEquals(ScrapBinContent.GENERIC, ScrapBinContent.forItem(Items.IRON_INGOT));
        assertEquals(ScrapBinContent.GENERIC, ScrapBinContent.forItem(Items.DIAMOND));
        // A recompile item that is not a binnable material must not accidentally map to a color.
        assertEquals(ScrapBinContent.GENERIC, ScrapBinContent.forItem(RCItems.PRYBAR.get()));
    }

    @Test
    void emptyAndGenericAreWhiteSoTheNeutralTextureShows() {
        assertEquals(0xFFFFFF, ScrapBinContent.EMPTY.color());
        assertEquals(0xFFFFFF, ScrapBinContent.GENERIC.color());
    }

    @Test
    void everyMaterialColorIsDistinctSoAWallReadsByHue() {
        Set<Integer> colors = new HashSet<>();
        for (ScrapBinContent content : ScrapBinContent.values()) {
            if (content == ScrapBinContent.EMPTY || content == ScrapBinContent.GENERIC) {
                continue;
            }
            assertTrue(colors.add(content.color()),
                content + " shares a color with another material - bins would be ambiguous");
            assertNotEquals(0xFFFFFF, content.color(), content + " must not be the neutral white");
        }
    }
}
