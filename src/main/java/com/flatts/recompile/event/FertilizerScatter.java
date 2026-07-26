package com.flatts.recompile.event;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Fertilizer's scatter (Vegetation tier, rung 2): a surface-aware "fancy bonemeal". On grass it scatters
 * dump-friendly weeds and real-weed wildflowers; on mycelium it scatters mushrooms (the P1.9 forage
 * loop). The footprint is vanilla bonemeal's - 128 attempts walking outward - so the reach matches
 * bonemeal exactly; the only change is that placements are revealed over a few seconds, rippling out from
 * the click.
 *
 * <p>No BlockEntity and no saved state: pending placements live in a static queue drained on the server
 * tick (server thread only). If the server stops mid-ripple the remainder is dropped - the timing is
 * cosmetic.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class FertilizerScatter {

    private record Weighted(Block block, int weight) {}

    private record Pending(ResourceKey<Level> dim, BlockPos pos, BlockState plant, long placeAt) {}

    private static final List<Pending> QUEUE = new ArrayList<>();

    /** How far (blocks) the ripple takes to reach; matches bonemeal's outward walk. */
    private static final int RIPPLE_REACH = 8;

    private FertilizerScatter() {
    }

    // Grass-dominant: mostly weeds and grasses, flowers a sparse accent (~80% grasses, ~20% flowers).
    // Real-weed wildflowers only (dandelion/poppy/oxeye/cornflower); the two custom pioneers ride along.
    private static List<Weighted> grassScatter() {
        return List.of(
            new Weighted(Blocks.SHORT_GRASS, 25),
            new Weighted(RCBlocks.WEEDGRASS.get(), 20),
            new Weighted(Blocks.FERN, 12),
            new Weighted(Blocks.TALL_GRASS, 6),
            new Weighted(Blocks.LARGE_FERN, 3),
            new Weighted(Blocks.DANDELION, 4),
            new Weighted(Blocks.POPPY, 4),
            new Weighted(Blocks.OXEYE_DAISY, 3),
            new Weighted(Blocks.CORNFLOWER, 2),
            new Weighted(RCBlocks.FIREWEED.get(), 3));
    }

    private static List<Weighted> mushroomScatter() {
        return List.of(
            new Weighted(RCBlocks.DUMP_MUSHROOM.get(), 50),
            new Weighted(Blocks.BROWN_MUSHROOM, 25),
            new Weighted(Blocks.RED_MUSHROOM, 25));
    }

    /** Schedule the ripple scatter around a fertilized grass/mycelium block. */
    public static void schedule(ServerLevel level, BlockPos clicked, boolean grass) {
        scatter(level, clicked, grass, false);
    }

    /** Test seam: scatter and place immediately (no ripple), returning how many plants were placed. */
    public static int scatterForTest(ServerLevel level, BlockPos clicked, boolean grass) {
        return scatter(level, clicked, grass, true);
    }

    private static int scatter(ServerLevel level, BlockPos clicked, boolean grass, boolean immediate) {
        RandomSource rand = level.getRandom();
        Block surface = grass ? Blocks.GRASS_BLOCK : Blocks.MYCELIUM;
        List<Weighted> set = grass ? grassScatter() : mushroomScatter();
        int total = set.stream().mapToInt(Weighted::weight).sum();
        int attempts = RCConfig.FERTILIZER_ATTEMPTS.get();
        int ripple = RCConfig.FERTILIZER_RIPPLE_TICKS.get();
        long now = level.getGameTime();
        BlockPos above = clicked.above();
        int placed = 0;
        // Vanilla bonemeal's footprint: later attempts walk further, so density falls off with distance.
        outer:
        for (int i = 0; i < attempts; i++) {
            BlockPos.MutableBlockPos target = above.mutable();
            for (int step = 0; step < i / 16; step++) {
                target.move(rand.nextInt(3) - 1, (rand.nextInt(3) - 1) * rand.nextInt(3) / 2, rand.nextInt(3) - 1);
                if (!level.getBlockState(target.below()).is(surface)) {
                    continue outer;
                }
            }
            BlockPos at = target.immutable();
            if (!level.getBlockState(at).isAir()) {
                continue;   // skip occupied cells - top-up, never overwrite
            }
            BlockState plant = pick(set, total, rand).defaultBlockState();
            if (!canPlace(level, at, plant)) {
                continue;
            }
            if (immediate) {
                place(level, at, plant);
            } else {
                int dist = (int) Math.round(Math.sqrt(above.distSqr(at)));
                long delay = ripple <= 0 ? 0 : Math.min(ripple, (long) dist * ripple / RIPPLE_REACH);
                QUEUE.add(new Pending(level.dimension(), at, plant, now + delay));
            }
            placed++;
        }
        return placed;
    }

    private static boolean canPlace(ServerLevel level, BlockPos at, BlockState plant) {
        if (!plant.canSurvive(level, at)) {
            return false;
        }
        // a two-block plant needs headroom for its upper half
        return !(plant.getBlock() instanceof DoublePlantBlock) || level.getBlockState(at.above()).isAir();
    }

    private static Block pick(List<Weighted> set, int total, RandomSource rand) {
        int roll = rand.nextInt(total);
        for (Weighted w : set) {
            roll -= w.weight();
            if (roll < 0) {
                return w.block();
            }
        }
        return set.get(0).block();
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState plant) {
        if (plant.getBlock() instanceof DoublePlantBlock) {
            DoublePlantBlock.placeAt(level, plant, pos, Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, plant, Block.UPDATE_ALL);
        }
        level.levelEvent(1505, pos, 0);   // vanilla bonemeal green sparkle, so the ripple twinkles out
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        QUEUE.removeIf(p -> {
            ServerLevel level = server.getLevel(p.dim());
            // Drop rather than force a synchronous chunk load: if the player walked off during the
            // ripple the far cells just do not sprout, which is fine for cosmetic timing.
            if (level == null || !level.hasChunkAt(p.pos())) {
                return true;
            }
            if (level.getGameTime() < p.placeAt()) {
                return false;  // not due yet
            }
            if (level.getBlockState(p.pos()).isAir() && canPlace(level, p.pos(), p.plant())) {
                place(level, p.pos(), p.plant());
            }
            return true;   // placed, or no longer valid - drop from the queue
        });
    }

    /** Pending ripples do not survive a world change - they carry a dimension key and a game time. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        QUEUE.clear();
    }
}
