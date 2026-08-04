package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * What the dump's strays are allowed to stand on (#133).
 *
 * <p>Vanilla gates animal spawning on the block underneath, per species, and the dump's surface is
 * <b>coarse dirt</b> everywhere - the {@code noise_settings} surface rule. Left alone that means a
 * biome full of animals that can never spawn in it.
 *
 * <p>Each species needed a different amount of help, which is worth knowing rather than rediscovering:
 *
 * <ul>
 *   <li><b>Wolves need nothing.</b> {@code #minecraft:wolves_spawnable_on} already contains coarse
 *       dirt, so vanilla has always considered a landfill wolf country.</li>
 *   <li><b>Cats gate on {@code #minecraft:animals_spawnable_on}</b>, which is grass block and nothing
 *       else. Widened here rather than by editing that tag, because the tag is shared by every ordinary
 *       animal and changing it would quietly make cows spawnable on coarse dirt everywhere. An
 *       {@code OR} on the cat's own predicate keeps the blast radius to cats.</li>
 *   <li><b>The pigeon</b> is ours, so it simply declares its own rule.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCStraySpawns {

    private RCStraySpawns() {
    }

    @SubscribeEvent
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // OR, not REPLACE: a cat must still spawn everywhere vanilla lets it. This adds the dump, it
        // does not take villages and swamp huts away.
        // No heightmap or placement type with OR - NeoForge only allows those on REPLACE, because
        // anything else would be silently overwriting vanilla's choice of where to even look. The cat
        // keeps vanilla's; only its predicate widens.
        event.register(EntityType.CAT,
            (type, level, reason, pos, random) ->
                level.getBlockState(pos.below()).is(Blocks.COARSE_DIRT)
                    && brightEnough(level, reason, pos),
            RegisterSpawnPlacementsEvent.Operation.OR);

        // The pigeon has no vanilla rule to preserve, so this is its whole placement: on the ground,
        // on the dump's own surface.
        // REPLACE, and with the full signature: the pigeon has no vanilla placement to preserve, so
        // this declares the whole thing rather than widening anything.
        event.register(RCEntities.PIGEON.get(),
            SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) ->
                level.getBlockState(pos.below()).is(Blocks.COARSE_DIRT)
                    && brightEnough(level, reason, pos),
            RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * Vanilla's daylight requirement, restored to both predicates.
     *
     * <p><b>Neither of them had it, and coarse dirt is the entire surface of this world.</b> Vanilla's
     * animal rule is {@code ground is in #animals_spawnable_on AND bright enough}, and writing a bare
     * ground test dropped the second half - for the cat because an {@code OR} branch replaces the whole
     * conjunction rather than widening one term of it, and for the pigeon because it declares its rule
     * outright. The result was strays spawning in the dark on any coarse dirt, which in this mod means
     * inside an unlit building whose floor the player never replaced. Ambiance that appears in your
     * base at night is not ambiance.
     *
     * <p>{@code ignoresLightRequirements} is honoured so a spawn egg, a command or a test still works in
     * the dark, exactly as vanilla does it.
     */
    private static boolean brightEnough(LevelAccessor level, EntitySpawnReason reason, BlockPos pos) {
        return EntitySpawnReason.ignoresLightRequirements(reason)
            || level.getRawBrightness(pos, 0) > 8;
    }
}
