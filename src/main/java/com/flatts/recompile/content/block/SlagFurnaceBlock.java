package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.SlagFurnaceBlockEntity;
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
 * The Slag Furnace (#236): where the Cupola's waste becomes obsidian.
 *
 * <p><b>A single block with a screen, like every other furnace in the game</b> (owner, 2026-08-19).
 * The first sketch had it as a powered multiblock alongside the Trommel, Separator and Pulverizer;
 * those three are GUI-less because they are conveyor machines you feed and walk away from, and this is
 * not one. You put a lump in and watch it melt.
 *
 * <p><b>It runs {@code recompile:vitrifying} and nothing else can</b>, which is the whole point. See
 * {@link com.flatts.recompile.content.recipe.VitrifyingRecipe} for why the recipe type is the gate:
 * smelting would hand obsidian to a vanilla furnace, and blasting would hand it to a vanilla blast
 * furnace, which is craftable in this world because iron is reachable through the Cupola. The gate is
 * a property of the machine rather than an absence of materials - the distinction the iron gate cost
 * two designs to learn (#91).
 *
 * <p><b>Vitrification is real and it is what a dump can do.</b> Plasma gasification melts mixed waste
 * and taps a molten slag that quenches to a black silicate glass; the same silicates go in and come
 * out with their structure changed and their composition intact. That is the fourth verb this mod's
 * machine table needed - the other three change what a material is or how fine it is, and this one
 * changes its state.
 *
 * <p><b>Its ticker wrapper does one thing, and only one.</b> Like the Cupola and the Burn Barrel it
 * wraps {@code createFurnaceTicker} - to drain finished glass into a connected Scrap Network, nothing
 * more. What it does NOT need is what those two needed the seam for: a byproduct vanilla blasting has
 * no slot for, and an allowlist on a machine whose recipe lookup is private. This machine's whole
 * smelting rule is "run vitrifying recipes", and a {@code RecipeType} says that exactly.
 */
public class SlagFurnaceBlock extends AbstractFurnaceBlock {

    public static final MapCodec<SlagFurnaceBlock> CODEC = simpleCodec(SlagFurnaceBlock::new);

    public SlagFurnaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SlagFurnaceBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new SlagFurnaceBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> blockEntityType) {
        BlockEntityTicker<T> furnace =
            createFurnaceTicker(level, blockEntityType, RCBlockEntities.SLAG_FURNACE.get());
        if (furnace == null || level.isClientSide()) {
            return furnace;
        }
        // Smelt, then push the glass into a connected Scrap Network, the way the Cupola and the Burn
        // Barrel both do. A player who has wired one machine's output expects the next one to behave.
        return (lvl, pos, st, be) -> {
            furnace.tick(lvl, pos, st, be);
            if (lvl instanceof ServerLevel serverLevel && be instanceof SlagFurnaceBlockEntity furnaceBe) {
                furnaceBe.drainOutput(serverLevel);
            }
        };
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof SlagFurnaceBlockEntity furnace) {
            player.openMenu(furnace);
        }
    }
}
