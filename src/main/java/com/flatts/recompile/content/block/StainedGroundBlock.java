package com.flatts.recompile.content.block;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.world.level.block.Block;

/**
 * Stained Ground: the patch where something leaked (#285), and since P1.6-R the radioactive dump's
 * memory block as well.
 *
 * <p>It shipped as pure dressing - no pull stream, no drops of its own - so that the ground the drums
 * sit on reads as contaminated, the region's identity in V1 coming from what is found and what it
 * looks like because the radiation is deferred to Mekanism. Regrowth then needed a memory block under
 * every tailings column and this was already there, already painted across the whole impoundment
 * footprint by {@code TailingsHeapFeature}, and already the right thing to look at. So the dump's
 * half of P1.6-R cost a superclass and a {@code randomTicks()} rather than a new block.
 *
 * <p><b>Most of the disc is still dressing, and that is what {@code HEIGHT} 0 means.</b> The stain
 * reaches a ring past the toe of the pile, and those cells carry no height - so they are inert by
 * exactly the rule that makes a hand-placed one inert. Only the columns the feature actually built on
 * remember anything.
 *
 * <p><b>It cannot be healed, and that is the design rather than a limitation.</b> Being a SURFACE
 * block it is the one qualification to the rule that this biome keeps household's coarse-dirt
 * surface. Kept out of {@code #minecraft:substrate_overworld}, grass will never spread onto it and the
 * Grass Spreader will not convert it: contamination that scrubs clean is not contamination.
 *
 * <p><b>Since P1.6-R that has a second consequence, and the owner ruled it deliberately
 * (2026-09-08): a tailings impoundment NEVER RETIRES.</b> Greening a footprint is what retires a
 * mound and a rubble pile, and there is no green here to do it with - so the radioactive dump refills
 * forever and is the one region of the world that cannot be reclaimed. Three ways out were rejected:
 * a new remediation rung (which would make the no-heal rule decorative), a finite regrowth count
 * (invisible to the player, so a heap that stopped would read as a bug), and simply letting the Grass
 * Spreader heal this block (which takes the region's identity with it).
 *
 * <p>There is precedent for a bespoke ground block sitting outside the dirt tags. {@code MoundGround}
 * is deliberately out of {@code #minecraft:dirt}, because membership would reach {@code #encroachable}
 * through {@code #substrate_overworld} and the junkyard would eat its own memory.
 *
 * <p>So two ground types behave differently inside one biome: <b>grass is contested on the clean
 * ground and impossible on the stained patches.</b> Unlike the demolition yard's reverted encroachment
 * exception, this asymmetry is <em>discoverable</em> - the ground looks different, which is the entire
 * point of the block.
 *
 * <p><b>Unlike the other two grounds this one keeps its item and its loot table</b>, because it was a
 * material a builder could collect before it was a memory and taking that away would be a regression
 * for a change that is supposed to add. A placed one carries {@code HEIGHT} 0 and is inert.
 */
public class StainedGroundBlock extends RegrowingGroundBlock {

    public StainedGroundBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected Block regrown() {
        return RCBlocks.MILL_TAILINGS.get();
    }
}
