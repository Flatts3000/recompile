package com.flatts.recompile.content.block;

import net.minecraft.world.level.block.Block;

/**
 * Stained Ground: the patch where something leaked (#285). Dressing, not loot.
 *
 * <p>It carries no pull stream and no drops of its own. It exists so the ground the drums sit on reads
 * as contaminated, which is the whole of its job - the region's identity in V1 comes from what is
 * found and what it looks like, because the radiation is deferred to Mekanism.
 *
 * <p><b>It cannot be healed, and that is the design rather than a limitation.</b> Being a SURFACE block
 * it is the one qualification to the rule that this biome keeps household's coarse-dirt surface. Kept
 * out of {@code #minecraft:substrate_overworld}, grass will never spread onto it and the Grass Spreader
 * will not convert it: contamination that scrubs clean is not contamination.
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
 * <p>An ordinary block, deliberately: it has no behaviour to implement. It is coarse dirt's hardness
 * and sound with a different name and a stained face.
 */
public class StainedGroundBlock extends Block {

    public StainedGroundBlock(Properties properties) {
        super(properties);
    }
}
