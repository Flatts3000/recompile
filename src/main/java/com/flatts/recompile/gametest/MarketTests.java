package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.MarketTerminalBlock;
import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.market.Market;
import com.flatts.recompile.content.menu.BuyTerminalMenu;
import com.flatts.recompile.content.menu.SellTerminalMenu;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCDataMaps;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * The market (spec {@code docs/market_spec.md}, #311): the six tests the spec names, plus the ones
 * that fell out of building it.
 *
 * <p>Each of these is a ruling that would otherwise be prose. The sell list excluding junk, the
 * balance surviving death, two players having two accounts, a hopper moving nothing - none of those
 * is enforced by a line of code that says so. They are consequences of how the thing is built, and a
 * consequence is exactly what the next person changes without noticing.
 */
final class MarketTests {

    private static final BlockPos TERMINAL = new BlockPos(2, 2, 2);
    private static final BlockPos ABOVE = TERMINAL.above();
    private static final BlockPos BELOW = TERMINAL.below();

    private MarketTests() {
    }

    private static Set<Item> sellable() {
        Set<Item> out = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.builtInRegistryHolder().is(RCTags.SELLABLE)) {
                out.add(item);
            }
        }
        return out;
    }

    private static int blueprintsHeld(Inventory inventory, Identifier set) {
        int held = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (set.equals(BlueprintItem.blueprintOf(stack))) {
                held += stack.getCount();
            }
        }
        return held;
    }

    static void register() {
        /*
         * The door fails closed. A tag member with no price would sell for nothing at runtime; this
         * makes it fail the build instead, the same shape as every_sortable_block_is_in_a_vacuum_band
         * and for the same reason.
         */
        RCGameTests.test("every_sellable_item_has_a_price", 20, helper -> {
            List<String> unpriced = new ArrayList<>();
            Set<Item> sellable = sellable();
            for (Item item : sellable) {
                if (item.builtInRegistryHolder().getData(RCDataMaps.SCRIP_VALUE) == null) {
                    unpriced.add(String.valueOf(BuiltInRegistries.ITEM.getKey(item)));
                }
            }
            helper.assertTrue(sellable.size() >= 5,
                "only " + sellable.size() + " sellable items - discovery is broken, so this would "
                    + "pass by checking nothing");
            helper.assertTrue(unpriced.isEmpty(),
                "in #recompile:sellable with no scrip_value entry, so they would sell for nothing: "
                    + unpriced);
            helper.succeed();
        });

        /*
         * Section 6 of the spec made mechanical: the market buys products, never refuse. Nothing
         * sellable may be raw scrap (binnable), and nothing sellable may be craftable from binnable
         * inputs alone - which is what "one press away from junk" means, and what Pressed Junk is.
         */
        RCGameTests.test("nothing_sellable_is_raw_scrap_or_one_step_from_junk", 40, helper -> {
            ServerLevel level = helper.getLevel();
            ContextMap context = SlotDisplayContext.fromLevel(level);
            Set<Item> sellable = sellable();
            List<String> broken = new ArrayList<>();

            for (Item item : sellable) {
                if (item.builtInRegistryHolder().is(RCTags.BINNABLE)) {
                    broken.add(BuiltInRegistries.ITEM.getKey(item) + " is raw scrap");
                }
            }

            int swept = 0;
            int junkOnly = 0;
            for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().values()) {
                if (holder.value().getType() != RecipeType.CRAFTING) {
                    continue;
                }
                swept++;
                PlacementInfo placement = holder.value().placementInfo();
                if (placement.isImpossibleToPlace() || placement.ingredients().isEmpty()) {
                    continue;
                }
                boolean allJunk = placement.ingredients().stream().allMatch(
                    ingredient -> ingredient.items().allMatch(h -> h.is(RCTags.BINNABLE)));
                if (!allJunk) {
                    continue;
                }
                junkOnly++;
                for (RecipeDisplay display : holder.value().display()) {
                    for (ItemStack result : display.result().resolveForStacks(context)) {
                        if (sellable.contains(result.getItem())) {
                            broken.add(holder.id().identifier() + " makes "
                                + BuiltInRegistries.ITEM.getKey(result.getItem())
                                + " from binnable inputs alone");
                        }
                    }
                }
            }
            helper.assertTrue(swept > 100,
                "only " + swept + " crafting recipes swept - the sweep is broken");
            // Pressed Junk is junk-only by construction, so a sweep that finds no such recipe is
            // not looking at ingredients at all.
            helper.assertTrue(junkOnly > 0,
                "no recipe was recognised as junk-only, but Pressed Junk exists - the ingredient "
                    + "walk is broken and this test would pass against anything");
            helper.assertTrue(broken.isEmpty(),
                "the sell list hands junk a price (" + broken.size() + "): " + broken);
            helper.succeed();
        });

        /*
         * copyOnDeath asserted rather than assumed. An account that empties on death reads as the
         * shop being broken, and nobody would report it as the bug it is. Driven through
         * restoreFrom, which is the path PlayerList.respawn takes, rather than through the attachment
         * copy directly.
         */
        RCGameTests.test("a_scrip_balance_survives_death", 20, helper -> {
            ServerPlayer dead = helper.makeMockServerPlayerInLevel();
            Market.setBalance(dead, 0);
            Market.credit(dead, 150);

            ServerPlayer respawned = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(Market.balance(respawned) == 0,
                "a new player must start at zero, got " + Market.balance(respawned));
            // false = not keepEverything, which is what a death is.
            respawned.restoreFrom(dead, false);
            helper.assertTrue(Market.balance(respawned) == 150,
                "the balance did not survive death: expected 150, got " + Market.balance(respawned));
            helper.succeed();
        });

        /* The per-player claim asserted rather than assumed. */
        RCGameTests.test("two_players_have_two_balances", 20, helper -> {
            ServerPlayer first = helper.makeMockServerPlayerInLevel();
            ServerPlayer second = helper.makeMockServerPlayerInLevel();
            Market.setBalance(first, 0);
            Market.setBalance(second, 0);

            Market.credit(first, 50);
            helper.assertTrue(Market.balance(first) == 50, "first: " + Market.balance(first));
            helper.assertTrue(Market.balance(second) == 0,
                "crediting one player moved the other's balance to " + Market.balance(second));

            helper.assertTrue(Market.debit(first, 20), "a debit within the balance must succeed");
            helper.assertTrue(!Market.debit(second, 1),
                "a debit from an empty account must be refused");
            helper.assertTrue(Market.balance(first) == 30 && Market.balance(second) == 0,
                "after the debits: " + Market.balance(first) + " and " + Market.balance(second));
            helper.succeed();
        });

        /*
         * Automation is refused by the design rather than by a rule: there is no block entity for a
         * hopper to find. This is what keeps that true when someone later adds a capability out of
         * habit. Both terminals, both directions.
         */
        hopperTest("a_hopper_against_the_sell_terminal_moves_nothing", RCBlocks.SELL_TERMINAL::get);
        hopperTest("a_hopper_against_the_buy_terminal_moves_nothing", RCBlocks.BUY_TERMINAL::get);

        /*
         * The purchase, paired: the successful buy spends exactly the price and yields exactly one
         * sheet with the right component, and the refused one spends nothing and yields nothing.
         * Paired because "it produced no sheet" passes just as well on a terminal that never works.
         */
        RCGameTests.test("buying_a_blueprint_spends_the_balance_and_yields_the_sheet", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            List<Market.Offer> offers = MarketTerminalBlock.Buy.offers(level.getServer());
            helper.assertTrue(offers.size() >= 3,
                "only " + offers.size() + " market offers loaded - the stock is broken");
            int index = -1;
            for (int i = 0; i < offers.size(); i++) {
                if (offers.get(i).blueprint().equals(BlueprintItem.BATTERY)) {
                    index = i;
                }
            }
            helper.assertTrue(index >= 0, "no offer sells the Battery blueprint");
            int price = offers.get(index).price();

            Market.setBalance(player, 0);
            Market.credit(player, price + 100);
            BuyTerminalMenu menu = new BuyTerminalMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(TERMINAL)), offers);
            helper.assertTrue(menu.clickMenuButton(player, index),
                "the purchase was refused with " + Market.balance(player) + " against a price of "
                    + price);
            helper.assertTrue(Market.balance(player) == 100,
                "expected 100 left after paying " + price + ", got " + Market.balance(player));
            helper.assertTrue(blueprintsHeld(player.getInventory(), BlueprintItem.BATTERY) == 1,
                "expected exactly one Battery blueprint in the inventory, found "
                    + blueprintsHeld(player.getInventory(), BlueprintItem.BATTERY));
            helper.succeed();
        });

        RCGameTests.test("buying_with_too_little_spends_nothing_and_yields_nothing", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            List<Market.Offer> offers = MarketTerminalBlock.Buy.offers(level.getServer());
            int index = -1;
            for (int i = 0; i < offers.size(); i++) {
                if (offers.get(i).blueprint().equals(BlueprintItem.BATTERY)) {
                    index = i;
                }
            }
            helper.assertTrue(index >= 0, "no offer sells the Battery blueprint");
            int price = offers.get(index).price();

            Market.setBalance(player, price - 1);
            BuyTerminalMenu menu = new BuyTerminalMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(TERMINAL)), offers);
            helper.assertTrue(!menu.clickMenuButton(player, index),
                "a purchase one scrip short went through");
            helper.assertTrue(Market.balance(player) == price - 1,
                "a refused purchase changed the balance to " + Market.balance(player));
            helper.assertTrue(blueprintsHeld(player.getInventory(), BlueprintItem.BATTERY) == 0,
                "a refused purchase still handed over a sheet");
            // And an index off the end of the list is a refusal, not a crash.
            helper.assertTrue(!menu.clickMenuButton(player, offers.size()),
                "an out-of-range row bought something");
            helper.succeed();
        });

        /*
         * The sale: the quote is what the screen shows before the click, and it is exactly what is
         * credited after it. The grid empties; a second click on an empty grid is refused rather than
         * a zero-scrip sale.
         */
        RCGameTests.test("selling_credits_the_quote_and_takes_the_goods", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            Market.setBalance(player, 0);
            SellTerminalMenu menu = new SellTerminalMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(TERMINAL)));

            menu.goods().setItem(0, new ItemStack(RCItems.PUMP.get(), 2));
            menu.goods().setItem(4, new ItemStack(RCItems.BULB.get(), 3));
            menu.goods().setItem(8, new ItemStack(RCItems.cleanMattress(DyeColor.RED)));
            int expected = 2 * Market.priceOf(RCItems.PUMP.get())
                + 3 * Market.priceOf(RCItems.BULB.get())
                + Market.priceOf(RCItems.cleanMattress(DyeColor.RED));
            helper.assertTrue(expected > 0, "the three goods priced at nothing between them");
            helper.assertTrue(menu.quote() == expected,
                "the quote reads " + menu.quote() + " against " + expected);

            helper.assertTrue(menu.clickMenuButton(player, SellTerminalMenu.SELL_BUTTON),
                "the sale was refused");
            helper.assertTrue(Market.balance(player) == expected,
                "credited " + Market.balance(player) + " for a quote of " + expected);
            helper.assertTrue(menu.goods().isEmpty(), "the goods were paid for and not taken");
            helper.assertTrue(!menu.clickMenuButton(player, SellTerminalMenu.SELL_BUTTON),
                "an empty grid sold");
            helper.assertTrue(Market.balance(player) == expected,
                "an empty sale changed the balance to " + Market.balance(player));
            helper.succeed();
        });

        /*
         * The slot is where a player learns an item has no price. Raw scrap, junk, a building block
         * pressed from junk, and a tagged-but-priceless stack are all refused; a component goes in.
         * A stack that reaches the grid by force still pays nothing, so the door is closed twice.
         */
        RCGameTests.test("the_sell_terminal_refuses_what_is_not_for_sale", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            Market.setBalance(player, 0);
            SellTerminalMenu menu = new SellTerminalMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(TERMINAL)));
            var slot = menu.slots.get(0);

            List<String> wrong = new ArrayList<>();
            for (Item refused : List.of(RCItems.JUNK.get(), RCItems.SCRAP_METAL.get(),
                    RCItems.PRESSED_JUNK_BLOCK.get(), RCItems.SEQUENCER.get())) {
                if (slot.mayPlace(new ItemStack(refused))) {
                    wrong.add(BuiltInRegistries.ITEM.getKey(refused) + " was accepted");
                }
            }
            for (Item accepted : List.of(RCItems.PUMP.get(), RCItems.BATTERY.get(),
                    RCItems.cleanMattress(DyeColor.LIME))) {
                if (!slot.mayPlace(new ItemStack(accepted))) {
                    wrong.add(BuiltInRegistries.ITEM.getKey(accepted) + " was refused");
                }
            }
            helper.assertTrue(wrong.isEmpty(), "the slot gate is wrong: " + wrong);

            menu.goods().setItem(0, new ItemStack(RCItems.JUNK.get(), 64));
            helper.assertTrue(menu.quote() == 0, "a stack of junk was quoted at " + menu.quote());
            helper.assertTrue(!menu.clickMenuButton(player, SellTerminalMenu.SELL_BUTTON),
                "junk that reached the grid was sold");
            helper.assertTrue(Market.balance(player) == 0,
                "junk paid " + Market.balance(player));
            helper.succeed();
        });

        /* The grid is the player's for the duration of the screen and not a second longer. */
        RCGameTests.test("closing_the_sell_screen_hands_the_goods_back", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().clearContent();
            SellTerminalMenu menu = new SellTerminalMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(TERMINAL)));
            menu.goods().setItem(3, new ItemStack(RCItems.MOTOR.get(), 2));

            menu.removed(player);

            int back = player.getInventory().countItem(RCItems.MOTOR.get());
            // A mock player may count as disconnected, in which case vanilla drops the stack at
            // their feet instead - still handed back, just onto the floor.
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(4.0))) {
                if (drop.getItem().is(RCItems.MOTOR.get())) {
                    back += drop.getItem().getCount();
                }
            }
            helper.assertTrue(menu.goods().isEmpty(), "the grid kept the goods after closing");
            helper.assertTrue(back == 2, "expected both Motors back, found " + back);
            helper.succeed();
        });

        /*
         * The Buy Terminal has no machine slot, so MenuTransferTests' conservation sweep cannot run
         * on it and excuses it by name to here. Its whole quickMoveStack is backpack to hotbar and
         * back; this pins that it moves and conserves rather than minting or eating.
         */
        RCGameTests.test("buy_terminal_shift_click_moves_between_backpack_and_hotbar", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().clearContent();
            BuyTerminalMenu menu = new BuyTerminalMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(TERMINAL)), List.of());

            // Slot 0 of the menu is backpack slot 9; slot 27 is hotbar slot 0.
            menu.slots.get(0).set(new ItemStack(RCItems.SCRAP_METAL.get(), 40));
            ItemStack moved = menu.quickMoveStack(player, 0);
            helper.assertTrue(!moved.isEmpty(), "a backpack stack refused to move at all");
            int hotbar = 0;
            for (int i = 27; i < 36; i++) {
                hotbar += menu.slots.get(i).getItem().getCount();
            }
            helper.assertTrue(menu.slots.get(0).getItem().isEmpty() && hotbar == 40,
                "expected all 40 in the hotbar and none left behind, got " + hotbar + " and "
                    + menu.slots.get(0).getItem().getCount());

            ItemStack back = menu.quickMoveStack(player, 27);
            helper.assertTrue(!back.isEmpty(), "a hotbar stack refused to move back");
            int backpack = 0;
            for (int i = 0; i < 27; i++) {
                backpack += menu.slots.get(i).getItem().getCount();
            }
            helper.assertTrue(backpack == 40 && menu.slots.get(27).getItem().isEmpty(),
                "expected all 40 back in the backpack, got " + backpack);
            helper.assertTrue(menu.quickMoveStack(player, 27).isEmpty(),
                "shift-clicking an empty slot must return EMPTY, or vanilla's caller loops forever");
            helper.succeed();
        });

        /*
         * Every line of stock sells a sheet that some blueprint_crafting recipe reads, and that has
         * a name. An offer for a set nothing uses would sell a sheet that does nothing, which is the
         * same silent failure the dangling-teaches sweep exists for, seen from the other side.
         */
        RCGameTests.test("every_market_offer_sells_a_blueprint_something_makes", 20, helper -> {
            ServerLevel level = helper.getLevel();
            List<Market.Offer> offers = MarketTerminalBlock.Buy.offers(level.getServer());
            helper.assertTrue(offers.size() >= 3,
                "only " + offers.size() + " market offers loaded - the stock is broken");

            Set<Identifier> built = new HashSet<>();
            for (RecipeHolder<BlueprintCraftingRecipe> holder : level.recipeAccess().recipeMap()
                    .byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                built.add(holder.value().blueprint());
            }
            List<String> dead = new ArrayList<>();
            Set<Identifier> seen = new HashSet<>();
            for (Market.Offer offer : offers) {
                if (!built.contains(offer.blueprint())) {
                    dead.add(offer.blueprint() + " is on sale and no recipe reads it");
                }
                if (!seen.add(offer.blueprint())) {
                    dead.add(offer.blueprint() + " is on sale twice");
                }
                String key = "blueprint." + offer.blueprint().getNamespace() + "."
                    + offer.blueprint().getPath();
                if (BlueprintItem.setName(offer.blueprint()).getString().equals(key)) {
                    dead.add(offer.blueprint() + " has no name to list it under");
                }
            }
            helper.assertTrue(dead.isEmpty(), "the stock is wrong (" + dead.size() + "): " + dead);
            helper.succeed();
        });
    }

    private static void hopperTest(String name, Supplier<Block> terminal) {
        RCGameTests.test(name, 80, helper -> {
            ServerLevel level = helper.getLevel();
            helper.setBlock(TERMINAL, terminal.get());
            helper.setBlock(ABOVE, Blocks.HOPPER);
            helper.setBlock(BELOW, Blocks.HOPPER);
            BlockPos abs = helper.absolutePos(TERMINAL);

            helper.assertTrue(level.getBlockEntity(abs) == null,
                "the terminal has a block entity, so it is a container after all");
            helper.assertTrue(level.getCapability(Capabilities.Item.BLOCK, abs, null) == null,
                "the terminal exposes an item capability, so a pipe could reach into it");

            HopperBlockEntity feeder = (HopperBlockEntity) level.getBlockEntity(helper.absolutePos(ABOVE));
            feeder.setItem(0, new ItemStack(RCItems.PUMP.get(), 1));

            helper.runAfterDelay(40, () -> {
                HopperBlockEntity above = (HopperBlockEntity) level.getBlockEntity(helper.absolutePos(ABOVE));
                HopperBlockEntity below = (HopperBlockEntity) level.getBlockEntity(helper.absolutePos(BELOW));
                helper.assertTrue(above.getItem(0).is(RCItems.PUMP.get())
                        && above.getItem(0).getCount() == 1,
                    "the hopper above pushed its Pump somewhere - a terminal took it");
                helper.assertTrue(below.isEmpty(),
                    "the hopper below pulled something out of a block that holds nothing");
                helper.succeed();
            });
        });
    }
}
