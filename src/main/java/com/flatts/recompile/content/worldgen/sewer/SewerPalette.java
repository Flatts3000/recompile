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
 * {@code SewerTests.the_sewer_palette_opens_no_gate} does.
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
     * {@code MobSpawnType.isSpawner} branch that skips the water test, so a plain spawner works with no
     * custom rules at all. Vanilla puts a cave spider spawner in its mineshafts for the same reason.
     */
    public static final BlockState SPAWNER = Blocks.SPAWNER.defaultBlockState();

    /** Air inside the tunnels. {@code CAVE_AIR} rather than air, as every vanilla structure uses. */
    public static final BlockState HOLLOW = Blocks.CAVE_AIR.defaultBlockState();

    /** Everything above, for the test that has to walk it. */
    public static final List<BlockState> ALL = List.of(WALL, GRATE, FLUID, WEB, STEP, SPAWNER, HOLLOW);

    private SewerPalette() {
    }
}
