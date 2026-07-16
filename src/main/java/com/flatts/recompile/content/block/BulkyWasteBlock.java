package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bulky Waste (design P1.11): something big is buried here. Break it to find out what.
 *
 * <p>The real term - municipalities run "bulky waste collection" for the stuff too big
 * for the bag, which is exactly what this holds. It replaces the appliance, which was a
 * placeholder for a fridge or a washer rather than any actual object.
 *
 * <p><b>It is a tell, and that is the whole point.</b> A Block of Garbage is an opaque
 * cube that reveals nothing until a random number decides for you; this one you can
 * <em>see</em> and choose to dig for. See {@code ../trashlands/docs/concept.md} ("Why
 * sifting garbage is fun") - the player has to be the filter, not the RNG. Its texture
 * must never hint at the contents or it spoils its own reveal.
 *
 * <p><b>There is deliberately no interaction.</b> The appliance was pried open with a
 * right-click; this is just broken, and its loot table is the find. That means worldgen
 * only ever places <em>one</em> block type and a find never exists in the world as a
 * specific object - it becomes a mattress (or anything else) only as an item in your
 * hand. Adding a find later is a line in {@code loot_table/blocks/bulky_waste.json},
 * not code, not a model, not a structure template.
 *
 * <p>The prybar is its tool, via {@code recompile:mineable/prybar}. It obeys gravity like
 * the rest of the garbage (P0.3), so mounds still slump when quarried around it.
 */
public class BulkyWasteBlock extends FallingBlock {

    public static final MapCodec<BulkyWasteBlock> CODEC = simpleCodec(BulkyWasteBlock::new);

    public BulkyWasteBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BulkyWasteBlock> codec() {
        return CODEC;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (RCConfig.GARBAGE_GRAVITY_ENABLED.get()) {
            super.tick(state, level, pos, random);
        }
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getMapColor(level, pos).col;
    }
}
