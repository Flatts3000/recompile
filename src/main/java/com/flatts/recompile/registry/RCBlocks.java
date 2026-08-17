package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.ManholeBlock;
import com.flatts.recompile.content.block.FilingCabinetBlock;
import com.flatts.recompile.content.block.PulverizerCoreBlock;
import com.flatts.recompile.content.block.PulverizerPartBlock;
import com.flatts.recompile.content.block.TrommelCoreBlock;
import com.flatts.recompile.content.block.TrommelDrumBlock;
import com.flatts.recompile.content.block.TrommelPartBlock;
import com.flatts.recompile.content.block.BulkyWasteBlock;
import com.flatts.recompile.content.block.CupolaFurnaceBlock;
import com.flatts.recompile.content.block.BurnBarrelBlock;
import com.flatts.recompile.content.block.BurnerGeneratorBlock;
import com.flatts.recompile.content.block.CompostCageBlock;
import com.flatts.recompile.content.block.CompostHeapCoreBlock;
import com.flatts.recompile.content.block.AnimalBaitBlock;
import com.flatts.recompile.content.block.TreeNurseryCoreBlock;
import com.flatts.recompile.content.block.TreeNurseryTankBlock;
import com.flatts.recompile.content.block.DisplayPedestalBlock;
import com.flatts.recompile.content.block.HydroponicsBayBlock;
import com.flatts.recompile.content.block.LeachateBlock;
import com.flatts.recompile.content.block.MattressBlock;
import com.flatts.recompile.content.block.CompactedBaleBlock;
import com.flatts.recompile.content.block.MechanicalWasteBlock;
import com.flatts.recompile.content.block.MoundGroundBlock;
import com.flatts.recompile.content.block.RubbleBlock;
import com.flatts.recompile.content.block.SeparatorPartBlock;
import com.flatts.recompile.content.block.SeparatorChamberBlock;
import com.flatts.recompile.content.block.SeparatorCoreBlock;
import com.flatts.recompile.content.block.SteelBeamBlock;
import com.flatts.recompile.content.block.DumpMushroomBlock;
import com.flatts.recompile.content.block.DumpPlantBlock;
import com.flatts.recompile.content.block.GarbageBlock;
import com.flatts.recompile.content.block.GrassSpreaderCoreBlock;
import com.flatts.recompile.content.block.GrassSpreaderFrameBlock;
import com.flatts.recompile.content.block.GrassSpreaderSpigotBlock;
import com.flatts.recompile.content.block.FoundApplianceBlock;
import com.flatts.recompile.content.block.TallApplianceBlock;
import com.flatts.recompile.content.block.WaterTankBlock;
import com.flatts.recompile.content.block.SolarPanelBlock;
import com.flatts.recompile.content.block.RainCollectorCoreBlock;
import com.flatts.recompile.content.block.RainCollectorFunnelBlock;
import com.flatts.recompile.content.block.RecompileWorkbenchBlock;
import com.flatts.recompile.content.block.ScrapBarrelBlock;
import com.flatts.recompile.content.block.ScrapBinBlock;
import com.flatts.recompile.content.block.ScrapCraftingTableBlock;
import com.flatts.recompile.content.block.SortingTarpBlock;
import com.flatts.recompile.content.block.TrashBagBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry. Phase 1 adds the household Block of Garbage (design P0.3): the
 * unit of mixed trash you carry, stack, and pick through. Hand-breakable but slow,
 * shovel-class fast; drops itself.
 *
 * <p>Uses the factory form ({@code registerBlock(name, factory, propsSupplier)})
 * because MC 26.1 sets the {@code ResourceKey} on Properties before the block
 * constructor runs.
 */
