package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.AnimalBaitBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * The mod's NeoForge data maps - per-registry-entry values a datapack can retune without a mod release.
 *
 * <p>Same mechanism as the Burn Barrel's fuel table ({@code data/neoforge/data_maps/item/furnace_fuels.json}),
 * only keyed on our own map instead of one of NeoForge's.
 */
public final class RCDataMaps {

    private RCDataMaps() {
    }

    /**
     * Per-mob Animal Bait spawn weighting: how often a bait draws this mob, and which terrain it favours.
     *
     * <p>Ships in {@code data/recompile/data_maps/entity_type/bait_weight.json}. A mob with no entry - a
     * pack-added one, or one a pack tags into a diet without tuning it - falls back to
     * {@link AnimalBaitBlock#DEFAULT_WEIGHT} and no terrain affinity, so tagging alone is enough to make a
     * mob reachable and an entry here is purely tuning.
     *
     * <p><b>Deliberately not synced.</b> The draw happens server-side in {@code AnimalBaitBlock.pick}, and
     * the Jade provider reads only blockstate and placement, so the client never needs these values. If a
     * client-side consumer is ever added - the spec's {@code Expecting: <weighted shortlist>} line is the
     * obvious candidate - this must gain a {@code .synced(...)} call, or {@code getData} will return null
     * on the client and every mob will silently read as {@link AnimalBaitBlock#DEFAULT_WEIGHT}.
     */
    public static final DataMapType<EntityType<?>, BaitWeight> BAIT_WEIGHT = DataMapType
        .builder(Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "bait_weight"), Registries.ENTITY_TYPE,
            BaitWeight.CODEC)
        .build();

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RCDataMaps::onRegisterDataMaps);
    }

    private static void onRegisterDataMaps(RegisterDataMapTypesEvent event) {
        event.register(BAIT_WEIGHT);
    }

    /**
     * One mob's bait tuning.
     *
     * @param weight  base draw weight, before terrain
     * @param terrain the terrain this mob is drawn to; {@link AnimalBaitBlock.Terrain#NONE} for no affinity
     */
    public record BaitWeight(int weight, AnimalBaitBlock.Terrain terrain) {

        /** Ceiling on a single mob's weight; see the codec below. */
        public static final int MAX_WEIGHT = 100_000;

        /**
         * Both fields are optional so a pack can state only what it means to change. An entry that sets just
         * a terrain still gets the default weight, and one that sets just a weight stays unaffiliated.
         */
        public static final Codec<BaitWeight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Upper bound is a guard, not a design limit: pick() sums every candidate's weight into an
            // int, so unbounded pack values could overflow the total negative and make random.nextInt
            // throw mid-spawn. 100k is far past any sane tuning and cannot overflow across a diet tag.
            Codec.intRange(1, MAX_WEIGHT)
                .optionalFieldOf("weight", AnimalBaitBlock.DEFAULT_WEIGHT)
                .forGetter(BaitWeight::weight),
            AnimalBaitBlock.Terrain.CODEC
                .optionalFieldOf("terrain", AnimalBaitBlock.Terrain.NONE)
                .forGetter(BaitWeight::terrain)
        ).apply(instance, BaitWeight::new));
    }
}
