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

        /**
         * Both fields are optional so a pack can state only what it means to change. An entry that sets just
         * a terrain still gets the default weight, and one that sets just a weight stays unaffiliated.
         */
        public static final Codec<BaitWeight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, Integer.MAX_VALUE)
                .optionalFieldOf("weight", AnimalBaitBlock.DEFAULT_WEIGHT)
                .forGetter(BaitWeight::weight),
            AnimalBaitBlock.Terrain.CODEC
                .optionalFieldOf("terrain", AnimalBaitBlock.Terrain.NONE)
                .forGetter(BaitWeight::terrain)
        ).apply(instance, BaitWeight::new));
    }
}
