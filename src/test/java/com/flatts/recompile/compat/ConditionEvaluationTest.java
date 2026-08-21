package com.flatts.recompile.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code RecipeFiles.conditionsHold} is the one place that decides whether a bundled file the game
 * skipped should still be shown to the player, and every way it can be wrong is silent.
 *
 * <p><b>Why this is a unit test rather than a GameTest.</b> The answer depends on which mods are
 * installed, so the interesting cases only differ in a run that has AE2 - and CI never does. Pure
 * JSON in, boolean out, no world: a GameTest would be the slower instrument and would still only
 * cover the half CI can reach.
 *
 * <p><b>Both cases below shipped as bugs and both were found by something other than a test of this
 * method.</b> {@code neoforge:not} was unrecognised, so a viewer read the sky stone strip modifier as
 * active even with AE2 installed and hid a drop the game was really producing - caught by the rate
 * census on a manual with-AE2 run, which is not a thing that happens on a schedule. The non-array
 * case threw {@code ClassCastException} out of JEI category construction. Neither can reach a player
 * as anything but "this item has no recipe" or "this drop does not exist".
 */
class ConditionEvaluationTest {

    private static boolean holds(String json) {
        JsonObject owner = new JsonObject();
        owner.add("neoforge:conditions", JsonParser.parseString(json));
        return RecipeFiles.conditionsHold(owner);
    }

    /** A mod id that is certainly absent, so the false branch is really false. */
    private static final String ABSENT = "no_such_mod_ships_under_this_id";

    @Test
    @DisplayName("a file with no conditions is always shown")
    void unconditional() {
        assertTrue(RecipeFiles.conditionsHold(new JsonObject()),
            "a recipe with no neoforge:conditions must be shown; almost every recipe in the mod is "
                + "this case, so a false here hides the entire viewer");
    }

    @Test
    @DisplayName("mod_loaded follows whether the mod is loaded")
    void modLoaded() {
        assertTrue(holds("[{\"type\":\"neoforge:mod_loaded\",\"modid\":\"recompile\"}]"),
            "this mod is loaded in the test context, so its own id must satisfy mod_loaded");
        assertFalse(holds("[{\"type\":\"neoforge:mod_loaded\",\"modid\":\"" + ABSENT + "\"}]"),
            "a guard naming an absent mod must NOT hold - this is what keeps JEI from advertising "
                + "the AE2 sourcing recipes to players who cannot make them");
    }

    @Test
    @DisplayName("not() inverts, which is what gates the strip modifiers")
    void inverted() {
        assertFalse(holds("[{\"type\":\"neoforge:not\",\"value\":"
                + "{\"type\":\"neoforge:mod_loaded\",\"modid\":\"recompile\"}}]"),
            "not(mod_loaded recompile) must be false while this mod is loaded");
        assertTrue(holds("[{\"type\":\"neoforge:not\",\"value\":"
                + "{\"type\":\"neoforge:mod_loaded\",\"modid\":\"" + ABSENT + "\"}}]"),
            "not(mod_loaded <absent>) must be TRUE. This is the shape of no_sky_stone.json's guard: "
                + "read as false, the viewer decides the strip is inactive and predicts a drop the "
                + "game deletes; read as unrecognised-and-satisfied, it decides the strip is always "
                + "active and hides a drop the game really produces. The second is what shipped.");
    }

    @Test
    @DisplayName("a condition type nobody handles is treated as satisfied, not as an error")
    void unknownTypeIsSatisfied() {
        assertTrue(holds("[{\"type\":\"neoforge:some_future_condition\"}]"),
            "an unrecognised condition must default to SHOWN. Hiding on unknown would make every "
                + "viewer go blank the first time NeoForge adds a condition type.");
    }

    @Test
    @DisplayName("a malformed conditions block is survivable, not a crash")
    void malformedDoesNotThrow() {
        JsonObject notAnArray = new JsonObject();
        notAnArray.addProperty("neoforge:conditions", "oops");
        assertTrue(RecipeFiles.conditionsHold(notAnArray),
            "conditions that are not an array must read as satisfied. Calling getAsJsonArray on this "
                + "throws ClassCastException, and it propagates out of ofType into JEI category "
                + "construction - a viewer fault becoming a crash on world join, which this class's "
                + "own read path is written to prevent.");

        assertTrue(holds("[\"not an object\"]"),
            "a non-object entry inside the array must be skipped rather than parsed");

        assertTrue(holds("[{\"type\":\"neoforge:not\"}]"),
            "a not() with nothing to negate must be satisfied rather than inverted; guessing at what "
                + "it meant is how a viewer ends up confidently wrong");

        assertTrue(holds("[{\"type\":\"neoforge:mod_loaded\"}]"),
            "mod_loaded with no modid names nothing, so there is nothing to fail on");
    }
}
