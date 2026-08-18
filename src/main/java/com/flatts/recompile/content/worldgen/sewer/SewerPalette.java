package com.flatts.recompile.content.worldgen.sewer;

import com.flatts.recompile.registry.RCBlocks;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every block a sewer can place, in one list.
 *
 * <p><b>It is a list so a test can walk it.</b> Phase 2's acceptance criterion is that nothing the
 * structure places drops a member of {@code #minecraft:stone_crafting_materials} - which would hand the
 * player a vanilla furnace and skip the Cupola - and the criterion says explicitly that it must be
 * asserted "by a test that walks every block the structure can place, not by reading the palette". A
 * palette scattered through four piece classes cannot be walked; this one can, and
 * {@code SewerLootTests.the_sewer_palette_opens_no_gate} does.
 *
 * <p><b>Three slots, the way vanilla's mineshaft has three.</b> {@code MineshaftStructure.Type} carries
 * wood, planks and fence and the corridor code names nothing else except a handful of decorations. Ours
 * carries wall, grate and fluid for the same reason: the geometry should not know what it is built from.
 */
public final class SewerPalette {

    /** Corridor walls, floors and ceilings. Brick, because a sewer is brick. */
    public static final BlockState WALL = Blocks.BRICKS.defaultBlockState();

    /**
     * The grate over the channel and the odd wall vent - "scattered pipe" in the spec's Look row.
     *
     * <p>Iron bars rather than the mod's own Copper Pipe, deliberately. Copper Pipe is a crafted
     * component with a real cost in the copper economy, and scattering it through a structure would hand
     * out free components; iron bars craft into nothing and cannot be turned back into iron, so a player
     * who carries some home has decoration rather than material.
     */
    public static final BlockState GRATE = Blocks.IRON_BARS.defaultBlockState();

    /**
     * What runs in the channel. Leachate, one block deep, per the 2026-08-17 decision.
     *
     * <p>Placed as a <b>source in every cell of the channel</b> rather than as one source left to flow.
     * A filled channel cannot spread - there is nowhere for it to go - so the structure cannot flood a
     * corridor during generation, which is the other phase 2 acceptance criterion. It also means the
     * generator never depends on fluid ticks having run, which is not something worldgen can wait for.
     */
    public static final BlockState FLUID = RCBlocks.LEACHATE.get().defaultBlockState();

    /** The mineshaft parallel, and the only source of cobwebs in the game. */
    public static final BlockState WEB = Blocks.COBWEB.defaultBlockState();

    /**
     * What you actually walk down in a stairs piece.
     *
     * <p>The first version of the stairs placed none: it hollowed a nine-tall shaft and called it a
     * descent, so entering one was a five-block fall into a room with no way back up - the palette had
     * no ladder, no slab and no stair in it at all.
     */
    public static final BlockState STEP = Blocks.BRICK_STAIRS.defaultBlockState();

    /**
     * The drowned spawner - the sewer's threat, and the mineshaft parallel again.
     *
     * <p><b>A spawner is the only way drowned can exist here.</b> {@code spawn_overrides} sets which
     * mobs a structure offers but does not bypass {@code SpawnPlacements}, and drowned are registered
     * {@code IN_WATER}, which tests {@code FluidTags.WATER} - leachate is deliberately outside that tag,
     * so natural spawning yields none, ever. {@code Drowned.checkDrownedSpawnRules} has an explicit
     * {@code EntitySpawnReason.isSpawner} branch that skips the water test, so a plain spawner works with no
     * custom rules at all. Vanilla puts a cave spider spawner in its mineshafts for the same reason.
     */
    public static final BlockState SPAWNER = Blocks.SPAWNER.defaultBlockState();

    /** Air inside the tunnels. {@code CAVE_AIR} rather than air, as every vanilla structure uses. */
    public static final BlockState HOLLOW = Blocks.CAVE_AIR.defaultBlockState();

    /**
     * The wet course: the wall block that sits beside the channel.
     *
     * <p><b>Decay follows water.</b> Moss grows where it is damp and brick cracks where it is wetted and
     * dried, so the course level with the channel is the one that goes green while the wall above it
     * stays clean. Picking this by height rather than by a die roll is the difference between a sewer
     * that looks old and one that looks speckled.
     *
     * <p>Mossy <em>stone</em> bricks, not mossy cobblestone: cobblestone is in
     * {@code #minecraft:stone_crafting_materials}, which crafts a vanilla furnace and opens the iron
     * gate. The classic mossy-cobble sewer is the one thing this structure cannot be built from.
     */
    public static final BlockState WET_COURSE = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();

    /** The same course where it has cracked rather than greened. */
    public static final BlockState CRACKED_COURSE = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();

    /**
     * Silt: what settles where the flow slows, and what you can dig through it.
     *
     * <p><b>Suspicious gravel rather than gravel</b> (owner, 2026-08-18). A sewer's deposits are the one
     * place in this world where the fiction and vanilla's archaeology already agree: silt is a slow
     * accumulation of everything that came down the pipe, which is exactly what a brushable block is for.
     * It costs the mod nothing in code - the block is vanilla and the payout is a loot table.
     *
     * <p>Two consequences worth knowing before tuning it. A brushable block <b>drops nothing when
     * mined</b> ({@code loot_table/blocks/suspicious_gravel.json} has no pools at all), so the silt is no
     * longer a free source of gravel and flint; brushing it turns it into ordinary gravel, which is. And
     * the block is only worth placing if something is in it - a brushable with no loot table set on its
     * block entity brushes away to nothing at all, silently, which is strictly worse than the gravel it
     * replaced. {@code SewerPiece.deposit} is the only thing that may place one, for that reason.
     */
    public static final BlockState SILT = Blocks.SUSPICIOUS_GRAVEL.defaultBlockState();

    /**
     * The finer half of the same deposit.
     *
     * <p><b>Sand, not clay, and the first draft got this wrong.</b> Clay is the obvious silt and it is
     * also the entire #115 chain: sherd, Grog, Kitty Litter, Dry Clay Body, water cauldron. Nothing else
     * in this world drops a clay ball, so a clay block in worldgen would retire that chain outright -
     * four balls from any tool. Sand is already obtainable from sledgehammered Reinforced Concrete, so
     * it costs nothing to hand out.
     *
     * <p>The improvements spec flagged this in the same sentence that proposed it - "clay is the chain
     * from #115, so a free source needs a glance at the gates doc" - and the glance was not taken. A
     * warning you write and then walk past is worse than no warning, because the next reader assumes it
     * was heeded.
     *
     * <p><b>Suspicious sand now</b>, for the reasons on {@link #SILT} - and it hands out even less than
     * before, because a brushable drops nothing when mined and has to be brushed into plain sand first.
     */
    public static final BlockState FINE_SILT = Blocks.SUSPICIOUS_SAND.defaultBlockState();

    /**
     * Damp growth, which needs dark and gets it everywhere down here.
     *
     * <p><b>RED, not brown, and the difference is not aesthetic.</b> {@code brown_mushroom} carries
     * {@code .lightLevel(1)}. A hostile spawn needs block light <b>0</b>, so a scatter of brown mushrooms
     * down a corridor quietly switches off the spawns that corridor exists to host - the same failure as
     * hanging a lantern there, hidden in a block nobody thinks of as a light. Red emits nothing.
     *
     * <p><b>It shipped brown, and the test that exists to catch exactly this went green for a whole
     * phase.</b> The growth is seeded from the piece's own bounding box, and a GameTest plot lands at a
     * different absolute position on every run - so whether any mushroom was placed at all in the probed
     * corridor was a coin flip per run, and the first several came up empty. A flaky test that fails open
     * reads as a passing one.
     *
     * <p>Not the mod's own Dump Mushroom, which would be the thematic pick: its {@code mayPlaceOn} is
     * restricted to mycelium and dirt, and widening that to accept brick would change where a player can
     * plant one everywhere in the world - a bigger change than this corridor deserves.
     */
    public static final BlockState GROWTH = Blocks.RED_MUSHROOM.defaultBlockState();

    /**
     * Maintenance lighting.
     *
     * <p><b>Light marks where people worked, and this system was abandoned.</b> It belongs at the shaft
     * foot, in the chamber and in the dens, and nowhere else - which is also exactly where spawning must
     * not happen, because a hostile spawn needs block light 0 and any source suppresses it. The fiction
     * and the mob rule want the same rooms lit, which is the tell that the placement is right rather
     * than a compromise.
     */
    public static final BlockState LIGHT = Blocks.LANTERN.defaultBlockState()
        .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);

    /** Ladders out of the entrance shaft. */
    public static final BlockState LADDER = Blocks.LADDER.defaultBlockState();

    /** The surface marker: a 3x3 pad, so a one-block cover is findable. */
    public static final BlockState PAD =
        com.flatts.recompile.registry.RCBlocks.REINFORCED_CONCRETE.get().defaultBlockState();

    /** The cover itself - prybar-only, and the reason a sewer is not simply open. */
    public static final BlockState COVER =
        com.flatts.recompile.registry.RCBlocks.MANHOLE.get().defaultBlockState();

    /** What the loot sits in. */
    public static final BlockState BARREL = Blocks.BARREL.defaultBlockState();

    /**
     * The turtle den's floor.
     *
     * <p>Sand because that is what a turtle wants - {@code TurtleEggBlock.onSand} is half of vanilla's
     * own spawn rule, and a turtle standing on sand beside water is the animal in its habitat rather
     * than an animal that happens to be here. It does <b>not</b> make them renewable: the other half of
     * that rule is {@code y < seaLevel + 4}, and this world's sea level is -64.
     *
     * <p><b>Plain sand, and deliberately not the suspicious kind the silt became.</b> Vanilla puts
     * {@code suspicious_sand} in {@code #minecraft:sand}, so the turtle rule would survive the swap - the
     * reason is what the two surfaces are for. Silt is a deposit and a den floor is a habitat, and a
     * habitat floored in two dozen brushable blocks is a dig site with animals standing on it. It is also
     * the room a player is meant to look into rather than take apart.
     */
    public static final BlockState TURTLE_BED = Blocks.SAND.defaultBlockState();

    /**
     * The frog den's floor.
     *
     * <p>Mud, and this one is exact: {@code #minecraft:frogs_spawnable_on} is grass block, mud and the
     * two mangrove roots. Mud is the only member of that tag a sewer could plausibly contain, so a frog
     * on mud is standing on the one surface vanilla itself considers frog ground.
     */
    public static final BlockState FROG_BED = Blocks.MUD.defaultBlockState();

    /**
     * Everything above, for the test that has to walk it.
     *
     * <p><b>It has to be everything, or it stops being a guard and becomes a list that reads as one.</b>
     * The ladder, the pad, the cover and the barrel were all placed by the structure from constants
     * living elsewhere, so the palette walk quietly covered less than it claimed - four blocks in, which
     * is the point at which nobody re-checks. They are declared here now, which is also why the
     * entrance's own constants are gone: one home, so a new block cannot be added anywhere else.
     */
    public static final List<BlockState> ALL =
        List.of(WALL, GRATE, FLUID, WEB, STEP, SPAWNER, LADDER, PAD, COVER, BARREL,
            TURTLE_BED, FROG_BED, WET_COURSE, CRACKED_COURSE, SILT, FINE_SILT, GROWTH,
            LIGHT, HOLLOW);

    private SewerPalette() {
    }
}
