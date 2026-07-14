package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * BlockEntity for {@link com.flatts.recompile.content.block.GarbageBlock}. Tracks
 * hand-sorting progress (design P0.4): each empty-hand pull yields one drop, and
 * the block crumbles after 4-6 pulls. The crumble threshold is rolled lazily on
 * the first pull so every block wears out at a slightly different point.
 */
public class GarbageBlockEntity extends BlockEntity {

    private static final int MIN_PULLS = 4;
    private static final int MAX_PULLS = 6;

    /** How many pulls have been taken from this block. */
    private int pulls;

    /** The pull count at which this block crumbles; 0 until rolled on the first pull. */
    private int crumbleAt;

    public GarbageBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.GARBAGE_BLOCK.get(), pos, state);
    }

    /**
     * Record one pull and report whether the block should now crumble. The crumble
     * threshold is chosen in [{@value #MIN_PULLS}, {@value #MAX_PULLS}] on the first
     * pull. Server-side only.
     */
    public boolean recordPullAndCheckCrumble(RandomSource random) {
        if (crumbleAt == 0) {
            crumbleAt = MIN_PULLS + random.nextInt(MAX_PULLS - MIN_PULLS + 1);
        }
        pulls++;
        setChanged();
        return pulls >= crumbleAt;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Pulls", pulls);
        output.putInt("CrumbleAt", crumbleAt);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pulls = input.getIntOr("Pulls", 0);
        crumbleAt = input.getIntOr("CrumbleAt", 0);
    }
}
