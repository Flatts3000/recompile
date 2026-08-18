package com.flatts.recompile.gametest;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Reading what a recipe makes, for the sweeps that ask "what produces this item".
 *
 * <p>26.1 exposes a recipe's output as a {@link SlotDisplay} tree rather than as a stack, so answering
 * that question means walking composites and remainders rather than comparing two items. Two tests need
 * the same walk and each had grown its own copy.
 *
 * <p><b>It does not see everything, and that is the important part.</b> {@code display()} is empty for
 * this mod's own recipe types - separating and pulverizing both return {@code List.of()}, and teardown
 * never overrides it - so a sweep built on this alone is blind to exactly the schemas a datapack extends.
 * Anything asserting that nothing produces an item needs a second pass over those types; see
 * {@code FoundNotCraftedTests}, which allowlists them explicitly, and the echo shard sweep in
 * {@code SewerTests}, which reads their JSON.
 */
final class RecipeResults {

    private RecipeResults() {
    }

    /** Whether a recipe's result display names an item, following composites and remainders. */
    static boolean produces(SlotDisplay display, Item item) {
        return switch (display) {
            case SlotDisplay.ItemSlotDisplay slot -> slot.item().value() == item;
            case SlotDisplay.ItemStackSlotDisplay slot -> slot.stack().item().value() == item;
            case SlotDisplay.Composite composite ->
                composite.contents().stream().anyMatch(inner -> produces(inner, item));
            case SlotDisplay.WithRemainder remainder -> produces(remainder.input(), item);
            case SlotDisplay.OnlyWithComponent only -> produces(only.source(), item);
            default -> false;
        };
    }
}
