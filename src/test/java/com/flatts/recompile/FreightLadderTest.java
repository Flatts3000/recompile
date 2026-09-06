package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.freight.FreightManifest;
import com.flatts.recompile.content.freight.FreightPhases;
import com.flatts.recompile.content.recipe.FreightPhaseRecipe;
import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/**
 * The freight ladder's arithmetic (#387).
 *
 * <p><b>Every failure this covers is silent in play.</b> A duplicated tier and a gap both look like a
 * ladder that is simply shorter than the pack author thought: no error, no log line, just a rung that
 * never arrives. That is what makes it worth a unit test rather than a GameTest - the logic is pure,
 * so a world is the wrong instrument and a slower one.
 */
class FreightLadderTest {

    private static FreightPhaseRecipe phase(int tier) {
        return new FreightPhaseRecipe(tier, "freight.test.phase." + tier,
            List.of(new FreightPhaseRecipe.Requirement(Items.IRON_INGOT, 1)));
    }

    // ---- the ladder ---------------------------------------------------------------------

    @Test
    void phases_come_back_in_tier_order_whatever_order_they_loaded_in() {
        // Datapack load order is not file order and is not guaranteed, so the ladder cannot depend
        // on it. Shuffled deliberately.
        List<FreightPhaseRecipe> usable =
            FreightPhases.usable(List.of(phase(3), phase(1), phase(4), phase(2)));
        assertEquals(List.of(1, 2, 3, 4), usable.stream().map(FreightPhaseRecipe::tier).toList());
    }

    @Test
    void a_duplicate_tier_is_dropped_rather_than_taking_the_world_down() {
        // Two files claiming tier 2. One wins, the ladder stays dense, and nothing throws - a pack
        // shipping a duplicate should cost a rung, not a save.
        List<FreightPhaseRecipe> usable =
            FreightPhases.usable(List.of(phase(1), phase(2), phase(2), phase(3)));
        assertEquals(List.of(1, 2, 3), usable.stream().map(FreightPhaseRecipe::tier).toList());
    }

    @Test
    void the_ladder_stops_at_the_first_gap() {
        // 1, 2, then 4. Tier 4 is unreachable because nothing describes tier 3, so advancing into it
        // would leave a player staring at an empty manifest with no way to read why. Truncate.
        List<FreightPhaseRecipe> usable =
            FreightPhases.usable(List.of(phase(1), phase(2), phase(4), phase(5)));
        assertEquals(List.of(1, 2), usable.stream().map(FreightPhaseRecipe::tier).toList());
    }

    @Test
    void a_ladder_that_does_not_start_at_one_is_empty() {
        // Tier 0 does not exist: tier 0 is the state of having shipped nothing. A pack whose lowest
        // phase is 2 has no reachable ladder at all, and saying so is better than starting at 2.
        assertTrue(FreightPhases.usable(List.of(phase(2), phase(3))).isEmpty());
    }

    @Test
    void an_empty_datapack_is_an_empty_ladder_and_not_a_crash() {
        assertTrue(FreightPhases.usable(List.of()).isEmpty());
    }

    // ---- one file's own validity --------------------------------------------------------

    @Test
    void a_phase_that_requires_nothing_is_refused_at_parse() {
        // It would be complete the instant it started, so it is an authoring mistake with no sensible
        // reading. IllegalArgumentException specifically, because that is the type
        // SimpleJsonResourceReloadListener catches per file - anything else takes the reload down.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> new FreightPhaseRecipe(1, "freight.test.empty", List.of()));
        assertTrue(thrown.getMessage().contains("requires nothing"));
    }

    @Test
    void a_phase_naming_the_same_item_twice_is_refused_at_parse() {
        // The terminal counts against whichever line it hits first, so the other could never fall.
        assertThrows(IllegalArgumentException.class,
            () -> new FreightPhaseRecipe(1, "freight.test.dupe", List.of(
                new FreightPhaseRecipe.Requirement(Items.IRON_INGOT, 4),
                new FreightPhaseRecipe.Requirement(Items.IRON_INGOT, 8))));
    }

    @Test
    void a_phase_may_have_more_lines_than_the_screen_draws() {
        // THE REGRESSION THIS PINS. FreightManifest truncates to MAX_LINES because that is what the
        // screen can draw, and for one commit the terminal filtered ADMISSION off the manifest too.
        // A pack shipping a seven-line phase then got a terminal that silently refused the seventh
        // item while isSatisfied still waited for it: hopper backs up, nothing logged, ladder stuck.
        // `required` is what admission reads, so it must answer for every line regardless of MAX_LINES.
        List<FreightPhaseRecipe.Requirement> many = List.of(
            new FreightPhaseRecipe.Requirement(Items.IRON_INGOT, 1),
            new FreightPhaseRecipe.Requirement(Items.GOLD_INGOT, 2),
            new FreightPhaseRecipe.Requirement(Items.COPPER_INGOT, 3),
            new FreightPhaseRecipe.Requirement(Items.DIAMOND, 4),
            new FreightPhaseRecipe.Requirement(Items.EMERALD, 5),
            new FreightPhaseRecipe.Requirement(Items.COAL, 6),
            new FreightPhaseRecipe.Requirement(Items.REDSTONE, 7));
        assertTrue(many.size() > FreightManifest.MAX_LINES,
            "this test is vacuous unless it uses more lines than the screen draws");

        FreightPhaseRecipe phase = new FreightPhaseRecipe(1, "freight.test.many", many);
        assertEquals(7, phase.required(Items.REDSTONE),
            "the last line is invisible to admission, so the phase can never be completed");

        // And the manifest really does truncate, which is the other half of the pair.
        assertEquals(FreightManifest.MAX_LINES,
            FreightManifest.of(phase, 0, 1).lines().size());
    }

    @Test
    void required_answers_zero_for_anything_the_phase_does_not_want() {
        // This is what the terminal's slot filter reads, so a wrong answer here is the difference
        // between a hopper backing up and the terminal eating a stack.
        FreightPhaseRecipe phase = new FreightPhaseRecipe(1, "freight.test.one", List.of(
            new FreightPhaseRecipe.Requirement(Items.IRON_INGOT, 12)));
        assertEquals(12, phase.required(Items.IRON_INGOT));
        assertEquals(0, phase.required(Items.GOLD_INGOT));
    }
}
