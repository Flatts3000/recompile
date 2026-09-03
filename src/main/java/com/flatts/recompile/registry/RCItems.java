package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.AnimalBaitBlock.Diet;
import com.flatts.recompile.content.item.AmberItem;
import com.flatts.recompile.content.item.AnimalBaitItem;
import com.flatts.recompile.content.item.CuttingTorchItem;
import com.flatts.recompile.content.item.FertilizerItem;
import com.flatts.recompile.content.item.GarbageVacuumItem;
import com.flatts.recompile.content.item.OpenedCanItem;
import com.flatts.recompile.content.item.UnknownSeedlingItem;
import com.flatts.recompile.content.item.SealedCanItem;
import com.flatts.recompile.content.item.VacuumTier;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry. Phase 1 adds the base material vocabulary (design P0.4) - the
 * seven sorted-material items every machine tier speaks: scrap metal, plastic
 * scrap, glass shards, organic muck, fiber scrap, e-scrap (rare), and junk (the
 * filler majority; fuel later, not worthless).
 *
 * <p>Uses the factory form ({@code registerItem(name, factory)}) because MC 26.1
 * sets the {@code ResourceKey} on Properties before the item constructor runs.
 */
public final class RCItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(Recompile.MOD_ID);

    // ---------------- Base material vocabulary (P0.4) ----------------
    /**
     * A bucket of leachate (#156). Exists because a fluid without a bucket cannot be picked up, moved,
     * or looked at in creative - not because carrying it around is a designed activity.
     *
     * <p>Placing it does not irrigate: that is a property of the fluid, not of how it got there
     * (see {@link RCFluids}).
     */
    public static final DeferredItem<BucketItem> LEACHATE_BUCKET = ITEMS.registerItem(
        "leachate_bucket",
        props -> new BucketItem(RCFluids.LEACHATE.get(), props),
        () -> new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)
    );

    /**
     * Bulb: the first CRAFTING component (owner, 2026-08-06), as opposed to the placeable ones.
     * Found in household sorting, and spent as an ingredient rather than stacked into a structure -
     * the Hydroponics Bay needs one, because a bay under a mound has no other light.
     */
    public static final DeferredItem<Item> BULB = ITEMS.registerItem("bulb", Item::new);

    /**
     * Depleted Battery: a dead household cell, found loose in the garbage (owner, 2026-09-03).
     *
     * <p>It is not a component and cannot be spent as one. It is a <b>teardown input</b>: cut one open
     * at the workbench for scrap and, four of them in, the knowledge of how to build a live one. That
     * is the whole of what it does, and it is the most literal possible statement of the mod's thesis -
     * the dead thing in the bin is where the idea comes from.
     *
     * <p><b>This is also what settles the Water Tank problem</b> (#229: a component named for a
     * capacity that holds nothing reads as broken). A DEPLETED battery holding nothing is not a bug,
     * it is the noun. The live {@link #BATTERY} you make from it is an ingredient you spend, which is
     * what a crafting component is.
     */
    public static final DeferredItem<Item> DEPLETED_BATTERY =
        ITEMS.registerItem("depleted_battery", Item::new);

    /**
     * Battery: the second CRAFTING component, and the power cell the Garbage Vacuum and the Charging
     * Station are both built around (#336).
     *
     * <p><b>Manufactured, never found</b>, which makes it the first component of its kind here. The
     * Pump, Motor and Bulb are salvage first and blueprint second; this one is blueprint ONLY, because
     * what the garbage gives you is a {@link #DEPLETED_BATTERY} and a dead cell is not a live one. So
     * it is deliberately absent from {@code ComponentBlueprintTests}' salvage-and-blueprint list and
     * covered instead by {@code a_blueprint_result_has_no_other_route}, which is the sweep that
     * actually means something for it.
     *
     * <p>It gates the whole powered-tool tier: no cell, no charger, no vacuum.
     */
    public static final DeferredItem<Item> BATTERY = ITEMS.registerItem("battery", Item::new);

    public static final DeferredItem<Item> SCRAP_METAL = ITEMS.registerItem("scrap_metal", Item::new);
    public static final DeferredItem<Item> PLASTIC_SCRAP = ITEMS.registerItem("plastic_scrap", Item::new);
    public static final DeferredItem<Item> GLASS_SHARDS = ITEMS.registerItem("glass_shards", Item::new);
    public static final DeferredItem<Item> ORGANIC_MUCK = ITEMS.registerItem("organic_muck", Item::new);
    public static final DeferredItem<Item> FIBER_SCRAP = ITEMS.registerItem("fiber_scrap", Item::new);
    public static final DeferredItem<Item> E_SCRAP = ITEMS.registerItem("e_scrap", Item::new);
    public static final DeferredItem<Item> JUNK = ITEMS.registerItem("junk", Item::new);

    /**
     * Cardboard: the paper fibre, kept separate from {@link #FIBER_SCRAP}, which is the cloth one.
     *
     * <p><b>Its own material rather than a derivative</b> (#309). Deriving it from fiber scrap would
     * make the two harder to tell apart than the shared compost route already does, and cardboard is
     * the single commonest thing in household waste - it should come out of the mound, not out of
     * something else that came out of the mound.
     *
     * <p><b>Found, not crafted, like all seven of its siblings.</b> An earlier draft of this comment
     * claimed the opposite - that building materials stay craftable - which is contradicted by every
     * other base material in the game: junk, scrap metal, rebar, plastic scrap, glass shards, fiber
     * scrap, organic muck and e-scrap are all in {@code #recompile:found_only}. Cardboard is too, and
     * the source is {@link com.flatts.recompile.content.block.CardboardPileBlock}. What P2.11's rule
     * actually permits is the BLOCK family being craftable from the material, which it is.
     *
     * <p>That the paper form ({@code minecraft:paper}) is in the same tag is consistent rather than
     * a coincidence: a sheet of writing paper is a product, a flattened box is stock.
     */
    public static final DeferredItem<Item> CARDBOARD = ITEMS.registerItem("cardboard", Item::new);

    /** The eight base materials in canonical order (creative tab + docs use this). */
    public static final List<DeferredItem<Item>> BASE_MATERIALS = List.of(
        SCRAP_METAL, PLASTIC_SCRAP, GLASS_SHARDS, ORGANIC_MUCK, FIBER_SCRAP, E_SCRAP, JUNK, CARDBOARD);

    // ---------------- Demolition yard: stone shards (frontier) ----------------
    // Sifted out of Rubble (one per vanilla stone type), assembled back into their stone block at the
    // Scrap Crafting Table. The stone half of the demolition yard's "stone + iron" goal.
    public static final DeferredItem<Item> STONE_SHARD = ITEMS.registerItem("stone_shard", Item::new);
    public static final DeferredItem<Item> GRANITE_SHARD = ITEMS.registerItem("granite_shard", Item::new);
    public static final DeferredItem<Item> DIORITE_SHARD = ITEMS.registerItem("diorite_shard", Item::new);
    public static final DeferredItem<Item> ANDESITE_SHARD = ITEMS.registerItem("andesite_shard", Item::new);
    public static final DeferredItem<Item> DEEPSLATE_SHARD = ITEMS.registerItem("deepslate_shard", Item::new);
    public static final DeferredItem<Item> TUFF_SHARD = ITEMS.registerItem("tuff_shard", Item::new);
    public static final DeferredItem<Item> CALCITE_SHARD = ITEMS.registerItem("calcite_shard", Item::new);

    /** Stone shards in creative-tab order. */
    public static final List<DeferredItem<Item>> STONE_SHARDS = List.of(
        STONE_SHARD, GRANITE_SHARD, DIORITE_SHARD, ANDESITE_SHARD, DEEPSLATE_SHARD, TUFF_SHARD, CALCITE_SHARD);

    // ---------------- The compacted depths: nether terrain shards ----------------
    // The same trade the demolition yard makes for stone, in the dimension that has no terrain of its
    // own to mine. The depths generate NO vanilla nether blocks - no netherrack, no basalt, no soul
    // sand - so every one of them is rebuilt four shards at a time out of Slag Rubble. That is what
    // makes the dimension worth mining rather than a place to pass through.
    //
    // The two soul blocks are CLUMPS rather than shards: soul sand and soul soil are granular, and a
    // "shard" of sand reads as a mistake. Same reason the overworld's sand arrives whole from
    // Reinforced Concrete instead of as a shard.
    public static final DeferredItem<Item> NETHERRACK_SHARD = ITEMS.registerItem("netherrack_shard", Item::new);
    public static final DeferredItem<Item> BASALT_SHARD = ITEMS.registerItem("basalt_shard", Item::new);
    public static final DeferredItem<Item> BLACKSTONE_SHARD = ITEMS.registerItem("blackstone_shard", Item::new);
    public static final DeferredItem<Item> SOUL_SAND_SHARD = ITEMS.registerItem("soul_sand_shard", Item::new);
    public static final DeferredItem<Item> SOUL_SOIL_SHARD = ITEMS.registerItem("soul_soil_shard", Item::new);

    /**
     * A Sky Stone Shard (#276): a piece of the meteorite buried in the compacted depths.
     *
     * <p><b>It is only worth anything with AE2 installed</b>, which is the one uncomfortable thing
     * about it. Four craft an {@code ae2:sky_stone_block}, and that recipe is guarded, so without AE2
     * the shard has no use at all. CLAUDE.md: a find that is neither useful nor wanted is clutter.
     *
     * <p><b>So the DROP is gated, by {@code loot_modifiers/no_sky_stone.json}, which strips this item
     * from every loot roll when AE2 is absent.</b> The shard is named directly in
     * {@code slag_rubble_pulls} - it is our own item, so the id always resolves - in a pool of its
     * own, and the modifier takes it back out again. Gating the pull stream's ENTRY instead was tried
     * and measured wrong twice: a condition on a tag file is silently ignored in 26.1, and a
     * conditional nested table leaves the entry in place, so it kept winning 15 rolls in 405 and
     * handing back nothing - a silent one-in-27 empty pull in the default install. See
     * {@code StripItemModifier} for the whole account.
     *
     * <p><b>The item itself is registered unconditionally and that is deliberate.</b> It is in the
     * creative tab and JEI's item list whether or not AE2 is present, because a registry entry that
     * comes and goes with another mod breaks every world that ever held one. Only the drop and the
     * recipe are conditional, which is the half a player can notice.
     *
     * <p>It is a shard rather than the block dropping whole because every entry in the terrain pool of
     * {@code slag_rubble_pulls} is a shard, four to a block. Breaking that pattern for one material
     * would read as an oversight rather than a decision (owner, 2026-08-20).
     */
    public static final DeferredItem<Item> SKY_STONE_SHARD = ITEMS.registerItem("sky_stone_shard", Item::new);

    // ---------------- The compacted depths: scrap categories ----------------
    // The dump gives SCRAP; machines give materials. These are the Nether's three, and each feeds one
    // machine verb: circuitry SEPARATES into quartz (a board really is a mixture), phosphor
    // PULVERIZES into glowstone dust (a lamp really is coated in it), organics SEPARATE into nether
    // wart (a fungus really does outlast everything else in a dump).
    //
    // E-waste at civilisational scale rather than science fiction, deliberately: server racks, lamps
    // and screens, and the biological matter cooked in with them. Every one has a real-world referent,
    // which is the standard material_economy.md holds the whole matrix to.
    public static final DeferredItem<Item> FUSED_CIRCUITRY = ITEMS.registerItem("fused_circuitry", Item::new);
    public static final DeferredItem<Item> PHOSPHOR_SCRAP = ITEMS.registerItem("phosphor_scrap", Item::new);
    public static final DeferredItem<Item> RENDERED_ORGANICS = ITEMS.registerItem("rendered_organics", Item::new);

    /**
     * Oily Swarf: the chip waste off a lathe, still soaked in cutting fluid.
     *
     * <p><b>It answers to two verbs, which is why it is one item and not two.</b> Magnesium and
     * titanium swarf is a real fire hazard - fine enough to ignite in air - so the mill reduces it to
     * blaze powder. And swarf really does come off the tool wet, which is why real swarf recycling
     * begins by separating the metal from the oil - so the separator divides it into magma cream and
     * the chips themselves.
     *
     * <p>Slag already works this way (separating, pulverizing and vitrifying into three different
     * things), so one material serving several machines is the established shape rather than a
     * special case. It also gives magma cream a route that is not a mob drop: vanilla wants a slime ball for it, which
     * this world has no slime.
     */
    public static final DeferredItem<Item> OILY_SWARF = ITEMS.registerItem("oily_swarf", Item::new);

    /**
     * Lignite: brown coal, and the only source of coal in this world (owner, 2026-08-19; #226).
     *
     * <p>It sits in the waste table DIRECTLY, which every other material there has to earn, and it
     * earns it the same way slag and netherite scrap do - by being <b>already unprocessed</b>. Lignite
     * is not a stand-in for coal, it is a real rank of it: the one between peat and the bituminous coal
     * vanilla simply calls "coal". Finding a lump of unfinished coal in buried compacted organics is
     * not a shortcut past a machine, it is what that ground is made of.
     *
     * <p>Which is also why it comes from here rather than the demolition yard, the other candidate
     * #226 offered. The compacted depths are buried organic waste under heat and weight; that is not
     * <i>like</i> a coal seam forming, it is one. A demolition yard is a place things fell over.
     *
     * <p>It burns on its own at half a coal's worth, so it is useful the moment you find it and better
     * once you cook it - the depths' only solid fuel, in a dimension with no wood.
     *
     * <p>Deliberately NOT in {@code #minecraft:coals}: that tag is what torches and campfires take, and
     * admitting lignite there would let the unfinished thing do the finished thing's job. Upgrade it
     * first. (This world's torch does not want coal anyway - it burns an Oily Rag.)
     */
    public static final DeferredItem<Item> LIGNITE = ITEMS.registerItem("lignite", Item::new);

    /**
     * A Worn Forging Die: the compacted depths' one findable OBJECT, and the bootstrap for netherite
     * gear (#249).
     *
     * <p>A forging die is the block a drop forge presses metal against, and the pattern lives in the
     * die. That is what a smithing template is, so this is not an analogy - it is the same object doing
     * the same job, which is the standard {@code material_economy.md} holds this mod to.
     *
     * <p><b>It exists because vanilla's netherite template cannot be made.</b> The only recipe for one
     * consumes one (seven diamond, netherrack, and a template, for two), so its sole starting point is
     * a bastion chest. Netherite scrap comes out of the waste and gold off the E-Scrap chain, which
     * means a player could craft ingots and upgrade nothing at all.
     *
     * <p>Everything else in the depths is a material you process. This is a thing you take apart, and
     * what survives a worn-out die is its SHAPE rather than its steel - so it teaches rather than
     * drops, which is the mod's thesis in one item.
     *
     * <p><b>Deliberately NOT in {@link #DEPTHS_SCRAP}, and therefore not binnable.</b> That list means
     * "bulk material out of the depths", and everything on it belongs in a Scrap Bin. A die is a find
     * you tear down, the same class of thing as a bucket or a tin can out of a household mound, and
     * none of those are binnable either. {@code every_shard_and_scrap_is_binnable} caught this when it
     * was first filed under scrap - the tag was not the defect, the membership was.
     */
    public static final DeferredItem<Item> WORN_FORGING_DIE =
        ITEMS.registerItem("worn_forging_die", Item::new);

    /**
     * Sculk powder: what a broken seam of Ancient Sculk leaves, and the whole deep dark in one item.
     *
     * <p>It crafts the sculk family rather than being smelted or milled, which is a departure from the
     * depths' other materials and a deliberate one - every scrap down there is a FEED for a machine,
     * and this is a MATERIAL you shape. Slag Rubble's terrain shards are the nearer relative, though
     * those are pieces of the block they came from and this is not.
     *
     * <p>The catalyst is the expensive recipe (owner, 2026-08-20) and it costs an <b>echo shard</b>,
     * which is a sewer's one prize. That is the design rather than a price: a catalyst grows sculk
     * wherever something dies near it, so one is a forever supply, and the only fitting cost is the
     * other thing in this world that came out of the deep dark.
     */
    public static final DeferredItem<Item> SCULK_POWDER = ITEMS.registerItem("sculk_powder", Item::new);

    /**
     * Fossilised tree resin with something trapped in it (#294). The entry point to the whole spawner
     * chain, and the reason this mod has a use for a creature that died before the dump existed.
     *
     * <p><b>Found in the ordinary garbage rather than in the depths</b>, and that is pacing rather
     * than geology. The Broken Spawner is down below, so an amber found up top is a reason to make
     * the trip; put both in the same place and the chain has no early hook at all.
     *
     * <p>Carries {@link RCDataComponents#SPECIES}. A piece with no species is inert and says so - see
     * {@link com.flatts.recompile.content.item.AmberItem}.
     */
    public static final DeferredItem<Item> AMBER = ITEMS.registerItem("amber", AmberItem::new);

    /**
     * What the Sequencer leaves after it has read the creature out (#231).
     *
     * <p><b>The inert body of the resin chain, and the reason the chain does not tax the spawn eggs.</b>
     * Reading an amber used to destroy it. It now hands the husk back, so the two things a piece of
     * amber is good for compose rather than compete for the same drop: you take the DNA out, and what
     * is left is the material. Every amber in both pull streams is stamped, so without this a clump of
     * resin would cost a spawn egg's worth of sequencing.
     *
     * <p>Useless on its own, exactly like Grog. Amber is polymerised and cross-linked and cannot be
     * softened back into fresh sap - the fired-clay problem verbatim, which this mod refused once
     * already. {@link #TURPENTINE} is what makes it workable, because turpentine IS the volatile
     * terpene fraction that fossilisation drove off. The pair does not reverse anything; it puts back
     * the one thing that left.
     */
    public static final DeferredItem<Item> SPENT_AMBER = ITEMS.registerItem("spent_amber", Item::new);

    /**
     * A half-used tin of turpentine (#231, owner 2026-08-29).
     *
     * <p><b>Found, never made</b>, and it passes the P2.11 test without argument: a tin of solvent
     * going off in a shed is exactly what a person throws away. It is in {@code #recompile:found_only}
     * and {@code FoundNotCraftedTests} holds both halves of that - no recipe may make one, and it must
     * have a loot table that does.
     *
     * <p>It exists to answer the one question that blocked #231 for eleven days: option A needed
     * something to supply what fossilisation removed, and nothing in the material set fit. Turpentine
     * is distilled from pine resin, so it is not a stand-in for the missing fraction, it is that
     * fraction.
     */
    public static final DeferredItem<Item> TURPENTINE = ITEMS.registerItem("turpentine", Item::new);

    /**
     * A spawner cage with the soul burned out of it (#294). Tear it down at the Workbench to learn how
     * the cage was made; the creature to put in it comes from Amber.
     *
     * <p><b>This mod's own item, not Ender IO's.</b> Ender IO ships {@code enderio:broken_spawner} and
     * it was the obvious candidate, but this mod never REQUIRES another mod - and Ender IO's feeds its
     * Powered Spawner rather than repairing a vanilla one, so building on it would mean adopting that
     * ecosystem instead of the vanilla retype this chain is made of. Both can be installed at once.
     */
    public static final DeferredItem<Item> BROKEN_SPAWNER =
        ITEMS.registerItem("broken_spawner", Item::new);

    /** The depths' scrap in creative-tab order. */
    public static final List<DeferredItem<Item>> DEPTHS_SCRAP = List.of(
        FUSED_CIRCUITRY, PHOSPHOR_SCRAP, RENDERED_ORGANICS, OILY_SWARF, LIGNITE);

    /**
     * The two nylium shards, and they carry more than their own block.
     *
     * <p>Nylium is netherrack with a fungal crust, so it is terrain and belongs with the shards. What
     * makes it the most load-bearing entry in the rubble table is what vanilla does next: bone meal on
     * nylium grows roots and fungus, and a fungus bone-mealed on its own nylium becomes a HUGE fungus -
     * stems, shroomlight, weeping vines. The compacted depths grow no fungus forest, so these two are
     * the only seed for one.
     */
    public static final DeferredItem<Item> CRIMSON_NYLIUM_SHARD =
        ITEMS.registerItem("crimson_nylium_shard", Item::new);
    public static final DeferredItem<Item> WARPED_NYLIUM_SHARD =
        ITEMS.registerItem("warped_nylium_shard", Item::new);

    /** Nether terrain shards in creative-tab order. */
    public static final List<DeferredItem<Item>> NETHER_SHARDS = List.of(
        NETHERRACK_SHARD, BASALT_SHARD, BLACKSTONE_SHARD, SOUL_SAND_SHARD, SOUL_SOIL_SHARD,
        CRIMSON_NYLIUM_SHARD, WARPED_NYLIUM_SHARD, SKY_STONE_SHARD);

    /**
     * Fertilizer (Mod Jam - the fertilizer tier): the Compost Heap's output, composted from muck +
     * fiber. It is the gate the Vegetation and Farming tiers consume - never crafted, only composted.
     */
    public static final DeferredItem<FertilizerItem> FERTILIZER =
        ITEMS.registerItem("fertilizer", FertilizerItem::new);
    // A compost volunteer: plant it like a seed and it becomes a random vanilla crop at plant time.
    public static final DeferredItem<UnknownSeedlingItem> UNKNOWN_SEEDLING =
        ITEMS.registerItem("unknown_seedling", UnknownSeedlingItem::new);

    // Oily Rag (P1.4-A): fiber + muck, the trash world's "coal" - a general fuel that burns in
    // any furnace (charcoal parity) and is the head of the Scrap Torch. No consumer forces it
    // yet (no furnace/burn barrel exists), so it is the fuel primitive, ready for the burn tier.
    public static final DeferredItem<Item> OILY_RAG = ITEMS.registerItem("oily_rag", Item::new);

    // ---------------- Crafting components + trash-tier tools (P1.2) ----------------
    // Rebar is the universal handle (the analog of vanilla sticks) - drops from the
    // scrap-metal pull stream. Tools are tier-zero (stone-class); no pickaxe, on
    // purpose (nothing to mine). The knife cuts bales, the prybar digs out Bulky Waste
    // (and is a weak weapon), the junk shovel digs garbage fast.
    public static final DeferredItem<Item> REBAR = ITEMS.registerItem("rebar", Item::new);
    /**
     * Steel Offcut: what a Cutting Torch leaves behind when it cuts a Steel I-Beam. Graded scrap, the way
     * real demolition steel comes off a site (ISRI calls it Plate &amp; Structural), NOT ore - recycled
     * structural steel is already-reduced metal and is remelted, never returned to ore. It remelts to iron
     * in the Cupola Furnace (#50) and nowhere else, which is what makes iron a gated upgrade.
     */
    public static final DeferredItem<Item> STEEL_OFFCUT = ITEMS.registerItem("steel_offcut", Item::new);

    /**
     * Slag: what is left on top of a melt, and the one material in this mod nobody chooses to make.
     *
     * <p><b>Slag is a byproduct, not a product</b> (owner, 2026-08-18, #236). It is the non-metallic
     * fraction - silica, alumina, lime, iron oxides - that floats off every remelt of scrap; the operator
     * taps the metal from the bottom and rakes this off the top because it is in the way. So it has no
     * recipe and never will: the {@link com.flatts.recompile.content.block.CupolaFurnaceBlock} hands one
     * over every time it finishes a smelt, whether the player wants it or not.
     *
     * <p>That is the point of it. Every other material here is something you went and got; this is a
     * waste stream that accumulates because you were doing something else, which is the only kind of
     * problem this mod is actually about.
     *
     * <p>It is deliberately <b>out of {@code #recompile:found_only}</b>. That tag is about finished goods
     * a person would throw away, and slag is neither finished nor thrown away by anybody - it is
     * industrial residue, and the twin tests that guard the tag have nothing to say about it.
     */
    public static final DeferredItem<Item> SLAG = ITEMS.registerItem("slag", Item::new);

    // Blueprints (#95). BLUEPRINT is one item covering every blueprint the mod or a pack ships; which
    // one it is lives in a data component. CLEAN_MATTRESS is the proof of concept's payoff and is
    // deliberately craftable by nothing - the only route to it is the blueprint bench, which is what
    // blueprint_crafting_is_the_only_route_to_a_clean_mattress asserts.
    public static final DeferredItem<com.flatts.recompile.content.item.IdeaFragmentItem> IDEA_FRAGMENT =
        ITEMS.registerItem("idea_fragment", com.flatts.recompile.content.item.IdeaFragmentItem::new);

    public static final DeferredItem<com.flatts.recompile.content.item.BlueprintItem> BLUEPRINT =
        ITEMS.registerItem("blueprint", com.flatts.recompile.content.item.BlueprintItem::new);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> FILING_CABINET =
        ITEMS.registerSimpleBlockItem("filing_cabinet", RCBlocks.FILING_CABINET);

    /**
     * The Clean Mattress, one item per dye colour (#95).
     *
     * <p><b>Sixteen items rather than one with a colour component</b>, the way wool is sixteen blocks
     * rather than one dyed one. That is the shape every player already knows, and it buys three things
     * a component could not: JEI lists each colour as its own item, the bed recipes become sixteen
     * ordinary shaped recipes that a recipe viewer draws natively, and nothing has to match on a
     * component - which no vanilla ingredient can do.
     *
     * <p>One texture between them, tinted per item by a {@code minecraft:constant} in the client item
     * definition. Sixteen near-identical PNGs would be the same picture sixteen times.
     */
    public static final java.util.List<DeferredItem<Item>> CLEAN_MATTRESSES =
        java.util.stream.Stream.of(net.minecraft.world.item.DyeColor.values())
            .map(colour -> ITEMS.registerItem(colour.getName() + "_clean_mattress", Item::new))
            .toList();

    /** The Clean Mattress of one colour. */
    public static Item cleanMattress(net.minecraft.world.item.DyeColor colour) {
        return CLEAN_MATTRESSES.get(colour.getId()).get();
    }

    public static final DeferredItem<Item> JUNK_SHOVEL = ITEMS.registerItem(
        "junk_shovel", props -> new Item(props.shovel(ToolMaterial.STONE, 1.5F, -3.0F)));
    // The knife is a cutting tool, not a sword: it mines its own tag
    // (recompile:mineable/knife = bales) the way a shovel mines mineable/shovel, so the
    // one tool that opens a bale is also the one that cuts it loose for the tarp.
    //
    // tool() rather than sword() is a deliberate trade, not an oversight - the two
    // builders hand out different components, and the knife wants the tool side of each:
    //   - 1 durability per block broken, where a sword costs 2. This is the knife's day
    //     job (a bale per cut), so its real cost halved.
    //   - 2 durability per melee hit, where a sword costs 1. The knife is not the weapon,
    //     so it can eat that; the prybar below is, and pins the cost back to Weapon(1).
    //   - it can break blocks in creative, which swords refuse - correct for a tool.
    // Dropped with sword(): fast cobweb mining and the SWORD_INSTANTLY_MINES /
    // SWORD_EFFICIENT overrides. Nothing in a garbage world has cobwebs or leaves.
    public static final DeferredItem<Item> SCRAP_KNIFE = ITEMS.registerItem(
        "scrap_knife",
        props -> new Item(props.tool(ToolMaterial.STONE, RCTags.MINEABLE_WITH_KNIFE, 1.0F, -2.0F, 0.0F)));

    // The prybar digs out Bulky Waste and levers the Scrap Barrel apart - a vanilla
    // barrel answers to an axe, but this one is welded steel and axes have no place in a
    // world with no trees. Same tool()-over-a-tag treatment as the knife.
    //
    // It is also the trio's weak weapon, and tool() would quietly double its melee cost:
    // sword() ships Weapon(1), tool() ships Weapon(2). The knife can eat that trade
    // because it is not the weapon - this one cannot, so the melee profile is pinned back
    // to Weapon(1) explicitly. The override is chained AFTER tool() on purpose: components
    // are last-write-wins, so the order is what makes the pin hold. What it keeps from
    // tool(): the mining rule, and 1 durability per block broken rather than a sword's 2.
    //
    // It does still give up the sword-only combat rules - fast cobweb cutting and the
    // SWORD_INSTANTLY_MINES / SWORD_EFFICIENT overrides - and that is accepted rather than
    // overlooked. Those rules need cobwebs or foliage to matter; this world has no trees,
    // its starting biome spawns nothing, no vanilla structure generates in it (its biome is
    // in no vanilla biome tag), and the Nether and End are locked. There is nothing here
    // for them to bite on. Revisit if a themed dimension ever ships webs.
    public static final DeferredItem<Item> PRYBAR = ITEMS.registerItem(
        "prybar",
        props -> new Item(props.tool(ToolMaterial.STONE, RCTags.MINEABLE_WITH_PRYBAR, 2.0F, -2.6F, 0.0F)
            .component(DataComponents.WEAPON, new Weapon(1))));

    /** The starter tool trio (creative tab ordering). */
    public static final List<DeferredItem<Item>> TRASH_TOOLS = List.of(
        SCRAP_KNIFE, PRYBAR, JUNK_SHOVEL);

    // ---------------- Demolition yard: the Sledgehammer (frontier) ----------------
    // Crushes Reinforced Concrete for rebar + aggregate. Copper tier is the entry rung of the
    // copper->iron->diamond->netherite ladder (later tiers land as their materials become reachable).
    // Repairs with copper ingots via the NeoForge common tag. Between stone (131 durability) and iron.
    public static final ToolMaterial COPPER_TIER = new ToolMaterial(
        BlockTags.INCORRECT_FOR_STONE_TOOL, 200, 5.0F, 1.5F, 12,
        TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/copper")));

    // Slow, hard-hitting: high attack damage, negative speed. Mines only #recompile:mineable/sledgehammer.
    // The full tier ladder ships; copper + iron are reachable now, diamond + netherite light up when their
    // materials do (the crystals gap #46 / the Nether unlock) - a designed ladder, top rungs future-gated.
    public static final DeferredItem<Item> COPPER_SLEDGEHAMMER = ITEMS.registerItem(
        "copper_sledgehammer",
        props -> new Item(props.tool(COPPER_TIER, RCTags.MINEABLE_WITH_SLEDGEHAMMER, 5.0F, -3.2F, 0.0F)));
    public static final DeferredItem<Item> IRON_SLEDGEHAMMER = ITEMS.registerItem(
        "iron_sledgehammer",
        props -> new Item(props.tool(ToolMaterial.IRON, RCTags.MINEABLE_WITH_SLEDGEHAMMER, 6.0F, -3.2F, 0.0F)));
    public static final DeferredItem<Item> DIAMOND_SLEDGEHAMMER = ITEMS.registerItem(
        "diamond_sledgehammer",
        props -> new Item(props.tool(ToolMaterial.DIAMOND, RCTags.MINEABLE_WITH_SLEDGEHAMMER, 7.0F, -3.2F, 0.0F)));
    public static final DeferredItem<Item> NETHERITE_SLEDGEHAMMER = ITEMS.registerItem(
        "netherite_sledgehammer",
        props -> new Item(props.tool(ToolMaterial.NETHERITE, RCTags.MINEABLE_WITH_SLEDGEHAMMER, 8.0F, -3.2F, 0.0F)));

    /** The Sledgehammer tier ladder, in creative-tab order. */
    public static final List<DeferredItem<Item>> SLEDGEHAMMERS = List.of(
        COPPER_SLEDGEHAMMER, IRON_SLEDGEHAMMER, DIAMOND_SLEDGEHAMMER, NETHERITE_SLEDGEHAMMER);

    // ---------------- The Garbage Vacuum (#336) ----------------
    // Bulk block collection for FE, on the sledgehammer's tier ladder: the same four materials, so the
    // rungs light up together. stacksTo(1) because the charge is per stack and a stack of two vacuums
    // sharing one component would be one charge worn twice. The tier material decides which gated
    // TIER BANDS are data: #recompile:vacuumable/<tier>, each including the band below it,
    // so copper handles household waste, iron adds the demolition yard, diamond the
    // radioactive dump and netherite the depths. See RCTags.vacuumable.
    public static final DeferredItem<GarbageVacuumItem> COPPER_GARBAGE_VACUUM = ITEMS.registerItem(
        "copper_garbage_vacuum",
        props -> new GarbageVacuumItem(props.stacksTo(1), VacuumTier.COPPER));
    public static final DeferredItem<GarbageVacuumItem> IRON_GARBAGE_VACUUM = ITEMS.registerItem(
        "iron_garbage_vacuum",
        props -> new GarbageVacuumItem(props.stacksTo(1), VacuumTier.IRON));
    public static final DeferredItem<GarbageVacuumItem> DIAMOND_GARBAGE_VACUUM = ITEMS.registerItem(
        "diamond_garbage_vacuum",
        props -> new GarbageVacuumItem(props.stacksTo(1), VacuumTier.DIAMOND));
    public static final DeferredItem<GarbageVacuumItem> NETHERITE_GARBAGE_VACUUM = ITEMS.registerItem(
        "netherite_garbage_vacuum",
        props -> new GarbageVacuumItem(props.stacksTo(1), VacuumTier.NETHERITE));

    /** The vacuum tier ladder, in creative-tab order. */
    public static final List<DeferredItem<GarbageVacuumItem>> GARBAGE_VACUUMS = List.of(
        COPPER_GARBAGE_VACUUM, IRON_GARBAGE_VACUUM, DIAMOND_GARBAGE_VACUUM, NETHERITE_GARBAGE_VACUUM);

    // The Cutting Torch: cuts Steel I-Beams (a sledgehammer cannot - you crush concrete, you cut steel).
    // Single tool, not a tier ladder. Durability is its fuel tank (v1); the Oily Rag in its recipe is the
    // fuel. Iron in the recipe gates it one step past first-iron (rebar bootstrap).
    public static final ToolMaterial TORCH_TIER = new ToolMaterial(
        BlockTags.INCORRECT_FOR_STONE_TOOL, 180, 6.0F, 1.0F, 12,
        TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/copper")));

    // UNBREAKABLE is deliberate: the torch's fuel is an Oily Rag spent per cut (see RCTorchFuel), so the
    // sink is the rag line, not the tool. Leaving durability on would tax the same action twice. The
    // ToolMaterial still supplies the mine tier, speed and attack stats - only its durability is moot.
    public static final DeferredItem<CuttingTorchItem> CUTTING_TORCH = ITEMS.registerItem(
        "cutting_torch",
        props -> new CuttingTorchItem(props.tool(TORCH_TIER, RCTags.MINEABLE_WITH_CUTTING_TORCH,
                1.0F, -2.8F, 0.0F)
            .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
            // Hide the "Unbreakable" line. It is true of the tool and false of the experience - the torch
            // absolutely does stop working, it just runs out of fuel rather than wearing out - and the
            // fuel line right above it says the useful thing.
            .component(DataComponents.TOOLTIP_DISPLAY,
                TooltipDisplay.DEFAULT.withHidden(DataComponents.UNBREAKABLE, true))));

    // ---------------- Food (P1.9) ----------------
    // Scavenged tin cans: a sealed can opens with a scrap knife into an opened can
    // that eats like Suspicious Stew (a random effect - the risk staple). The dump
    // mushroom is the humble forage staple, foraged off garbage mycelium.
    public static final DeferredItem<Item> TIN_CAN = ITEMS.registerItem("tin_can", SealedCanItem::new);
    public static final DeferredItem<Item> TIN_CAN_OPEN = ITEMS.registerItem(
        "tin_can_open",
        props -> new OpenedCanItem(props.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).build())));
    // A BlockItem for the dump mushroom block, so it has parity with vanilla mushrooms: forage it,
    // then replant it on mycelium or dirt. Still food (eat it by right-clicking with no block targeted),
    // still the block's item form - one id that both places and feeds.
    public static final DeferredItem<Item> DUMP_MUSHROOM = ITEMS.registerItem(
        "dump_mushroom",
        props -> new BlockItem(RCBlocks.DUMP_MUSHROOM.get(),
            props.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build())));

    /** Food items in creative-tab order. */
    /**
     * Raw Roach and Cooked Roach (#78) - what a roach drops, and what the Burn Barrel turns it into.
     *
     * <p><b>The barrel needs no allowlist change.</b> Its rule is
     * {@code input.has(DataComponents.FOOD) || input.is(BURN_BARREL_SMELTABLE)}, so anything edible
     * burns by construction - these qualify the moment they carry {@code food(...)}. That is why a
     * protein was chosen over organic muck as the drop: muck would have made roaches compete with the
     * Compost Heap for the material the food economy runs on.
     *
     * <p><b>Nutrition is a progression lever, not flavour.</b> Raw is 1, below dump mushrooms at 2;
     * cooked is 4, matching an opened tin can. That is deliberate: roaches come out of the first garbage
     * block a player touches, so this is the earliest renewable food in the game, arriving at tier 0
     * where renewable protein otherwise does not exist until rung 5 behind the whole reclamation ladder.
     * Cooked deliberately does not BEAT the can - it matches it, so the dump feeds you as badly as
     * scavenging does. First-pass; balance is #36.
     */
    public static final DeferredItem<Item> RAW_ROACH = ITEMS.registerItem(
        "raw_roach",
        props -> new Item(props.food(new FoodProperties.Builder()
            .nutrition(1).saturationModifier(0.1F).build())));

    public static final DeferredItem<Item> COOKED_ROACH = ITEMS.registerItem(
        "cooked_roach",
        props -> new Item(props.food(new FoodProperties.Builder()
            .nutrition(4).saturationModifier(0.3F).build())));

    /**
     * A cream-filled snack cake, found (#303). <b>The one food down here that is simply fine.</b>
     *
     * <p>Everything else you eat in this world costs you something or gambles: a tin can rolls an
     * effect the way Suspicious Stew does, a mushroom is forage, a roach is a roach. This is the
     * industrial sponge cake that famously outlasts everything, and finding one intact in a landfill
     * is the whole of it - so it does <b>not</b> roll an effect. A second random-effect food would
     * have been the tin can again, and the joke only lands if the thing is genuinely still good.
     *
     * <p><b>Nutrition 4, saturation 0.1, and the split is the point.</b> It feeds you as well as a
     * cooked roach and holds you nowhere near as long, which is what empty calories are. Compare the
     * can and the cooked roach at 4 / 0.3. First-pass; balance is #36.
     *
     * <p>Found-only by tag, so it has a pull-stream source and no recipe. It is deliberately rarer
     * than a tin can: the can is the food economy's floor and this is a thing you are pleased to see.
     */
    public static final DeferredItem<Item> SNACK_CAKE = ITEMS.registerItem(
        "snack_cake",
        props -> new Item(props.food(new FoodProperties.Builder()
            .nutrition(4).saturationModifier(0.1F).build())));

    public static final List<DeferredItem<Item>> FOOD =
        List.of(TIN_CAN, TIN_CAN_OPEN, DUMP_MUSHROOM, RAW_ROACH, COOKED_ROACH, SNACK_CAKE);

    // ---------------- Collectibles (design I-2) ----------------
    // Artifacts from the past, assembled from thematic pieces the player finds in the garbage. A piece
    // is a rare pull-stream drop; a set of them crafts the trophy at the Scrap Crafting Table. The
    // Puzzle Cube is v1: nine pieces fill the 3x3 grid (the grid IS the cube's face). Adding a
    // collectible is a piece item + a trophy item + a recipe + loot lines - no new code.
    public static final DeferredItem<Item> PUZZLE_CUBE_PIECE = ITEMS.registerItem("puzzle_cube_piece", Item::new);
    // The Puzzle Cube is a placeable block (two states), so these are block-items - they render as the
    // real 3D cube in hand, inventory, and on a Display Pedestal. Craft one into the other to swap states.
    /** The Hydroponics Bay (#43): the only source of cane, bamboo, cactus and sweet berries. */
    public static final DeferredItem<BlockItem> HYDROPONICS_BAY =
        ITEMS.registerSimpleBlockItem("hydroponics_bay", RCBlocks.HYDROPONICS_BAY);

    public static final DeferredItem<BlockItem> PUZZLE_CUBE =
        ITEMS.registerSimpleBlockItem("puzzle_cube", RCBlocks.PUZZLE_CUBE);
    public static final DeferredItem<BlockItem> PUZZLE_CUBE_SCRAMBLED =
        ITEMS.registerSimpleBlockItem("puzzle_cube_scrambled", RCBlocks.PUZZLE_CUBE_SCRAMBLED);
    /** Collectibles ported from open-source CC0 3D models via the voxel porter. */
    public static final DeferredItem<BlockItem> AVOCADO =
        ITEMS.registerSimpleBlockItem("avocado", RCBlocks.AVOCADO);
    public static final DeferredItem<BlockItem> PRESENT =
        ITEMS.registerSimpleBlockItem("present", RCBlocks.PRESENT);
    public static final DeferredItem<BlockItem> GOLD_COIN =
        ITEMS.registerSimpleBlockItem("gold_coin", RCBlocks.GOLD_COIN);
    public static final DeferredItem<BlockItem> TOY_CAR =
        ITEMS.registerSimpleBlockItem("toy_car", RCBlocks.TOY_CAR);

    /** The collectible piece (item); the cube blocks are placed via their own block-items. */
    public static final List<DeferredItem<Item>> COLLECTIBLES = List.of(PUZZLE_CUBE_PIECE);

    // ---------------- Blocks-as-items ----------------
    public static final DeferredItem<BlockItem> GARBAGE_BLOCK =
        ITEMS.registerSimpleBlockItem("garbage_block", RCBlocks.GARBAGE_BLOCK);

    /** The Nether's bulk fill, and its Block of Garbage. */
    public static final DeferredItem<BlockItem> TECHNO_ORGANIC_WASTE =
        ITEMS.registerSimpleBlockItem("techno_organic_waste", RCBlocks.TECHNO_ORGANIC_WASTE);

    /** The loose burnt fraction inside that fill - the Nether's Stone Rubble. */
    public static final DeferredItem<BlockItem> SLAG_RUBBLE =
        ITEMS.registerSimpleBlockItem("slag_rubble", RCBlocks.SLAG_RUBBLE);

    // ---------------- The radioactive dump (#285) ----------------

    public static final DeferredItem<BlockItem> MILL_TAILINGS =
        ITEMS.registerSimpleBlockItem("mill_tailings", RCBlocks.MILL_TAILINGS);
    public static final DeferredItem<BlockItem> WASTE_DRUM =
        ITEMS.registerSimpleBlockItem("waste_drum", RCBlocks.WASTE_DRUM);
    public static final DeferredItem<BlockItem> STAINED_GROUND =
        ITEMS.registerSimpleBlockItem("stained_ground", RCBlocks.STAINED_GROUND);
    public static final DeferredItem<BlockItem> URANIUM_GLASS =
        ITEMS.registerSimpleBlockItem("uranium_glass", RCBlocks.URANIUM_GLASS);

    // The consumer-scale finds. All four are ordinary domestic objects that really are mildly
    // radioactive, which is the point: they tie this region back to the household sprawl instead of
    // making it a separate science-fiction place. Each passes "would a person throw this away".

    /** Radium dial paint on a clock face, and the Radium Girls behind it. */
    public static final DeferredItem<Item> RADIUM_DIAL_CLOCK =
        ITEMS.registerItem("radium_dial_clock", Item::new);

    /** An americium sealed source inside the most mundane object in the house. */
    public static final DeferredItem<Item> SMOKE_DETECTOR =
        ITEMS.registerItem("smoke_detector", Item::new);

    /** Still sold, mildly radioactive, thrown away constantly. The most industrial of the four. */
    public static final DeferredItem<Item> THORIATED_WELDING_RODS =
        ITEMS.registerItem("thoriated_welding_rods", Item::new);
    /** Rubble: the demolition yard's pick-through stone-shard source. */
    public static final DeferredItem<BlockItem> PULVERIZER =
        ITEMS.registerSimpleBlockItem("pulverizer", RCBlocks.PULVERIZER);
    public static final DeferredItem<BlockItem> PULVERIZER_HOUSING =
        ITEMS.registerSimpleBlockItem("pulverizer_housing", RCBlocks.PULVERIZER_HOUSING);
    public static final DeferredItem<BlockItem> TROMMEL =
        ITEMS.registerSimpleBlockItem("trommel", RCBlocks.TROMMEL);
    public static final DeferredItem<BlockItem> TROMMEL_DRUM =
        ITEMS.registerSimpleBlockItem("trommel_drum", RCBlocks.TROMMEL_DRUM);
    public static final DeferredItem<BlockItem> TROMMEL_STAND =
        ITEMS.registerSimpleBlockItem("trommel_stand", RCBlocks.TROMMEL_STAND);
    public static final DeferredItem<BlockItem> TROMMEL_CHUTE =
        ITEMS.registerSimpleBlockItem("trommel_chute", RCBlocks.TROMMEL_CHUTE);
    public static final DeferredItem<BlockItem> SEPARATOR =
        ITEMS.registerSimpleBlockItem("separator", RCBlocks.SEPARATOR);
    public static final DeferredItem<BlockItem> SEPARATOR_CHAMBER =
        ITEMS.registerSimpleBlockItem("separator_chamber", RCBlocks.SEPARATOR_CHAMBER);
    public static final DeferredItem<BlockItem> SEPARATOR_HOUSING =
        ITEMS.registerSimpleBlockItem("separator_housing", RCBlocks.SEPARATOR_HOUSING);
    public static final DeferredItem<BlockItem> SEPARATOR_CHUTE =
        ITEMS.registerSimpleBlockItem("separator_chute", RCBlocks.SEPARATOR_CHUTE);

    public static final DeferredItem<BlockItem> MECHANICAL_WASTE =
        ITEMS.registerSimpleBlockItem("mechanical_waste", RCBlocks.MECHANICAL_WASTE);

    /**
     * The three industrial scrap variants Mechanical Waste sorts into
     * ({@code docs/gem_tier_spec.md} Phase 1). Distinct items rather than variants of one, because each
     * feeds a different {@code recompile:separating} recipe and a recipe keys on an item.
     */
    public static final DeferredItem<Item> SPENT_ABRASIVE = ITEMS.registerItem("spent_abrasive", Item::new);
    public static final DeferredItem<Item> MAGNET_SCRAP = ITEMS.registerItem("magnet_scrap", Item::new);
    public static final DeferredItem<Item> QUARTZ_GRIT = ITEMS.registerItem("quartz_grit", Item::new);

    /**
     * Ground circuit board, the middle of the gold chain (#120).
     *
     * <p>Not a member of {@link #INDUSTRIAL_SCRAP}: those are things you FIND in Mechanical Waste and feed
     * to the Separator. This is something the player MAKES, one stage into a two-stage refine, and
     * grouping it with the found materials would say the opposite of what it is.
     */
    public static final DeferredItem<Item> CIRCUIT_POWDER = ITEMS.registerItem("circuit_powder", Item::new);
    /**
     * The clay chain (#115), which is a recycling line rather than a dig.
     *
     * <p><b>Grog</b> is crushed potsherds, and the word means exactly that. It is a NON-PLASTIC
     * temper: its real job is controlling shrinkage and thermal shock, and it actively reduces
     * plasticity - so it cannot become clay on its own, which is the whole reason the chain has a
     * second ingredient.
     *
     * <p><b>Kitty Litter</b> is bentonite, the standard fix for a body that comes out short. The two
     * are useless apart and correct together.
     *
     * <p><b>Dry Clay Body</b> is the blend before water. Dry-blending first is real practice, not a
     * concession to the interface - which is what lets the last step be a cauldron instead of a
     * machine nobody needed.
     */
    public static final DeferredItem<Item> GROG = ITEMS.registerItem("grog", Item::new);
    public static final DeferredItem<Item> KITTY_LITTER = ITEMS.registerItem("kitty_litter", Item::new);
    public static final DeferredItem<Item> DRY_CLAY_BODY = ITEMS.registerItem("dry_clay_body", Item::new);


    /** Sorted in the order the Separator will consume them. */
    public static final List<DeferredItem<Item>> INDUSTRIAL_SCRAP =
        List.of(SPENT_ABRASIVE, MAGNET_SCRAP, QUARTZ_GRIT);

    public static final DeferredItem<BlockItem> STONE_RUBBLE =
        ITEMS.registerSimpleBlockItem("stone_rubble", RCBlocks.STONE_RUBBLE);
    /** Reinforced Concrete: sledged for rebar + aggregate. */
    public static final DeferredItem<BlockItem> REINFORCED_CONCRETE =
        ITEMS.registerSimpleBlockItem("reinforced_concrete", RCBlocks.REINFORCED_CONCRETE);
    /** Steel I-Beam: cut with the Cutting Torch for bulk raw iron. */
    public static final DeferredItem<BlockItem> STEEL_I_BEAM =
        ITEMS.registerSimpleBlockItem("steel_i_beam", RCBlocks.STEEL_I_BEAM);
    public static final DeferredItem<BlockItem> TRASH_BAG =
        ITEMS.registerSimpleBlockItem("trash_bag", RCBlocks.TRASH_BAG);
    public static final DeferredItem<BlockItem> COMPACTED_BALE =
        ITEMS.registerSimpleBlockItem("compacted_bale", RCBlocks.COMPACTED_BALE);
    public static final DeferredItem<BlockItem> CARDBOARD_PILE =
        ITEMS.registerSimpleBlockItem("cardboard_pile", RCBlocks.CARDBOARD_PILE);
    /**
     * Bulky Waste (P1.11). Registered so creative can place it; it is unobtainable in
     * survival, because breaking the block yields the <em>find</em> rather than itself,
     * so there is nothing to dupe.
     */
    public static final DeferredItem<BlockItem> BULKY_WASTE =
        ITEMS.registerSimpleBlockItem("bulky_waste", RCBlocks.BULKY_WASTE);
    // Vegetation tier (rung 2): the custom pioneer weeds. Block-items so a plant retrieved with shears
    // can be replaced by hand.
    public static final DeferredItem<BlockItem> WEEDGRASS =
        ITEMS.registerSimpleBlockItem("weedgrass", RCBlocks.WEEDGRASS);
    public static final DeferredItem<BlockItem> FIREWEED =
        ITEMS.registerSimpleBlockItem("fireweed", RCBlocks.FIREWEED);
    /**
     * The Dead Fridge: two blocks tall, and the one teardown where which component you recover is
     * a draw rather than a certainty.
     */
    public static final DeferredItem<BlockItem> FRIDGE =
        ITEMS.registerSimpleBlockItem("fridge", RCBlocks.FRIDGE);

    /**
     * The mattress: place it and it is a bed, or tear it down at the Recompile Workbench
     * (P1.4) for string. Never craftable - the dump gives you the bed (P1.11).
     */
    public static final DeferredItem<BlockItem> MATTRESS =
        ITEMS.registerSimpleBlockItem("mattress", RCBlocks.MATTRESS);
    public static final DeferredItem<BlockItem> SCRAP_CRAFTING_TABLE =
        ITEMS.registerSimpleBlockItem("scrap_crafting_table", RCBlocks.SCRAP_CRAFTING_TABLE);
    public static final DeferredItem<BlockItem> SORTING_TARP =
        ITEMS.registerSimpleBlockItem("sorting_tarp", RCBlocks.SORTING_TARP);
    public static final DeferredItem<BlockItem> RECOMPILE_WORKBENCH =
        ITEMS.registerSimpleBlockItem("recompile_workbench", RCBlocks.RECOMPILE_WORKBENCH);
    public static final DeferredItem<BlockItem> DISPLAY_PEDESTAL =
        ITEMS.registerSimpleBlockItem("display_pedestal", RCBlocks.DISPLAY_PEDESTAL);
    public static final DeferredItem<BlockItem> CHARGING_STATION =
        ITEMS.registerSimpleBlockItem("charging_station", RCBlocks.CHARGING_STATION);
    public static final DeferredItem<BlockItem> SCRAP_BARREL =
        ITEMS.registerSimpleBlockItem("scrap_barrel", RCBlocks.SCRAP_BARREL);
    public static final DeferredItem<BlockItem> SCRAP_BIN =
        ITEMS.registerSimpleBlockItem("scrap_bin", RCBlocks.SCRAP_BIN);
    public static final DeferredItem<BlockItem> CUPOLA_FURNACE =
        ITEMS.registerSimpleBlockItem("cupola_furnace", RCBlocks.CUPOLA_FURNACE);

    public static final DeferredItem<BlockItem> BURN_BARREL =
        ITEMS.registerSimpleBlockItem("burn_barrel", RCBlocks.BURN_BARREL);

    /** The Slag Furnace (#236): the only machine that vitrifies, and so the only route to obsidian. */
    public static final DeferredItem<BlockItem> SLAG_FURNACE =
        ITEMS.registerSimpleBlockItem("slag_furnace", RCBlocks.SLAG_FURNACE);

    /** The Sintering Kiln (#248): the fifth verb, and the first that puts something back together. */
    public static final DeferredItem<BlockItem> SINTERING_KILN =
        ITEMS.registerSimpleBlockItem("sintering_kiln", RCBlocks.SINTERING_KILN);

    /** Ancient Sculk (#266): the seam, in item form. See RCBlocks for why it needs a diamond hammer. */
    public static final DeferredItem<BlockItem> ANCIENT_SCULK =
        ITEMS.registerSimpleBlockItem("ancient_sculk", RCBlocks.ANCIENT_SCULK);

    /**
     * A Blaze Briquette: four blaze powder pressed into one compact, and the Sintering Kiln's feed.
     *
     * <p><b>It exists so that a cooking recipe cannot become an infinite loop.</b> Vanilla crafts one
     * blaze rod into TWO blaze powder, and a cooking recipe consumes exactly one item - so a direct
     * {@code powder -> rod} recipe would hand back more than it took and multiply forever. Four powder
     * to a briquette makes a rod cost four and give back two, which loses in both directions.
     *
     * <p>The step is not a workaround, it is the rest of the real process. Powder metallurgy compacts
     * a green body first and sinters it second, and a green compact is famously fragile until it is
     * fired - which is exactly what a briquette of blaze powder should feel like.
     */
    public static final DeferredItem<Item> BLAZE_BRIQUETTE =
        ITEMS.registerItem("blaze_briquette", Item::new);

    /**
     * A Propellant Briquette: four gunpowder pressed into one compact, and the Sintering Kiln's
     * second feed. Fired, it becomes a Breeze Rod (#278).
     *
     * <p><b>The referent is a solid propellant grain</b>, which is the standard
     * {@code material_economy.md} holds every route to. A rocket motor and an airbag inflator both
     * work the same way: compact a powder into a solid mass that, once lit, releases a large volume of
     * gas very fast. That is a rod, made of pressed powder, that produces a burst of wind - so a Breeze
     * Rod in this world is <b>manufactured, not harvested off a mob</b>.
     *
     * <p><b>It has to exist for the same mechanical reason the Blaze Briquette does:</b> a cooking
     * recipe consumes exactly one item, so four gunpowder cannot feed the kiln directly. Pressing them
     * into a green body first is also the rest of the real process, not a workaround.
     *
     * <p><b>Why this route exists at all.</b> A Breeze Rod drops only from a Breeze, a Breeze spawns
     * only from a trial spawner, and this world opts into exactly three structures - nether fortress,
     * bastion remnant, and the mod's own sewer. So the whole family was unobtainable. See #278 for what
     * that does and does not unlock: Wind Charges yes, the Mace no, because a Heavy Core is
     * ominous-vault-only and stays absent.
     *
     * <p><b>The loop risk runs the other way here, and is smaller.</b> Vanilla turns one rod into FOUR
     * wind charges and ships no reverse recipe, so nothing can be fed back. The invariant to protect is
     * that it stays that way - {@code nothing_turns_wind_back_into_propellant} fails the build if any
     * recipe ever converts a rod or a charge back into gunpowder or a briquette.
     */
    public static final DeferredItem<Item> PROPELLANT_BRIQUETTE =
        ITEMS.registerItem("propellant_briquette", Item::new);
    /**
     * The Roach's spawn egg (#78). Not a convenience - an entity with no egg cannot be placed by hand in
     * creative, which is the loop this whole feature gets tuned through.
     */
    public static final DeferredItem<Item> ROACH_SPAWN_EGG = ITEMS.registerItem(
        "roach_spawn_egg",
        props -> new net.minecraft.world.item.SpawnEggItem(
            props.spawnEgg(RCEntities.ROACH.get())));

    /** The Pigeon's spawn egg (#133), for the same reason the Roach has one: an entity with no egg
     *  cannot be placed by hand in creative, which is the loop this gets tuned through. */
    public static final DeferredItem<Item> PIGEON_SPAWN_EGG = ITEMS.registerItem(
        "pigeon_spawn_egg",
        props -> new net.minecraft.world.item.SpawnEggItem(
            props.spawnEgg(RCEntities.PIGEON.get())));

    public static final DeferredItem<BlockItem> SEQUENCER =
        ITEMS.registerSimpleBlockItem("sequencer", RCBlocks.SEQUENCER);

    public static final DeferredItem<BlockItem> BURNER_GENERATOR =
        ITEMS.registerSimpleBlockItem("burner_generator", RCBlocks.BURNER_GENERATOR);
    public static final DeferredItem<BlockItem> RAIN_COLLECTOR =
        ITEMS.registerSimpleBlockItem("rain_collector", RCBlocks.RAIN_COLLECTOR);
    /**
     * The shared multiblock component.
     *
     * <p>This used to say the funnel below deliberately has no item because a dummy cell is never
     * crafted or held. It is a dummy cell, but it is also a component you craft and place by hand,
     * and its item is declared two lines down - {@code RegistryCompletenessTests.NO_ITEM_FORM}
     * says so explicitly and lists the cells that really are formed-only.
     */
    public static final DeferredItem<BlockItem> MACHINE_FRAME =
        ITEMS.registerSimpleBlockItem("machine_frame", RCBlocks.MACHINE_FRAME);
    /** A Machine Frame wrapped in plastic sheeting - the collector's catch, crafted then placed. */
    public static final DeferredItem<BlockItem> RAIN_COLLECTOR_FUNNEL =
        ITEMS.registerSimpleBlockItem("rain_collector_funnel", RCBlocks.RAIN_COLLECTOR_FUNNEL);
    public static final DeferredItem<BlockItem> GRASS_SPREADER =
        ITEMS.registerSimpleBlockItem("grass_spreader", RCBlocks.GRASS_SPREADER);
    public static final DeferredItem<BlockItem> COMPOST_HEAP =
        ITEMS.registerSimpleBlockItem("compost_heap", RCBlocks.COMPOST_HEAP);

    /** Tree Nursery core item (reclamation rung 4). Placing it auto-assembles the 2x2x1 wall. */
    public static final DeferredItem<BlockItem> TREE_NURSERY =
        ITEMS.registerSimpleBlockItem("tree_nursery", RCBlocks.TREE_NURSERY);

    // Animal bait (reclamation rung 5): three diets, each with a Rich grade that seeds a pair. All place
    // the one animal_bait block with their diet + grade set.
    public static final DeferredItem<AnimalBaitItem> HERBIVORE_BAIT = ITEMS.registerItem("herbivore_bait",
        props -> new AnimalBaitItem(RCBlocks.ANIMAL_BAIT.get(), props, Diet.HERBIVORE, false));
    public static final DeferredItem<AnimalBaitItem> CARNIVORE_BAIT = ITEMS.registerItem("carnivore_bait",
        props -> new AnimalBaitItem(RCBlocks.ANIMAL_BAIT.get(), props, Diet.CARNIVORE, false));
    public static final DeferredItem<AnimalBaitItem> OMNIVORE_BAIT = ITEMS.registerItem("omnivore_bait",
        props -> new AnimalBaitItem(RCBlocks.ANIMAL_BAIT.get(), props, Diet.OMNIVORE, false));
    public static final DeferredItem<AnimalBaitItem> RICH_HERBIVORE_BAIT = ITEMS.registerItem("rich_herbivore_bait",
        props -> new AnimalBaitItem(RCBlocks.ANIMAL_BAIT.get(), props, Diet.HERBIVORE, true));
    public static final DeferredItem<AnimalBaitItem> RICH_CARNIVORE_BAIT = ITEMS.registerItem("rich_carnivore_bait",
        props -> new AnimalBaitItem(RCBlocks.ANIMAL_BAIT.get(), props, Diet.CARNIVORE, true));
    public static final DeferredItem<AnimalBaitItem> RICH_OMNIVORE_BAIT = ITEMS.registerItem("rich_omnivore_bait",
        props -> new AnimalBaitItem(RCBlocks.ANIMAL_BAIT.get(), props, Diet.OMNIVORE, true));
    /** Salvaged, never crafted - it comes out of a broken appliance at the workbench. */
    /** The Motor's item form - a placeable component, so a block item like the Pump. */
    public static final DeferredItem<BlockItem> MOTOR =
        ITEMS.registerSimpleBlockItem("motor", RCBlocks.MOTOR);

    public static final DeferredItem<BlockItem> PUMP =
        ITEMS.registerSimpleBlockItem("pump", RCBlocks.PUMP);
    public static final DeferredItem<BlockItem> SOLAR_PANEL =
        ITEMS.registerSimpleBlockItem("solar_panel", RCBlocks.SOLAR_PANEL);
    /** Crafted from a Rain Collector - the machine you already built, plumbed into a bigger one. */
    public static final DeferredItem<BlockItem> WATER_TANK =
        ITEMS.registerSimpleBlockItem("water_tank", RCBlocks.WATER_TANK);
    /** Copper's first job - four of these ring a spreader and become its drip spigots. */
    public static final DeferredItem<BlockItem> COPPER_PIPE =
        ITEMS.registerSimpleBlockItem("copper_pipe", RCBlocks.COPPER_PIPE);
    /**
     * The find the Pump comes out of. A Bulky Waste line, torn down at the Recompile Workbench -
     * it restores the appliance P1.11 dropped when Bulky Waste replaced it. Placeable like the
     * mattress, the other find: you can carry one home and put it down instead of only feeding it
     * to the Workbench.
     */
    public static final DeferredItem<BlockItem> BROKEN_HYDROPONICS_BAY =
        ITEMS.registerSimpleBlockItem("broken_hydroponics_bay", RCBlocks.BROKEN_HYDROPONICS_BAY);

    public static final DeferredItem<BlockItem> WASHING_MACHINE =
        ITEMS.registerSimpleBlockItem("washing_machine", RCBlocks.WASHING_MACHINE);

    public static final DeferredItem<BlockItem> PRINTER =
        ITEMS.registerSimpleBlockItem("printer", RCBlocks.PRINTER);

    /** One item places the standing torch on the floor and the wall torch on walls (vanilla torch). */
    public static final DeferredItem<StandingAndWallBlockItem> SCRAP_TORCH = ITEMS.registerItem(
        "scrap_torch",
        props -> new StandingAndWallBlockItem(
            RCBlocks.SCRAP_TORCH.get(), RCBlocks.WALL_SCRAP_TORCH.get(), Direction.DOWN, props));

    /** The garbage-block family in creative-tab order. */
    public static final List<DeferredItem<BlockItem>> GARBAGE_BLOCKS = List.of(
        GARBAGE_BLOCK, TRASH_BAG, CARDBOARD_PILE, COMPACTED_BALE, BULKY_WASTE);

    // ---------------- Building blocks (P1.12): the deliberate shelter tier ----------------
    // Refined from scrap into blocks you would choose to build a home from. Full kit per
    // material (base + slab + stairs + wall); also the material sink for bulk scrap.
    public static final DeferredItem<BlockItem> PRESSED_JUNK_BLOCK =
        ITEMS.registerSimpleBlockItem("pressed_junk_block", RCBlocks.PRESSED_JUNK_BLOCK);
    public static final DeferredItem<BlockItem> PRESSED_JUNK_SLAB =
        ITEMS.registerSimpleBlockItem("pressed_junk_slab", RCBlocks.PRESSED_JUNK_SLAB);
    public static final DeferredItem<BlockItem> PRESSED_JUNK_STAIRS =
        ITEMS.registerSimpleBlockItem("pressed_junk_stairs", RCBlocks.PRESSED_JUNK_STAIRS);
    public static final DeferredItem<BlockItem> PRESSED_JUNK_WALL =
        ITEMS.registerSimpleBlockItem("pressed_junk_wall", RCBlocks.PRESSED_JUNK_WALL);
    public static final DeferredItem<BlockItem> SCRAP_PLATING =
        ITEMS.registerSimpleBlockItem("scrap_plating", RCBlocks.SCRAP_PLATING);
    public static final DeferredItem<BlockItem> SCRAP_PLATING_SLAB =
        ITEMS.registerSimpleBlockItem("scrap_plating_slab", RCBlocks.SCRAP_PLATING_SLAB);
    public static final DeferredItem<BlockItem> SCRAP_PLATING_STAIRS =
        ITEMS.registerSimpleBlockItem("scrap_plating_stairs", RCBlocks.SCRAP_PLATING_STAIRS);
    public static final DeferredItem<BlockItem> SCRAP_PLATING_WALL =
        ITEMS.registerSimpleBlockItem("scrap_plating_wall", RCBlocks.SCRAP_PLATING_WALL);
    public static final DeferredItem<BlockItem> CORRUGATED_METAL =
        ITEMS.registerSimpleBlockItem("corrugated_metal", RCBlocks.CORRUGATED_METAL);
    public static final DeferredItem<BlockItem> CORRUGATED_METAL_SLAB =
        ITEMS.registerSimpleBlockItem("corrugated_metal_slab", RCBlocks.CORRUGATED_METAL_SLAB);
    public static final DeferredItem<BlockItem> CORRUGATED_METAL_STAIRS =
        ITEMS.registerSimpleBlockItem("corrugated_metal_stairs", RCBlocks.CORRUGATED_METAL_STAIRS);
    public static final DeferredItem<BlockItem> CORRUGATED_METAL_WALL =
        ITEMS.registerSimpleBlockItem("corrugated_metal_wall", RCBlocks.CORRUGATED_METAL_WALL);
    public static final DeferredItem<BlockItem> PLASTIC_PANEL =
        ITEMS.registerSimpleBlockItem("plastic_panel", RCBlocks.PLASTIC_PANEL);
    public static final DeferredItem<BlockItem> PLASTIC_PANEL_SLAB =
        ITEMS.registerSimpleBlockItem("plastic_panel_slab", RCBlocks.PLASTIC_PANEL_SLAB);
    public static final DeferredItem<BlockItem> PLASTIC_PANEL_STAIRS =
        ITEMS.registerSimpleBlockItem("plastic_panel_stairs", RCBlocks.PLASTIC_PANEL_STAIRS);
    public static final DeferredItem<BlockItem> PLASTIC_PANEL_WALL =
        ITEMS.registerSimpleBlockItem("plastic_panel_wall", RCBlocks.PLASTIC_PANEL_WALL);
    public static final DeferredItem<BlockItem> CARDBOARD_BLOCK =
        ITEMS.registerSimpleBlockItem("cardboard_block", RCBlocks.CARDBOARD_BLOCK);
    public static final DeferredItem<BlockItem> CARDBOARD_SLAB =
        ITEMS.registerSimpleBlockItem("cardboard_slab", RCBlocks.CARDBOARD_SLAB);
    public static final DeferredItem<BlockItem> CARDBOARD_STAIRS =
        ITEMS.registerSimpleBlockItem("cardboard_stairs", RCBlocks.CARDBOARD_STAIRS);
    public static final DeferredItem<BlockItem> CARDBOARD_WALL =
        ITEMS.registerSimpleBlockItem("cardboard_wall", RCBlocks.CARDBOARD_WALL);

    public static final DeferredItem<BlockItem> CULLET_GLASS =
        ITEMS.registerSimpleBlockItem("cullet_glass", RCBlocks.CULLET_GLASS);
    public static final DeferredItem<BlockItem> CULLET_GLASS_PANE =
        ITEMS.registerSimpleBlockItem("cullet_glass_pane", RCBlocks.CULLET_GLASS_PANE);

    /** Building blocks in creative-tab order (grouped by family: base, slab, stairs, wall/pane). */
    public static final List<DeferredItem<BlockItem>> BUILDING_BLOCKS = List.of(
        PRESSED_JUNK_BLOCK, PRESSED_JUNK_SLAB, PRESSED_JUNK_STAIRS, PRESSED_JUNK_WALL,
        SCRAP_PLATING, SCRAP_PLATING_SLAB, SCRAP_PLATING_STAIRS, SCRAP_PLATING_WALL,
        CORRUGATED_METAL, CORRUGATED_METAL_SLAB, CORRUGATED_METAL_STAIRS, CORRUGATED_METAL_WALL,
        PLASTIC_PANEL, PLASTIC_PANEL_SLAB, PLASTIC_PANEL_STAIRS, PLASTIC_PANEL_WALL,
        CARDBOARD_BLOCK, CARDBOARD_SLAB, CARDBOARD_STAIRS, CARDBOARD_WALL,
        CULLET_GLASS, CULLET_GLASS_PANE);

    private RCItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
