package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GameTests for the Cupola Furnace (#50): the machine that makes iron reachable, and the machine that
 * finally automates. Both are things the Burn Barrel deliberately withholds, so both are asserted here -
 * and the automation test is the exact inverse of {@code burn_barrel_blocks_a_hopper}.
 */
final class CupolaFurnaceTests {

    private CupolaFurnaceTests() {
    }

    /** How much slag is sitting in the Cupola's own output slot. */
    private static int slagIn(CupolaFurnaceBlockEntity cupola) {
        return cupola.getItem(CupolaFurnaceBlockEntity.SLAG_SLOT).getCount();
    }

    /**
     * Loose slag anywhere in THIS test's plot - which must always be none.
     *
     * <p>{@code getBounds()} and an item filter, both of which matter. Tests register with padding 1, so
     * batched 5x5x5 plots sit about six blocks apart and an eight-block bubble reaches well into the
     * neighbours; an earlier version of this cleared every ItemEntity in that bubble and was deleting
     * other tests' dropped loot mid-assertion.
     */
    private static int looseSlag(net.minecraft.gametest.framework.GameTestHelper helper) {
        int total = 0;
        for (var entity : helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds(),
                e -> e.getItem().is(RCItems.SLAG.get()))) {
            total += entity.getItem().getCount();
        }
        return total;
    }

    private static CupolaFurnaceBlockEntity place(net.minecraft.gametest.framework.GameTestHelper helper,
            BlockPos pos) {
        helper.setBlock(pos, RCBlocks.CUPOLA_FURNACE.get());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof CupolaFurnaceBlockEntity cupola) {
            return cupola;
        }
        helper.fail("the cupola furnace has no BlockEntity");
        return null;
    }

    static void register() {
        // The payoff: a Steel Offcut cut off a beam becomes iron here, and ONLY here. Nothing else in
        // this world runs BLASTING - a vanilla blast furnace costs 5 iron ingots, which is what this gates.
        RCGameTests.test("cupola_remelts_offcuts_into_iron", 250, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(1, 1, 1));
            if (cupola == null) {
                return;
            }
            cupola.setItem(0, new ItemStack(RCItems.STEEL_OFFCUT.get()));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.succeedWhen(() ->
                helper.assertTrue(cupola.getItem(2).is(Items.IRON_INGOT),
                    "the cupola must remelt a steel offcut into iron, output was " + cupola.getItem(2)));
        });

        // Rebar is the trickle: a nugget, not an ingot, so nine rebar per ingot.
        RCGameTests.test("cupola_remelts_rebar_into_a_nugget", 250, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(3, 1, 1));
            if (cupola == null) {
                return;
            }
            cupola.setItem(0, new ItemStack(RCItems.REBAR.get()));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.succeedWhen(() ->
                helper.assertTrue(cupola.getItem(2).is(Items.IRON_NUGGET),
                    "rebar must yield an iron NUGGET, not an ingot - output was " + cupola.getItem(2)));
        });

        // Upgrading must not COST you anything. The Cupola is blast-only, so the Burn Barrel's copper
        // recipe (plain smelting) would not run in it - copper_from_scrap_blasting is the twin that keeps
        // it working, the same way vanilla gives every ore both a smelting and a blasting recipe. Without
        // it, turning a barrel into a Cupola would silently lose the copper line.
        RCGameTests.test("cupola_still_makes_copper_after_the_upgrade", 250, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(3, 1, 3));
            if (cupola == null) {
                return;
            }
            cupola.setItem(0, new ItemStack(RCItems.SCRAP_METAL.get()));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.succeedWhen(() ->
                helper.assertTrue(cupola.getItem(2).is(Items.COPPER_NUGGET),
                    "the cupola must still make copper, output was " + cupola.getItem(2)));
        });

        // A cupola furnace melts metal; it does not cook dinner. This is the deliberate half of going
        // blast-only (#91) and it is asserted so nobody "fixes" it back into a general furnace without
        // noticing that the iron gate rides on the recipe type. The Burn Barrel keeps food, and it is
        // still craftable on its own, so nothing is lost.
        RCGameTests.test("cupola_does_not_cook_food", 120, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(5, 1, 1));
            if (cupola == null) {
                return;
            }
            cupola.setItem(0, new ItemStack(Items.BEEF));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.runAfterDelay(100, () -> {
                helper.assertTrue(cupola.getItem(2).isEmpty(),
                    "a blast-only cupola must not cook beef, output was " + cupola.getItem(2));
                helper.succeed();
            });
        });

        // IT RAKES SLAG OFF, one lump per cupolaSmeltsPerSlag smelts (#236).
        //
        // Slag cannot be a recipe output and never will be. The Cupola is a BLASTING machine because
        // that IS the iron gate, and vanilla blasting has one result and no byproduct slot; the recipe
        // lookup is private behind a static tick. So the count lives in the block's ticker wrapper -
        // the same seam drainOutput uses - and it works by watching the result slot across the tick,
        // which is exact because vanilla refuses every other route into slot 2.
        //
        // Counted rather than rolled, so this can assert the number instead of waiting for luck.
        RCGameTests.test("the_cupola_rakes_slag_into_its_own_slot", 20, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(1, 1, 1));
            if (cupola == null) {
                return;
            }
            int per = RCConfig.CUPOLA_SMELTS_PER_SLAG.get();
            helper.assertTrue(per > 0, "precondition: slag is enabled, cupolaSmeltsPerSlag=" + per);

            // Drive the counter directly: this asserts the arithmetic of the rake, and a real cook is
            // covered by the two tests below.
            cupola.rakeSlag(helper.getLevel(), per - 1);
            helper.assertTrue(slagIn(cupola) == 0,
                "slag came off after only " + (per - 1) + " smelts, so the ratio is not being counted");

            cupola.rakeSlag(helper.getLevel(), 1);
            helper.assertTrue(slagIn(cupola) == 1,
                "the " + per + "th smelt put " + slagIn(cupola) + " slag in the slot rather than 1");
            helper.assertTrue(cupola.smeltsSinceSlag() == 0,
                "the count must reset after a rake, left at " + cupola.smeltsSinceSlag());

            // AND THE REMAINDER CARRIES. Integer division that throws the leftover away is the obvious
            // way to write this and it silently eats slag on every hopper-fed run.
            cupola.rakeSlag(helper.getLevel(), per * 2 + (per - 1));
            helper.assertTrue(slagIn(cupola) == 3,
                "a further " + (per * 2 + per - 1) + " smelts left " + slagIn(cupola)
                    + " slag in the slot rather than 3");
            helper.assertTrue(cupola.smeltsSinceSlag() == per - 1,
                "the leftover " + (per - 1) + " smelts were dropped rather than carried, count is "
                    + cupola.smeltsSinceSlag());

            // AND NOTHING WAS THROWN ON THE FLOOR, which is the whole point of the slot (owner,
            // 2026-08-18). The first version of this feature popped every lump as an item entity.
            helper.assertTrue(looseSlag(helper) == 0,
                looseSlag(helper) + " slag was dropped as an item instead of going in the slot");
            helper.succeed();
        });

        // AND A REAL COOK ADVANCES THE COUNT, which is the half that was not covered at all.
        //
        // The test above calls rakeSlag directly, so it measures the arithmetic and nothing else. The
        // fragile part is in CupolaFurnaceBlock's ticker: the result slot is sampled BEFORE the furnace
        // tick and before drainOutput, because drainOutput empties the slot and reading after it makes
        // every smelt on a wired Cupola look like nothing happened. Review found that deleting that
        // call outright left all 495 tests green - a machine that never makes slag in the world, and
        // the whole suite calling it healthy.
        //
        // It is also what backs the allowlist entry in every_separating_input_is_findable_scrap. That
        // test now accepts slag as a machine-made feed; without this, nothing anywhere asserts a
        // machine makes any.
        RCGameTests.test("smelting_in_the_cupola_counts_toward_slag", 250, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(1, 1, 1));
            if (cupola == null) {
                return;
            }
            helper.assertTrue(cupola.smeltsSinceSlag() == 0, "precondition: a fresh Cupola has smelted nothing");
            cupola.setItem(0, new ItemStack(RCItems.STEEL_OFFCUT.get(), 4));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.succeedWhen(() -> {
                helper.assertTrue(cupola.getItem(2).is(Items.IRON_INGOT),
                    "precondition: the cupola is smelting, output was " + cupola.getItem(2));
                helper.assertTrue(cupola.smeltsSinceSlag() > 0,
                    "the Cupola smelted and its slag count is still 0, so the ticker is not seeing "
                        + "completed smelts - which is exactly what deleting the rake looks like");
            });
        });

        // AND IT STILL COUNTS WHEN THE OUTPUT IS WIRED, which is the case the ordering exists for.
        //
        // The test above cannot see a swapped rakeSlag/drainOutput because an UNWIRED Cupola's drain
        // does nothing - the ingot is still in the slot either way, so both orderings pass. Wire a
        // barrel to it and the drain empties the slot the moment the smelt lands, so a rake that reads
        // the slot afterwards sees zero forever. Verified: with the two calls swapped this fails and
        // the unwired test does not.
        RCGameTests.test("a_wired_cupola_still_counts_its_smelts", 250, helper -> {
            BlockPos cupolaPos = new BlockPos(1, 1, 1);
            helper.setBlock(cupolaPos, RCBlocks.CUPOLA_FURNACE.get());
            helper.setBlock(new BlockPos(2, 1, 1), RCBlocks.SCRAP_BARREL.get());
            var cupola = (CupolaFurnaceBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(cupolaPos));
            if (cupola == null) {
                helper.fail("no cupola block entity");
                return;
            }
            cupola.setItem(0, new ItemStack(RCItems.STEEL_OFFCUT.get(), 4));
            cupola.setItem(1, new ItemStack(RCItems.OILY_RAG.get(), 8));
            helper.succeedWhen(() -> helper.assertTrue(cupola.smeltsSinceSlag() > 0,
                "a Cupola with storage wired to it smelted and counted nothing - the rake is reading "
                    + "the result slot after drainOutput has already emptied it, so no automated "
                    + "Cupola in the world will ever produce slag"));
        });

        // A FULL SLAG SLOT LOSES NOTHING AND STOPS NOTHING.
        //
        // The first version of this held the machine: skip the tick while the slot is full. Review
        // caught what that costs - AbstractFurnaceBlockEntity.serverTick is the only code that clears
        // the LIT blockstate, so a Cupola stopped for slag stays lit forever, burning nothing and
        // looking exactly like a machine that is working.
        //
        // And holding bought nothing, because rakeSlag carries its remainder. A full slot means the
        // slag waits on the counter, the metal keeps coming, and the debt pays out the moment there is
        // room. That is what this asserts: keep smelting, drop nothing, forget nothing.
        RCGameTests.test("a_full_slag_slot_loses_no_slag", 260, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(3, 1, 1));
            if (cupola == null) {
                return;
            }
            int per = RCConfig.CUPOLA_SMELTS_PER_SLAG.get();
            cupola.setItem(CupolaFurnaceBlockEntity.SLAG_SLOT, new ItemStack(RCItems.SLAG.get(), 64));
            cupola.rakeSlag(helper.getLevel(), per * 3);

            helper.assertTrue(slagIn(cupola) == 64,
                "a full slot must stay at 64, found " + slagIn(cupola));
            helper.assertTrue(looseSlag(helper) == 0,
                looseSlag(helper) + " slag was thrown on the floor rather than owed");
            helper.assertTrue(cupola.slagOwed() == 3,
                "the machine owes " + cupola.slagOwed() + " slag rather than 3, so a full slot "
                    + "silently destroys what it cannot hold");

            // And the debt pays out once there is room.
            cupola.setItem(CupolaFurnaceBlockEntity.SLAG_SLOT, ItemStack.EMPTY);
            cupola.rakeSlag(helper.getLevel(), 0);
            helper.assertTrue(cupola.slagOwed() == 3,
                "a rake of zero smelts must not pay the debt out on its own");
            cupola.rakeSlag(helper.getLevel(), per);
            helper.assertTrue(slagIn(cupola) == 4,
                "emptying the slot must release the owed slag, got " + slagIn(cupola) + " of 4");
            helper.succeed();
        });

        // A HOPPER UNDER THE CUPOLA PULLS THE SLAG OUT, which is the PR's automation claim and had
        // nothing asserting it. cupola_allows_a_hopper only ever put an ingot in the result slot, and
        // the insert-parity test probes the other direction - so slot 3 joining the DOWN face, and
        // canTakeItemThroughFace allowing it, could both have regressed in silence.
        RCGameTests.test("a_hopper_can_pull_the_slag_out", 100, helper -> {
            BlockPos cupolaPos = new BlockPos(1, 2, 1);
            helper.setBlock(cupolaPos, RCBlocks.CUPOLA_FURNACE.get());
            helper.setBlock(new BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.HOPPER);
            var cupola = (CupolaFurnaceBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(cupolaPos));
            if (cupola == null) {
                helper.fail("no cupola block entity");
                return;
            }
            cupola.setItem(CupolaFurnaceBlockEntity.SLAG_SLOT, new ItemStack(RCItems.SLAG.get(), 4));
            helper.succeedWhen(() -> {
                var hopper = (net.minecraft.world.Container)
                    helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
                int pulled = 0;
                for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                    if (hopper.getItem(slot).is(RCItems.SLAG.get())) {
                        pulled += hopper.getItem(slot).getCount();
                    }
                }
                helper.assertTrue(pulled > 0,
                    "a hopper under the Cupola pulled no slag, so the only way out of the slag slot is "
                        + "the GUI and an automated machine backs up on a byproduct nobody asked for");
            });
        });

        // AND IT HAS SOMEWHERE TO GO. An item that accumulates with no sink is clutter, and slag
        // accumulates whether the player wants it or not - so the disposal route is part of shipping it
        // rather than a later nicety. Obsidian is the payoff and comes with the Slag Furnace; this is
        // the route for the pile you are not going to vitrify.
        RCGameTests.test("slag_can_be_disposed_of", 20, helper -> {
            var level = helper.getLevel();
            boolean sink = level.recipeAccess().recipeMap().values().stream().anyMatch(holder -> {
                var id = holder.id().identifier();
                if (!Recompile.MOD_ID.equals(id.getNamespace())) {
                    return false;
                }
                try (var in = CupolaFurnaceTests.class.getResourceAsStream(
                        "/data/" + id.getNamespace() + "/recipe/" + id.getPath() + ".json")) {
                    return in != null
                        && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .contains("\"recompile:slag\"");
                } catch (java.io.IOException failed) {
                    return false;
                }
            });
            helper.assertTrue(sink,
                "nothing in the mod consumes slag, so the Cupola now hands the player a lump every few "
                    + "smelts that can only be thrown on the floor");
            helper.succeed();
        });

        // THE GATE, as an assertion rather than a comment (#91).
        //
        // Iron is Cupola-only because both its recipes are minecraft:blasting: a vanilla furnace cannot
        // run one, and a vanilla blast furnace costs five iron ingots, which is circular. The previous
        // gate was "no other furnace exists", which was an absence of materials rather than a property of
        // a machine - and it quietly stopped being true when the Tree Nursery shipped wood, because wood
        // makes a pickaxe, a pickaxe makes cobbled deepslate, and that crafts a furnace. Nothing failed.
        //
        // This walks every smelting recipe in the game and asserts none of them produces iron. Adding one
        // back as `minecraft:smelting` re-opens the gate, and this is what will say so.
        // JEI must describe the machine the game actually runs, and for this one that means the SLAG.
        // Vanilla's blasting display has a single result slot, so a player reading "Blasting" is told
        // the Cupola makes a gold nugget and nothing else - true, and materially incomplete, because
        // the byproduct is the sole input to the Separator, the Pulverizer and the Slag Furnace. The
        // whole obsidian chain hangs off a thing the shared category structurally cannot draw (#243).
        //
        // CupolaData parses the bundled JSON rather than the recipe manager (recipes are not
        // client-synced in 26.1), which means it can quietly disagree with the game - and it fails
        // SILENTLY, because a category with no rows registers without error. Same failure TeardownData
        // shipped once: it named its recipes in a constant, a third shipped, and every viewer denied
        // it existed.
        RCGameTests.test("jei_sees_every_cupola_blasting_recipe", 20, helper -> {
            var rows = com.flatts.recompile.compat.CupolaData.all();
            helper.assertTrue(!rows.isEmpty(), "the Cupola category reads no recipes at all");

            // Only THIS MOD's blasting recipes, because that is what CupolaData reads. Vanilla ships
            // its own (iron ore and friends) and they are unreachable here for want of ore; counting
            // them would make this permanently red for a reason that is not a defect.
            int mine = 0;
            for (RecipeHolder<net.minecraft.world.item.crafting.BlastingRecipe> holder
                    : helper.getLevel().recipeAccess().recipeMap().byType(RecipeType.BLASTING)) {
                if (!Recompile.MOD_ID.equals(holder.id().identifier().getNamespace())) {
                    continue;
                }
                mine++;
                boolean matched = false;
                for (var row : rows) {
                    if (holder.value().matches(new SingleRecipeInput(row.input()), helper.getLevel())) {
                        matched = true;
                    }
                }
                helper.assertTrue(matched, "JEI's Cupola category does not show " + holder.id());
            }
            helper.assertTrue(mine > 0 && rows.size() == mine,
                "the mod ships " + mine + " blasting recipes and the Cupola category reads "
                    + rows.size());

            // The OUTPUT too. A result is an ItemStackTemplate and spells its field `id` where an
            // ingredient is a bare string - two shapes in one file, and a parser that reads only one
            // yields a row whose output box draws empty.
            for (var row : rows) {
                helper.assertTrue(!row.output().isEmpty(),
                    "a Cupola row parsed its input but not its result, so JEI would draw an empty "
                        + "output box for " + row.input());
            }

            // And the rate the category prints has to be the rate the machine keeps. It is config,
            // not a literal 8 - a retuned pack must not be contradicted by its own viewer.
            helper.assertTrue(com.flatts.recompile.compat.CupolaData.smeltsPerSlag()
                    == RCConfig.CUPOLA_SMELTS_PER_SLAG.get(),
                "the category prints a different slag rate than the machine uses");
            helper.succeed();
        });

        RCGameTests.test("no_smelting_recipe_turns_a_mod_item_into_iron", 20, helper -> {
            // Scoped to THIS MOD's items as inputs, deliberately. Vanilla ships four smelting recipes
            // that make iron, and all four are unreachable here: three need iron ore or raw iron, which
            // this world has none of, and iron_nugget_from_smelting melts iron gear, which needs iron
            // first. Flagging those would make the test permanently red and teach everyone to ignore it.
            // What matters is whether anything a player can actually obtain smelts into iron.
            List<String> leaks = new ArrayList<>();
            int checked = 0;
            var recipeMap = helper.getLevel().getServer().getRecipeManager().recipeMap();
            for (Item item : BuiltInRegistries.ITEM) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (!Recompile.MOD_ID.equals(id.getNamespace())) {
                    continue;
                }
                checked++;
                for (RecipeHolder<SmeltingRecipe> holder : recipeMap.getRecipesFor(
                        RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(item)),
                        helper.getLevel()).toList()) {
                    ItemStack out = holder.value().assemble(new SingleRecipeInput(new ItemStack(item)));
                    if (out.is(Items.IRON_INGOT) || out.is(Items.IRON_NUGGET)) {
                        leaks.add(id + " -> " + out + " via " + holder.id());
                    }
                }
            }
            helper.assertTrue(checked > 50,
                "only " + checked + " mod items were swept - discovery is broken, so this would pass "
                    + "against any leak");
            helper.assertTrue(leaks.isEmpty(),
                "these smelt into iron in ANY vanilla furnace, which opens the iron gate: " + leaks);
            helper.succeed();
        });

        // THE SAME GATE, FOR GOLD (#120), and it is a separate test rather than a widened one because
        // the two gates fail for different reasons and a shared failure message would name the wrong
        // one.
        //
        // Gold is blast-only by design: a vanilla furnace cannot run a blasting recipe at all, and a
        // vanilla blast furnace costs five iron ingots, so gold sits behind iron. That is a property of
        // the RECIPE TYPE, and the chain test asserts the opposite direction - it checks the two stages
        // connect, and would still pass if someone added circuit_powder -> gold_nugget as
        // minecraft:smelting, which would open gold in a furnace made of stone with nothing failing.
        // That is exactly the #91 failure mode the iron sweep above was written for.
        RCGameTests.test("no_smelting_recipe_turns_a_mod_item_into_gold", 20, helper -> {
            List<String> leaks = new ArrayList<>();
            int checked = 0;
            var recipeMap = helper.getLevel().getServer().getRecipeManager().recipeMap();
            for (Item item : BuiltInRegistries.ITEM) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (!Recompile.MOD_ID.equals(id.getNamespace())) {
                    continue;
                }
                checked++;
                for (RecipeHolder<SmeltingRecipe> holder : recipeMap.getRecipesFor(
                        RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(item)),
                        helper.getLevel()).toList()) {
                    ItemStack out = holder.value().assemble(new SingleRecipeInput(new ItemStack(item)));
                    if (out.is(Items.GOLD_INGOT) || out.is(Items.GOLD_NUGGET)) {
                        leaks.add(id + " -> " + out + " via " + holder.id());
                    }
                }
            }
            helper.assertTrue(checked > 50,
                "only " + checked + " mod items were swept - discovery is broken, so this would pass "
                    + "against any leak");
            helper.assertTrue(leaks.isEmpty(),
                "these smelt into gold in ANY vanilla furnace, which skips the Cupola and the iron "
                    + "gate behind it: " + leaks);
            helper.succeed();
        });

        // THE CUPOLA IS A SCRAP NETWORK MEMBER AND MUST ACT LIKE ONE. It has carried
        // #recompile:scrap_connectable since it shipped and did nothing with it: being in the tag made
        // it a stepping stone for everything else routing through, while its own iron sat in the result
        // slot waiting to be picked up by hand. The Burn Barrel two blocks away has drained into the
        // network since P2.10, so a player who wires a barrel to the Cupola reasonably expects the same.
        RCGameTests.test("cupola_pushes_its_output_into_connected_storage", 40, helper -> {
            BlockPos cupolaPos = new BlockPos(1, 1, 1);
            BlockPos barrelPos = new BlockPos(2, 1, 1);
            helper.setBlock(cupolaPos, RCBlocks.CUPOLA_FURNACE.get());
            helper.setBlock(barrelPos, RCBlocks.SCRAP_BARREL.get());
            var cupola = (com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(cupolaPos));
            var barrel = (net.minecraft.world.Container)
                helper.getLevel().getBlockEntity(helper.absolutePos(barrelPos));

            cupola.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
            cupola.drainOutput(helper.getLevel());

            helper.assertTrue(cupola.getItem(2).isEmpty(),
                "finished metal must leave the result slot when storage is connected");
            int inBarrel = 0;
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                if (barrel.getItem(slot).is(Items.IRON_INGOT)) {
                    inBarrel += barrel.getItem(slot).getCount();
                }
            }
            helper.assertTrue(inBarrel == 3,
                "and land in the connected barrel; found " + inBarrel + " ingots there");
            helper.succeed();
        });

        // Standalone, it must NOT vanish. A machine that eats its own output when nothing is wired to
        // it would be far worse than one that never routed at all.
        RCGameTests.test("a_lone_cupola_keeps_its_output", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.CUPOLA_FURNACE.get());
            var cupola = (com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(pos));

            cupola.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
            cupola.drainOutput(helper.getLevel());
            helper.assertTrue(cupola.getItem(2).getCount() == 3,
                "with no storage connected the output stays put, to be taken through the GUI");
            helper.succeed();
        });

        // You can get it back. It costs a Burn Barrel to build, so a Cupola that cannot be picked up is a
        // machine you lose forever by placing it in the wrong spot. requiresCorrectToolForDrops reads as
        // the obvious call for a stone machine and is exactly the trap here - the block is named in no
        // mineable tag, so "correct tool" would mean no tool exists. Asserted through a real
        // drop-yielding break.
        RCGameTests.test("cupola_can_be_picked_back_up", 40, helper -> {
            BlockPos pos = new BlockPos(5, 1, 3);
            helper.setBlock(pos, RCBlocks.CUPOLA_FURNACE.get());
            BlockPos abs = helper.absolutePos(pos);
            // destroyBlock on the LEVEL, not the helper - the helper's passes dropBlock=false.
            helper.getLevel().destroyBlock(abs, true);
            helper.succeedWhen(() -> helper.assertItemEntityPresent(RCItems.CUPOLA_FURNACE.get(), pos, 2.0));
        });

        // The other half of the upgrade: this one automates. The Burn Barrel exposes no slots to any face
        // on purpose and a hopper under it pulls nothing; a hopper under this one must pull, or the
        // machine tier is only a metal tier.
        RCGameTests.test("cupola_allows_a_hopper", 60, helper -> {
            BlockPos pos = new BlockPos(1, 2, 3);
            CupolaFurnaceBlockEntity cupola = place(helper, pos);
            if (cupola == null) {
                return;
            }
            helper.setBlock(pos.below(), Blocks.HOPPER);
            cupola.setItem(2, new ItemStack(Items.IRON_INGOT));
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(cupola.getItem(2).isEmpty(),
                    "a hopper must pull from the cupola - automation is the upgrade's other reward, got "
                        + cupola.getItem(2));
                helper.succeed();
            });
        });

        // The exact inverse of burn_barrel_refuses_pipe_insertion, through the same capability a pipe
        // mod uses. The pair is what makes the upgrade real: if both refused, the Cupola would buy only
        // a metal tier; if both accepted, the barrel's manual-only rule would be decoration.
        RCGameTests.test("cupola_accepts_pipe_insertion", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.CUPOLA_FURNACE.get());
            BlockPos abs = helper.absolutePos(pos);

            int total = 0;
            for (Direction side : Direction.values()) {
                ResourceHandler<ItemResource> handler = helper.getLevel()
                    .getCapability(Capabilities.Item.BLOCK, abs, side);
                if (handler == null) {
                    continue;
                }
                try (Transaction tx = Transaction.openRoot()) {
                    total += handler.insert(ItemResource.of(RCItems.STEEL_OFFCUT.get()), 1, tx);
                    tx.commit();
                }
            }
            helper.assertTrue(total > 0, "the Cupola must accept automation on at least one face");
            helper.succeed();
        });
    }
}
