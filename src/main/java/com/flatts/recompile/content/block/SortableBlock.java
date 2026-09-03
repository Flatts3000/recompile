package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.item.GarbageVacuumItem;
import com.flatts.recompile.event.RCAnalytics;
import com.flatts.recompile.registry.RCEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared "pick-through" behaviour for the sortable garbage variants (design P0.4 /
 * P1.1): right-click a placed block to pull one drop from its region table; after a
 * few pulls the block crumbles. Sort progress lives in a blockstate {@code sorted}
 * property (a palette flyweight - the garbage blocks are the mod's bulk block, so no
 * per-instance BlockEntity).
 *
 * <p>Each concrete variant supplies its own pull table, crumble range, and the tool
 * it takes to open (null = bare hand). Subclasses provide their own {@code sorted}
 * property so the persisted range matches how many pulls that variant allows.
 *
 * <p><b>Recovery tiers.</b> The pull table says what is <em>in</em> a block; the method
 * says how much of it you get out. The ladder is hand &lt;&lt; Sorting Tarp &lt;&lt;
 * automation (a later phase), and the lever is rolls per block, so a table edit retunes
 * every tier at once. Expected pulls are E[crumble] over {@link #shouldCrumble}, not
 * {@link #maxPulls}, which is why these look low:
 *
 * <pre>
 *   block            hand (avg)   machine   ratio
 *   garbage_block       2.5           6      2.4x
 *   trash_bag           2.0           4      2.0x
 *   compacted_bale      3.5           8      2.3x
 * </pre>
 *
 * Hand-sorting used to average 4.9/2.5/6.9 against a tarp that gave 5/2/12, so hand was
 * as good as the tarp for a garbage block and strictly better for a bag - the station
 * was a downgrade, and the early game handed out materials far too fast. Keep hand
 * visibly worse: it is the always-available option and needs no station and no hauling.
 *
 * <p><b>The ladder is two rungs of YIELD, not three</b> (owner, 2026-08-03, reversing
 * "automation must clear the tarp by a similar margin"). The Trommel sorts at exactly
 * the tarp's rate - both read {@link #sortRolls} - and its reward is that it runs
 * unattended. A third 2.4x step would have flooded the late game with scrap at the point
 * the player has least use for more of it, and would have made the tarp's tuning
 * pointless rather than merely superseded.
 *
 * <p><b>{@code minPulls} is a floor, and it is load-bearing.</b> It is not a tuning knob:
 * it is the guarantee that a block never comes apart in one touch. Dropping it to 1 made
 * a third of garbage blocks and half of all bags vanish on the first click, which reads
 * as an instant break and let bare hands strip ground faster than any tool - no cooldown
 * fixes that, because the block is already gone. Keep {@code minPulls >= 2}.
 *
 * <p><b>Pulls are yield AND time; the two cannot be tuned apart.</b> Fewer pulls means
 * less yield but a faster crumble, so cutting pulls to slow the economy silently speeds
 * up clearing. Yield is traded against the tarp's rolls, never against the floor. Each
 * block has exactly one tool - garbage digs with the junk shovel, a bale is cut with the
 * knife, an appliance is pried - and no bare-hand action may out-clear a tool. Re-check
 * these ticks (20 = 1s) against {@code minecraft:mineable/shovel} before touching a range:
 *
 * <pre>
 *   block            right-click   dig
 *   garbage_block       20.0         5   shovel-tagged, 4.0x faster
 *   trash_bag           16.0         6   no shovel bonus by design
 *   compacted_bale      28.0        27   knife's job, not the shovel's
 * </pre>
 *
 * <p>Garbage obeys gravity (design P0.3): it is a {@link FallingBlock} so mounds slump
 * when quarried. Config-gated by {@code world.garbageGravityEnabled} - the scheduled
 * fall tick only drops the block when gravity is on.
 */
public abstract class SortableBlock extends FallingBlock {

    /**
     * Ticks between pulls from one player's hands. Matches the Sorting Tarp's sift
     * cadence, so the whole mod picks through trash at one rhythm.
     *
     * <p>Without this, holding right-click pulled every 4 ticks (the client's use
     * delay), which tore a garbage block apart in ~8 ticks - faster than the 18 ticks
     * of digging it out by hand and not far off the shovel's 5, so hands rivalled
     * tools at clearing ground. It also has to be a multiple of the 4-tick use delay,
     * or click-spam would outpace holding and reward exactly the RSI-farming the
     * design rules out.
     *
     * <p>Keyed through {@link net.minecraft.world.item.ItemCooldowns}, whose only
     * public query is by {@link ItemStack} - so a bare-hand pull keys on the empty
     * stack, whose cooldown group is {@code minecraft:air}. Vanilla never puts a
     * cooldown on air, and keying on the *empty* stack rather than whatever is held
     * means a player cannot dodge the gate by swapping items between pulls.
     */
    public static final int PULL_COOLDOWN_TICKS = 8;

    protected SortableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(sortedProperty(), 0));
    }

    /**
     * Whether this block actually falls.
     *
     * <p><b>Two gates, and they answer different questions.</b> {@code GARBAGE_GRAVITY_ENABLED} is the
     * player's, a global "do piles slump"; this one is the block's, and it is not tunable because it is
     * not a preference. A sortable that IS the terrain - the Nether's bulk fill, floor to ceiling -
     * cannot fall, because the first tunnel would bring the dimension down on top of the player and
     * keep going. A pile on a plain can and should.
     *
     * <p>The class still extends {@code FallingBlock} either way. Overriding this leaves the falling
     * machinery present and never entered: {@code isFree}, {@code onLand} and {@code getDustColor} only
     * run during an actual fall, and the ticks {@code onPlace} schedules simply no-op. Splitting the
     * hierarchy instead would mean a second copy of the whole pull mechanic to avoid inheriting one
     * method, which is a worse trade.
     */
    protected boolean obeysGravity() {
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (obeysGravity() && RCConfig.GARBAGE_GRAVITY_ENABLED.get()) {
            super.tick(state, level, pos, random);
        }
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getMapColor(level, pos).col;
    }

    /** The {@code sorted} progress property (0 .. maxPulls-1), defined per variant. */
    protected abstract IntegerProperty sortedProperty();

    /** The region pull table this variant draws from. */
    protected abstract ResourceKey<LootTable> pullTable();

    /** Crumble window: never before minPulls, certain at maxPulls, rising chance between. */
    protected abstract int minPulls();

    protected abstract int maxPulls();

    /** The item required to sort this variant, or null to sort with an empty hand. */
    @Nullable
    protected abstract Item requiredTool();

    /**
     * A tool FAMILY, for when one item cannot express the gate.
     *
     * <p><b>Why this exists.</b> {@link #requiredTool()} names a single {@code Item}, which is exactly
     * right for the Prybar and the Scrap Knife - there is one of each. There are <b>four</b>
     * sledgehammers (copper, iron, diamond, netherite), so naming one would silently exclude three:
     * a player holding a diamond sledgehammer would be told to fetch a copper one.
     *
     * <p><b>A variant with a family still declares a representative in {@link #requiredTool()}</b>, and
     * that is deliberate rather than redundant. Viewers render an ITEM - Jade's tool hint takes
     * {@link #sortTool()} and draws it - so a family with no representative would render as "sort by
     * hand", which is wrong in the one direction that matters. The representative is the lowest tier,
     * because that is the one a player is told to go and get.
     *
     * <p>The in-game nudge uses the family's own name where there is one, so it says "Sledgehammer"
     * rather than naming a tier the gate does not actually require.
     */
    @Nullable
    protected TagKey<Item> requiredToolFamily() {
        return null;
    }

    /** Whether this variant needs anything at all in hand. */
    protected final boolean requiresTool() {
        return requiredTool() != null || requiredToolFamily() != null;
    }

    /** Whether a held stack opens this variant. */
    protected final boolean isCorrectTool(ItemStack stack) {
        Item single = requiredTool();
        if (single != null && stack.is(single)) {
            return true;
        }
        TagKey<Item> family = requiredToolFamily();
        return family != null && stack.is(family);
    }

    /** What the "you need a tool" nudge should name: the family if there is one, else the item. */
    protected final Component toolName() {
        TagKey<Item> family = requiredToolFamily();
        if (family != null) {
            // tool.<ns>.<path>, which RCHarvestGate.toolKey already uses for the dig nudge on these
            // same blocks. A second key for the same word off the same tag path would drift in
            // translation, and review of #286 caught the duplicate before it could.
            return Component.translatable(
                "tool." + family.location().getNamespace() + "." + family.location().getPath());
        }
        return Component.translatable(requiredTool().getDescriptionId());
    }

    // ---- read-only accessors for the Jade tooltip (compat.jade), which lives in
    // another package and cannot see the protected sort internals. ----

    /** The tool this variant is sorted with, or null for bare hand. */
    @Nullable
    public Item sortTool() {
        return requiredTool();
    }

    /**
     * The tool FAMILY this variant accepts, or null if it names a single item (or none).
     *
     * <p>Public for the same reason {@link #sortTool()} is: the compat and test packages cannot see
     * the protected internals. A viewer should keep drawing {@code sortTool()}, which is the
     * representative - this is here so a check can ask what the gate actually is.
     */
    @Nullable
    public TagKey<Item> sortToolFamily() {
        return requiredToolFamily();
    }

    /** The family's display name, for viewers. Public mirror of {@link #toolName()}. */
    public Component toolFamilyName() {
        return toolName();
    }

    /** Whether a held stack opens this variant. Public mirror of the protected gate, for tests. */
    public boolean acceptsTool(ItemStack stack) {
        return isCorrectTool(stack);
    }

    /** Pulls taken so far, from the {@code sorted} blockstate (0 .. maxPulls-1). */
    public int sortedCount(BlockState state) {
        return state.getValue(sortedProperty());
    }

    /** Pulls at which the block is certain to crumble (the progress denominator). */
    public int sortCrumbleAt() {
        return maxPulls();
    }

    // ---- the machine rung of the recovery ladder, shared by every machine that sorts ----

    /**
     * How many material rolls one of this item yields to a <b>machine</b>. 0 = not a sorting input.
     *
     * <p><b>One function, every machine.</b> The Sorting Tarp and the Trommel both call this, which
     * is what makes "the Trommel yields exactly what the tarp yields" a structural fact rather than
     * two numbers somebody has to keep in sync. The Trommel's reward for existing is that it runs
     * unattended, not that it produces more (owner, 2026-08-03) - which reverses the earlier plan for
     * automation to clear the tarp by another 2.0-2.4x. A third multiplying step would have flooded the
     * late game with scrap at exactly the point the player has least use for it.
     *
     * <p>These are the middle rung of the recovery ladder documented on this class: each must stay
     * clearly above what the same block gives to bare hands. Hand averages are E[crumble] over
     * {@link #shouldCrumble}, which is NOT {@code (min+max)/2} once a window is wider than one step:
     *
     * <pre>
     *   block              window   hand (avg)   machine   ratio
     *   garbage_block        2-3        2.50         6      2.40x
     *   trash_bag            2-2        2.00         4      2.00x
     *   compacted_bale       3-4        3.50         8      2.29x
     *   stone_rubble         2-4        2.89         7      2.42x
     *   mechanical_waste     3-4        3.50         8      2.29x
     *   techno_organic_waste 3-5        3.89         9      2.31x
     *   slag_rubble          2-4        2.89         7      2.42x
     * </pre>
     *
     * <p>Mechanical Waste is derived rather than picked: it shares the bale's 3-4 window, so it shares
     * the bale's hand average, so it takes the bale's number. Stone Rubble's 7 was chosen the same way,
     * to land inside the band rather than by eye.
     *
     * <p><b>The two Nether blocks follow the same two rules.</b> Slag Rubble shares Stone Rubble's 2-4
     * window exactly, so it takes Stone Rubble's 7 - the Mechanical Waste move. Techno-Organic Waste has
     * a window nothing else has (3-5, hand 3.89), so its number was computed against the band rather
     * than copied: 9 gives 2.31x, alongside the bale and Mechanical Waste, where 8 would have been
     * 2.06x and sat at the bottom on its own. The curve those averages come from is
     * {@code shouldCrumble} below, and reproducing the five shipped numbers from it is what makes these
     * two derived rather than guessed.
     */
    public static int sortRolls(Item item) {
        if (item == RCItems.GARBAGE_BLOCK.get().asItem()) {
            return 6;
        }
        if (item == RCItems.TRASH_BAG.get().asItem()) {
            return 4;
        }
        if (item == RCItems.COMPACTED_BALE.get().asItem()) {
            return 8;
        }
        if (item == RCItems.STONE_RUBBLE.get().asItem()) {
            return 7;
        }
        if (item == RCItems.MECHANICAL_WASTE.get().asItem()) {
            return 8;
        }
        if (item == RCItems.TECHNO_ORGANIC_WASTE.get().asItem()) {
            return 9;
        }
        if (item == RCItems.SLAG_RUBBLE.get().asItem()) {
            return 7;
        }
        // The radioactive dump (#285). Same standard as the rest: land inside the 2.0-2.4x band
        // against the block's simulated hand average, rather than picking a number by eye.
        //
        // Tailings run a 3-6 window - wider than anything else here, because a spoil heap is bulk.
        // Over shouldCrumble that is 0.25/0.375/0.28125/0.09375 across pulls 3..6, so the hand
        // average is 4.219 and 10 gives 2.37x. (Stated as 4.4 and 2.27x when written; the numbers
        // were wrong and review of #286 caught them. The same formula reproduces stone rubble's
        // documented 2.89, which is what makes it checkable.)
        //
        // The drum shares Stone Rubble's 2-4 window and therefore its 7.
        if (item == RCItems.MILL_TAILINGS.get().asItem()) {
            return 10;
        }
        if (item == RCItems.WASTE_DRUM.get().asItem()) {
            return 7;
        }
        return 0;
    }

    /**
     * The pull stream an item's block yields, or null if it is not a sortable block at all.
     *
     * <p><b>Asked of the block, not looked up in a list.</b> This replaced a hand-written item -> table
     * mapping that had to be extended for every new variant; deriving it means a new {@code
     * SortableBlock} works in every machine the day it is registered. A static may read another
     * instance's protected {@link #pullTable()} because it lives in the same class.
     */
    @Nullable
    public static ResourceKey<LootTable> pullTableFor(Item item) {
        return pullTableOf(Block.byItem(item));
    }

    /** The same question asked of a block, for callers that have one in the world already. */
    @Nullable
    public static ResourceKey<LootTable> pullTableOf(Block block) {
        return block instanceof SortableBlock sortable ? sortable.pullTable() : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(sortedProperty());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!requiresTool()) {
            if (!takePull(player, ItemStack.EMPTY)) {
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel serverLevel) {
                sort(serverLevel, pos);
            }
            return InteractionResult.SUCCESS;
        }
        // Needs a tool: nudge the player, don't consume the block.
        if (!level.isClientSide()) {
            player.sendSystemMessage(
                Component.translatable("message.recompile.needs_tool", toolName()));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Whether a held item takes this pile's right-click away from it.
     *
     * <p><b>Vanilla resolves a right-click BLOCK-FIRST, then the item.</b> {@code
     * ServerPlayerGameMode.useItemOn} asks the block, and a {@code TRY_WITH_EMPTY_HAND} answer sends
     * it straight to {@link #useWithoutItem} - the hand pull - before the item in hand is ever
     * consulted. So without this the CROSSHAIR decides: point at the pile you mean to vacuum and it
     * hand-sorts instead, and the Garbage Vacuum only works when aimed at nothing, which is not where
     * anyone stands. Reported on the tool's first playtest, and it read as the vacuum being broken
     * rather than as the block winning.
     *
     * <p>Returning {@code PASS} rather than {@code TRY_WITH_EMPTY_HAND} is the whole fix: it is the
     * one answer that is neither consumed nor routed to the empty-hand path, so the chain falls
     * through to the item.
     *
     * <p>Scoped to a tool whose entire purpose is these blocks. Bulky Waste deliberately still wins
     * its own right-click while a vacuum is held, because the vacuum refuses it anyway and the
     * "you need a Prybar" nudge is the useful answer there; a hold sweeping across one does not
     * stutter, since only the opening click resolves against a block at all.
     */
    private static boolean itemOverridesSorting(ItemStack stack) {
        return stack.getItem() instanceof GarbageVacuumItem;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (itemOverridesSorting(stack)) {
            return InteractionResult.PASS;
        }
        if (isCorrectTool(stack)) {
            if (!takePull(player, stack)) {
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel serverLevel) {
                sort(serverLevel, pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Claim this player's next pull, or refuse if they are still on cooldown.
     * Runs on both sides, matching the Sorting Tarp: the client gate keeps it from
     * spamming use packets the server would only drop.
     */
    private static boolean takePull(Player player, ItemStack key) {
        if (player.getCooldowns().isOnCooldown(key)) {
            return false;
        }
        player.getCooldowns().addCooldown(key, PULL_COOLDOWN_TICKS);
        return true;
    }

    /** Pull once: roll this variant's table, drop it, advance progress, crumble if spent. */
    public boolean sort(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SortableBlock)) {
            return false;
        }
        SoundType sound = state.getSoundType();

        // A roach instead of a pull (#78, spec docs/roach_spec.md). Deliberately placed BEFORE the loot
        // roll and returning early: the roach replaces the item rather than accompanying it, so a
        // disturbed pull costs you the material as well as the fight. It also does not advance the
        // sorted count, so the block is not consumed by an encounter - you can try again.
        if (releaseRoach(level, pos)) {
            RCAnalytics.roach(state.getBlock());
            return false;
        }

        LootTable table = level.getServer().reloadableRegistries().getLootTable(pullTable());
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .create(LootContextParamSets.CHEST);
        List<ItemStack> pulled = table.getRandomItems(params);
        for (ItemStack drop : pulled) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
        // The one place a pull BY HAND can happen - not the one place a pull can happen. This comment
        // claimed the latter and the claim went straight into the analytics' own commit message, so
        // the first real playtest recorded 136 blocks broken and zero pulls: the player mined the
        // mound and sifted it at a Sorting Tarp, which rolls the same table from its own code. The
        // Tarp and the Trommel log their own (RCAnalytics.sifted) and
        // RollSitesAreInstrumentedTest fails the build if a new roll site records nothing.
        RCAnalytics.pull(level, state.getBlock(), pulled);
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.6F, 0.9F);

        int pulls = state.getValue(sortedProperty()) + 1;
        if (shouldCrumble(pulls, level.getRandom())) {
            RCAnalytics.crumble(state.getBlock());
            level.destroyBlock(pos, false);
            level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
            return true;
        }
        level.setBlock(pos, state.setValue(sortedProperty(), pulls), Block.UPDATE_ALL);
        return false;
    }

    private boolean shouldCrumble(int pulls, RandomSource random) {
        if (pulls >= maxPulls()) {
            return true;
        }
        if (pulls < minPulls()) {
            return false;
        }
        float chance = (float) (pulls - (minPulls() - 1)) / (maxPulls() - (minPulls() - 1));
        return random.nextFloat() < chance;
    }

    /**
     * Roll for a roach, and release one if it comes up.
     *
     * <p>Split out and public so a GameTest can drive it directly rather than rolling until it
     * fires - the {@code sortOnce} convention. Returns whether the pull was interrupted.
     *
     * <p><b>Only garbage, not every sortable.</b> {@link #harboursRoaches} is false by default, so the
     * demolition yard's rubble stays roach-free: the yard already has four hostile spawns and this
     * mechanic is about the starting biome having one thing that reacts to being disturbed.
     */
    public boolean releaseRoach(ServerLevel level, BlockPos pos) {
        if (!harboursRoaches() || !RCConfig.ROACHES_ENABLED.get()) {
            return false;
        }
        if (level.getRandom().nextInt(RCConfig.ROACH_CHANCE_DENOMINATOR.get()) != 0) {
            return false;
        }
        return spawnRoach(level, pos);
    }

    /** Place exactly one roach on top of the block being disturbed. Public for tests, the same reason sortTool and sortedCount are. */
    public static boolean spawnRoach(ServerLevel level, BlockPos pos) {
        Entity roach = RCEntities.ROACH.get().spawn(level, pos.above(), EntitySpawnReason.TRIGGERED);
        if (roach == null) {
            return false;   // no room; the pull carries on as normal rather than being eaten
        }
        level.playSound(null, pos, SoundEvents.SILVERFISH_AMBIENT, SoundSource.BLOCKS, 0.7F, 1.4F);
        return true;
    }

    /**
     * Whether this variant can hide a roach. Only household garbage does.
     *
     * <p>Overridden rather than assumed so a new sortable has to make the choice deliberately - the
     * default is no, because the alternative is every future pick-through block quietly becoming a
     * spawner.
     */
    public boolean harboursRoaches() {
        return false;
    }

    /** Single entry point for interactions and gametests: sort the sortable block at pos. */
    public static boolean sortOnce(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof SortableBlock block) {
            return block.sort(level, pos);
        }
        return false;
    }
}
