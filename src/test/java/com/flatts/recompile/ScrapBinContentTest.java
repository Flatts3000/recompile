package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.block.ScrapBinContent;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /**
     * Distinct is not the same as distinguishable, and the difference was a real bug.
     *
     * <p>The test above only rejects an exact collision, so two colours one step apart passed it.
     * SCRAP_METAL and ANDESITE_SHARD sat 6 apart in the 0-765 sum-of-channels range - the same grey to
     * any eye - and a wall of bins could not be read by hue, which is the entire job of these colours.
     * Found while giving the shards real art (#76), because that is when someone finally measured.
     *
     * <p>The bar is 20 against a real minimum of 23, so it has a little headroom and still fails the
     * moment two materials drift back together. Raising it means retuning a colour, which is the point.
     */
    @Test
    void everyMaterialColorIsFarEnoughApartToActuallyTellApart() {
        final int minSeparation = 20;
        List<ScrapBinContent> materials = new ArrayList<>();
        for (ScrapBinContent content : ScrapBinContent.values()) {
            if (content != ScrapBinContent.EMPTY && content != ScrapBinContent.GENERIC) {
                materials.add(content);
            }
        }
        assertTrue(materials.size() > 10,
            "only " + materials.size() + " materials were swept - the filter is wrong, so this would "
                + "pass against any collision");

        for (int i = 0; i < materials.size(); i++) {
            for (int j = i + 1; j < materials.size(); j++) {
                int a = materials.get(i).color();
                int b = materials.get(j).color();
                int distance = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                    + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                    + Math.abs((a & 0xFF) - (b & 0xFF));
                assertTrue(distance >= minSeparation,
                    materials.get(i) + " and " + materials.get(j) + " are only " + distance
                        + " apart (need " + minSeparation + ") - two bins in a wall would read the same");
            }
        }
    }
}
