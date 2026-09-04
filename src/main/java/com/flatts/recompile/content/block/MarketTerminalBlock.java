package com.flatts.recompile.content.block;

import com.flatts.recompile.content.market.Market;
import com.flatts.recompile.content.menu.BuyTerminalMenu;
import com.flatts.recompile.content.menu.SellTerminalMenu;
import com.flatts.recompile.content.recipe.MarketOfferRecipe;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The two market terminals (spec {@code docs/market_spec.md}, #311): the block where the player
 * SELLS products for company scrip, and the block where the player BUYS Blueprints with it.
 *
 * <p><b>Named from the player's verb, not the company's.</b> "The block that sells" reads equally as
 * the one that sells TO you and the one you sell AT, and two blocks whose names can be read backwards
 * into each other is a support question forever. At the {@link Sell} terminal the player sells and
 * the balance goes up; at the {@link Buy} terminal the player buys and it goes down.
 *
 * <p><b>A terminal, not a container.</b> No block entity, no {@code Container}, no item capability,
 * no power. The balance lives on the player as a data attachment, the sell screen's goods grid is a
 * menu-local container that hands everything back on close, and the buy screen's stock is read off
 * the recipe manager when it opens. A hopper against either face therefore moves nothing, and that is
 * not a rule anyone wrote - there is nothing here for it to reach into.
 * {@code a_hopper_against_either_terminal_moves_nothing} keeps it true when somebody later adds a
 * capability out of habit.
 *
 * <p><b>The ninth and tenth custom screens in this mod</b>, and a recorded reversal of the standing
 * no-new-screen rule (owner, 2026-08-30). The justification is the same shape as every existing
 * exception: no vanilla screen shows a price. A chest screen would show the goods and hide the only
 * number that matters.
 */
public abstract class MarketTerminalBlock extends HorizontalDirectionalBlock {

    protected MarketTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Face the player, like every other block here with a front. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            open(level, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Open this terminal's screen for the player. Server side only. */
    protected abstract void open(Level level, BlockPos pos, Player player);

    /** Where the player hands products over and the balance goes up. */
    public static final class Sell extends MarketTerminalBlock {

        public static final MapCodec<Sell> CODEC = simpleCodec(Sell::new);

        public Sell(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<Sell> codec() {
            return CODEC;
        }

        @Override
        protected void open(Level level, BlockPos pos, Player player) {
            player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> new SellTerminalMenu(id, inventory,
                    ContainerLevelAccess.create(level, pos)),
                Component.translatable("container.recompile.sell_terminal")));
        }
    }

    /** Where the player spends on Blueprints and the balance goes down. */
    public static final class Buy extends MarketTerminalBlock {

        public static final MapCodec<Buy> CODEC = simpleCodec(Buy::new);

        public Buy(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<Buy> codec() {
            return CODEC;
        }

        @Override
        protected void open(Level level, BlockPos pos, Player player) {
            MinecraftServer server = level.getServer();
            List<Market.Offer> offers = server == null ? List.of() : offers(server);
            // The stock rides the open buffer, so the client screen lists exactly what the server
            // will sell from and no second sync path exists to drift.
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, opener) -> new BuyTerminalMenu(id, inventory,
                        ContainerLevelAccess.create(level, pos), offers),
                    Component.translatable("container.recompile.buy_terminal")),
                buffer -> Market.Offer.LIST_STREAM_CODEC.encode(buffer, offers));
        }

        /**
         * Everything on sale, cheapest first and by id within a price so the order is stable across
         * reloads. Read fresh each time the screen opens, so a pack that adds an offer is on the
         * shelf after a reload without a restart.
         */
        public static List<Market.Offer> offers(MinecraftServer server) {
            List<Market.Offer> offers = new ArrayList<>();
            for (RecipeHolder<MarketOfferRecipe> holder
                    : server.getRecipeManager().recipeMap().byType(RCRecipeTypes.MARKET_OFFER.get())) {
                offers.add(holder.value().offer());
            }
            offers.sort(Comparator.comparingInt(Market.Offer::price)
                .thenComparing(offer -> offer.blueprint().toString()));
            return offers;
        }
    }
}
