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
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.crafting.Ingredient;
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
         * sellable may itself be raw scrap, and nothing sellable may be craftable from binnable
         * inputs ALONE - which is what the owner's "one press away from raw junk" means, and what
         * Pressed Junk is.
         *
         * ONE PRESS IS THE RULE, AND A TRANSITIVE CLOSURE DELIBERATELY IS NOT. Review of #368 read
         * the rule as reaching all the way down and reported the sell list as a junk sink, on the
         * grounds that a Solar Panel is panes and plating which are glass shards and scrap metal.
         * That reading cannot be the rule, because EVERY material in this world descends from a
         * pull stream - applied transitively it empties the sell list and deletes the feature. What
         * section 8 refuses is a price on `recompile:junk`, the 30-percent landfill item, and that
         * holds: junk is consumed by exactly two recipes in the mod (the schema's example door and
         * Pressed Junk) and neither is sellable, so its only sink is still the Burn Barrel. Scrip
         * flowing from renewable scrap is section 4 in as many words - "a Blueprint's price is a
         * time cost and not a scarcity cost" - rather than a leak.
         *
         * IT COVERS BLUEPRINT RECIPES TOO, and it did not until review caught it: the sweep took
         * only RecipeType.CRAFTING, so the Pump, Motor, Bulb and Battery - four of the nine members
         * - were never inspected at all and the test went green without looking at them. The
         * coverage assertion at the bottom is the real fix: a member this sweep never saw PRODUCED
         * fails, so a sellable item whose only recipe is of a type not read here can no longer pass
         * by being invisible.
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

            Set<Item> seenProduced = new HashSet<>();
            int swept = 0;
            int junkOnly = 0;

            for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().values()) {
                List<Ingredient> inputs = new ArrayList<>();
                List<Item> results = new ArrayList<>();

                if (holder.value() instanceof BlueprintCraftingRecipe blueprint) {
                    // Its placementInfo is NOT_PLACEABLE, so the vanilla route reads no ingredients
                    // from it at all - which is exactly how these four hid.
                    blueprint.ingredients().forEach(slot -> slot.ifPresent(inputs::add));
                    results.add(blueprint.result().item());
                } else if (holder.value().getType() == RecipeType.CRAFTING) {
                    PlacementInfo placement = holder.value().placementInfo();
                    if (placement.isImpossibleToPlace()) {
                        // A special recipe computes its result, so there is nothing to read. The
                        // coverage assertion below is what stops that being a silent hole.
                        continue;
                    }
                    inputs.addAll(placement.ingredients());
                    for (RecipeDisplay display : holder.value().display()) {
                        for (ItemStack stack : display.result().resolveForStacks(context)) {
                            results.add(stack.getItem());
                        }
                    }
                } else {
                    continue;
                }
                if (inputs.isEmpty() || results.isEmpty()) {
                    continue;
                }
                swept++;
                results.stream().filter(sellable::contains).forEach(seenProduced::add);

                boolean allJunk = inputs.stream().allMatch(
                    ingredient -> ingredient.items().allMatch(h -> h.is(RCTags.BINNABLE)));
                if (!allJunk) {
                    continue;
                }
                junkOnly++;
                for (Item result : results) {
                    if (sellable.contains(result)) {
                        broken.add(holder.id().identifier() + " makes "
                            + BuiltInRegistries.ITEM.getKey(result) + " from binnable inputs alone");
                    }
                }
            }

            helper.assertTrue(swept > 100,
                "only " + swept + " readable recipes swept - the sweep is broken");
            // Pressed Junk is junk-only by construction, so a sweep that finds no such recipe is
            // not looking at ingredients at all.
            helper.assertTrue(junkOnly > 0,
                "no recipe was recognised as junk-only, but Pressed Junk exists - the ingredient "
                    + "walk is broken and this test would pass against anything");

            // THE COVERAGE HALF, and the one that would have caught the gap review found. Finding
            // no violation means nothing unless every member was actually looked at.
            List<String> unseen = new ArrayList<>();
            for (Item item : sellable) {
                if (!seenProduced.contains(item)) {
                    unseen.add(String.valueOf(BuiltInRegistries.ITEM.getKey(item)));
                }
            }
            helper.assertTrue(unseen.isEmpty(),
                "these sellable items are produced by no recipe this sweep can read, so the check "
                    + "above says nothing about them: " + unseen);
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
                if (BlueprintItem.BATTERY.equals(offers.get(i).blueprint())) {
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
                if (BlueprintItem.BATTERY.equals(offers.get(i).blueprint())) {
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
         * The scrip tooltip's strings resolve.
         *
         * <p>The handler itself is Dist.CLIENT and no test layer here can render a tooltip, but the
         * failure that costs a player something is the cheap one: a missing lang key renders the raw
         * key, so the line reads "tooltip.recompile.scrip_value" on every sellable item in the game.
         * Both forms are checked, because the stack form only ever shows on a count above one and so
         * would go unnoticed longest. Same guard the guidebook keys get.
         */
        RCGameTests.test("the_scrip_tooltip_has_something_to_say", 20, helper -> {
            List<String> raw = new ArrayList<>();
            for (String key : List.of("tooltip.recompile.scrip_value",
                    "tooltip.recompile.scrip_value_stack")) {
                if (Component.translatable(key).getString().equals(key)) {
                    raw.add(key);
                }
            }
            helper.assertTrue(raw.isEmpty(),
                "these render as their own key on every sellable item: " + raw);

            // And the tooltip's predicate is the terminal's, so the two cannot disagree about what
            // is worth something. A tag member with no price must show no line AND be refused.
            helper.assertTrue(Market.isSellable(new ItemStack(RCItems.PUMP.get())),
                "a priced tag member must be sellable, or its tooltip would say nothing");
            helper.assertTrue(!Market.isSellable(new ItemStack(RCItems.JUNK.get())),
                "junk must not be sellable, or its tooltip would quote a price the terminal refuses");
            helper.succeed();
        });

        /*
         * A sale takes what it paid for and nothing else.
         *
         * <p>The grid used to be cleared wholesale, which deleted anything unsellable sitting in it.
         * mayPlace keeps such a stack out at the slot, so the way one gets there is a /reload that
         * drops an item from #recompile:sellable or from the price map while the screen is open -
         * narrow, but silent item loss, and a pack editing either surface is the likely trigger.
         * Written to the container directly, which is exactly the state that reload leaves behind.
         */
        RCGameTests.test("selling_leaves_an_unsellable_stack_alone", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            Market.setBalance(player, 0);
            SellTerminalMenu menu = new SellTerminalMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(TERMINAL)));

            menu.goods().setItem(0, new ItemStack(RCItems.PUMP.get(), 2));
            menu.goods().setItem(1, new ItemStack(RCItems.JUNK.get(), 5));
            int expected = 2 * Market.priceOf(RCItems.PUMP.get());

            helper.assertTrue(menu.quote() == expected,
                "junk in the grid changed the quote to " + menu.quote());
            helper.assertTrue(menu.clickMenuButton(player, SellTerminalMenu.SELL_BUTTON),
                "the sale was refused");
            helper.assertTrue(Market.balance(player) == expected,
                "credited " + Market.balance(player) + " for a quote of " + expected);
            helper.assertTrue(menu.goods().getItem(0).isEmpty(),
                "the Pumps were paid for and not taken");
            helper.assertTrue(menu.goods().getItem(1).is(RCItems.JUNK.get())
                    && menu.goods().getItem(1).getCount() == 5,
                "the junk was destroyed by a sale that did not pay for it, got "
                    + menu.goods().getItem(1));
            helper.succeed();
        });

        /*
         * A full grid must not swallow the click. Shift-clicking a sellable stack with all nine
         * goods slots taken used to do nothing at all - the sell branch was tried, failed, and
         * returned EMPTY as a sibling of the backpack/hotbar shuffle rather than falling through to
         * it. No loss, but a dead click on a screen whose whole job is to take that item.
         */
        RCGameTests.test("a_full_grid_still_shift_clicks_within_the_inventory", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().clearContent();
            SellTerminalMenu menu = new SellTerminalMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(TERMINAL)));

            for (int slot = 0; slot < SellTerminalMenu.GOODS_SLOTS; slot++) {
                menu.goods().setItem(slot, new ItemStack(RCItems.PUMP.get(), 1));
            }
            // Menu slot 36 is hotbar slot 0: past INV_MAIN_END, so the fallthrough is the backpack.
            int hotbar = 36;
            menu.slots.get(hotbar).set(new ItemStack(RCItems.MOTOR.get(), 3));

            ItemStack moved = menu.quickMoveStack(player, hotbar);
            helper.assertTrue(!moved.isEmpty(),
                "a sellable stack shift-clicked against a full grid did nothing at all");
            helper.assertTrue(menu.slots.get(hotbar).getItem().isEmpty(),
                "it stayed in the hotbar: " + menu.slots.get(hotbar).getItem());

            int inBackpack = 0;
            for (int slot = 9; slot < 36; slot++) {
                if (menu.slots.get(slot).getItem().is(RCItems.MOTOR.get())) {
                    inBackpack += menu.slots.get(slot).getItem().getCount();
                }
            }
            helper.assertTrue(inBackpack == 3,
                "expected all 3 Motors in the backpack, found " + inBackpack);
            // And the grid was not disturbed by the attempt.
            helper.assertTrue(menu.goods().getItem(0).is(RCItems.PUMP.get()),
                "the failed store emptied a goods slot");
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
            Set<String> seen = new HashSet<>();
            int knowledge = 0;
            int things = 0;
            for (Market.Offer offer : offers) {
                helper.assertTrue(!offer.stack().isEmpty(),
                    "an offer hands over nothing at " + offer.price() + " scrip");
                Identifier set = offer.blueprint();
                if (set != null) {
                    knowledge++;
                    if (!built.contains(set)) {
                        dead.add(set + " is on sale and no recipe reads it");
                    }
                    String key = "blueprint." + set.getNamespace() + "." + set.getPath();
                    if (BlueprintItem.setName(set).getString().equals(key)) {
                        dead.add(set + " has no name to list it under");
                    }
                } else {
                    things++;
                }
                // Keyed on what the row IS rather than on a set id, since an item row has none.
                if (!seen.add(offer.displayName().getString())) {
                    dead.add(offer.displayName().getString() + " is on the shelf twice");
                }
            }
            helper.assertTrue(knowledge > 0 && things > 0,
                "the shelf should carry both knowledge and things, got " + knowledge + " and "
                    + things + " - if a kind has gone, the checks above stopped covering it");
            helper.assertTrue(dead.isEmpty(), "the stock is wrong (" + dead.size() + "): " + dead);
            helper.succeed();
        });

        /*
         * Buying a THING rather than knowledge (spec section 14). The blueprint path is covered
         * above; this is the other kind of line, and it is the one with no precedent in the mod -
         * an object entering the world without being found, grown or built. Asserted on the totem
         * because that is the shipped instance and because nothing else here can produce one at
         * all, so a regression would silently make it unobtainable rather than merely awkward.
         */
        RCGameTests.test("buying_a_thing_hands_over_the_thing", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().clearContent();
            List<Market.Offer> offers = MarketTerminalBlock.Buy.offers(level.getServer());

            int index = -1;
            for (int i = 0; i < offers.size(); i++) {
                if (offers.get(i).stack().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
                    index = i;
                }
            }
            helper.assertTrue(index >= 0,
                "no offer sells a Totem of Undying, which has no other source in this world");
            Market.Offer offer = offers.get(index);
            helper.assertTrue(offer.blueprint() == null,
                "the totem line is selling knowledge rather than the thing");

            Market.setBalance(player, offer.price() + 7);
            BuyTerminalMenu menu = new BuyTerminalMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(TERMINAL)), offers);
            helper.assertTrue(menu.clickMenuButton(player, index), "the purchase was refused");
            helper.assertTrue(Market.balance(player) == 7,
                "expected 7 left, got " + Market.balance(player));
            helper.assertTrue(
                player.getInventory().countItem(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) == 1,
                "expected one totem in the inventory, found " + player.getInventory()
                    .countItem(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));

            // The shelf must not be spent by the sale: offers are shared by every row the screen
            // draws, so handing over the offer's own stack rather than a copy would empty it.
            helper.assertTrue(offer.stack().getCount() == 1,
                "the purchase consumed the offer's own stack, so the row now sells nothing");
            helper.succeed();
        });

        /*
         * THE THIRD AXIS FAILS CLOSED AGAINST THE SECOND (spec section 14).
         *
         * <p>Until the market there were two ways to hold a thing: find it, or build it.
         * `#recompile:found_only` is the rule that some things may only be found, and
         * FoundNotCraftedTests enforces it by sweeping RECIPES - which a shop counter is not. So a
         * market line selling a found-only item would put a second source on it and every existing
         * guard would stay green, which is exactly the shape of silent leak this repo keeps paying
         * for. The market may sell what the dump cannot give; it may not sell what the dump is
         * SUPPOSED to be the only giver of.
         *
         * <p>It covers what a blueprint line unlocks too, not just the sheet, since selling the
         * knowledge to craft a found-only item reaches the same end one step later.
         */
        RCGameTests.test("the_market_never_sells_what_is_meant_to_be_found", 20, helper -> {
            ServerLevel level = helper.getLevel();
            List<Market.Offer> offers = MarketTerminalBlock.Buy.offers(level.getServer());
            helper.assertTrue(!offers.isEmpty(), "no offers loaded - this would check nothing");

            java.util.Map<Identifier, Item> unlocks = new java.util.HashMap<>();
            for (RecipeHolder<BlueprintCraftingRecipe> holder : level.recipeAccess().recipeMap()
                    .byType(RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                unlocks.put(holder.value().blueprint(), holder.value().result().item());
            }

            List<String> leaks = new ArrayList<>();
            for (Market.Offer offer : offers) {
                if (offer.stack().is(RCTags.FOUND_ONLY)) {
                    leaks.add(offer.displayName().getString() + " is sold outright while being in "
                        + "#recompile:found_only");
                }
                Identifier set = offer.blueprint();
                Item unlocked = set == null ? null : unlocks.get(set);
                if (unlocked != null && unlocked.builtInRegistryHolder().is(RCTags.FOUND_ONLY)) {
                    leaks.add(set + " is sold and unlocks "
                        + BuiltInRegistries.ITEM.getKey(unlocked) + ", which is found-only");
                }
            }
            helper.assertTrue(leaks.isEmpty(),
                "the market undercuts the found-only rule, which no recipe sweep can see: " + leaks);
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
