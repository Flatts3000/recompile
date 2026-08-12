package com.flatts.recompile.content.recipe;

import com.flatts.recompile.registry.RCRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * The public {@code recompile:teardown} recipe type - the data spine of the whole
 * mod (design P0.5, "teardown table schema"). One found item is torn down at a
 * station into a fixed deterministic core ({@code results}) plus weighted bonus
 * outputs ({@code extras}), and may reveal one or more recipes to study
 * ({@code teaches}). All datapack JSON; packs and addons extend the teardown tree
 * without a mod release.
 *
 * <p>JSON shape ({@code data/<ns>/recipe/<name>.json}):
 * <pre>{@code
 * {
 *   "type": "recompile:teardown",
 *   "input": "minecraft:iron_door",
 *   "station": "recompile:workbench",
 *   "results": [ { "item": "minecraft:iron_nugget", "count": 2 } ],
 *   "extras":  [ { "item": "minecraft:iron_bars", "chance": 0.35 } ],
 *   "teaches": [ { "recipe": "minecraft:iron_door", "chance": 0.25, "scraps_required": 3 } ]
 * }
 * }</pre>
 *
 * <p>Notes on the schema:
 * <ul>
 *   <li>{@code results} is the deterministic material core and must be non-empty:
 *       every teardown yields materials (even data recovery gives e-scrap).
 *       Knowledge-only acquisition is via loot caches (schematic drops), not a
 *       material-less teardown, so an empty {@code results} is a load error.
 *   <li>{@code input} is an {@link Ingredient}: a bare item id string
 *       ({@code "minecraft:iron_door"}), a tag with a {@code #} prefix
 *       ({@code "#c:ingots/copper"}), or a JSON array of item ids. Tag-keyed inputs
 *       are what P2.8 leans on to cover thousands of items with one rule.
 *   <li>{@code station} is a tier-gate string (hand / tarp / machine / advanced;
 *       {@code recompile:workbench} is the bench). One format covers the whole
 *       progression. Defaults to the workbench.
 *   <li>{@code teaches} ships from day one even though the knowledge system is P1,
 *       so the knowledge axis is never retrofitted into a live schema.
 *       {@code scraps_required} is the deterministic study-point threshold (how many
 *       teardowns complete the study); {@code chance} is acceleration only (a lucky
 *       insight grants extra progress) - there is no bad-streak failure. The materials
 *       workbench (P1.4, built) ignores {@code teaches} entirely - it is dormant until
 *       the knowledge/function axis is decided.
 *   <li>{@code tool} (optional) is an {@link Ingredient} naming the tool that must be
 *       racked at the bench to run this teardown (e.g. {@code "recompile:scrap_knife"});
 *       omit it for a no-tool teardown. {@code ticks} (optional, default
 *       {@value #DEFAULT_TICKS}) is how long the player holds to complete one breakdown.
 * </ul>
 *
 * <p>The item-shaped {@link Recipe} surfaces ({@link #assemble}) return the primary
 * result for recipe-book sanity, but the workbench (P1.4) reads {@link #results()},
 * {@link #extras()}, and {@link #teaches()} directly. {@link #isSpecial()} is true so
 * the recipe book does not try to surface it.
 */
public class TeardownRecipe implements Recipe<SingleRecipeInput> {

    /** The default station id when a table omits {@code station}. */
    public static final String DEFAULT_STATION = "recompile:workbench";

    /** The default breakdown time (ticks) when a table omits {@code ticks}. 80 = 4s. */
    public static final int DEFAULT_TICKS = 80;

    /** A fixed, deterministic core output: an item and a count. */
    public record ItemResult(Item item, int count) {
        public static final Codec<ItemResult> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemResult::item),
            Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(ItemResult::count)
        ).apply(i, ItemResult::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ItemResult> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), ItemResult::item,
                ByteBufCodecs.VAR_INT, ItemResult::count,
                ItemResult::new
            );

        public ItemStack toStack() {
            return new ItemStack(item, count);
        }
    }

    /** A chance-weighted bonus output. Which extras can drop is later influenced by racked tools. */
    public record ChanceResult(Item item, float chance) {
        public static final Codec<ChanceResult> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ChanceResult::item),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ChanceResult::chance)
        ).apply(i, ChanceResult::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ChanceResult> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), ChanceResult::item,
                ByteBufCodecs.FLOAT, ChanceResult::chance,
                ChanceResult::new
            );
    }


    /**
     * One weighted entry in a {@link Pool}. An entry with no {@code item} is the empty slot that
     * makes a pool sometimes give nothing - the same idiom the mod's pull streams use.
     */
    public record PoolEntry(Optional<Item> item, int weight, int count) {
        public static final Codec<PoolEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(PoolEntry::item),
            Codec.intRange(1, 100000).optionalFieldOf("weight", 1).forGetter(PoolEntry::weight),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(PoolEntry::count)
        ).apply(i, PoolEntry::new));
    }

    /**
     * A weighted draw: {@code rolls} independent picks from {@code entries}.
     *
     * <p><b>Pick-one, not chance-each, and that is the whole reason this exists.</b> {@link
     * ChanceResult} rolls every extra independently, so it cannot express "one of motor, pump or
     * bulb" - it gives none of them a quarter of the time and two of them another quarter. A pool
     * draws exactly {@code rolls} times from the weights, which is how every other random table in
     * this mod already works.
     *
     * <p>{@code rolls} is also how quantity is expressed: six rolls over plastic, metal and e-scrap
     * gives six scrap split by weight, differently each time, rather than a fixed pile.
     *
     * <p><b>{@code teaches} couples knowledge to the draw.</b> A teaching pool grants an Idea
     * Fragment for the recipe whose id matches the item it just produced - so tearing a fridge down
     * and getting a motor teaches the motor, and getting a bulb teaches the bulb. That identity is
     * real rather than a convention worth checking: every component blueprint in this mod is
     * registered at the same id as the component it makes.
     */
    public record Pool(int rolls, boolean teaches, int scrapsRequired, List<PoolEntry> entries) {
        public static final Codec<Pool> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 256).optionalFieldOf("rolls", 1).forGetter(Pool::rolls),
            Codec.BOOL.optionalFieldOf("teaches", false).forGetter(Pool::teaches),
            Codec.intRange(1, 99).optionalFieldOf("scraps_required", 4).forGetter(Pool::scrapsRequired),
            ExtraCodecs.nonEmptyList(PoolEntry.CODEC.listOf()).fieldOf("entries").forGetter(Pool::entries)
        ).apply(i, Pool::new));

        public int totalWeight() {
            int total = 0;
            for (PoolEntry entry : entries) {
                total += entry.weight();
            }
            return total;
        }

        /** One draw, or empty for the filler slot. */
        public Optional<PoolEntry> draw(RandomSource random) {
            int total = totalWeight();
            if (total <= 0) {
                return Optional.empty();
            }
            int pick = random.nextInt(total);
            for (PoolEntry entry : entries) {
                pick -= entry.weight();
                if (pick < 0) {
                    return entry.item().isPresent() ? Optional.of(entry) : Optional.empty();
                }
            }
            return Optional.empty();
        }
    }

    /**
     * A recipe this teardown can reveal. {@code scrapsRequired} is the deterministic
     * study threshold; {@code chance} is acceleration-only bonus progress.
     */
    public record TeachEntry(Identifier recipe, float chance, int scrapsRequired) {
        public static final Codec<TeachEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("recipe").forGetter(TeachEntry::recipe),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 0.0F).forGetter(TeachEntry::chance),
            Codec.intRange(1, 99).optionalFieldOf("scraps_required", 1).forGetter(TeachEntry::scrapsRequired)
        ).apply(i, TeachEntry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, TeachEntry> STREAM_CODEC =
            StreamCodec.composite(
                Identifier.STREAM_CODEC, TeachEntry::recipe,
                ByteBufCodecs.FLOAT, TeachEntry::chance,
                ByteBufCodecs.VAR_INT, TeachEntry::scrapsRequired,
                TeachEntry::new
            );
    }

    public static final MapCodec<TeardownRecipe> CODEC = RecordCodecBuilder.<TeardownRecipe>mapCodec(instance -> instance.group(
        Ingredient.CODEC.fieldOf("input").forGetter(TeardownRecipe::input),
        Codec.STRING.optionalFieldOf("station", DEFAULT_STATION).forGetter(TeardownRecipe::station),
        ItemResult.CODEC.listOf().optionalFieldOf("results", List.of()).forGetter(TeardownRecipe::results),
        ChanceResult.CODEC.listOf().optionalFieldOf("extras", List.of()).forGetter(TeardownRecipe::extras),
        Pool.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter(TeardownRecipe::pools),
        TeachEntry.CODEC.listOf().optionalFieldOf("teaches", List.of()).forGetter(TeardownRecipe::declaredTeaches),
        Ingredient.CODEC.optionalFieldOf("tool").forGetter(TeardownRecipe::tool),
        Codec.intRange(1, 72000).optionalFieldOf("ticks", DEFAULT_TICKS).forGetter(TeardownRecipe::ticks)
    ).apply(instance, TeardownRecipe::new)).validate(TeardownRecipe::mustProduceSomething);

    /**
     * {@code results} was a nonEmptyList before {@code pools} existed, and that guard has to survive
     * the field becoming optional: a teardown that yields nothing is a load error, not a quiet no-op.
     * "Non-empty" now means EITHER form, so an old recipe and a pooled one are both valid while a
     * recipe with neither still fails loudly at datapack load.
     */
    private static DataResult<TeardownRecipe> mustProduceSomething(TeardownRecipe recipe) {
        if (recipe.results().isEmpty() && recipe.pools().isEmpty()) {
            return DataResult.error(() ->
                "a teardown must produce something: give it \"results\", \"pools\", or both");
        }
        return DataResult.success(recipe);
    }

    // Bridged from the map codec rather than composed field-by-field: recipes sync once on
    // join, and this has no component-arity ceiling as the schema grows (the knowledge axis
    // will add more fields). The nested records' STREAM_CODECs are kept as wire-shape docs.
    public static final StreamCodec<RegistryFriendlyByteBuf, TeardownRecipe> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private final Ingredient input;
    private final String station;
    private final List<ItemResult> results;
    private final List<ChanceResult> extras;
    private final List<Pool> pools;
    private final List<TeachEntry> declaredTeaches;
    private final List<TeachEntry> teaches;
    private final Optional<Ingredient> tool;
    private final int ticks;

    public TeardownRecipe(Ingredient input, String station, List<ItemResult> results,
                          List<ChanceResult> extras, List<Pool> pools, List<TeachEntry> teaches,
                          Optional<Ingredient> tool, int ticks) {
        this.input = input;
        this.station = station;
        this.results = List.copyOf(results);
        this.extras = List.copyOf(extras);
        this.pools = List.copyOf(pools);
        this.declaredTeaches = List.copyOf(teaches);

        // teaches() reports the declared entries PLUS one per teaching-pool entry, so everything
        // downstream - fragment assembly, the guidebook checks, JEI - sees the full set of recipes
        // this teardown can reveal without any of them learning about pools. Only the bench's
        // GRANTING needs to know the difference, because a pool teaches whichever item it drew.
        List<TeachEntry> all = new java.util.ArrayList<>(this.declaredTeaches);
        for (Pool pool : this.pools) {
            if (!pool.teaches()) {
                continue;
            }
            for (PoolEntry entry : pool.entries()) {
                entry.item().ifPresent(item -> {
                    Identifier id = BuiltInRegistries.ITEM.getKey(item);
                    if (all.stream().noneMatch(e -> e.recipe().equals(id))) {
                        all.add(new TeachEntry(id, 0.0F, pool.scrapsRequired()));
                    }
                });
            }
        }
        this.teaches = List.copyOf(all);
        this.tool = tool;
        this.ticks = ticks;
    }

    /** Every pool this teardown draws from. */
    public List<Pool> pools() {
        return pools;
    }

    /**
     * Only the entries written in the {@code teaches} field.
     *
     * <p>The bench grants these unconditionally on their own {@code chance}; pool-taught recipes are
     * granted by the draw instead, so iterating {@link #teaches()} to hand out fragments would teach
     * every component at once - the exact opposite of "you learn the one you pulled out".
     */
    public List<TeachEntry> declaredTeaches() {
        return declaredTeaches;
    }

    public Ingredient input() {
        return input;
    }

    public String station() {
        return station;
    }

    public List<ItemResult> results() {
        return results;
    }

    public List<ChanceResult> extras() {
        return extras;
    }

    public List<TeachEntry> teaches() {
        return teaches;
    }

    /** The tool that must be racked at the bench to run this teardown; empty = no tool needed. */
    public Optional<Ingredient> tool() {
        return tool;
    }

    /** Ticks the player must hold to complete this breakdown (default {@link #DEFAULT_TICKS}). */
    public int ticks() {
        return ticks;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).toStack();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<TeardownRecipe> getSerializer() {
        return RCRecipeTypes.TEARDOWN_SERIALIZER.get();
    }

    @Override
    public RecipeType<TeardownRecipe> getType() {
        return RCRecipeTypes.TEARDOWN.get();
    }
}
