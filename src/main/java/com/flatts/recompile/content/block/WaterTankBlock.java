package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.WaterTankBlockEntity;
import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

/**
 * The Water Tank: a shared placeable component, the Grass Spreader's tank cell - and, since #229, a
 * tank that actually holds water.
 *
 * <p><b>It holds water now, and it did not before.</b> Owner ruling 2026-08-18, from playtest: two
 * people read a block called Water Tank that could not be filled as broken, ninety minutes apart and
 * from opposite directions. It is a scoped reversal of P2.4-R item 6 and the only one - every other
 * shared component is still inert, because every other one is named for an <em>action</em> it does not
 * perform rather than a <em>capacity</em> it does not have.
 *
 * <p><b>It is still not a Rain Collector.</b> The collector is built <em>from</em> a tank - a Copper
 * Pipe over a tank - so the dependency runs that way and not the other, and the collector keeps the
 * one thing this block will never do: fill itself from the sky. A tank is filled by hand or by pipe.
 * Both machines share the part because the tank is the primitive.
 *
 * <p><b>This javadoc has been wrong twice</b> (#202): it once described the tank as "the Rain
 * Collector incorporated into the machine" and claimed forming drained a real collector you supplied,
 * which was a superseded draft that had never been true of the code, and it produced wrong guidebook
 * and pack copy both times before anyone read the class. Whatever this file says about the block is
 * worth checking against {@link WaterTankBlockEntity} before it is copied anywhere.
 *
 * <p><b>A bucket on a formed machine's cell is the machine's business.</b> {@link MultiblockDummyBlock}
 * redirects use and break to the core, so hovering a Grass Spreader's tank cell with a bucket talks to
 * the spreader, not to the tank. That keeps the second half of the same ruling - the Grass Spreader
 * consumes nothing, by design - from reading as a tank the machine refuses to drink.
 *
 * <p><b>A pipe is not redirected, and that asymmetry is a choice rather than an oversight.</b> The
 * fluid capability is registered unconditionally, so plumbing can fill a formed spreader's tank cell
 * even though nothing will ever draw on it. Making the capability disappear while formed would match
 * the bucket exactly, and it would be unreliable: {@code findCore} depends on a NEIGHBOUR, and forming
 * a machine does not change this block (its formed block and its component are the same one), so
 * nothing invalidates the cached capability at this position. A quiet, position-dependent
 * fluid-capability bug is worse than water sitting unused in a machine that ignores it.
 */
public class WaterTankBlock extends MultiblockDummyBlock implements EntityBlock {

    public static final MapCodec<WaterTankBlock> CODEC = simpleCodec(WaterTankBlock::new);

    public WaterTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends WaterTankBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterTankBlockEntity(pos, state);
    }

    /**
     * A bucket on a loose tank fills or empties it; on a formed machine's cell it is the machine's
     * business, so that case falls through to {@link MultiblockDummyBlock}'s redirect.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (findCore(level, pos) != null) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!(level.getBlockEntity(pos) instanceof WaterTankBlockEntity be)) {
            return InteractionResult.PASS;
        }
        // The server does the real transfer; the client optimistically succeeds for vanilla buckets so
        // the arm swings. Same split as the Rain Collector, and for the same reason.
        if (!level.isClientSide()) {
            return FluidUtil.interactWithFluidHandler(player, hand, pos, be.fluidHandler())
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET)
            ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
