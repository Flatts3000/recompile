package com.flatts.recompile.content.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The region system (demolition_yard_spec.md S1): a distance-gradient biome source.
 *
 * <p>Within {@code coreRadius} of world origin the biome is always {@code household} - the guaranteed
 * safe, resource-complete home (large enough that the whole hostile spawn range is inside the
 * empty-spawner biome, so standing at spawn you are 100% protected). Beyond the core, the probability of
 * household decays with distance from ~1.0 at the edge toward {@code householdFloor}, so safe patches thin
 * outward but never vanish; the complementary share goes to the frontier biomes, each of which only
 * appears past its own {@code onset} distance (so variety grows with distance). A low-frequency
 * self-seeded noise picks the local patch, giving coherent blobs rather than salt-and-pepper.
 *
 * <p>Why a custom source and not data-only {@code multi_noise}: vanilla has no distance-from-origin
 * density function, so nothing data-driven can guarantee household fills the spawn area. This is a
 * registered {@link BiomeSource} codec (not a mixin). The flat {@code noise_settings} router leaves the
 * vanilla {@link Climate.Sampler} constant, so the local pick uses our own {@link NormalNoise} instead.
 *
 * <p>v1 note: the noise is seeded from a codec field (deterministic, same layout every world). Deriving
 * it from the world seed is a later refinement; determinism is convenient for testing the feel.
 */
public class RegionBiomeSource extends BiomeSource {

    /** A frontier biome and the distance from origin past which it can appear. */
    public record FrontierEntry(Holder<Biome> biome, int onset) {
        public static final Codec<FrontierEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Biome.CODEC.fieldOf("biome").forGetter(FrontierEntry::biome),
            Codec.INT.optionalFieldOf("onset", 0).forGetter(FrontierEntry::onset)
        ).apply(inst, FrontierEntry::new));
    }

    public static final MapCodec<RegionBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        Biome.CODEC.fieldOf("household").forGetter(s -> s.household),
        FrontierEntry.CODEC.listOf().optionalFieldOf("frontier", List.of()).forGetter(s -> s.frontier),
        Codec.INT.optionalFieldOf("core_radius", 512).forGetter(s -> s.coreRadius),
        Codec.FLOAT.optionalFieldOf("falloff", 768.0F).forGetter(s -> s.falloff),
        Codec.FLOAT.optionalFieldOf("household_floor", 0.2F).forGetter(s -> s.householdFloor),
        // Blob size: features span roughly 128/noise_scale blocks (the first-octave wavelength over the
        // coord scale). ~0.5 gives a few-hundred-block mix; tiny values give world-spanning blobs that
        // collapse the gradient into visible concentric rings (the noise stops varying across the map).
        Codec.DOUBLE.optionalFieldOf("noise_scale", 0.5).forGetter(s -> s.noiseScale),
        Codec.LONG.optionalFieldOf("seed", 2611L).forGetter(s -> s.seed)
    ).apply(inst, RegionBiomeSource::new));

    private final Holder<Biome> household;
    private final List<FrontierEntry> frontier;
    private final int coreRadius;
    private final float falloff;
    private final float householdFloor;
    private final double noiseScale;
    private final long seed;

    // Two low-frequency noises: one decides household-vs-frontier, one picks which frontier biome.
    private final NormalNoise blobNoise;
    private final NormalNoise pickNoise;

    public RegionBiomeSource(Holder<Biome> household, List<FrontierEntry> frontier, int coreRadius,
            float falloff, float householdFloor, double noiseScale, long seed) {
        this.household = household;
        this.frontier = List.copyOf(frontier);
        this.coreRadius = coreRadius;
        this.falloff = falloff;
        this.householdFloor = householdFloor;
        this.noiseScale = noiseScale;
        this.seed = seed;
        NormalNoise.NoiseParameters params = new NormalNoise.NoiseParameters(-7, 1.0);
        this.blobNoise = NormalNoise.create(new XoroshiroRandomSource(seed), params);
        this.pickNoise = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x9E3779B97F4A7C15L), params);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(Stream.of(household), frontier.stream().map(FrontierEntry::biome));
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        int x = QuartPos.toBlock(qx);
        int z = QuartPos.toBlock(qz);
        double d = Math.sqrt((double) x * x + (double) z * z);

        // Inside the core: always the safe home.
        if (d < coreRadius) {
            return household;
        }

        // Probability of household past the core: ~1.0 at the edge, decaying to the floor over `falloff`.
        float t = Mth.clamp((float) (d - coreRadius) / falloff, 0.0F, 1.0F);
        double pHousehold = Mth.lerp(t, 1.0F, householdFloor);

        double blob = (blobNoise.getValue(x * noiseScale, 0.0, z * noiseScale) + 1.0) / 2.0;
        if (blob < pHousehold) {
            return household;
        }

        // Frontier: only biomes whose onset this location has reached are eligible.
        List<Holder<Biome>> eligible = new ArrayList<>();
        for (FrontierEntry entry : frontier) {
            if (d >= entry.onset()) {
                eligible.add(entry.biome());
            }
        }
        if (eligible.isEmpty()) {
            return household;
        }
        double pick = (pickNoise.getValue(x * noiseScale, 0.0, z * noiseScale) + 1.0) / 2.0;
        int index = Mth.clamp((int) (pick * eligible.size()), 0, eligible.size() - 1);
        return eligible.get(index);
    }
}
