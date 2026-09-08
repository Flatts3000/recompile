package com.flatts.recompile.content.block;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.world.level.block.Block;

/**
 * Mound Ground: coarse dirt that remembers a garbage mound stood on it (design P1.6, Phase 5).
 *
 * <p>The first of the three regrowing grounds and the one the mechanic was built for.
 * {@link RegrowingGroundBlock} carries all of the behaviour; what is here is the material it grows
 * back and the things true of this block rather than of the mechanic.
 *
 * <p><b>It is coarse dirt with a different name and a darker face</b> (owner, 2026-08-05), and the
 * rest follows from that. Same hardness, same sound, same shovel, no tool gate. Grass will not spread
 * onto it by itself, exactly as it will not spread onto coarse dirt, so the Grass Spreader is the
 * only thing that greens it - and greening it is what retires the mound. It is deliberately kept OUT
 * of {@code #minecraft:dirt}: membership would drag it into {@code #encroachable} through
 * {@code substrate_overworld}, and the junkyard eating its own memory is not a fight, it is a bug.
 *
 * <p><b>No item form and it drops nothing</b>, so it digs exactly like the coarse dirt it is and
 * there is nothing to carry off - this is world memory rather than a material. Digging one out simply
 * forgets that column, which is the same outcome as retiring it with grass and wants no second rule.
 * {@link RubbleGroundBlock} is the same in both respects; {@link StainedGroundBlock} is not, because
 * it was dressing before it was a memory.
 */
public class MoundGroundBlock extends RegrowingGroundBlock {

    public MoundGroundBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected Block regrown() {
        return RCBlocks.GARBAGE_BLOCK.get();
    }
}
