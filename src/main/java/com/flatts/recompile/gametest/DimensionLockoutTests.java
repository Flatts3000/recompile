package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.event.RCDimensionLockout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * GameTests for {@link RCDimensionLockout}: the door on the End, and the pair of hooks that keeps a
 * shut dimension from leaving dead portal frames standing around.
 *
 * <p>{@code DimensionDefaultsTest} asserts the config DEFAULTS, which is what a fresh install gets -
 * but a default is only a design if something reads it. Nothing did: both handlers were untouched by
 * any test, so the End lockout was asserted entirely by proxy. These drive the handlers themselves.
 *
 * <p>Both events are ordinary constructible objects, so each test builds one and calls the handler
 * directly rather than trying to shove a mock player through a real portal.
 */
final class DimensionLockoutTests {

    private DimensionLockoutTests() {
    }

    private static final BlockPos FRAME = new BlockPos(2, 1, 2);

    static void register() {
        // The End is the thing keeping free resources out of a closed trash economy. Delete this and
        // the lockout can stop firing entirely - travel goes through, and a player reaches shulker
        // shells, elytra and end stone without any of the found economy this mod is about.
        RCGameTests.test("the_end_refuses_travel_and_tells_the_player", 20, helper -> {
            // A real ServerPlayer, because the message branch is only reachable for one - and a
            // handler that threw while telling the player off would be a crash on a portal.
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            // Set the flag rather than reading whatever run/config happens to hold: what the mod
            // SHIPS with is DimensionDefaultsTest's job, and this one is about the handler.
            boolean was = RCConfig.END_ENABLED.get();
            try {
                RCConfig.END_ENABLED.set(false);
                EntityTravelToDimensionEvent event = new EntityTravelToDimensionEvent(player, Level.END);
                RCDimensionLockout.onTravel(event);
                helper.assertTrue(event.isCanceled(),
                    "travel to the End must be refused while END_ENABLED is false");
            } finally {
                RCConfig.END_ENABLED.set(was);
            }
            helper.succeed();
        });

        // The gate has to be a CONFIG gate, not a hardcode. Delete this and the handler could start
        // cancelling End travel unconditionally: a pack that opens the End in its config would find
        // the flag does nothing, with no error anywhere to say why.
        RCGameTests.test("the_end_opens_when_its_config_flag_does", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            boolean was = RCConfig.END_ENABLED.get();
            try {
                RCConfig.END_ENABLED.set(true);
                EntityTravelToDimensionEvent event = new EntityTravelToDimensionEvent(player, Level.END);
                RCDimensionLockout.onTravel(event);
                helper.assertFalse(event.isCanceled(),
                    "with END_ENABLED on, the lockout must let the player through");
            } finally {
                // Restore in finally: this config is global, so leaking it on would silently open
                // the End for every test that runs after this one.
                RCConfig.END_ENABLED.set(was);
            }
            helper.succeed();
        });

        // The two halves the class deliberately holds together. Delete this and they can come apart
        // in either direction, and both failures are ugly: cancel travel without refusing the frame
        // and a player builds a portal, lights it, watches it open and bounces off an invisible wall;
        // refuse the frame while travel is allowed and any other route in (a pack's teleport, a mod's
        // portal) walks straight past a lockout the player was told is on.
        RCGameTests.test("one_flag_governs_both_nether_travel_and_portal_formation", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos abs = helper.absolutePos(FRAME);
            boolean was = RCConfig.NETHER_ENABLED.get();
            try {
                RCConfig.NETHER_ENABLED.set(false);

                EntityTravelToDimensionEvent shutTravel =
                    new EntityTravelToDimensionEvent(player, Level.NETHER);
                RCDimensionLockout.onTravel(shutTravel);
                helper.assertTrue(shutTravel.isCanceled(),
                    "a shut Nether must refuse travel");

                BlockEvent.PortalSpawnEvent shutFrame = portalSpawn(helper.getLevel(), abs);
                RCDimensionLockout.onNetherPortalSpawn(shutFrame);
                helper.assertTrue(shutFrame.isCanceled(),
                    "a shut Nether must refuse the FRAME too, or the player is left with a dead portal");

                RCConfig.NETHER_ENABLED.set(true);

                EntityTravelToDimensionEvent openTravel =
                    new EntityTravelToDimensionEvent(player, Level.NETHER);
                RCDimensionLockout.onTravel(openTravel);
                helper.assertFalse(openTravel.isCanceled(),
                    "the Nether ships open (owner, 2026-08-19) - travel must go through");

                BlockEvent.PortalSpawnEvent openFrame = portalSpawn(helper.getLevel(), abs);
                RCDimensionLockout.onNetherPortalSpawn(openFrame);
                helper.assertFalse(openFrame.isCanceled(),
                    "an open Nether must let a frame light, or the door is shut in practice");
            } finally {
                RCConfig.NETHER_ENABLED.set(was);
            }
            helper.succeed();
        });

        // The control, and it is not decoration: onTravel reads the destination and every dimension
        // change in the game goes through it. Delete this and a broadened condition (or a swapped
        // comparison) could cancel ordinary travel - the symptom being a player unable to be
        // teleported at all, with only a "dimension locked" message to go on.
        RCGameTests.test("travel_to_the_overworld_is_never_touched", 20, helper -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            EntityTravelToDimensionEvent event =
                new EntityTravelToDimensionEvent(player, Level.OVERWORLD);

            RCDimensionLockout.onTravel(event);

            helper.assertFalse(event.isCanceled(),
                "the lockout holds two dimensions and must not touch the one the mod is set in");
            helper.succeed();
        });
    }

    /**
     * A portal-formation event for a frame that would light at {@code abs}.
     *
     * <p>The {@code PortalShape} is null on purpose: the handler never reads it, and building a real
     * one would mean building a real obsidian frame and testing vanilla's portal scanner instead of
     * this lockout. If the handler ever starts reading the shape, this fails loudly rather than
     * quietly asserting nothing.
     */
    private static BlockEvent.PortalSpawnEvent portalSpawn(LevelAccessor level, BlockPos abs) {
        return new BlockEvent.PortalSpawnEvent(level, abs, Blocks.OBSIDIAN.defaultBlockState(), null);
    }
}
