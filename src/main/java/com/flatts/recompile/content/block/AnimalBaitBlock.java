package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.registry.RCDataMaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Animal bait (reclamation rung 5, spec {@code docs/animals_tier_spec.md}): a small lure the player
 * places on healed grass and walks away from. On a self-scheduled tick it <b>settles</b> only while
 * undisturbed - a nearby player resets it, and it will not sit too close to another bait - and when it
 * has settled it spawns one wildlife mob (weighted by the surrounding terrain) from its diet's allowlist
 * tag, then destroys itself. Vanilla breeding takes over from there; the bait only seeds the population.
 *
 * <p><b>No BlockEntity.</b> The only state is the {@link #SETTLE} progress, a coarse blockstate
 * flyweight (the {@code SortableBlock.sorted} pattern), advanced or reset by a self-rescheduling block
 * tick (the Grass Spreader pattern). Everything else - which mob, whether a player is near - is read
 * from the world at tick time, so nothing serialises.
 */
public class AnimalBaitBlock extends Block {

    public static final MapCodec<AnimalBaitBlock> CODEC = simpleCodec(AnimalBaitBlock::new);

    /** Which allowlist this bait draws from. Set by the placing item; drives the spawn tag. */
    public static final EnumProperty<Diet> DIET = EnumProperty.create("diet", Diet.class);
    /** Whether this is a Rich bait - seeds a bonded pair (adult + baby) rather than a lone adult. */
    public static final BooleanProperty RICH = BooleanProperty.create("rich");
    /** Coarse "how long undisturbed" progress; fires at {@link #SETTLE_MAX}. Reset when a player is near. */
    public static final IntegerProperty SETTLE = IntegerProperty.create("settle", 0, 7);
    public static final int SETTLE_MAX = 7;

    /** A flat lure on the ground - a low plate, so it reads as placed bait, not a full block. */
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0);

    /** How far out the environment scan looks when weighting which mob the land draws. */
    private static final int ENV_RADIUS = 4;

    public AnimalBaitBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
            .setValue(DIET, Diet.HERBIVORE)
            .setValue(RICH, false)
            .setValue(SETTLE, 0));
    }

    @Override
    protected MapCodec<? extends AnimalBaitBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIET, RICH, SETTLE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // A lure sits on solid ground; if the block under it goes away it pops off like a torch/flower.
    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        schedule(level, pos, RCConfig.ANIMAL_BAIT_SETTLE_INTERVAL_TICKS.get());
    }

    // ---------------- the settle / fire tick ----------------

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        settleOnce(level, pos);
    }

    /**
     * One settle step - the static entry point GameTests drive directly (the {@code sortOnce} convention).
     * Returns what happened, so a test can assert the reason.
     */
    public static Outcome settleOnce(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AnimalBaitBlock bait)) {
            return Outcome.GONE;
        }
        if (!RCConfig.ANIMAL_BAIT_ENABLED.get()) {
            return Outcome.DISABLED;
        }
        int interval = RCConfig.ANIMAL_BAIT_SETTLE_INTERVAL_TICKS.get();

        if (!onGrass(level, pos)) {
            schedule(level, pos, interval);   // wrong ground: idle, recheck later
            return Outcome.NO_GRASS;
        }
        if (playerNear(level, pos)) {
            if (state.getValue(SETTLE) != 0) {
                level.setBlock(pos, state.setValue(SETTLE, 0), Block.UPDATE_CLIENTS);   // watched: reset
                level.playSound(null, pos, SoundEvents.FOX_SNIFF, SoundSource.NEUTRAL, 0.3F, 1.4F);
            }
            schedule(level, pos, interval);
            return Outcome.PLAYER_NEAR;
        }
        if (baitNear(level, pos)) {
            schedule(level, pos, interval);   // crowded: hold, do not settle
            return Outcome.CROWDED;
        }

        int settle = state.getValue(SETTLE);
        if (settle < SETTLE_MAX) {
            level.setBlock(pos, state.setValue(SETTLE, settle + 1), Block.UPDATE_CLIENTS);
            schedule(level, pos, interval);
            return Outcome.SETTLING;
        }
        return bait.fire(level, pos, state) ? Outcome.FIRED : rescheduleAndWait(level, pos, interval);
    }

    private static Outcome rescheduleAndWait(ServerLevel level, BlockPos pos, int interval) {
        schedule(level, pos, interval);
        return Outcome.SETTLING;
    }

    /** Spawn the drawn mob (weighted by terrain), seed a pair if Rich, and consume the bait. */
    private boolean fire(ServerLevel level, BlockPos pos, BlockState state) {
        EntityType<?> type = pick(level, pos, state.getValue(DIET), level.getRandom());
        if (type == null) {
            rescheduleAndWait(level, pos, RCConfig.ANIMAL_BAIT_SETTLE_INTERVAL_TICKS.get());
            return false;
        }
        level.removeBlock(pos, false);   // the bait is used up, before spawning into the space
        Entity spawned = type.spawn(level, pos, EntitySpawnReason.NATURAL);   // finalizeSpawn -> biome variant
        if (spawned == null) {
            return true;   // bait still consumed; the spot could not hold the mob
        }
        if (state.getValue(RICH) && spawned instanceof AgeableMob) {
            Entity mate = type.spawn(level, pos, EntitySpawnReason.NATURAL);
            if (mate instanceof AgeableMob baby) {
                baby.setBaby(true);   // herd-seeding: a bonded pair - an adult plus its young
            }
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
            12, 0.4, 0.3, 0.4, 0.0);
        level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.NEUTRAL, 0.7F, 1.1F);
        return true;
    }

    // ---------------- the environment-weighted pick ----------------

    /** Pick a mob from the diet tag, weighted by the terrain around the bait, or null if the tag is empty. */
    public static @Nullable EntityType<?> pick(ServerLevel level, BlockPos pos, Diet diet, RandomSource random) {
        java.util.Optional<HolderSet.Named<EntityType<?>>> tag = BuiltInRegistries.ENTITY_TYPE.get(diet.tag());
        if (tag.isEmpty() || tag.get().size() == 0) {
            return null;
        }
        Terrain dominant = scan(level, pos);
        List<EntityType<?>> types = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (var holder : tag.get()) {
            EntityType<?> type = holder.value();
            int weight = weightOf(holder, dominant);
            types.add(type);
            weights.add(weight);
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < types.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return types.get(i);
            }
        }
        return types.get(types.size() - 1);
    }

    /**
     * One mob's draw weight on the given terrain, read from the {@code recompile:bait_weight} data map.
     *
     * <p>A mob with no entry rides {@link #DEFAULT_WEIGHT} with no affinity, so a pack can make a mob
     * reachable with nothing but a diet tag and tune it later.
     */
    public static int weightOf(Holder<EntityType<?>> holder, Terrain dominant) {
        RCDataMaps.BaitWeight data = holder.getData(RCDataMaps.BAIT_WEIGHT);
        int base = data == null ? DEFAULT_WEIGHT : data.weight();
        Terrain affinity = data == null ? Terrain.NONE : data.terrain();
        // NONE is "no affinity", not a terrain to match against - an unaffiliated mob must never collect
        // the bonus. Guarding it here rather than relying on scan() never returning NONE, so the rule holds
        // for every caller of this method rather than only for the one that happens to feed it today.
        boolean drawn = affinity != Terrain.NONE && affinity == dominant;
        return base + (drawn ? TERRAIN_BONUS : 0);
    }

    /** What the land around the bait mostly is - the terrain the spawn weighting keys on. */
    public static Terrain scan(BlockGetter level, BlockPos pos) {
        int grass = 0;
        int sand = 0;
        int leaves = 0;
        for (int dx = -ENV_RADIUS; dx <= ENV_RADIUS; dx++) {
            for (int dz = -ENV_RADIUS; dz <= ENV_RADIUS; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockState s = level.getBlockState(pos.offset(dx, dy, dz));
                    if (s.is(Blocks.GRASS_BLOCK)) {
                        grass++;
                    } else if (s.is(net.minecraft.tags.BlockTags.SAND)) {
                        sand++;
                    } else if (s.is(net.minecraft.tags.BlockTags.LEAVES)) {
                        leaves++;
                    }
                }
            }
        }
        if (leaves > grass && leaves > sand) {
            return Terrain.LEAVES;
        }
        if (sand > grass) {
            return Terrain.SAND;
        }
        return Terrain.GRASS;
    }

    // ---------------- gates ----------------

    public static boolean onGrass(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK);
    }

    public static boolean playerNear(Level level, BlockPos pos) {
        double r = RCConfig.ANIMAL_BAIT_PLAYER_RADIUS.get();
        return level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, r);
    }

    /**
     * Whether this bait is crowded out - i.e. it should hold rather than settle because a nearby bait
     * has priority. It yields only to a bait that sorts <b>earlier</b> by position (a deterministic total
     * order), so in any cluster exactly one bait - the earliest - is never crowded and settles; when it
     * fires and is gone, the next-earliest takes over. That resolves a cluster one at a time instead of
     * deadlocking it (every bait blocking every other, so none ever fire).
     */
    public static boolean baitNear(Level level, BlockPos pos) {
        int r = RCConfig.ANIMAL_BAIT_SPACING.get();
        long self = pos.asLong();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos other = pos.offset(dx, dy, dz);
                    if (level.getBlockState(other).getBlock() instanceof AnimalBaitBlock
                            && other.asLong() < self) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void schedule(Level level, BlockPos pos, int delay) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, level.getBlockState(pos).getBlock())) {
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), delay);
        }
    }

    // ---------------- settling ambience ----------------

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(SETTLE) > 0 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.COMPOSTER, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                pos.getY() + 0.2, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
        }
    }

    // ---------------- Jade read-only accessors ----------------

    /** Coarse settle progress (0..MAX) for Jade / tests. */
    public static int settle(BlockState state) {
        return state.getValue(SETTLE);
    }

    /** What a settle step did - so tests can assert the reason and Jade can name the blocker. */
    public enum Outcome {
        SETTLING, FIRED, PLAYER_NEAR, CROWDED, NO_GRASS, DISABLED, GONE
    }

    /** The three diets; each maps to its allowlist entity-type tag. */
    public enum Diet implements StringRepresentable {
        HERBIVORE("herbivore"),
        CARNIVORE("carnivore"),
        OMNIVORE("omnivore");

        private final String name;
        private final TagKey<EntityType<?>> tag;

        Diet(String name) {
            this.name = name;
            this.tag = TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("recompile", "bait/" + name));
        }

        public TagKey<EntityType<?>> tag() {
            return tag;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * The terrain a mob is drawn to - keys the spawn weighting.
     *
     * <p>{@link #NONE} means no affinity. {@link #scan} never returns it (bare ground reads as
     * {@link #GRASS}), so an unaffiliated mob simply never collects the terrain bonus.
     */
    public enum Terrain implements StringRepresentable {
        GRASS, SAND, LEAVES, NONE;

        public static final Codec<Terrain> CODEC = StringRepresentable.fromEnum(Terrain::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Draw weight for a mob with no {@code bait_weight} entry - enough to be reachable, not favoured. */
    public static final int DEFAULT_WEIGHT = 5;
    private static final int TERRAIN_BONUS = 5;
}
