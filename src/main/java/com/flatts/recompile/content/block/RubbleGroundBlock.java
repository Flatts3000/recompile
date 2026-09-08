package com.flatts.recompile.content.block;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.world.level.block.Block;

/**
 * Rubble Ground: the demolition yard's memory block, under a rubble pile's footprint (P1.6-R).
 *
 * <p>Named the way {@link MoundGroundBlock} is - for what it remembers rather than for what it looks
 * like - and built the same way: vanilla coarse dirt retinted, so it is the same material with the
 * concrete dust of the yard ground into it rather than a new one. The yard's floor is coarse dirt
 * like everywhere else, so without a distinct face there would be no way to see where a pile used to
 * stand, and seeing that is half of what the memory is for.
 *
 * <p><b>It exists because the yard had no ground block of its own and the other two regions did.</b>
 * The sprawl has Mound Ground and the dump has Stained Ground, both already painted under their
 * piles; extending regrowth to the yard is the only part of P1.6-R that needed a new block at all.
 *
 * <p><b>In {@code #recompile:spreadable}, so a rubble pile RETIRES.</b> That is the difference
 * between this region and the dump: green the footprint and the memory goes with it, exactly as a
 * mound does. Stained Ground is deliberately not spreadable, which is why a tailings impoundment
 * never retires. Also in {@code #recompile:hostile_ground}, so the frontier reads it as unhealed,
 * and out of {@code #minecraft:dirt} for the same reason Mound Ground is - membership would reach
 * {@code #encroachable} and the yard would eat its own memory.
 *
 * <p>No item form and no drops, the same as Mound Ground: world memory is not a material.
 */
public class RubbleGroundBlock extends RegrowingGroundBlock {

    public RubbleGroundBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected Block regrown() {
        return RCBlocks.STONE_RUBBLE.get();
    }
}
