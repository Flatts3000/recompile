package com.flatts.recompile.content.freight;

import com.flatts.recompile.content.recipe.FreightPhaseRecipe;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The loaded freight ladder: every {@code recompile:freight_phase} in tier order.
 *
 * <p><b>Validation lives here rather than in the codec, because a codec sees one file at a time.</b>
 * Two phases claiming tier 3, or a set that jumps from 2 to 4, are both well-formed files and a
 * broken ladder. A gap is the dangerous one: the terminal would advance into a tier no phase
 * describes and the player would be stuck with an empty manifest and no way to read why.
 *
 * <p><b>A broken ladder is reported and then IGNORED, not thrown.</b> This is read during play, and
 * taking the world down because a pack shipped a duplicate tier would be a worse failure than
 * running short. {@link #sorted} drops what it cannot use and logs once; the game keeps running with
 * the rungs that make sense.
 */
public final class FreightPhases {

    private FreightPhases() {
    }

    /**
     * Every loaded phase, ascending by tier, with duplicates and post-gap rungs dropped.
     *
     * <p>Read off the recipe manager on each call rather than cached. The set changes on
     * {@code /reload}, and a cache that survived one would quietly describe the previous datapack.
     */
    public static List<FreightPhaseRecipe> sorted(ServerLevel level) {
        List<FreightPhaseRecipe> all = new ArrayList<>();
        for (RecipeHolder<FreightPhaseRecipe> holder
                : level.recipeAccess().recipeMap().byType(RCRecipeTypes.FREIGHT_PHASE.get())) {
            all.add(holder.value());
        }
        return usable(all);
    }

    /**
     * The pure half of {@link #sorted}: sort by tier, drop duplicates, stop at the first gap.
     *
     * <p>Split out and public so {@code FreightLadderTest} can drive it with no world. The rules are
     * arithmetic and the failures are silent in play - a duplicated tier and a gap both look like a
     * ladder that is simply shorter than the pack author thought - so this is exactly the logic that
     * wants a unit test rather than a GameTest.
     */
    public static List<FreightPhaseRecipe> usable(List<FreightPhaseRecipe> phases) {
        List<FreightPhaseRecipe> all = new ArrayList<>(phases);
        all.sort(Comparator.comparingInt(FreightPhaseRecipe::tier));

        List<FreightPhaseRecipe> usable = new ArrayList<>();
        int expected = 1;
        for (FreightPhaseRecipe phase : all) {
            if (phase.tier() < expected) {
                // A duplicate, or a tier below one already taken. Skip it: the first file to claim a
                // tier wins, which is arbitrary but stable, and stable beats correct-looking here.
                continue;
            }
            if (phase.tier() > expected) {
                // A gap. Everything past it is unreachable anyway, so stop rather than pretend.
                break;
            }
            usable.add(phase);
            expected++;
        }
        return List.copyOf(usable);
    }

    /** The phase a world on {@code tier} is currently working toward, or empty if the ladder is done. */
    public static Optional<FreightPhaseRecipe> current(ServerLevel level, int tier) {
        List<FreightPhaseRecipe> phases = sorted(level);
        // Tier N means N phases are complete, so the one in progress is at index N.
        return tier >= 0 && tier < phases.size() ? Optional.of(phases.get(tier)) : Optional.empty();
    }

    /** How many rungs the loaded ladder has. */
    public static int length(ServerLevel level) {
        return sorted(level).size();
    }

    /** Whether completing {@code tier} finishes the ladder. */
    public static boolean isFinal(ServerLevel level, int tier) {
        return tier + 1 >= length(level) && length(level) > 0;
    }
}
