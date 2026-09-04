package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.AnimalBaitBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import java.util.Optional;
import net.minecraft.world.item.Item;
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

    /**
     * What the Hydroponics Bay does with one plantable: what it yields, and what it throws off besides.
     *
     * <p>Ships in {@code data/recompile/data_maps/item/hydroponic_crop.json}. <b>Both fields are optional
     * and the default is the elegant case</b> - an entry-less plantable yields itself and nothing else,
     * which is exactly right for sugar cane, cactus, bamboo, berries, kelp and the rest. So adding a plant
     * to {@code #recompile:hydroponic} is still the whole of what makes it growable, and this map exists
     * only for the plants that need more than that.
     *
     * <p>Which is the seed-based ones. <b>A seed-based crop is planted as its seed</b>, the same as in the
     * ground: wheat grows from wheat seeds and yields wheat, not the other way round, and a wheat item is
     * not something you can plant. That mapping cannot come from the tag, because the tag says what goes
     * in and this says what comes out.
     *
     * <p><b>Synced, unlike {@link #BAIT_WEIGHT}.</b> JEI runs on the client and its Hydroponics category
     * lists both the yield and the byproduct; without the sync {@code getData} returns null there and
     * every crop silently reads as producing itself with nothing else. That is the exact failure the bait
     * map's comment warns about, and this is the consumer it was warning about.
     */
    public static final DataMapType<Item, Crop> HYDROPONIC_CROP = DataMapType
        .builder(Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "hydroponic_crop"),
            Registries.ITEM, Crop.CODEC)
        .synced(Crop.CODEC, true)
        .build();

    /**
     * What the Sell Terminal pays for one of an item, in company scrip (spec
     * {@code docs/market_spec.md}, #311).
     *
     * <p>Ships in {@code data/recompile/data_maps/item/scrip_value.json}. A bare integer per entry,
     * and a tag key works the way it does in any data map, which is how sixteen colours of Clean
     * Mattress are one line.
     *
     * <p><b>Membership is the tag, not this map.</b> {@code #recompile:sellable} says what may be sold
     * because that is the ruling and a tag is what a pack already knows how to extend; this says what
     * it is worth because a tag cannot carry a number. A tag member with no entry here is a build
     * failure ({@code every_sellable_item_has_a_price}) and is refused at the slot, never bought for
     * nothing. Prices are flat per product and first-pass; tuning belongs with the balance pass (#36).
     *
     * <p><b>Synced</b>, because the sell screen quotes the price before the sale and the slot refuses
     * unpriced goods on both sides. Without the sync the client would read null and every item would
     * be refused at the client while the server accepted it.
     */
    public static final DataMapType<Item, Integer> SCRIP_VALUE = DataMapType
        .builder(Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "scrip_value"),
            Registries.ITEM, Codec.intRange(1, com.flatts.recompile.content.market.Market.MAX_BALANCE))
        .synced(Codec.INT, true)
        .build();

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RCDataMaps::onRegisterDataMaps);
    }

    private static void onRegisterDataMaps(RegisterDataMapTypesEvent event) {
        event.register(BAIT_WEIGHT);
        event.register(HYDROPONIC_CROP);
        event.register(SCRIP_VALUE);
    }

    /**
     * One plantable's harvest.
     *
     * @param yields    what a batch produces; empty means the plantable itself
     * @param byproduct what else comes off it; empty means nothing does
     */
    public record Crop(Optional<Item> yields, Optional<Byproduct> byproduct) {

        public static final Codec<Crop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("yields").forGetter(Crop::yields),
            Byproduct.CODEC.optionalFieldOf("byproduct").forGetter(Crop::byproduct)
        ).apply(instance, Crop::new));
    }

    /**
     * One crop's byproduct.
     *
     * @param item   what comes off it
     * @param count  how many per batch that produces one
     * @param chance 0..1, rolled per batch - vanilla's poisonous potato is 0.02
     */
    public record Byproduct(Item item, int count, float chance) {

        public static final Codec<Byproduct> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Byproduct::item),
            // Count and chance are optional so the common case - "one of these, every time" - is a single
            // line. Seeds off wheat want exactly that; only the poisonous potato needs a chance.
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Byproduct::count),
            Codec.floatRange(0.0f, 1.0f).optionalFieldOf("chance", 1.0f).forGetter(Byproduct::chance)
        ).apply(instance, Byproduct::new));
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
