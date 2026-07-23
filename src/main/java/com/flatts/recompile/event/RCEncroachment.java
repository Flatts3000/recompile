package com.flatts.recompile.event;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Encroachment (design P1.7-R): the junkyard fights back. Healed ground does not stay healed
 * for free - coarse earth takes back grass that borders unhealed ground, and only the
 * reclamation ladder stops it.
 *
 * <p><b>Why this needs no saved state.</b> {@code worldgen/noise_settings/garbage.json} makes
 * coarse dirt the universal surface of the entire world, so every healed patch is by definition
 * ringed by unhealed ground. "Am I on the frontier?" is therefore a purely local neighbour check
 * - no mound memory, no region tracking, no {@code SavedData}. The same fact is why nothing
 * renews on its own: vanilla grass cannot spread onto coarse dirt, so the world stays dead
 * without our help. (It follows that the rung-1 spreader must convert coarse dirt <em>straight</em>
 * to grass - leave plain dirt as an intermediate and vanilla spread finishes the job for free.)
 *
 * <p><b>The ladder is the answer.</b> Bare soil reverts; soil under cover loses the cover
 * instead; soil near logs or leaves is permanent. Permanence is earned at the top of the
 * reclamation chain rather than granted at the bottom, which is what gives rungs 2-3 a purpose
 * beyond yield and makes the first forest a border rather than an ornament.
 *
 * <p><b>Everything here is tag-driven, including the exceptions.</b> The target is the dirt
 * family via {@code #minecraft:substrate_overworld} rather than a list of block ids, so a
 * chisel-style mod's dirt variants are covered without a mod release. (Not
 * {@code #minecraft:dirt} - 26.1 narrowed that one to three blocks and it no longer contains
 * grass.) Two blocks are carved back out: coarse dirt
 * (the revert target - otherwise the sweep churns bare ground forever) and mycelium (the
 * substrate the dump mushrooms grow on, so eating it would erode the P1.9 forage economy).
 *
 * <p><b>Two kinds of permanence, and only one is contested.</b> Encroachment reverts grass to
 * <em>plain</em> coarse dirt. Mound retirement (Phase 5) is a separate layer and stays permanent,
 * so healing a mound's footprint still retires it forever even if the surface later erodes. The
 * P1.7 endgame thesis - "you no longer need the dump" - is untouched; only the green is a fight.
 *
 * <p>Erosion therefore marches inward: each reversion exposes a new frontier one block in, so an
 * unstabilised patch is eaten ring by ring from its edge. That is the intent, not a leak - a
 * visible advancing front reads as a fight, where grass popping at random would read as rot.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCEncroachment {

    /** What a single encroachment attempt did. Returned so GameTests can assert the ladder rung. */
    public enum Outcome {
        /** Not takeable soil - either outside the dirt family, or explicitly immune. */
        NOT_A_TARGET,
        /** Takeable, but ringed by healed ground. Safe until the front reaches it. */
        INTERIOR,
        /** Rung 3: a log or leaf holds this ground permanently. */
        ANCHORED,
        /** Rung 2: the cover absorbed the hit and was stripped. The soil survives. */
        COVER_STRIPPED,
        /** Rung 1: bare soil went back to coarse dirt. */
        REVERTED
    }

    private RCEncroachment() {
    }

    /**
     * The sweep. Sampling is anchored to players rather than to loaded chunks for three reasons:
     * the work is bounded by player count instead of by view distance, it happens where the
     * player can actually watch it (which is the entire point of the junkyard fighting back),
     * and an unattended base cannot rot while its owner is away - which keeps faith with the
     * P1.6 item 4 clause that this must never threaten builds.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level raw = event.getLevel();
        if (!(raw instanceof ServerLevel level) || !RCConfig.ENCROACHMENT_ENABLED.get()) {
            return;
        }
        if (level.getGameTime() % RCConfig.ENCROACHMENT_INTERVAL_TICKS.get() != 0L) {
            return;
        }

        int attempts = RCConfig.ENCROACHMENT_ATTEMPTS_PER_PLAYER.get();
        int radius = RCConfig.ENCROACHMENT_RADIUS.get();
        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            BlockPos anchor = player.blockPosition();
            for (int i = 0; i < attempts; i++) {
                BlockPos column = anchor.offset(
                    random.nextInt(radius * 2 + 1) - radius,
                    0,
                    random.nextInt(radius * 2 + 1) - radius);
                // Never pull a chunk in just to erode it.
                if (!level.hasChunkAt(column)) {
                    continue;
                }
                // Inert outside the garbage regions, so the mod does nothing to a vanilla
                // overworld (where coarse dirt occurs naturally in badlands and taiga).
                if (!level.getBiome(column).is(RCTags.ENCROACHES)) {
                    continue;
                }
                // MOTION_BLOCKING_NO_LEAVES rather than WORLD_SURFACE: it steps over flowers and
                // grasses, so the block we test is the soil itself and not the cover standing on
                // it. With WORLD_SURFACE every vegetated block would read as "not a target" and
                // rung 2 would silently never fire.
                BlockPos surface =
                    level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
                encroachOnce(level, surface);
            }
        }
    }

    /**
     * Apply the mechanic at one position: the frontier test, then the ladder. This is the static
     * entry point GameTests call directly rather than simulating ticks, matching
     * {@code SortableBlock.sortOnce} and {@code SortingTarpBlock.siftInput}.
     *
     * <p>Deliberately does <em>not</em> re-check the config flag or the biome tag - those are
     * targeting questions owned by the sweep. This method answers only "what happens to this
     * block", which is what makes it testable on the plain gametest plot.
     */
    public static Outcome encroachOnce(ServerLevel level, BlockPos pos) {
        BlockState soil = level.getBlockState(pos);
        // Allowlist minus denylist. Tags union but cannot subtract, so the two exceptions the
        // design needs - coarse dirt (the revert target) and mycelium (the forage substrate) -
        // have to be carved back out here rather than in the JSON.
        if (!soil.is(RCTags.ENCROACHABLE) || soil.is(RCTags.ENCROACHMENT_IMMUNE)) {
            return Outcome.NOT_A_TARGET;
        }
        if (isMoist(soil)) {
            return Outcome.NOT_A_TARGET;
        }
        if (!isFrontier(level, pos)) {
            return Outcome.INTERIOR;
        }
        if (hasAnchor(level, pos, RCConfig.TREE_ANCHOR_RADIUS.get())) {
            return Outcome.ANCHORED;
        }

        BlockPos above = pos.above();
        if (level.getBlockState(above).is(RCTags.FRONTIER_COVER)) {
            // The cover takes the hit and is torn out, not harvested - the junkyard does not
            // hand your flowers back. The soil survives to be taken on a later pass.
            level.destroyBlock(above, false);
            return Outcome.COVER_STRIPPED;
        }

        level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
        return Outcome.REVERTED;
    }

    /**
     * Water holds the ground. Wet farmland is spared and dry farmland is taken, so an irrigated
     * plot defends itself and an abandoned one dries out and goes back to the dump - which makes
     * the P1.10 water economy a reclamation defence rather than only an input.
     *
     * <p>Keyed on the <em>property</em> rather than on {@code minecraft:farmland}, so any modded
     * farmland reusing vanilla's {@code moisture} is covered without being named. This is the one
     * rule a tag cannot express: tags match blocks, and the distinction here is blockstate.
     *
     * <p>Consequence worth knowing: a crop standing on unwatered farmland goes with it. That is
     * the intent - water your crops or lose them - but it is the one place encroachment destroys
     * player investment, so it is deliberately gated behind "you let it dry out".
     */
    private static boolean isMoist(BlockState soil) {
        return soil.hasProperty(BlockStateProperties.MOISTURE)
            && soil.getValue(BlockStateProperties.MOISTURE) > 0;
    }

    /**
     * True when any of the eight horizontal neighbours is unhealed ground. Neighbours are checked
     * one block up and down as well, so a frontier still reads across the shallow steps the
     * terrain and mound skirts produce rather than stalling at every lip.
     */
    private static boolean isFrontier(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(cursor).is(RCTags.HOSTILE_GROUND)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True when a log or leaf stands within {@code radius}. The vertical band runs from the soil
     * upward only: a tree shelters the ground it grows on and around, not the ground beneath a
     * cliff it happens to sit above.
     */
    private static boolean hasAnchor(ServerLevel level, BlockPos pos, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= radius; dy++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(RCTags.FRONTIER_ANCHOR)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
