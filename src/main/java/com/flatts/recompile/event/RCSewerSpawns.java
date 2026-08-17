package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * What is allowed to spawn in a sewer, and why each one needed a different answer (#90 phase 3).
 *
 * <p><b>{@code spawn_overrides} decides which mobs a structure OFFERS; it does not bypass
 * {@code SpawnPlacements}.</b> The per-type predicate still runs, and most of the sewer's intended
 * inhabitants fail it in this world:
 *
 * <ul>
 *   <li><b>Drowned</b> are registered {@code IN_WATER}, which tests {@code FluidTags.WATER}, and
 *       leachate is deliberately outside that tag. They come from a <b>spawner</b> instead, which works
 *       because {@code Drowned.checkDrownedSpawnRules} has an explicit {@code isSpawner} branch.
 *   <li><b>Turtles and frogs</b> are <b>placed</b> by the structure and never spawn: a turtle wants
 *       {@code y < seaLevel + 4} against a sea level of <b>-64</b>, and a frog wants
 *       {@code #minecraft:animals_spawnable_on}, which is grass block and nothing else. Owner call: both
 *       are limited populations, so placing them is the mechanism AND the design.
 *   <li><b>Slime</b> spawns naturally, and this class is why. Owner call, 2026-08-17.
 *   <li><b>The Roach</b> is ours and has no vanilla rule at all, so it declares one.
 * </ul>
 *
 * <p>This follows {@link RCStraySpawns}, which solved the same shape of problem for the dump's strays
 * and records the rule that binds here too: <b>{@code OR} widens a predicate, {@code REPLACE} defines
 * one</b>, and NeoForge only accepts a placement type or heightmap on {@code REPLACE}.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCSewerSpawns {

    /** The structure the relaxations are scoped to. */
    public static final ResourceKey<Structure> SEWER = ResourceKey.create(Registries.STRUCTURE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "sewer"));

    private RCSewerSpawns() {
    }

    @SubscribeEvent
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // SLIME, NATURALLY, AND ONLY DOWN THERE.
        //
        // Vanilla gives slime two routes and this world closes both: the surface route needs the biome
        // in #minecraft:allows_surface_slime_spawns (swamps only) at y 50-70, and the slime-chunk route
        // needs y < 40 in one chunk in ten. A sewer that had slimes in a tenth of its lower corridors
        // and nowhere else is not "slimes live in the sewers", it is a coincidence the player cannot
        // read.
        //
        // OR rather than REPLACE, so vanilla slimes are untouched everywhere else - swamps and slime
        // chunks keep working exactly as they do now. The added route is gated on actually being inside
        // a sewer, which makes the containment a property of the predicate rather than an argument
        // about who lists slimes where. That matters: relying on "nothing else offers slimes" would be
        // true today and silently false the first time a biome or another structure adds them.
        event.register(EntityType.SLIME,
            (type, level, reason, pos, random) -> inSewer(level, pos) && dark(level, reason, pos),
            RegisterSpawnPlacementsEvent.Operation.OR);

        // THE ROACH declares its whole rule, the way the Pigeon does - it is our entity, it is in no
        // biome's spawner list, and until now it could only ever arrive by being disturbed out of a
        // garbage block or by a spawn egg. REPLACE with the full signature because there is no vanilla
        // placement to preserve.
        //
        // No light test: a roach in a dark sewer is the entire point, and the drowned spawner beside it
        // already requires darkness.
        event.register(RCEntities.ROACH.get(),
            SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> inSewer(level, pos),
            RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * Whether this position is inside a generated sewer.
     *
     * <p>{@code getStructureWithPieceAt} rather than a bounding-box test, so a corridor counts and the
     * rock between two corridors does not - the structure's own pieces are the room, which is what
     * {@code "bounding_box": "piece"} in the spawn overrides also means.
     */
    private static boolean inSewer(ServerLevelAccessor level, BlockPos pos) {
        return level.getLevel().structureManager()
            .getStructureWithPieceAt(pos, holder -> holder.is(SEWER)).isValid();
    }

    /**
     * Vanilla's darkness requirement, honouring {@code ignoresLightRequirements} so a spawn egg, a
     * command or a test still works - the same courtesy {@link RCStraySpawns} extends to its own rules.
     */
    private static boolean dark(ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos) {
        return EntitySpawnReason.ignoresLightRequirements(reason)
            || level.getMaxLocalRawBrightness(pos) <= 7;
    }
}
