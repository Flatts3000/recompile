package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Sledgehammer is a weapon as well as a tool (#379, owner 2026-09-05).
 *
 * <p><b>The ask was "as much damage as the sword equivalent", and per HIT it already was.</b> Copper
 * hit for 7.5 against a copper sword's 5, and netherite for 13 against 8. What it was not was as much
 * damage over TIME: at an attack speed of -3.2 it swings 0.8 times a second against a sword's 1.6, so
 * it landed about three quarters of the sword's damage per second at every rung. Reading the ask as
 * damage per second is what makes it a real choice rather than a strictly worse one, and it keeps the
 * slow heavy swing that is the tool's whole identity.
 *
 * <p>These are GameTests rather than unit tests because an item's attribute modifiers are a data
 * component, and components are not bound until the game has loaded - the same "Components not bound
 * yet" that the GUI framework's static layouts have to dodge. The knockback half has to be in-world
 * regardless: an attribute that is declared and never read would pass a pure test and do nothing.
 */
public final class SledgehammerCombatTests {

    /** Vanilla's own numbers, so a change to either side of the comparison shows up as a failure. */
    private static final double PLAYER_BASE_DAMAGE = 1.0;
    private static final double BASE_ATTACK_SPEED = 4.0;

    private record Rung(String name, Item sledgehammer, Item sword) {
    }

    private static List<Rung> ladder() {
        return List.of(
            // Every rung against its exact twin. 26.1 ships a copper sword, so the bottom rung has one
            // too - worth stating because this mod's COPPER_TIER is not vanilla's COPPER: ours carries
            // an attackDamageBonus of 1.5 against vanilla's 1.0, which is already folded into the
            // numbers below rather than being a discrepancy in them.
            new Rung("copper", RCItems.COPPER_SLEDGEHAMMER.get(), Items.COPPER_SWORD),
            new Rung("iron", RCItems.IRON_SLEDGEHAMMER.get(), Items.IRON_SWORD),
            new Rung("diamond", RCItems.DIAMOND_SLEDGEHAMMER.get(), Items.DIAMOND_SWORD),
            new Rung("netherite", RCItems.NETHERITE_SLEDGEHAMMER.get(), Items.NETHERITE_SWORD));
    }

    /** What one hit does, read off the item the way the tooltip does. */
    private static double perHit(Item item) {
        return PLAYER_BASE_DAMAGE + modifier(item, Attributes.ATTACK_DAMAGE);
    }

    /** Swings a second. Vanilla's base is 4.0 and every weapon subtracts from it. */
    private static double swingsPerSecond(Item item) {
        return BASE_ATTACK_SPEED + modifier(item, Attributes.ATTACK_SPEED);
    }

    private static double modifier(Item item, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        ItemAttributeModifiers modifiers = item.components().getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double total = 0.0;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.slot().test(EquipmentSlot.MAINHAND)) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }

    public static void register() {

        RCGameTests.test("a_sledgehammer_hits_at_least_as_hard_as_its_sword_over_time", 20, helper -> {
            for (Rung rung : ladder()) {
                double hammer = perHit(rung.sledgehammer()) * swingsPerSecond(rung.sledgehammer());
                double sword = perHit(rung.sword()) * swingsPerSecond(rung.sword());
                helper.assertTrue(hammer >= sword - 0.001,
                    rung.name() + " sledgehammer does " + String.format("%.1f", hammer)
                        + " damage a second against its sword's " + String.format("%.1f", sword)
                        + "; the point of #379 is that waiting for the slow swing has to pay");

                // ...and it is still a SLOW weapon. If this ever fails, someone has fixed the damage by
                // making it swing like a sword, which is the fix the issue explicitly rejected.
                helper.assertTrue(swingsPerSecond(rung.sledgehammer()) < swingsPerSecond(rung.sword()),
                    rung.name() + " sledgehammer swings at " + swingsPerSecond(rung.sledgehammer())
                        + "/s, no slower than its sword; it has stopped being a sledgehammer");

                // The heavy hit is the trade being bought. Per swing it must beat the sword outright.
                helper.assertTrue(perHit(rung.sledgehammer()) > perHit(rung.sword()),
                    rung.name() + " sledgehammer hits for " + perHit(rung.sledgehammer())
                        + " against its sword's " + perHit(rung.sword()));
            }
            helper.succeed();
        });

        RCGameTests.test("knockback_is_declared_on_every_rung_and_rises_with_the_tier", 20, helper -> {
            double previous = 0.0;
            for (Rung rung : ladder()) {
                double knockback = modifier(rung.sledgehammer(), Attributes.ATTACK_KNOCKBACK);
                helper.assertTrue(knockback > 0.0,
                    rung.name() + " sledgehammer declares no ATTACK_KNOCKBACK; #379 asked for knockback "
                        + "and an attribute nobody set is the silent way to not have it");
                helper.assertTrue(knockback >= previous,
                    "the ladder goes backwards at " + rung.name() + ": " + knockback + " after " + previous);
                previous = knockback;

                // Vanilla puts none on any weapon, so this is the whole of the effect and it should not
                // quietly grow into a launcher.
                helper.assertTrue(knockback <= 2.0,
                    rung.name() + " carries " + knockback + " knockback, more than Knockback II");
            }
            helper.succeed();
        });

        RCGameTests.test("a_sledgehammer_throws_a_target_further_than_a_bare_hand", 40, helper -> {
            // The attribute is declared; this is whether a BLOW carries it. Player.attack reads
            // ATTACK_KNOCKBACK through LivingEntity.getKnockback, so an attribute nobody reads would
            // pass the test above and do nothing in a fight.
            //
            // THE MODIFIER IS APPLIED BY HAND, and that is a real limit of this test worth stating.
            // An item's modifiers normally reach its wielder through LivingEntity's equipment sweep,
            // which runs on the entity's tick - and the harness has no ticking player, only
            // makeMockServerPlayerInLevel. The first version equipped the hammer, waited three ticks
            // and still measured 0.0 on the player, which reads as "the item declares nothing" when in
            // fact nothing had run to apply it. So the value is read OFF THE ITEM and applied through
            // vanilla's own AttributeInstance call, the same one the sweep makes. What this proves is
            // that the number the item declares turns into a harder blow; that equipping applies it at
            // all is vanilla's job and is not what #379 changed.
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            // makeMockServerPlayerInLevel does NOT default to survival. It changes nothing here, but
            // the habit is cheap and the opposite has bitten this repo before.
            player.setGameMode(GameType.SURVIVAL);
            BlockPos stand = helper.absolutePos(new BlockPos(2, 1, 0));
            player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.0F, 0.0F);

            double bare = launch(helper, player, 1);

            Item hammer = RCItems.NETHERITE_SLEDGEHAMMER.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hammer));
            AttributeInstance knockback = player.getAttribute(Attributes.ATTACK_KNOCKBACK);
            helper.assertTrue(knockback != null, "a player has no ATTACK_KNOCKBACK attribute to modify");
            double declared = modifier(hammer, Attributes.ATTACK_KNOCKBACK);
            helper.assertTrue(declared > 0.0, "the netherite sledgehammer declares no knockback");
            knockback.addTransientModifier(new AttributeModifier(
                Identifier.fromNamespaceAndPath("recompile", "sledgehammer_knockback_test"),
                declared, AttributeModifier.Operation.ADD_VALUE));

            double swung = launch(helper, player, 3);
            helper.assertTrue(swung > bare * 1.2,
                "a netherite sledgehammer threw its target " + String.format("%.4f", swung)
                    + " against a bare hand's " + String.format("%.4f", bare)
                    + "; the wielder carries " + declared + " knockback and the blow is not using it");
            helper.succeed();
        });
    }

    /**
     * Hit a fresh target with {@code weapon} and report how hard it left, measured as the horizontal
     * speed it picks up on the tick of the blow.
     *
     * <p>Delta movement rather than displacement over time: knockback IS a velocity change, and reading
     * it directly avoids waiting for the target to slide and be caught by friction, its own AI, or the
     * floor. Each call gets its own target at its own spot so two measurements cannot interfere, and the
     * player is passed in rather than made here because it must already have been holding the weapon
     * for a tick.
     */
    private static double launch(GameTestHelper helper, ServerPlayer player, int zOffset) {
        Mob target = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        helper.assertTrue(target != null, "could not create a target");
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, zOffset));
        target.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        target.setNoAi(true);                       // it must not walk out of its own measurement
        helper.getLevel().addFreshEntity(target);
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = false;

        // A full swing. Vanilla scales the blow by how charged the attack is, and a mock player has
        // never swung, so without this the two measurements are taken at different strengths.
        player.resetAttackStrengthTicker();
        player.attack(target);

        double speed = target.getDeltaMovement().horizontalDistance();
        target.discard();
        return speed;
    }

    private SledgehammerCombatTests() {
    }
}
