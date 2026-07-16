package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
 *   block            hand (avg)   tarp   ratio
 *   garbage_block       2.5         6     2.4x
 *   trash_bag           2.0         4     2.0x
 *   compacted_bale      3.5         8     2.3x
 * </pre>
 *
 * Hand-sorting used to average 4.9/2.5/6.9 against a tarp that gave 5/2/12, so hand was
 * as good as the tarp for a garbage block and strictly better for a bag - the station
 * was a downgrade, and the early game handed out materials far too fast. Keep hand
 * visibly worse: it is the always-available option and needs no station and no hauling.
 * Automation must clear the tarp by a similar margin when it lands.
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

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (RCConfig.GARBAGE_GRAVITY_ENABLED.get()) {
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

    // ---- read-only accessors for the Jade tooltip (compat.jade), which lives in
    // another package and cannot see the protected sort internals. ----

    /** The tool this variant is sorted with, or null for bare hand. */
    @Nullable
    public Item sortTool() {
        return requiredTool();
    }

    /** Pulls taken so far, from the {@code sorted} blockstate (0 .. maxPulls-1). */
    public int sortedCount(BlockState state) {
        return state.getValue(sortedProperty());
    }

    /** Pulls at which the block is certain to crumble (the progress denominator). */
    public int sortCrumbleAt() {
        return maxPulls();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(sortedProperty());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (requiredTool() == null) {
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
                Component.translatable("message.recompile.needs_tool",
                    Component.translatable(requiredTool().getDescriptionId())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        Item tool = requiredTool();
        if (tool != null && stack.is(tool)) {
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
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.6F, 0.9F);

        int pulls = state.getValue(sortedProperty()) + 1;
        if (shouldCrumble(pulls, level.getRandom())) {
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

    /** Single entry point for interactions and gametests: sort the sortable block at pos. */
    public static boolean sortOnce(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof SortableBlock block) {
            return block.sort(level, pos);
        }
        return false;
    }
}
