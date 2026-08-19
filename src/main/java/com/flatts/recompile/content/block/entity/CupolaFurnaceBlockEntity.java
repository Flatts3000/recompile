package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Cupola Furnace (#50): the world's second smelter, and the machine that makes iron reachable.
 *
 * <p>A <b>cupola</b> is a real thing - a coke-fired shaft furnace whose entire job is remelting scrap iron
 * and steel. It is non-electric and predates the electric era; EAFs and induction furnaces are its modern
 * replacements, not the other way round. That matters here because this world's only power is solar panels,
 * so the ladder's old top rung ("induction recycler") named a machine the economy cannot run. It also means
 * this is deliberately <b>not</b> a blast furnace in the metallurgical sense: a blast furnace reduces iron
 * ORE into pig iron, which is virgin production. This world has no ore. It remelts what was already made.
 *
 * <p>Mechanically it is a {@link RecipeType#BLASTING} machine, not a smelting one, and <b>that is the
 * gate.</b> A vanilla furnace cannot run a blasting recipe at all, and a vanilla blast furnace costs five
 * iron ingots - circular, so unreachable before iron. Both iron recipes (Steel Offcut -> ingot, rebar ->
 * nugget) are {@code minecraft:blasting}, which makes this block the only thing in the world that can
 * produce iron. Nothing about the world's materials has to hold for that to be true.
 *
 * <p><b>This replaced a gate that had already failed silently</b> (#91). The rule used to be "iron recipes
 * are ordinary smelting, and they are Cupola-only because the Burn Barrel refuses them and no other furnace
 * exists". The second clause stopped being true when the Tree Nursery shipped: wood makes a wooden pickaxe,
 * a wooden pickaxe drops cobbled deepslate, and cobbled deepslate is in
 * {@code #minecraft:stone_crafting_materials}. Worse, {@code rebar} is a weight-40 entry in
 * {@code household_pulls}, so a player could stockpile it on day one and smelt iron at rung 4 with no
 * demolition yard, no Cutting Torch and no Cupola. The old comment here named that exact failure mode as a
 * risk and it happened anyway, because <b>the gate was an absence of materials rather than a property of
 * the machine.</b> A recipe type is a property of the machine.
 *
 * <p>Being blast-only means this does not cook food, which is deliberate: a cupola furnace melts metal. The
 * Burn Barrel keeps refuse and food, and it is still craftable on its own, so upgrading loses nothing. Scrap
 * Metal has a blasting twin ({@code copper_from_scrap_blasting}) precisely so copper survives the upgrade -
 * the same way vanilla gives every ore both a smelting and a blasting recipe. See
 * {@code trashlands/docs/progression_gates.md}.
 *
 * <p><b>It automates, and the Burn Barrel does not.</b> The barrel exposes no slots to any face on purpose
 * (see {@link BurnBarrelBlockEntity}), with automation held back as the reward for a better machine. This is
 * that machine, so it inherits the vanilla furnace's face behaviour: hoppers, Create and pipes all work.
 * Upgrading is therefore two rewards in one - a metal tier and a machine tier.
 *
 * <p><b>One deliberate departure from furnace parity: automation cannot insert what cannot be smelted</b>
 * (owner call, 2026-07-31, spec {@code docs/automation_policy_spec.md}). Vanilla lets anything into the
 * input slot - verified, {@code cupola_refuses_unsmeltable_where_vanilla_accepts} asserts that vanilla
 * still does - which is harmless when a human is loading it and destructive when a pipe is. A pipe pushing a
 * non-smeltable fills the input slot and <b>bricks the machine</b> until someone empties it by hand;
 * found in playtest with the Cupola's own iron output looped back into its input.
 *
 * <p>The restriction is on {@link #canPlaceItemThroughFace} only, so <b>placing by hand is still exactly
 * vanilla</b>. The harm is automation-specific and so is the fix; a player who wants to park something in
 * the slot still can.
 */
public class CupolaFurnaceBlockEntity extends AbstractFurnaceBlockEntity {

    /** Vanilla's furnace layout plus one: 0 input, 1 fuel, 2 result, 3 slag. */
    private static final int RESULT_SLOT = 2;
    public static final int SLAG_SLOT = 3;
    public static final int SLOTS = 4;

    /**
     * Four slots, on a class that hard-codes three.
     *
     * <p>{@code AbstractFurnaceBlockEntity} holds its stacks in a {@code protected NonNullList items}
     * sized 3 and reads slots 0, 1 and 2 by index throughout, so widening the list is safe - the vanilla
     * cook loop never looks at slot 3 and everything it does look at is where it was. What is NOT safe
     * is vanilla's menu: {@code AbstractFurnaceMenu} calls {@code checkContainerSize(container, 3)} and
     * throws, which is why this machine has its own (see {@code CupolaFurnaceMenu}).
     */
    public CupolaFurnaceBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.CUPOLA_FURNACE.get(), worldPosition, blockState, RecipeType.BLASTING);
        this.items = net.minecraft.core.NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    }

    /**
     * Automation may only insert into the input slot what a smelting recipe actually consumes.
     *
     * <p>Fuel (slot 1) and output (slot 2) keep vanilla's behaviour untouched - the jam only ever happens
     * on the input, and narrowing the other two would break legitimate hopper setups.
     *
     * <p>Recipe lookup rather than a hand-kept allowlist: a pack adding a smelting recipe should not have
     * to be added here too, and the Burn Barrel's allowlist exists for a different reason (gating what it
     * is WILLING to smelt, not protecting it from jams).
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == 0 && this.level != null && this.level.getServer() != null) {
            boolean smeltable = this.level.getServer().getRecipeManager().recipeMap()
                .getRecipesFor(RecipeType.BLASTING, new SingleRecipeInput(stack), this.level)
                .findAny()
                .isPresent();
            if (!smeltable) {
                return false;
            }
        }
        return super.canPlaceItemThroughFace(slot, stack, side);
    }

    /**
     * Push finished metal into the connected Scrap Network.
     *
     * <p><b>The Cupola carries {@code #recompile:scrap_connectable} and did nothing with it.</b> Being
     * in the tag made it part of a cluster for everything ELSE routing through - it could be walked
     * across - while its own output sat in the result slot waiting to be collected by hand. A player
     * who has wired a barrel to it reasonably expects the iron to arrive there, and the Burn Barrel two
     * blocks away has done exactly that since P2.10.
     *
     * <p>Same shape as {@code BurnBarrelBlockEntity.drainOutput}, and for the same reason it bypasses
     * the face gate: the network is the machine's own internal mover, not an external hopper. With no
     * storage connected the output stays put and you take it through the GUI.
     */
    public void drainOutput(net.minecraft.server.level.ServerLevel level) {
        drainSlot(level, RESULT_SLOT);
        // AND THE SLAG. Without this a wired Cupola fills its slag slot and stops, which is the machine
        // working exactly as designed and reading as a machine that broke - the metal keeps leaving and
        // the furnace goes quiet anyway. A player who has wired the output has wired the output.
        drainSlot(level, SLAG_SLOT);
    }

    private void drainSlot(net.minecraft.server.level.ServerLevel level, int slot) {
        ItemStack held = getItem(slot);
        if (held.isEmpty()) {
            return;
        }
        ItemStack working = held.copy();
        com.flatts.recompile.content.block.ScrapNetwork.insertFromMember(
            level, worldPosition, working, false);
        if (working.getCount() != held.getCount()) {
            setItem(slot, working.isEmpty() ? ItemStack.EMPTY : working);
            setChanged();
        }
    }

    /**
     * Rake the slag off, once every {@code cupolaSmeltsPerSlag} smelts.
     *
     * <p><b>Slag is not a recipe output and cannot be</b> (#236). The Cupola is a
     * {@link RecipeType#BLASTING} machine because that is the iron gate, and vanilla blasting has one
     * result and no byproduct slot; {@code AbstractFurnaceBlockEntity} keeps its recipe lookup private
     * behind a static tick, so nothing can be added to the smelt itself. The tick wrapper in
     * {@code CupolaFurnaceBlock} is the only seam this machine has - the same one the network drain
     * uses, and the same one the Burn Barrel's refuse gate uses.
     *
     * <p><b>Counted, not rolled.</b> One in eight is roughly the real ratio - an arc furnace throws
     * 100-150kg of slag per tonne of steel - and counting makes it a trickle a player can plan around
     * rather than a run of luck. It also makes it exactly testable, which a chance is not.
     *
     * <p><b>It goes in the slag slot</b> (owner, 2026-08-18: a second output slot, not items on the
     * ground). If that slot is full the count simply carries: nothing is dropped and nothing is
     * deleted, and the debt pays out the moment there is room. The machine keeps smelting either way,
     * because stopping it would only freeze the burn that clears its own LIT state.
     */
    public void rakeSlag(net.minecraft.server.level.ServerLevel level, int smelted) {
        if (smelted <= 0) {
            return;   // the ticker calls this every tick; do not read config to learn nothing happened
        }
        int per = com.flatts.recompile.RCConfig.CUPOLA_SMELTS_PER_SLAG.get();
        if (per <= 0) {
            return;
        }
        smeltsSinceSlag += smelted;
        int made = smeltsSinceSlag / per;
        if (made <= 0) {
            return;
        }
        // INTO THE SLAG SLOT, and only as much as fits (owner, 2026-08-18: "the cupola furnace should
        // have a second output slot, not pop things onto the ground"). What does not fit stays on the
        // counter, so nothing is destroyed and nothing is littered - the machine simply owes you slag
        // until you take some, and pays the debt down the moment there is room for it.
        // WHATEVER IS IN THE SLOT MUST BE SLAG BEFORE ANYTHING GROWS IT. canPlaceItem shuts out pipes
        // and the menu's slot shuts out hands, but Container.setItem consults neither - a command or
        // any mod touching the Container API can seed slot 3, and without this guard the Cupola would
        // then mint free copies of whatever that is on every rake.
        ItemStack held = getItem(SLAG_SLOT);
        boolean slagThere = held.is(com.flatts.recompile.registry.RCItems.SLAG.get());
        if (!held.isEmpty() && !slagThere) {
            return;   // something else is parked there; owe the slag rather than corrupt the slot
        }
        int room = held.isEmpty()
            ? new ItemStack(com.flatts.recompile.registry.RCItems.SLAG.get()).getMaxStackSize()
            : held.getMaxStackSize() - held.getCount();
        made = Math.min(made, room);
        if (made <= 0) {
            return;
        }
        smeltsSinceSlag -= made * per;
        if (held.isEmpty()) {
            setItem(SLAG_SLOT, new ItemStack(com.flatts.recompile.registry.RCItems.SLAG.get(), made));
        } else {
            held.grow(made);
            setChanged();
        }
        setChanged();
    }

    /** How much slag the machine still owes, for the test that has to prove a full slot loses none. */
    public int slagOwed() {
        int per = com.flatts.recompile.RCConfig.CUPOLA_SMELTS_PER_SLAG.get();
        return per <= 0 ? 0 : smeltsSinceSlag / per;
    }

    /** How many smelts have gone by since the last slag came off. Survives save/load. */
    private int smeltsSinceSlag;

    /** For GameTests: the running count, so a test can assert the trickle rather than wait for it. */
    public int smeltsSinceSlag() {
        return smeltsSinceSlag;
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("smeltsSinceSlag", smeltsSinceSlag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        smeltsSinceSlag = input.getIntOr("smeltsSinceSlag", 0);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.recompile.cupola_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new com.flatts.recompile.content.menu.CupolaFurnaceMenu(
            containerId, inventory, this, this.dataAccess);
    }

    /**
     * Automation may take from the slag slot as well as the result slot.
     *
     * <p>Vanilla lets a hopper pull the result out of the bottom; slag is output too, so it leaves the
     * same way. Without this the only route out is the GUI, and an automated Cupola stalls on a byproduct
     * the player never asked for - which is the shape of the original complaint, just slower.
     */
    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] vanilla = super.getSlotsForFace(side);
        if (side != Direction.DOWN) {
            return vanilla;
        }
        int[] withSlag = java.util.Arrays.copyOf(vanilla, vanilla.length + 1);
        withSlag[vanilla.length] = SLAG_SLOT;
        return withSlag;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLAG_SLOT || super.canTakeItemThroughFace(slot, stack, side);
    }

    /**
     * Nothing may be put INTO the slag slot, by hand or by pipe.
     *
     * <p>Vanilla's {@code canPlaceItem} knows about three slots and returns <b>true</b> for anything
     * else - so simply widening the container made the slag slot insertable from the bottom face, and
     * {@code cupola_matches_vanilla_furnace} caught it: the machine accepted four items through a face
     * vanilla accepts none through.
     *
     * <p>What it costs if this is removed: {@link #rakeSlag} refuses to grow a slot holding something
     * that is not slag, so a single delivered item stops the machine paying out its slag for good and
     * the debt climbs with nothing to spend it on.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != SLAG_SLOT && super.canPlaceItem(slot, stack);
    }
}
