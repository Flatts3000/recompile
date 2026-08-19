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

    /** Loose slag items around the plot - where a rake with nothing wired up puts them. */
    private static int slagNear(net.minecraft.gametest.framework.GameTestHelper helper) {
        int total = 0;
        for (var entity : helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0, 0, 0)))
                    .inflate(8.0))) {
            if (entity.getItem().is(RCItems.SLAG.get())) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }

    private static void clearSlag(net.minecraft.gametest.framework.GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0, 0, 0)))
                    .inflate(8.0))
            .forEach(net.minecraft.world.entity.Entity::discard);
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
        RCGameTests.test("the_cupola_rakes_slag_off_every_few_smelts", 20, helper -> {
            CupolaFurnaceBlockEntity cupola = place(helper, new BlockPos(1, 1, 1));
            if (cupola == null) {
                return;
            }
            int per = RCConfig.CUPOLA_SMELTS_PER_SLAG.get();
            helper.assertTrue(per > 0, "precondition: slag is enabled, cupolaSmeltsPerSlag=" + per);

            // Drive the counter directly rather than smelting `per` times: this is asserting the
            // arithmetic of the rake, and a real cook is already covered by the tests above.
            cupola.rakeSlag(helper.getLevel(), per - 1);
            helper.assertTrue(slagNear(helper) == 0,
                "slag came off after only " + (per - 1) + " smelts, so the ratio is not being counted");
            helper.assertTrue(cupola.smeltsSinceSlag() == per - 1,
                "the running count is " + cupola.smeltsSinceSlag() + " rather than " + (per - 1));

            cupola.rakeSlag(helper.getLevel(), 1);
            helper.assertTrue(slagNear(helper) == 1,
                "the " + per + "th smelt produced " + slagNear(helper) + " slag rather than 1");
            helper.assertTrue(cupola.smeltsSinceSlag() == 0,
                "the count must reset after a rake, left at " + cupola.smeltsSinceSlag());

            // AND THE REMAINDER CARRIES. A batch of 2*per must give exactly two, not one and a lost
            // remainder - integer division that throws the leftover away is the obvious way to write
            // this and it silently eats slag on every hopper-fed run.
            clearSlag(helper);
            cupola.rakeSlag(helper.getLevel(), per * 2 + (per - 1));
            helper.assertTrue(slagNear(helper) == 2,
                "a batch of " + (per * 2 + per - 1) + " smelts gave " + slagNear(helper)
                    + " slag rather than 2");
            helper.assertTrue(cupola.smeltsSinceSlag() == per - 1,
                "the leftover " + (per - 1) + " smelts were dropped rather than carried, count is "
                    + cupola.smeltsSinceSlag());
            clearSlag(helper);
            helper.succeed();
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
