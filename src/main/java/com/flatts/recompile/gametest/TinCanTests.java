package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for the tin-can food chain (design P1.9): a sealed can, a scrap knife, and the gamble
 * that comes out of the tin.
 *
 * <p>{@code household_sprawl} ships with every spawner list empty, so cans and foraged mushrooms are
 * the only food in the starting biome. {@code ForageTests} covers the mushroom half; this is the
 * other one, and until now none of it was exercised at all.
 */
final class TinCanTests {

    private TinCanTests() {
    }

    /** Somewhere to stand inside the plot, so a dropped item lands in range of an assertion. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);

    static void register() {
        // "No knife, no lunch" is the whole gate on the mod's staple food. Delete this and a player
        // who has never found a Scrap Knife can still eat: the can would open on a bare right-click,
        // and the knife stops being a tool you need before you can feed yourself.
        RCGameTests.test("sealed_can_needs_a_knife", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack can = new ItemStack(RCItems.TIN_CAN.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, can);

            InteractionResult result = RCItems.TIN_CAN.get()
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            helper.assertTrue(result == InteractionResult.PASS,
                "a knifeless right-click must PASS so the can keeps behaving like an ordinary item, got "
                    + result);
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(RCItems.TIN_CAN.get()),
                "the sealed can must survive a knifeless right-click");
            helper.assertFalse(inventoryHas(player, RCItems.TIN_CAN_OPEN.get()),
                "no knife means no opened can - this is the gate on the mod's staple food");
            helper.succeed();
        });

        // The knife is a TOOL: it opens the can from anywhere in the inventory (the hand is holding
        // the can) and is never consumed. Delete this and the two halves of KnifeWork can drift -
        // a knife that only counts in hand makes the interaction impossible, and a knife that is
        // spent per can turns the staple food into a treadmill of finding knives.
        RCGameTests.test("sealed_can_opens_with_a_knife_anywhere_in_the_inventory", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            // makeMockPlayer only overrides gameMode(); abilities.instabuild is the field
            // SealedCanItem actually reads, so set it rather than trusting the game type.
            player.getAbilities().instabuild = false;
            player.getInventory().setItem(8, new ItemStack(RCItems.SCRAP_KNIFE.get()));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.TIN_CAN.get()));

            InteractionResult result = RCItems.TIN_CAN.get()
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            helper.assertTrue(result == InteractionResult.SUCCESS,
                "opening a can must report SUCCESS, got " + result);
            // Assert the sealed can is GONE from the inventory rather than that the hand is empty:
            // the shrink leaves a count-0 stack, which Inventory.getFreeSlot treats as free, so the
            // opened can lands right back in the hand slot. That is fine in game and is exactly the
            // sort of detail a "the hand is empty" assertion would fail on for the wrong reason.
            helper.assertFalse(inventoryHas(player, RCItems.TIN_CAN.get()),
                "the sealed can must be spent in survival, not duplicated");
            helper.assertTrue(inventoryHas(player, RCItems.TIN_CAN_OPEN.get()),
                "opening a can must hand back an opened can");
            helper.assertTrue(player.getInventory().getItem(8).is(RCItems.SCRAP_KNIFE.get()),
                "the knife is a tool - opening a can must not consume it");
            helper.succeed();
        });

        // The creative carve-out, tested from the side that can pass for the wrong reason. Without
        // it a creative player opening a can loses the can, which is the opposite of what creative
        // means; with the survival test above, neither half can pass vacuously.
        RCGameTests.test("creative_opening_a_can_keeps_the_can", 20, helper -> {
            // makeMockPlayer(CREATIVE) IS NOT ENOUGH - in 26.1 it overrides gameMode() and nothing
            // else, and SealedCanItem reads player.getAbilities().instabuild directly. Set the
            // ability a real creative player has, or this silently exercises the survival path.
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            player.getAbilities().instabuild = true;
            player.getInventory().setItem(8, new ItemStack(RCItems.SCRAP_KNIFE.get()));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.TIN_CAN.get()));

            RCItems.TIN_CAN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(RCItems.TIN_CAN.get()),
                "creative must keep its sealed can - the shrink is guarded on instabuild");
            helper.assertTrue(inventoryHas(player, RCItems.TIN_CAN_OPEN.get()),
                "creative must still get the opened can, or the interaction does nothing at all");
            helper.succeed();
        });

        // KnifeWork.give's second branch. With a full inventory the opened can has nowhere to go,
        // and the only two outcomes are "on the floor" or "gone". Delete this and the fallback can
        // rot into a silent void: the player right-clicks, the sealed can is consumed, and the food
        // it turned into never exists.
        RCGameTests.test("a_full_inventory_drops_the_opened_can_at_the_players_feet", 60, helper -> {
            helper.setBlock(FLOOR.below(), Blocks.STONE);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            // instabuild would make Inventory.add report success on a full inventory (it voids the
            // stack for infinite-materials players), so the drop branch is only reachable in survival.
            player.getAbilities().instabuild = false;
            Vec3 standing = helper.absoluteVec(FLOOR.getCenter());
            player.snapTo(standing.x, standing.y, standing.z);

            // Every main slot occupied: stone everywhere, the knife in one of them, and a stack of
            // two sealed cans in hand so the held slot is still full after one is spent.
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Blocks.STONE, 64));
            }
            player.getInventory().setItem(8, new ItemStack(RCItems.SCRAP_KNIFE.get()));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.TIN_CAN.get(), 2));

            RCItems.TIN_CAN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            helper.assertFalse(inventoryHas(player, RCItems.TIN_CAN_OPEN.get()),
                "the opened can must not fit - if it did, the inventory was not actually full and "
                    + "the drop branch was never exercised");
            helper.succeedWhen(() ->
                helper.assertItemEntityCountIs(RCItems.TIN_CAN_OPEN.get(), FLOOR, 3.0, 1));
        });

        // The gamble is the whole point of the can - it eats like Suspicious Stew, not like bread.
        // Delete this and the mystery roll can quietly stop firing (or fire with a zero duration),
        // leaving the mod's staple food as plain nutrition and the "sketchy food out of a dump"
        // beat as flavour text nobody experiences.
        RCGameTests.test("eating_an_opened_can_always_applies_one_timed_effect", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            // Survival matters twice here: ItemStack.consume skips the shrink for a player with
            // infinite materials, so a creative eater would never prove the can is spent.
            player.getAbilities().instabuild = false;
            Vec3 standing = helper.absoluteVec(FLOOR.getCenter());
            player.snapTo(standing.x, standing.y, standing.z);
            helper.assertTrue(player.getActiveEffects().isEmpty(),
                "precondition: a fresh mock player must carry no effects");

            ItemStack eaten = new ItemStack(RCItems.TIN_CAN_OPEN.get(), 2);
            ItemStack left = RCItems.TIN_CAN_OPEN.get().finishUsingItem(eaten, helper.getLevel(), player);

            helper.assertTrue(left.getCount() == 1,
                "eating one can must spend exactly one, got " + left.getCount() + " left of 2");
            helper.assertTrue(player.getActiveEffects().size() == 1,
                "eating an opened can must apply exactly one effect from the curated pool, got "
                    + player.getActiveEffects().size()
                    + " (the FOOD component carries no effects of its own, so anything else is a bug)");
            MobEffectInstance applied = player.getActiveEffects().iterator().next();
            helper.assertTrue(applied.getDuration() == 200,
                "the mystery effect must last its 10 seconds, got " + applied.getDuration() + " ticks");
            helper.succeed();
        });
    }

    /** True when any slot holds the item. Mirrors what KnifeWork.give had to achieve. */
    private static boolean inventoryHas(Player player, Item item) {
        return player.getInventory().contains(stack -> stack.is(item));
    }
}
