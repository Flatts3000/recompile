package com.flatts.recompile.content.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
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
    /** Equal-area cut points for the pick noise, by eligible-region count. See {@link #bucket}. */
    private final double[][] thresholds;

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
        this.thresholds = measureThresholds(this.pickNoise, noiseScale, this.frontier.size());
    }

    /**
     * Which eligible region a pick value falls to, by EQUAL AREA rather than by equal width (#290).
     *
     * <p><b>The pick noise is not uniform, and slicing it into equal-width buckets was the bug.</b>
     * {@link NormalNoise} normalises toward a target deviation rather than flattening, so its output
     * is roughly Gaussian around zero. The old code did {@code (int)(pick * eligible)} over
     * {@code (value + 1) / 2}, which cuts the bell at evenly spaced values and hands the middle bucket
     * far more area than the outer ones: two regions split near 50/50 and looked fine, three came out
     * about 16/68/16, and four about 7/43/43/7. A region's share of the world therefore depended on
     * its POSITION in the preset's array, which is not something a reader of that file could guess.
     *
     * <p>{@link #thresholds} are the real quantiles of this noise, measured from the noise itself at
     * construction, so every bucket gets the same area whatever the count. Two regions are unaffected:
     * the single cut is the median, and the median of a symmetric noise is zero, which is exactly where
     * the old code cut. So this fixes three-and-more without moving anything in a world generated
     * today.
     */
    private int bucket(double pick, int eligible) {
        double[] cuts = thresholds[eligible];
        int target = 0;
        while (target < cuts.length && pick >= cuts[target]) {
            target++;
        }
        return Mth.clamp(target, 0, eligible - 1);
    }

    /**
     * Quantiles of the pick noise, indexed by how many regions are eligible.
     *
     * <p>MEASURED FROM THE NOISE, not assumed. The deviation {@code NormalNoise} actually achieves
     * depends on its octaves and amplitudes, so a hardcoded sigma would be a second thing to keep in
     * sync with the constructor. Sampling costs a few thousand evaluations once per source rather than
     * anything per quart.
     */
    private static double[][] measureThresholds(NormalNoise noise, double noiseScale, int regions) {
        int samples = 4096;
        // MIRRORED, so the sample is exactly symmetric about zero. Two things follow, and the second
        // is the reason: the quantiles come out symmetric like the noise itself, and the two-region
        // cut lands on EXACTLY 0.0 - which is precisely where the old equal-width code cut. So a world
        // generated before this change and one generated after are identical while the mod ships two
        // frontier regions, and no existing save has its biomes move. Taking the raw empirical median
        // instead would put the cut a hair off zero and shuffle a scattering of boundary cells for no
        // benefit at all.
        double[] values = new double[samples * 2];
        // A coprime stride over a wide span, so the samples are spread across many wavelengths of the
        // noise rather than marching in step with one.
        for (int i = 0; i < samples; i++) {
            double value = noise.getValue(i * 37 * noiseScale, 0.0, i * 101 * noiseScale);
            values[i * 2] = value;
            values[i * 2 + 1] = -value;
        }
        samples *= 2;
        Arrays.sort(values);
        double[][] cuts = new double[regions + 1][];
        for (int count = 0; count <= regions; count++) {
            cuts[count] = new double[Math.max(0, count - 1)];
            for (int i = 1; i < count; i++) {
                // The MIDPOINT of the two samples the quantile falls between, which is the ordinary
                // definition of a quantile on an even-sized sample and is what makes the mirroring
                // above pay off: the two straddling values at the median are -eps and +eps, so their
                // midpoint is exactly 0. Taking the upper element instead lands on the smallest
                // positive sample - measured at 4.6e-5, which is not zero, and
                // `every_frontier_region_gets_an_even_share` failed on exactly that.
                int at = (int) ((long) samples * i / count);
                cuts[count][i - 1] = (values[at - 1] + values[at]) / 2.0;
            }
        }
        return cuts;
    }

    /** One measured cut point, so a test can prove the two-region split has not moved. */
    public double pickCut(int eligible) {
        double[] cuts = thresholds[eligible];
        return cuts.length == 1 ? cuts[0] : Double.NaN;
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

        // Frontier: only biomes whose onset this location has reached are eligible. Two passes over the
        // small frontier list (count, then select) avoid allocating a list on this per-quart hot path.
        int eligible = 0;
        for (FrontierEntry entry : frontier) {
            if (d >= entry.onset()) {
                eligible++;
            }
        }
        if (eligible == 0) {
            return household;
        }
        double pick = pickNoise.getValue(x * noiseScale, 0.0, z * noiseScale);
        int target = bucket(pick, eligible);
        int i = 0;
        for (FrontierEntry entry : frontier) {
            if (d >= entry.onset()) {
                if (i == target) {
                    return entry.biome();
                }
                i++;
            }
        }
        return household;   // unreachable: target < eligible
    }
}
