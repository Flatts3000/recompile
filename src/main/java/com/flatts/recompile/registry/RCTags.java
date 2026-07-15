package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tag keys this mod defines. Vanilla tags it merely contributes to live in JSON only. */
public final class RCTags {

    private RCTags() {
    }

    /**
     * Blocks the scrap knife cuts free quickly - the knife's answer to
     * {@code minecraft:mineable/shovel}.
     *
     * <p>Every garbage block has exactly one tool: garbage digs with the junk shovel, an
     * appliance is pried, and a bale is the knife's. The knife already <em>opens</em> a
     * bale in place, but a bale is also the Sorting Tarp's best input, and harvesting one
     * meant mining it with nothing that helps - 27 ticks, which made the richest block in
     * the game the slowest to actually cash in. Giving the knife a mining rule over this
     * tag lets it cut a bale loose as well as open it, without handing the shovel a job
     * that is not its own.
     */
    public static final TagKey<Block> MINEABLE_WITH_KNIFE = TagKey.create(
        Registries.BLOCK, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "mineable/knife"));
}
