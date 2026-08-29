package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.SequencerBlockEntity;
import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The resin family, and the two halves that open it (#231).
 *
 * <p><b>Why this exists at all.</b> Vanilla gives resin exactly one origin - it grows on pale oak in
 * the Pale Garden - and every route into it is closed here: no Pale Garden generates, the Creaking's
 * loot table is empty, growing pale oak yields none, and {@code creaking_heart}'s recipe consumes a
 * {@code resin_block}, so it is self-referential. Nine items sat unreachable for eleven days while the
 * decision was open.
 *
 * <p><b>The shape of the answer is Grog's, and that is deliberate.</b> Amber is polymerised and
 * cross-linked; it cannot be softened back into fresh sap, which is the fired-clay problem verbatim and
 * a trade this mod already refused once. So the amber is not reversed. Turpentine IS the volatile
 * terpene fraction distilled off pine resin - precisely what fossilisation drove out - and putting that
 * back is what makes the husk workable. Two halves, useless apart.
 */
final class ResinTests {

    private ResinTests() {
    }

    /** Everything vanilla hangs off {@code resin_clump}, which is why one source opens all of it. */
    private static final List<String> FAMILY = List.of(
        "resin_clump", "resin_block", "resin_brick", "resin_bricks", "resin_brick_slab",
        "resin_brick_stairs", "resin_brick_wall", "chiseled_resin_bricks", "creaking_heart");

    static void register() {

        // THE SEQUENCER KEEPS THE HUSK. It used to destroy the amber outright, and the ruling that
        // opened this chain turned that loss into its input - every amber in both pull streams is
        // STAMPED, so without the husk a clump of resin would cost a spawn egg's worth of sequencing
        // and the two chains would compete for one drop instead of composing.
        RCGameTests.test("sequencing_leaves_a_husk", 60, helper -> {
            var level = helper.getLevel();
            var pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.SEQUENCER.get());
            var machine = (SequencerBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
            helper.assertTrue(machine != null, "the sequencer has no block entity");

            ItemStack amber = new ItemStack(RCItems.AMBER.get(), 2);
            amber.set(RCDataComponents.SPECIES.get(),
                Identifier.fromNamespaceAndPath("minecraft", "cow"));
            machine.setItem(SequencerBlockEntity.INPUT_SLOT, amber);
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                machine.battery().insert(SequencerBlockEntity.CAPACITY, tx);
                tx.commit();
            }
            for (int i = 0; i <= SequencerBlockEntity.TICKS_PER_READ; i++) {
                SequencerBlockEntity.serverTick(level, helper.absolutePos(pos),
                    helper.getBlockState(pos), machine);
            }

            ItemStack husk = machine.getItem(SequencerBlockEntity.HUSK_SLOT);
            helper.assertTrue(husk.is(RCItems.SPENT_AMBER.get()),
                "reading an amber left " + husk + " rather than a Spent Amber, so the resin chain has "
                    + "no input and the only way to make one would be to spend a second whole amber");
            helper.assertTrue(machine.getItem(SequencerBlockEntity.OUTPUT_SLOT)
                    .is(RCItems.IDEA_FRAGMENT.get()),
                "the fragment must still come out - the husk is a byproduct, not a replacement");
            helper.succeed();
        });

        // A FULL HUSK SLOT COSTS NOTHING, which is the opposite of what this test asserted first.
        //
        // <p><b>The first version pinned a design this repo had already reverted, and cited it as the
        // precedent.</b> It made a full byproduct slot stop the read, "the Cupola's lesson about slag".
        // CupolaFurnaceBlock.getTicker says the reverse in as many words: the hold was tried, it froze
        // the machine mid-burn in a state that looked exactly like working, and it bought nothing,
        // because rakeSlag carries its remainder on a counter and a full slot loses no slag at all.
        //
        // <p>So the Sequencer owes husks rather than stalling for them. Two things have to hold and
        // both are asserted here, because either alone is satisfiable by a bug: the read must keep
        // going, and no husk may be lost while the slot is full.
        RCGameTests.test("a_full_husk_slot_costs_nothing", 80, helper -> {
            var level = helper.getLevel();
            var pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, com.flatts.recompile.registry.RCBlocks.SEQUENCER.get());
            var machine = (SequencerBlockEntity) level.getBlockEntity(helper.absolutePos(pos));

            ItemStack amber = new ItemStack(RCItems.AMBER.get(), 2);
            amber.set(RCDataComponents.SPECIES.get(),
                Identifier.fromNamespaceAndPath("minecraft", "cow"));
            machine.setItem(SequencerBlockEntity.INPUT_SLOT, amber);
            // Something ELSE in the byproduct slot, so it is not merely full but unusable.
            machine.setItem(SequencerBlockEntity.HUSK_SLOT, new ItemStack(RCItems.JUNK.get()));
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                machine.battery().insert(SequencerBlockEntity.CAPACITY, tx);
                tx.commit();
            }
            for (int i = 0; i <= SequencerBlockEntity.TICKS_PER_READ; i++) {
                SequencerBlockEntity.serverTick(level, helper.absolutePos(pos),
                    helper.getBlockState(pos), machine);
            }

            helper.assertTrue(machine.getItem(SequencerBlockEntity.INPUT_SLOT).getCount() == 1,
                "the read did not happen with a blocked byproduct slot, so the machine stalls for a "
                    + "slot the player may not care about - the Cupola's reverted mistake");
            helper.assertTrue(machine.getItem(SequencerBlockEntity.OUTPUT_SLOT)
                    .is(RCItems.IDEA_FRAGMENT.get()),
                "and the fragment must still come out, which is what the player is actually watching");

            // NOW CLEAR IT. The husk owed while the slot was blocked has to arrive, or the debt is
            // decoration and the resin chain silently loses an input every time the slot fills.
            machine.setItem(SequencerBlockEntity.HUSK_SLOT, ItemStack.EMPTY);
            SequencerBlockEntity.serverTick(level, helper.absolutePos(pos),
                helper.getBlockState(pos), machine);
            ItemStack paid = machine.getItem(SequencerBlockEntity.HUSK_SLOT);
            helper.assertTrue(paid.is(RCItems.SPENT_AMBER.get()) && paid.getCount() == 1,
                "clearing the slot paid back " + paid + " rather than the one husk owed, so a read "
                    + "taken while it was blocked lost its Spent Amber for good");
            helper.succeed();
        });

        // NEITHER HALF WORKS ALONE, which is the whole point of the Grog shape. If one of them made
        // resin by itself the other would be decoration.
        RCGameTests.test("resin_needs_both_halves", 20, helper -> {
            var level = helper.getLevel();
            helper.assertTrue(craft(level, new ItemStack(RCItems.SPENT_AMBER.get()),
                    new ItemStack(RCItems.TURPENTINE.get())).is(resin("resin_clump")),
                "a Spent Amber and a Turpentine must make a resin clump");
            helper.assertTrue(craft(level, new ItemStack(RCItems.SPENT_AMBER.get()),
                    ItemStack.EMPTY).isEmpty(),
                "a Spent Amber alone must make nothing - amber cannot be softened back into sap, which "
                    + "is the refusal this chain is built on");
            helper.assertTrue(craft(level, new ItemStack(RCItems.TURPENTINE.get()),
                    ItemStack.EMPTY).isEmpty(),
                "and Turpentine alone must make nothing");
            helper.assertTrue(craft(level, new ItemStack(RCItems.AMBER.get()),
                    new ItemStack(RCItems.TURPENTINE.get())).isEmpty(),
                "an UNREAD amber must not work either, or the husk is pointless and the resin chain "
                    + "goes back to competing with the spawn eggs for the same drop");
            helper.succeed();
        });

        // THE FAMILY HAS AN ENTRY POINT THAT IS NOT CIRCULAR, which is the issue's actual deliverable.
        //
        // <p><b>The obvious version of this test is vacuous and it was written that way first.</b>
        // Asking "does a recipe produce each of the nine" passes on a world with no resin in it at all:
        // vanilla ships those recipes and always has, so the check measured that vanilla exists. It
        // stayed green with this PR's recipe switched off, which is how it was caught.
        //
        // <p>What actually decides reachability is the ENTRY POINT. All nine hang off resin_clump, and
        // vanilla's only recipe for a clump consumes a resin_block - self-referential, exactly like
        // creaking_heart consuming the resin it is the source of. So the thing to assert is that
        // something makes a clump from ingredients that are NOT in the family, and that the rest of the
        // family really does hang off it.
        RCGameTests.test("the_resin_family_has_a_non_circular_entry_point", 20, helper -> {
            var level = helper.getLevel();
            java.util.Set<Item> family = new java.util.HashSet<>();
            for (String name : FAMILY) {
                Item item = resin(name);
                helper.assertTrue(item != net.minecraft.world.item.Items.AIR,
                    "minecraft:" + name + " does not exist, so this list is stale");
                family.add(item);
            }

            List<String> entries = new ArrayList<>();
            int swept = 0;
            for (var holder : level.recipeAccess().recipeMap().values()) {
                swept++;
                boolean makesClump = false;
                for (var display : holder.value().display()) {
                    for (var stack : display.result().resolveForStacks(
                            net.minecraft.world.item.crafting.display.SlotDisplayContext
                                .fromLevel(level))) {
                        if (stack.is(resin("resin_clump"))) {
                            makesClump = true;
                        }
                    }
                }
                if (!makesClump) {
                    continue;
                }
                // Circular if any ingredient is itself in the family. Vanilla's clump recipe consumes a
                // resin_block and is exactly that, so it must not count as a way in.
                boolean circular = false;
                for (var ingredient : holder.value().placementInfo().ingredients()) {
                    // items() is a Stream in 26.1, not a collection.
                    if (ingredient.items().anyMatch(h -> family.contains(h.value()))) {
                        circular = true;
                    }
                }
                if (!circular) {
                    entries.add(holder.id().toString());
                }
            }
            helper.assertTrue(swept > 100,
                "only " + swept + " recipes were swept - discovery is broken, so this would pass "
                    + "against a world with no resin source at all");
            helper.assertTrue(!entries.isEmpty(),
                "nothing makes a resin_clump out of anything but resin. Vanilla's own recipe consumes a "
                    + "resin_block, so without an outside source the whole family is unreachable and "
                    + "the nine items in #231 are still dead");

            // AND THE REST OF THE FAMILY REALLY DOES HANG OFF THE CLUMP. The comment above claimed this
            // was asserted and it was not - the fifth comment in two days describing coverage that did
            // not exist. Without it, dropping the creaking_heart or brick route would leave this green
            // while the entry point kept working, which is the shape of every stale-list bug here.
            List<String> orphans = new ArrayList<>();
            for (String name : FAMILY) {
                Item item = resin(name);
                if (item == resin("resin_clump")) {
                    continue;   // the entry point itself, checked above
                }
                boolean made = false;
                for (var holder : level.recipeAccess().recipeMap().values()) {
                    for (var display : holder.value().display()) {
                        for (var stack : display.result().resolveForStacks(
                                net.minecraft.world.item.crafting.display.SlotDisplayContext
                                    .fromLevel(level))) {
                            if (stack.is(item)) {
                                made = true;
                            }
                        }
                    }
                }
                if (!made) {
                    orphans.add(name);
                }
            }
            helper.assertTrue(orphans.isEmpty(),
                "these hang off nothing, so opening the clump does not open them: " + orphans);
            helper.succeed();
        });
    }

    /** A vanilla item by path, or AIR. */
    private static Item resin(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
    }

    /** What a 1x2 grid of these two makes, or nothing. */
    private static ItemStack craft(net.minecraft.server.level.ServerLevel level, ItemStack a,
            ItemStack b) {
        CraftingInput input = CraftingInput.of(1, 2, List.of(a, b));
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
            .map(r -> r.value().assemble(input))
            .orElse(ItemStack.EMPTY);
    }
}
