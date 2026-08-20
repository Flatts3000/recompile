package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.SinteringKilnBlockEntity;
import com.flatts.recompile.registry.RCBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Sintering Kiln (#248): where a pressed powder is fired back into a solid.
 *
 * <p><b>It exists because every other machine here runs the same direction.</b> The Trommel cuts, the
 * Separator divides, the Pulverizer reduces, the Slag Furnace changes state - four verbs, all of them
 * taking something apart. The Pulverizer ships seven recipes and every one produces a powder, so the
 * mod could make powder seven ways and turn it back into a solid no way at all. Blaze rods sat behind
 * that gap, and behind blaze rods sits the brewing stand and therefore every potion in the game.
 *
 * <p><b>A single block with a screen, not a powered multiblock</b>, on the Slag Furnace's precedent
 * and for its reason. The Trommel, Separator and Pulverizer are GUI-less because they are conveyor
 * machines you feed and walk away from; this is one you put a compact into and watch fire. So it burns
 * fuel rather than FE, subclasses {@code AbstractFurnaceBlock}, and does not appear in
 * {@code MachineParityTests} - that sweep derives its list from multiblock cores answering
 * {@code Capabilities.Energy.BLOCK}, and this answers neither. Its parity is with the Burn Barrel, the
 * Cupola and the Slag Furnace instead.
 *
 * <p><b>Sintering is real, and it is specifically not melting.</b> Powder metallurgy compacts a powder
 * into a fragile green body and then holds it below its melting point until the particles fuse into a
 * solid part. That is how rod stock is made from powder, and it is why the kiln cannot simply be handed
 * loose blaze powder: the pressing step is a separate operation and lives at a bench, which also stops
 * a one-for-one recipe becoming an infinite rod loop against vanilla's rod-to-two-powder crafting.
 *
 * <p>Its ticker wrapper does one thing: drain finished work into a connected Scrap Network, the way the
 * Cupola, the Burn Barrel and the Slag Furnace all do. It needs the seam for nothing else - this
 * machine's whole smelting rule is "run sintering recipes", and a {@code RecipeType} says that exactly.
 */
public class SinteringKilnBlock extends AbstractFurnaceBlock {

    public static final MapCodec<SinteringKilnBlock> CODEC = simpleCodec(SinteringKilnBlock::new);

    public SinteringKilnBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SinteringKilnBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new SinteringKilnBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> blockEntityType) {
        BlockEntityTicker<T> furnace =
            createFurnaceTicker(level, blockEntityType, RCBlockEntities.SINTERING_KILN.get());
        if (furnace == null || level.isClientSide()) {
            return furnace;
        }
        return (lvl, pos, st, be) -> {
            furnace.tick(lvl, pos, st, be);
            if (lvl instanceof ServerLevel serverLevel
                    && be instanceof SinteringKilnBlockEntity kiln) {
                kiln.drainOutput(serverLevel);
            }
        };
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof SinteringKilnBlockEntity kiln) {
            player.openMenu(kiln);
        }
    }
}