public final class RCBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(Recompile.MOD_ID);

    /**
     * The household Block of Garbage. Strength 0.6 (dirt-ish) and
     * {@code requiresCorrectToolForDrops}: the Junk Shovel (via {@code minecraft:mineable/shovel})
     * is the only way to take one home, and bare hands get nothing.
     *
     * <p><b>A {@code mineable} tag was never a gate on its own.</b> It sets speed, not permission, so
     * the shovel was pure convenience here for a long time and a bare hand dug up garbage in about a
     * second. {@code requiresCorrectToolForDrops} is what makes the tool required.
     *
     * <p><b>Digging is gated; sorting is deliberately not.</b> Right-clicking a placed block to pick
     * through it does not run the harvest check, so a new player still sorts by hand - which is what
     * keeps the gate safe to have. Rebar and Scrap Metal come from {@code household_pulls} and from
     * nowhere else ({@code bag_pulls} has neither) and the shovel costs 1 Scrap Metal + 2 Rebar, so
     * gating the SORT as well would be circular and a fresh world would be unwinnable.
     *
     * <p>Randomized visual variants come from the blockstate JSON, not code.
     */
    public static final DeferredBlock<GarbageBlock> GARBAGE_BLOCK = BLOCKS.registerBlock(
        "garbage_block",
        GarbageBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(0.6F)
            .sound(SoundType.GRAVEL)
            .requiresCorrectToolForDrops()
    );

    /**
     * The mound bed (P1.6, Phase 5): the memory under a mound's footprint and the block that grows it
     * back. Vanilla coarse dirt's own hardness, sound and map colour, because it IS coarse dirt with a
     * different name and a darker face - the texture is that same face retinted, not a new drawing.
     * The player learns to read the ground: dark means this one comes back, which puts the
     * quarry-or-heal decision underfoot.
     *
     * <p>No {@code requiresCorrectToolForDrops} and it drops nothing, so it digs exactly like the
     * coarse dirt it is - and there is nothing to carry off, because this is world memory rather than
     * a material. Digging one out simply forgets that column, which is the same outcome as retiring
     * it with grass and wants no second rule.
     */
    public static final DeferredBlock<MoundGroundBlock> MOUND_GROUND = BLOCKS.registerBlock(
        "mound_ground",
        MoundGroundBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(0.5F)
            .sound(SoundType.GRAVEL)
            .randomTicks()
    );

    /**
     * Rubble (demolition yard, reclamation frontier): a pick-through pile like a Block of Garbage, but
     * its pull stream is stone shards. Bare-hand sift; see {@link RubbleBlock}.
     */
    /**
     * Mechanical Waste: the yard's machinery pile and the gem tier's found half
     * ({@code docs/gem_tier_spec.md}). Beside Stone Rubble, and like it takes no tool <em>to sort</em>
     * - but {@code requiresCorrectToolForDrops} to carry off, and on the <b>pickaxe</b> rather than
     * the shovel: it is machinery, not a loose pile, and Stone Rubble beside it stays shovel work.
     *
     * <p><b>This mod ships no pickaxe</b> (tier-zero tools only, "nothing to mine"), so the tool is
     * vanilla's - reachable once the Tree Nursery gives wood, which is Phase 2 and long before the
     * demolition yard at Phase 4. A player who walks to the yard early can still <em>sort</em> the
     * pile bare-handed and simply cannot carry it home, so this gates hauling, never material.
     */
    public static final DeferredBlock<MechanicalWasteBlock> MECHANICAL_WASTE = BLOCKS.registerBlock(
        "mechanical_waste",
        MechanicalWasteBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .strength(0.9F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
    );

    /**
     * Stone Rubble (demolition yard): a pick-through pile like a Block of Garbage, but its pull stream
     * is stone shards. Bare-hand sift, shovel to carry off ({@code requiresCorrectToolForDrops}).
     */
    public static final DeferredBlock<RubbleBlock> STONE_RUBBLE = BLOCKS.registerBlock(
        "stone_rubble",
        RubbleBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(0.8F)
            .sound(SoundType.GRAVEL)
            .requiresCorrectToolForDrops()
    );

    /**
     * Reinforced Concrete (demolition yard): the husk's standing bones. Solid and
     * {@code requiresCorrectToolForDrops} - only the Sledgehammer crushes it (bare hands and the wrong
     * tool yield nothing). Its loot is aggregate + rebar; see {@code loot_table/blocks/reinforced_concrete}.
     */
    /**
     * Leachate: the pond block, the in-world half of {@link RCFluids#LEACHATE} (#156).
     *
     * <p>Water's own block properties, with one deliberate difference: {@code MapColor.TERRACOTTA_BROWN}
     * rather than {@code WATER}, so a pool reads as a dark stain on the map instead of a pretty lake.
     * {@code noLootTable} because breaking a liquid yields nothing, and {@code liquid()} so vanilla
     * treats it as one for pathing and placement.
     */
    public static final DeferredBlock<LeachateBlock> LEACHATE = BLOCKS.registerBlock(
        "leachate",
        props -> new LeachateBlock(RCFluids.LEACHATE.get(), props),
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_BROWN)
            .replaceable()
            .noCollision()
            .strength(100.0F)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY)
    );

    /**
     * The manhole cover: the way into a sewer (#90). Prybar-only, and it drops nothing - the reward for
     * opening one is the shaft underneath, not a plate to carry home.
     *
     * <p>{@code requiresCorrectToolForDrops} with no tool that satisfies it, which is the same shape as
     * Bulky Waste: mining it by hand gets you nothing, and the prybar interaction is the only route.
     */
    public static final DeferredBlock<ManholeBlock> MANHOLE = BLOCKS.registerBlock(
        "manhole",
        ManholeBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)
    );

    public static final DeferredBlock<Block> REINFORCED_CONCRETE = BLOCKS.registerBlock(
        "reinforced_concrete",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.2F, 6.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.STONE)
    );

    /**
     * Steel I-Beam (demolition yard): the husk's structural steel, a column or girder (axis-oriented like a
     * log). Solid, {@code requiresCorrectToolForDrops}, cut only by the Cutting Torch (not the sledgehammer -
     * you cut steel, not crush it). Its loot is raw iron in bulk; see {@code loot_table/blocks/steel_i_beam}.
     */
    public static final DeferredBlock<SteelBeamBlock> STEEL_I_BEAM = BLOCKS.registerBlock(
        "steel_i_beam",
        SteelBeamBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .sound(SoundType.METAL)
    );

    /**
     * Trash bag (P1.1): soft surface litter. Instant hand-break, quiet.
     *
     * <p><b>The one sortable pile with no {@code requiresCorrectToolForDrops}</b>, deliberately: it is
     * loose litter you gather by the armful, and it is the first block a new player meets. It is in
     * {@code mineable/shovel} anyway, so a shovel is faster - but faster is all it is, and bare hands
     * must keep working. Adding the gate here would make the opening move of the game destroy
     * something and say nothing.
     */
    public static final DeferredBlock<TrashBagBlock> TRASH_BAG = BLOCKS.registerBlock(
        "trash_bag",
        TrashBagBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_WHITE)
            .strength(0.2F)
            .sound(SoundType.WOOL)
    );

    /**
     * Compacted bale (P1.1): dense, strapped trash. Sturdier; opened with a scrap knife, and
     * {@code requiresCorrectToolForDrops} so the knife (via {@code recompile:mineable/knife}) is also
     * the only way to carry one off. The knife was already the tool that sorts it - now the same tool
     * cuts it loose, rather than the strapping mattering to a sort and not to a pair of hands.
     */
    public static final DeferredBlock<CompactedBaleBlock> COMPACTED_BALE = BLOCKS.registerBlock(
        "compacted_bale",
        CompactedBaleBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(0.9F)
            .sound(SoundType.GRASS)
            .requiresCorrectToolForDrops()
    );

    /**
     * Bulky Waste (P1.11): something big is buried here - pry it open to find out what.
     * Inherits the appliance's slot and feel. {@code requiresCorrectToolForDrops} so the
     * prybar (via {@code recompile:mineable/prybar}) is the <em>only</em> way in - bare
     * hands get nothing, matching the compacted bale, which keeps its "you need a Prybar"
     * nudge honest. The find itself lives in the loot table, which is the file that grows.
     */
    public static final DeferredBlock<BulkyWasteBlock> BULKY_WASTE = BLOCKS.registerBlock(
        "bulky_waste",
        BulkyWasteBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.4F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
    );

    /**
     * Scrap crafting table: the tier-zero 3x3 crafting station (no wood in this world).
     *
     * <p>{@code noOcclusion} because it draws the shared workstation bench rather than a full cube.
     * Without it the game still treats the block as solid for face culling and you see straight
     * through the ground next to it - a hole with no obvious cause, since the model is fine.
     */
    public static final DeferredBlock<ScrapCraftingTableBlock> SCRAP_CRAFTING_TABLE = BLOCKS.registerBlock(
        "scrap_crafting_table",
        ScrapCraftingTableBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /**
     * Sorting Tarp (P1.3): the manual batch-sorting table. Right-click holding a
     * garbage block / bag / bale to sift into the world; no GUI, no hoppers, no
     * BlockEntity. {@code noOcclusion} because the model is a table, not a full cube.
     */
    public static final DeferredBlock<SortingTarpBlock> SORTING_TARP = BLOCKS.registerBlock(
        "sorting_tarp",
        SortingTarpBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .strength(0.8F)
            .sound(SoundType.WOOL)
            .noOcclusion()
    );

    /**
     * Recompile Workbench (P1.4): the disassembly table - the teardown exit the found
     * economy needs (P1.11.5). Hold right-click with a found item to tear it into materials;
     * a scrap knife and prybar rest on it as the tool rack. {@code noOcclusion} because the
     * baked model carries the tool sprites on top. Metal, so it sounds and mines like scrap.
     */
    public static final DeferredBlock<RecompileWorkbenchBlock> RECOMPILE_WORKBENCH = BLOCKS.registerBlock(
        "recompile_workbench",
        RecompileWorkbenchBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /**
     * Display Pedestal (Collectibles, design I-2): a plinth that shows off one finished collectible
     * trophy. Not a full cube, so {@code noOcclusion()}; the trophy on top is drawn by the mod's one
     * BlockEntityRenderer (scoped reversal of P1.11.6).
     */
    public static final DeferredBlock<DisplayPedestalBlock> DISPLAY_PEDESTAL = BLOCKS.registerBlock(
        "display_pedestal",
        DisplayPedestalBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0F)
            .sound(SoundType.STONE)
            .noOcclusion()
    );

    /**
     * The Puzzle Cube (Collectibles, design I-2): a placeable 3x3 twisty-cube block. A full cube - each
     * of its six faces is its own 3x3 sticker texture, so it renders as a real 3D cube everywhere (in
     * hand, in inventory, and spinning on a Display Pedestal). Two states, {@code puzzle_cube} (solved)
     * and {@code puzzle_cube_scrambled}, craft into each other to swap.
     */
    public static final DeferredBlock<Block> PUZZLE_CUBE = BLOCKS.registerBlock(
        "puzzle_cube",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .strength(1.0F)
            .sound(SoundType.STONE)
    );

    /** The scrambled state of the {@link #PUZZLE_CUBE} - craft one into the other to swap. */
    public static final DeferredBlock<Block> PUZZLE_CUBE_SCRAMBLED = BLOCKS.registerBlock(
        "puzzle_cube_scrambled",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .strength(1.0F)
            .sound(SoundType.STONE)
    );

    /**
     * Avocado (Collectibles I-2): the first ported collectible - an open-source CC0 3D model
     * (Khronos glTF sample) voxelized to Minecraft's 16px grid via the voxel porter. A per-voxel
     * greedy-meshed model with a generated palette texture; not a full cube, so {@code noOcclusion()}.
     */
    public static final DeferredBlock<Block> AVOCADO = BLOCKS.registerBlock(
        "avocado",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN)
            .strength(0.5F)
            .sound(SoundType.WOOD)
            .noOcclusion()
    );

    /** Present (Collectibles I-2): a wrapped gift box, ported CC0 model via the voxel porter. */
    public static final DeferredBlock<Block> PRESENT = BLOCKS.registerBlock(
        "present",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .strength(0.3F)
            .sound(SoundType.WOOL)
            .noOcclusion()
    );

    /** Gold Coin (Collectibles I-2): a standing gold coin, ported CC0 model via the voxel porter. */
    public static final DeferredBlock<Block> GOLD_COIN = BLOCKS.registerBlock(
        "gold_coin",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.GOLD)
            .strength(0.5F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /** Toy Car (Collectibles I-2): a die-cast vintage racer, ported CC0 model via the voxel porter. */
    public static final DeferredBlock<Block> TOY_CAR = BLOCKS.registerBlock(
        "toy_car",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE)
            .strength(0.5F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /**
     * Burn Barrel (P2.2): the first smelter - a drum you burn refuse in. A vanilla-furnace reskin
     * that is manual-only (no automation). Glows and lights (13) while burning. Metal, full cube.
     */
    /**
     * Cupola Furnace (#50): the second smelter, and the only machine that makes iron. Runs BLASTING and
     * automates through its faces - both the rewards the Burn Barrel deliberately withholds. Stone-built,
     * so it is heavier than the drum it replaces.
     */
    public static final DeferredBlock<CupolaFurnaceBlock> CUPOLA_FURNACE = BLOCKS.registerBlock(
        "cupola_furnace",
        CupolaFurnaceBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(3.5F)
            // NO requiresCorrectToolForDrops. It reads as the right call for a stone machine and is a trap
            // here: this block is named in no mineable tag at all, so "correct tool" would mean *no* tool
            // exists and breaking it would drop nothing - losing the player's most expensive machine,
            // which ate their Burn Barrel to build.
            .sound(SoundType.STONE)
            .lightLevel(state -> state.getValue(AbstractFurnaceBlock.LIT) ? 13 : 0));

    /**
     * Burner Generator (#72): burns refuse into FE, the half of the power tier that works at night.
     * Fed by right-click, so it needs no screen and no menu.
     */
    public static final DeferredBlock<BurnerGeneratorBlock> BURNER_GENERATOR = BLOCKS.registerBlock(
        "burner_generator",
        BurnerGeneratorBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL)
            .lightLevel(state -> state.getValue(BurnerGeneratorBlock.LIT) ? 13 : 0));

    public static final DeferredBlock<BurnBarrelBlock> BURN_BARREL = BLOCKS.registerBlock(
        "burn_barrel",
        BurnBarrelBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL)
            .lightLevel(state -> state.getValue(AbstractFurnaceBlock.LIT) ? 13 : 0));

    /**
     * Scrap Barrel: the garbage world's storage. Every vanilla container is wood-gated
     * (chest and barrel want planks, hopper wants a chest), and this world has no trees,
     * so without this there is nowhere to put anything - which the Sorting Tarp makes
     * acute, since it sifts onto the ground. Metal, so it sounds and mines like one.
     */
    /**
     * The Filing Cabinet (#95): found in Bulky Waste, files blueprints, joins the Scrap Network by
     * placement. Metal because it is an office cabinet, and noOcclusion is deliberately NOT set - it is
     * a full cube, so a hole in the world is not on the table here.
     */
    public static final DeferredBlock<FilingCabinetBlock> FILING_CABINET = BLOCKS.registerBlock(
        "filing_cabinet",
        FilingCabinetBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN)
            .strength(1.6F)
            .sound(SoundType.METAL)
    );

    public static final DeferredBlock<ScrapBarrelBlock> SCRAP_BARREL = BLOCKS.registerBlock(
        "scrap_barrel",
        ScrapBarrelBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.6F)
            .sound(SoundType.METAL)
    );

    /**
     * Scrap Bin (P2.9): bulk single-type storage that binds to one salvage type and takes its color.
     * A solid four-face cube (tinted per material) with the bound item's own texture on a raised
     * front placard, chosen by the {@code content} blockstate. {@code noOcclusion} because that
     * placard sits slightly proud of the front, so the model is not a plain full block.
     */
    public static final DeferredBlock<ScrapBinBlock> SCRAP_BIN = BLOCKS.registerBlock(
        "scrap_bin",
        ScrapBinBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.4F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /**
     * Broken Hydroponics Bay: a Bulky Waste find, and the only thing that teaches the working one.
     *
     * <p><b>A plain cube with no behaviour.</b> No block entity, no facing, no menu - it is a wreck, and
     * the only thing you do with it is carry it to the Recompile Workbench. Its whole job is to be the
     * teardown input that yields parts, scrap and the ideas for building a bay that works.
     */
    public static final DeferredBlock<Block> BROKEN_HYDROPONICS_BAY = BLOCKS.registerBlock(
        "broken_hydroponics_bay",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .strength(1.4F)
            .sound(SoundType.METAL)
    );

    /**
     * Washing Machine: the second Bulky Waste find, and the only source of the Pump. Placeable so a
     * find can be carried home rather than only consumed, following the mattress. A plain full cube
     * with no behaviour - the four-face art and the facing are the whole block.
     */
    public static final DeferredBlock<FoundApplianceBlock> WASHING_MACHINE = BLOCKS.registerBlock(
        "washing_machine",
        FoundApplianceBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_WHITE)
            .strength(1.4F)
            .sound(SoundType.METAL)
    );

    /**
     * Printer (#112): a Bulky Waste find, and this world's only source of <b>ink</b>.
     *
     * <p>Black dye has exactly two vanilla sources - an ink sac, or a wither rose - and the wither rose
     * is behind the dimension lockout. So black dye was unreachable, which took grey dye with it and
     * left one of the sixteen Clean Mattresses with no route: v0.5.0 shipped a {@code gray_bed} that
     * could not be made. Tearing a printer down closes the sixteen-colour set.
     *
     * <p>It carries <b>lapis</b> too (owner, 2026-08-02), because lapis is a pigment and cyan toner is
     * phthalocyanine blue, so pigment out of a printer needs no hand-waving. Machinery, by contrast,
     * contains no lapis at all - it is ornamental stone with no industrial use - which is why it is not
     * in Mechanical Waste.
     *
     * <p>Softer and quieter than the metal appliances: a printer is mostly plastic shell.
     */
    public static final DeferredBlock<FoundApplianceBlock> PRINTER = BLOCKS.registerBlock(
        "printer",
        FoundApplianceBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_WHITE)
            .strength(1.2F)
            .sound(SoundType.WOOD)
    );

    /**
     * The Dead Fridge: the appliance find that replaced the Broken Fan and the Broken Light Fixture.
     *
     * <p><b>It earns the swap on identity alone.</b> A fridge is the one household appliance that
     * genuinely holds a compressor motor, a refrigerant pump and an interior bulb, so "tear it down
     * and get one of the three" needs no explaining. The fan and the fixture each existed to hang one
     * component on, and a light fixture only ever plausibly held a bulb - which is why the owner read
     * it as nothing at all in a junk pile (2026-08-12).
     *
     * <p><b>Which component you get is a lottery, and that reverses a written rule.</b>
     * {@code a_component_from_a_teardown_is_never_a_dice_roll} demanded one object and one guaranteed
     * component. The owner overrode it; what survives is that you never come away with NOTHING, which
     * the component pool guarantees by carrying no filler entry. See ComponentBlueprintTests.
     *
     * <p>Two blocks tall, so it is a {@link TallApplianceBlock} rather than the usual cube - the
     * halves keep each other alive by state validation and never break one another.
     *
     * <p>Takes the pair's combined weight in {@code bulky_spine}, so appliance finds arrive exactly as
     * often as they did before.
     */
    public static final DeferredBlock<TallApplianceBlock> FRIDGE = BLOCKS.registerBlock(
        "fridge",
        TallApplianceBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_WHITE)
            .strength(1.2F)
            .sound(SoundType.METAL)
    );

    /**
     * Mattress (P1.11): the first find in the Bulky Waste table, and this world's bed -
     * a vanilla bed needs planks, and there are no trees. Two blocks like a bed, soft and
     * quiet, {@code noOcclusion} because it is 5 pixels tall rather than a cube.
     */
    public static final DeferredBlock<MattressBlock> MATTRESS = BLOCKS.registerBlock(
        "mattress",
        MattressBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOL)
            .strength(0.2F)
            .sound(SoundType.WOOL)
            .noOcclusion()
    );

    /**
     * Rain Collector (P1.10): a caged IBC tote that holds the water tank - the only water source in
     * a world with none. This is the <b>core</b> of a two-cell multiblock: place it, add a Machine
     * Frame on top, and the frame becomes the tarp funnel that catches the rain. Holds the
     * BlockEntity; {@code noOcclusion} because the cage is not a solid cube.
     */
    public static final DeferredBlock<RainCollectorCoreBlock> RAIN_COLLECTOR = BLOCKS.registerBlock(
        "rain_collector",
        RainCollectorCoreBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.2F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /**
     * Machine Frame: the shared structural component every multiblock machine is completed with.
     * Loose it is a welded scrap scaffold; inside a formed machine it becomes that machine's own
     * part (in the Rain Collector, the tarp funnel). Cheap on purpose - it is the bulk piece.
     */
    public static final DeferredBlock<Block> MACHINE_FRAME = BLOCKS.registerBlock(
        "machine_frame",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.2F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    // ---------------- Grass Spreader (P2.4-R3): reclamation rung 1, a sprinkler ----------------

    /**
     * Grass Spreader core: the bottom of a four-cell sprinkler tower that converts dead ground to
     * grass within a radius, consuming nothing. Its own look - deliberately not the Rain Collector's
     * palette, so the two machines never read as the same object.
     */
    public static final DeferredBlock<GrassSpreaderCoreBlock> GRASS_SPREADER = BLOCKS.registerBlock(
        "grass_spreader",
        GrassSpreaderCoreBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.6F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /**
     * Compost Heap core (Mod Jam - the fertilizer tier): the master of a 2x2x2 salvage cage that
     * composts muck + fiber into Fertilizer, the gate to Vegetation and Farming. Carries the layer BE.
     */
    public static final DeferredBlock<CompostHeapCoreBlock> COMPOST_HEAP = BLOCKS.registerBlock(
        "compost_heap",
        CompostHeapCoreBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(1.2F)
            .sound(SoundType.GRAVEL)
            .noOcclusion()  // see-through wire shell + inset compost bands - not a full cube
    );

    /**
     * Tree Nursery core (reclamation rung 4): the master of a 2x2x1 wall (core + inert Water Tank on the
     * bottom, two Solar Panels on top) that raises saplings from water + Fertilizer + an Unknown
     * Seedling. Carries the nursery BE and the bespoke GUI; the loot strip keeps its saplings un-findable
     * so this is their only source.
     */
    /** The Hydroponics Bay (#43): water + power grows plants this world cannot otherwise reach. */
    public static final DeferredBlock<HydroponicsBayBlock> HYDROPONICS_BAY = BLOCKS.registerBlock(
        "hydroponics_bay",
        HydroponicsBayBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GREEN)
            .strength(1.5F)
            .sound(SoundType.METAL)
            // Grow-lights, lit while a batch is running. Same 13 as the nursery and a lit furnace.
            .lightLevel(state -> state.getValue(HydroponicsBayBlock.LIT) ? 13 : 0));

    public static final DeferredBlock<TreeNurseryCoreBlock> TREE_NURSERY = BLOCKS.registerBlock(
        "tree_nursery",
        TreeNurseryCoreBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.PODZOL)
            .strength(1.5F)
            .sound(SoundType.GRAVEL)
            // The grow-light glows while a sapling is cooking - light 13, like a lit furnace / Burn Barrel.
            .lightLevel(state -> state.getValue(TreeNurseryCoreBlock.ACTIVE) ? 13 : 0)
    );

    /**
     * Animal bait (reclamation rung 5): a flat lure placed on healed grass. Settles undisturbed, then
     * spawns wildlife from its diet tag and consumes itself. A no-collision plate; its item forms are the
     * three (six with Rich) {@code AnimalBaitItem}s in RCItems, so no auto block-item here.
     */
    public static final DeferredBlock<AnimalBaitBlock> ANIMAL_BAIT = BLOCKS.registerBlock(
        "animal_bait",
        AnimalBaitBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .strength(0.2F)
            .sound(SoundType.GRASS)
            .noCollision()
            .instabreak()
    );

    /** The Tree Nursery's formed water-tank cell: a full block clad in the machine's panels. A dummy. */
    public static final DeferredBlock<TreeNurseryTankBlock> TREE_NURSERY_TANK = BLOCKS.registerBlock(
        "tree_nursery_tank",
        TreeNurseryTankBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.PODZOL)
            .strength(1.5F)
            .sound(SoundType.GRAVEL)
    );

    /** A formed cell of the Compost Heap's cage. A dummy - no item; drops a Machine Frame on disband. */
    public static final DeferredBlock<CompostCageBlock> COMPOST_CAGE = BLOCKS.registerBlock(
        "compost_cage",
        CompostCageBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(1.2F)
            .sound(SoundType.GRAVEL)
            .noOcclusion()  // see-through wire shell + inset compost bands - not a full cube
    );

    /** A shared, inert component: a caged tote that holds nothing. Crafted and placed by hand. */
    public static final DeferredBlock<WaterTankBlock> WATER_TANK = BLOCKS.registerBlock(
        "water_tank",
        WaterTankBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.2F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /** The spreader's sprinkler head: what a Pump becomes. Sprays via {@code animateTick}. */
    public static final DeferredBlock<GrassSpreaderFrameBlock> GRASS_SPREADER_FRAME = BLOCKS.registerBlock(
        "grass_spreader_frame",
        GrassSpreaderFrameBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.2F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /**
     * Pump: a shared machine component, salvaged rather than made - it is teardown-only, torn out
     * of a broken appliance, which is what puts reclamation rung 1 behind the teardown spine.
     * <b>Inert</b>: no rotation, no kinetics, never requires Create (P2.3).
     */
    /**
     * Motor: the second placeable component (owner, 2026-08-06), and the drive of anything that
     * physically moves. Salvaged out of Mechanical Waste in the demolition yard - magnet scrap
     * already comes from that stream, and a magnet is what a motor is mostly made of.
     *
     * <p><b>Inert</b>, like every component in the vocabulary (P2.4-R item 6): it turns nothing,
     * exposes no kinetics and never requires Create. The name invites the opposite, which is exactly
     * why the rule is written down rather than assumed.
     *
     * <p>A plain cube on purpose. It forms into Separator Housing once the machine is assembled, so
     * it is only ever seen held or placed loose, and a boxy motor housing reads correctly at that
     * size without the bespoke multi-element model the Pump carries.
     */
    public static final DeferredBlock<Block> MOTOR = BLOCKS.registerBlock(
        "motor",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .strength(1.6F)
            .sound(SoundType.METAL)
    );

    public static final DeferredBlock<Block> PUMP = BLOCKS.registerBlock(
        "pump",
        Block::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.4F)
            .sound(SoundType.METAL)
            // Required, not cosmetic: the model is not a full cube, so without this the game still
            // treats it as one and culls the faces of neighbouring blocks it "covers" - which shows
            // up as a hole punched in the ground underneath.
            .noOcclusion()
    );

    /**
     * Copper Pipe: a shared machine component, and copper's first job. The Burn Barrel already
     * smelts scrap into copper nuggets (the copper-first inversion, P2.2), so this is what that
     * metal was for. Four of them ring a Grass Spreader and become its drip spigots.
     */
    // A RotatedPillarBlock, not a plain Block: a pipe is a length of tube, so it has to be able to lie
    // along an axis. Placed vertically it looks as it always did (the default), but a horizontal run now
    // reads as continuous pipe instead of a row of upright stubs. Safe for the Rain Collector, whose
    // Multiblock matches components with `.is(block)` rather than on exact state.
    public static final DeferredBlock<RotatedPillarBlock> COPPER_PIPE = BLOCKS.registerBlock(
        "copper_pipe",
        RotatedPillarBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(1.0F)
            .sound(SoundType.COPPER)
            .noOcclusion()
    );

    /** A formed drip spigot: what a Copper Pipe becomes on the side of a spreader. Drips water. */
    /**
     * The Separator's core and its three formed cell types (docs/gem_tier_spec.md, art
     * docs/separator_model_spec.md). The chamber is where material goes in, the chute is where it
     * comes out, the housing is everything else.
     */
    /**
     * The Trommel: the automated rung of the sorting ladder (#188).
     *
     * <p>Sorting moved here off the Separator, because a shear shredder destroys distinctions and
     * cannot sort - a real facility uses a different machine for each cut it makes, and a trommel makes
     * the size cut. See #187 for the removal.
     */
    public static final DeferredBlock<TrommelCoreBlock> TROMMEL = BLOCKS.registerBlock(
        "trommel",
        TrommelCoreBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F)
            .sound(SoundType.METAL)
    );

    /** A drum cell: perforated screen, and the only animated block in the mod. */
    public static final DeferredBlock<TrommelDrumBlock> TROMMEL_DRUM = BLOCKS.registerBlock(
        "trommel_drum",
        TrommelDrumBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F)
            .sound(SoundType.METAL)
    );

    public static final DeferredBlock<TrommelPartBlock> TROMMEL_STAND = BLOCKS.registerBlock(
        "trommel_stand",
        TrommelPartBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F)
            .sound(SoundType.METAL)
    );

    public static final DeferredBlock<TrommelPartBlock> TROMMEL_CHUTE = BLOCKS.registerBlock(
        "trommel_chute",
        TrommelPartBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    /**
     * The Pulverizer core (#189): a hammer mill, the machine that reduces rather than divides.
     *
     * <p>No requiresCorrectToolForDrops. A machine comes back however you break it (#195) - it is
     * assembled and disassembled, not quarried.
     */
    public static final DeferredBlock<PulverizerCoreBlock> PULVERIZER = BLOCKS.registerBlock(
        "pulverizer",
        PulverizerCoreBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.5F)
            .sound(SoundType.METAL)
    );

    /** A formed cell: sealed housing, all seven the same, because you cannot see into a mill. */
    public static final DeferredBlock<PulverizerPartBlock> PULVERIZER_HOUSING = BLOCKS.registerBlock(
        "pulverizer_housing",
        PulverizerPartBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.5F)
            .sound(SoundType.METAL)
    );


    public static final DeferredBlock<SeparatorCoreBlock> SEPARATOR = BLOCKS.registerBlock(
        "separator",
        SeparatorCoreBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE)
            .strength(3.0F)
            .sound(SoundType.METAL)
    );

    public static final DeferredBlock<SeparatorChamberBlock> SEPARATOR_CHAMBER = BLOCKS.registerBlock(
        "separator_chamber",
        SeparatorChamberBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE)
            .strength(3.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    public static final DeferredBlock<SeparatorPartBlock> SEPARATOR_HOUSING = BLOCKS.registerBlock(
        "separator_housing",
        SeparatorPartBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE)
            .strength(3.0F)
            .sound(SoundType.METAL)
    );

    public static final DeferredBlock<SeparatorPartBlock> SEPARATOR_CHUTE = BLOCKS.registerBlock(
        "separator_chute",
        SeparatorPartBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE)
            .strength(3.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
    );

    public static final DeferredBlock<GrassSpreaderSpigotBlock> GRASS_SPREADER_SPIGOT = BLOCKS.registerBlock(
        "grass_spreader_spigot",
        GrassSpreaderSpigotBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(1.0F)
            .sound(SoundType.COPPER)
            .noOcclusion()
    );

    /**
     * Solar Panel: a shared machine component. <b>Inert</b> - no light detection, no redstone, no
     * power (P3.5: no RF before the Nether). A recoloured vanilla daylight detector, so it costs no
     * new art.
     */
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.registerBlock(
        "solar_panel",
        SolarPanelBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE)
            .strength(1.0F)
            // It is a recoloured daylight detector, so it breaks like one: WOOD, vanilla's daylight
            // sensor sound. Not GLASS (it is not a pane) and not METAL - match the block it is built on.
            .sound(SoundType.WOOD)
            .noOcclusion()
    );

    /**
     * The Rain Collector's tarp funnel - the formed machine's upper cell. A dummy: no item, never
     * crafted, exists only inside an assembled collector, and breaking it takes the machine down.
     */
    public static final DeferredBlock<RainCollectorFunnelBlock> RAIN_COLLECTOR_FUNNEL = BLOCKS.registerBlock(
        "rain_collector_funnel",
        RainCollectorFunnelBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE)
            .strength(1.2F)
            .sound(SoundType.WOOL)
            .noOcclusion()
    );

    /**
     * Dump mushroom (P1.9): the forageable plant. Grows on vanilla mycelium in any
     * light; breaking it drops the edible {@code dump_mushroom} item, which is a
     * {@code BlockItem} (parity with vanilla mushrooms) so it can be replanted on
     * mycelium or dirt - foraging is a renewable loop, not a one-way strip.
     */
    public static final DeferredBlock<DumpMushroomBlock> DUMP_MUSHROOM = BLOCKS.registerBlock(
        "dump_mushroom",
        DumpMushroomBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY)
    );

    // Vegetation tier (rung 2): custom dump-friendly pioneer weeds that Fertilizer scatters onto
    // reclaimed grass. Vanilla grasses/ferns/flowers fill out the rest of the scatter (no blocks here).
    public static final DeferredBlock<DumpPlantBlock> WEEDGRASS = BLOCKS.registerBlock(
        "weedgrass",
        DumpPlantBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredBlock<DumpPlantBlock> FIREWEED = BLOCKS.registerBlock(
        "fireweed",
        DumpPlantBlock::new,
        () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PINK)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY)
    );

    // ---------------- Building blocks (P1.12): the deliberate shelter tier ----------------
    // Crafted from scrap at the Scrap Crafting Table; tier-0 and ungated (survival/shelter is
    // free, tech is locked). Hand-breakable and drop themselves - reclaiming your own walls
    // must not be punishing; the prybar is only the
    // *faster* tool on the metal ones (via the mineable/prybar tag), never required. Full kit
    // per material: base + slab + stairs + wall. The base block is declared immediately above
    // its stairs so the StairBlock factory can read its default state during registration.

    public static final DeferredBlock<Block> PRESSED_JUNK_BLOCK = BLOCKS.registerBlock(
        "pressed_junk_block", Block::new, RCBlocks::pressedJunkProps);
    public static final DeferredBlock<SlabBlock> PRESSED_JUNK_SLAB = BLOCKS.registerBlock(
        "pressed_junk_slab", SlabBlock::new, RCBlocks::pressedJunkProps);
    public static final DeferredBlock<StairBlock> PRESSED_JUNK_STAIRS = BLOCKS.registerBlock(
        "pressed_junk_stairs",
        props -> new StairBlock(PRESSED_JUNK_BLOCK.get().defaultBlockState(), props),
        RCBlocks::pressedJunkProps);
    public static final DeferredBlock<WallBlock> PRESSED_JUNK_WALL = BLOCKS.registerBlock(
        "pressed_junk_wall", WallBlock::new, RCBlocks::pressedJunkProps);

    public static final DeferredBlock<Block> SCRAP_PLATING = BLOCKS.registerBlock(
        "scrap_plating", Block::new, RCBlocks::metalBuildProps);
    public static final DeferredBlock<SlabBlock> SCRAP_PLATING_SLAB = BLOCKS.registerBlock(
        "scrap_plating_slab", SlabBlock::new, RCBlocks::metalBuildProps);
    public static final DeferredBlock<StairBlock> SCRAP_PLATING_STAIRS = BLOCKS.registerBlock(
        "scrap_plating_stairs",
        props -> new StairBlock(SCRAP_PLATING.get().defaultBlockState(), props),
        RCBlocks::metalBuildProps);
    public static final DeferredBlock<WallBlock> SCRAP_PLATING_WALL = BLOCKS.registerBlock(
        "scrap_plating_wall", WallBlock::new, RCBlocks::metalBuildProps);

    public static final DeferredBlock<Block> CORRUGATED_METAL = BLOCKS.registerBlock(
        "corrugated_metal", Block::new, RCBlocks::metalBuildProps);
    public static final DeferredBlock<SlabBlock> CORRUGATED_METAL_SLAB = BLOCKS.registerBlock(
        "corrugated_metal_slab", SlabBlock::new, RCBlocks::metalBuildProps);
    public static final DeferredBlock<StairBlock> CORRUGATED_METAL_STAIRS = BLOCKS.registerBlock(
        "corrugated_metal_stairs",
        props -> new StairBlock(CORRUGATED_METAL.get().defaultBlockState(), props),
        RCBlocks::metalBuildProps);
    public static final DeferredBlock<WallBlock> CORRUGATED_METAL_WALL = BLOCKS.registerBlock(
        "corrugated_metal_wall", WallBlock::new, RCBlocks::metalBuildProps);

    public static final DeferredBlock<Block> PLASTIC_PANEL = BLOCKS.registerBlock(
        "plastic_panel", Block::new, RCBlocks::plasticBuildProps);
    public static final DeferredBlock<SlabBlock> PLASTIC_PANEL_SLAB = BLOCKS.registerBlock(
        "plastic_panel_slab", SlabBlock::new, RCBlocks::plasticBuildProps);
    public static final DeferredBlock<StairBlock> PLASTIC_PANEL_STAIRS = BLOCKS.registerBlock(
        "plastic_panel_stairs",
        props -> new StairBlock(PLASTIC_PANEL.get().defaultBlockState(), props),
        RCBlocks::plasticBuildProps);
    public static final DeferredBlock<WallBlock> PLASTIC_PANEL_WALL = BLOCKS.registerBlock(
        "plastic_panel_wall", WallBlock::new, RCBlocks::plasticBuildProps);

    // Cullet Glass: just the block and its pane. Glass has no honest slab or stairs form
    // (vanilla ships neither), so the family is block + pane (an IronBarsBlock).
    public static final DeferredBlock<TransparentBlock> CULLET_GLASS = BLOCKS.registerBlock(
        "cullet_glass", TransparentBlock::new, RCBlocks::glassBuildProps);
    public static final DeferredBlock<IronBarsBlock> CULLET_GLASS_PANE = BLOCKS.registerBlock(
        "cullet_glass_pane", IronBarsBlock::new, RCBlocks::glassBuildProps);

    // Scrap Torch (P1.4-A lighting): a rag torch that is a 1:1 reskin of the vanilla torch -
    // an oily rag (the trash-world "coal") lashed to a rebar. Light 14, no burn-out. The wall
    // variant is placed by the same item (StandingAndWallBlockItem) and drops the standing item.
    public static final DeferredBlock<TorchBlock> SCRAP_TORCH = BLOCKS.registerBlock(
        "scrap_torch",
        props -> new TorchBlock(ParticleTypes.FLAME, props),
        RCBlocks::torchProps);
    public static final DeferredBlock<WallTorchBlock> WALL_SCRAP_TORCH = BLOCKS.registerBlock(
        "wall_scrap_torch",
        props -> new WallTorchBlock(ParticleTypes.FLAME, props),
        RCBlocks::torchProps);

    /** Compacted mixed trash - the WALL-E cube. Soft, cheap, the bulk junk sink. */
    private static BlockBehaviour.Properties pressedJunkProps() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(1.2F)
            .sound(SoundType.GRAVEL);
    }

    /** Salvaged sheet metal - sturdy, so slow by hand; the prybar is the faster tool. */
    private static BlockBehaviour.Properties metalBuildProps() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL);
    }

    /** Salvaged plastic sheeting - light and quick to work. */
    private static BlockBehaviour.Properties plasticBuildProps() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_WHITE)
            .strength(1.0F)
            .sound(SoundType.WOOL);
    }

    /** Vanilla-torch physics: no collision, instant break, full light, destroyed when pushed. */
    private static BlockBehaviour.Properties torchProps() {
        return BlockBehaviour.Properties.of()
            .noCollision()
            .instabreak()
            .lightLevel(state -> 14)
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.DESTROY);
    }

    /** Salvaged glass - fragile, near-instant to break, {@code noOcclusion} for transparency. */
    private static BlockBehaviour.Properties glassBuildProps() {
        return BlockBehaviour.Properties.of()
            .strength(0.4F)
            .sound(SoundType.GLASS)
            .noOcclusion();
    }

    private RCBlocks() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
