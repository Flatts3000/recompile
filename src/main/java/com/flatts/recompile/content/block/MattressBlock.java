package com.flatts.recompile.content.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The mattress (design P1.11): this world's bed, and the first find in the Bulky Waste
 * table. Two blocks, like a vanilla bed, because a mattress is longer than one.
 *
 * <p><b>It is not a stepping stone to a real bed - it IS the bed.</b> A vanilla bed is
 * 3 wool + {@code #minecraft:planks}, and this world has no trees, so no bed can be built
 * until wood recovery arrives mid-tier. You never craft a mattress either: the dump gives
 * you the bed.
 *
 * <p><b>Not a {@code BedBlock}, and it does not need to be.</b> Nothing on the sleep path
 * checks {@code instanceof BedBlock}, and it is deliberately not in {@code BlockTags.BEDS}
 * (that tag gates only villager and cat AI - nothing here). It carries no BlockEntity and
 * no renderer: vanilla's exist purely to hold a {@code DyeColor} for a shared model, which
 * is why vanilla's bed model has no geometry. A plain JSON model plus the default
 * {@code RenderShape.MODEL} is simpler and costs nothing per block.
 *
 * <p>Four things are load-bearing and all four fail <em>silently</em> - none of them
 * shows up in a compile, so {@code MattressTests} asserts them:
 * <ol>
 *   <li>{@link #FACING} must exist: {@code ServerPlayer.startSleepInBed} reads
 *       {@code getValue(HorizontalDirectionalBlock.FACING)} unconditionally, as does
 *       NeoForge's patched {@code LivingEntity.stopSleeping}.</li>
 *   <li>{@link #isBed} must return true, or NeoForge's patched
 *       {@code LivingEntity.checkBedExists()} ejects the sleeper on the very next tick.</li>
 *   <li>{@link #getRespawnPosition} must be overridden. Its default returns
 *       {@code Optional.empty()}, which is exactly vanilla's "no respawn block available".</li>
 *   <li>{@link #useWithoutItem} must call {@code startSleepInBed} itself - nothing does
 *       it for you.</li>
 * </ol>
 *
 * <p>Sleeping by day gives the vanilla "you can only sleep at night" message but
 * <em>still sets your spawn</em>, because the default {@code BedRule.canSetSpawn} is
 * ALWAYS. The starting biome spawns nothing, so the "not safe" gate never fires.
 */
public class MattressBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<MattressBlock> CODEC = simpleCodec(MattressBlock::new);

    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;

    /** Flat on the ground - a mattress, not a bed frame. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 5, 16);

    public MattressBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(PART, BedPart.FOOT)
            .setValue(OCCUPIED, false));
    }

    @Override
    protected MapCodec<? extends MattressBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OCCUPIED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ---------------- sleeping ----------------

    /** Replaces vanilla's {@code instanceof BedBlock}. Without this the sleeper is ejected next tick. */
    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) {
        return true;
    }

    /** Without this, respawning here reports "no respawn block available" - the default is empty. */
    @Override
    public Optional<ServerPlayer.RespawnPosAngle> getRespawnPosition(BlockState state, EntityType<?> type,
            LevelReader level, BlockPos pos, float orientation) {
        return BedBlock.findStandUpPosition(type, level, pos, state.getValue(FACING), orientation)
            .map(standUp -> ServerPlayer.RespawnPosAngle.of(standUp, pos, 0.0F));
    }

    /**
     * Right-click to sleep; <b>shift-right-click to set spawn without sleeping</b>.
     *
     * <p>The second half exists because sleeping now spends the mattress (#128). Without it, the only
     * way to put a respawn point on a Dirty Mattress was to destroy the Dirty Mattress - so a player
     * who wanted a spawn had to burn the thing that provided it, and one who wanted to keep it could
     * never respawn there. Setting spawn is not sleeping, and only one of the two wears a mattress out.
     *
     * <p>The message says the mattress is single-use, deliberately. That rule is otherwise learned by
     * losing one, and this is the moment the player is already thinking about the block.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // Sleep from the HEAD, matching vanilla: the range and obstruction checks in
        // ServerPlayer assume the pos plus the block behind it.
        BlockPos bedPos = pos;
        BlockState bedState = state;
        if (state.getValue(PART) != BedPart.HEAD) {
            bedPos = pos.relative(state.getValue(FACING));
            bedState = level.getBlockState(bedPos);
            if (!bedState.is(this)) {
                return InteractionResult.CONSUME;
            }
        }
        // Spawn without sleeping. BEFORE the occupied check, because setting a respawn point is not
        // sleeping - whether somebody else is currently asleep in it has nothing to do with whether you
        // may mark it as where you come back to. Placed after the head is resolved so the point lands
        // on the same block a night here would have used.
        if (player.isSecondaryUseActive()) {
            setSpawnHere(player, bedPos);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (bedState.getValue(OCCUPIED)) {
            player.sendOverlayMessage(Component.translatable("block.minecraft.bed.occupied"));
            return InteractionResult.SUCCESS_SERVER;
        }
        // Vanilla surfaces the problem on the action bar, not in chat - mirror it.
        player.startSleepInBed(bedPos).ifLeft(problem -> {
            if (problem.message() != null) {
                player.sendOverlayMessage(problem.message());
            }
        });
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Put the player's respawn point on this mattress, without sleeping and without spending it. */
    private static void setSpawnHere(Player player, BlockPos bedPos) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        // forced = false, matching a bed: if the mattress is gone when they die, they respawn at world
        // spawn rather than being dropped at a hole in the ground. That matters more here than for a
        // bed, because this block is designed to be used up.
        server.setRespawnPosition(new ServerPlayer.RespawnConfig(
            LevelData.RespawnData.of(server.level().dimension(), bedPos, server.getYRot(), 0.0F),
            false), false);
        player.sendOverlayMessage(Component.translatable("message.recompile.mattress_spawn_set"));
    }

    // ---------------- two halves ----------------

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos head = context.getClickedPos().relative(facing);
        Level level = context.getLevel();
        return level.getBlockState(head).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(head)
            ? this.defaultBlockState().setValue(FACING, facing)
            : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide()) {
            BlockPos head = pos.relative(state.getValue(FACING));
            level.setBlock(head, state.setValue(PART, BedPart.HEAD), 3);
            level.updateNeighborsAt(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    /** An orphaned half vanishes. This - not any removal hook - is what stops half-mattresses. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState,
            net.minecraft.util.RandomSource random) {
        if (direction == neighbourDirection(state.getValue(PART), state.getValue(FACING))) {
            return neighbourState.is(this) && neighbourState.getValue(PART) != state.getValue(PART)
                ? state.setValue(OCCUPIED, neighbourState.getValue(OCCUPIED))
                : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    private static Direction neighbourDirection(BedPart part, Direction facing) {
        return part == BedPart.FOOT ? facing : facing.getOpposite();
    }

    /** Creative-break the foot without the head dropping a second mattress. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            BedPart part = state.getValue(PART);
            if (part == BedPart.FOOT) {
                BlockPos head = pos.relative(neighbourDirection(part, state.getValue(FACING)));
                BlockState headState = level.getBlockState(head);
                if (headState.is(this) && headState.getValue(PART) == BedPart.HEAD) {
                    level.setBlock(head, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, head, Block.getId(headState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
