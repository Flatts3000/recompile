package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.DisplayPedestalBlockEntity;
import com.flatts.recompile.content.block.entity.HydroponicsBayBlockEntity;
import com.flatts.recompile.content.recipe.SeparatingRecipe;
import com.flatts.recompile.content.worldgen.aquarium.AquariumStructure;
import com.flatts.recompile.content.worldgen.aquarium.AquariumStructure.Room;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Municipal Aquarium in the world (spec {@code docs/municipal_aquarium_spec.md}).
 *
 * <p>The layout's arithmetic is proven by {@code AquariumLayoutTest} with no world. These build the
 * real pieces into the test level and read the blocks back, which is the half arithmetic cannot
 * hold: that a door cut by one piece survives the other piece writing the same wall, that the water is
 * where the layout says and nowhere else, that a spawner is configured rather than merely present.
 */
public final class AquariumTests {

    private AquariumTests() {
    }

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, path));
    }

    /**
     * Run every room's own postProcess into the test world, and hand back the origin they were laid out
     * from.
     *
     * <p><b>Each building test lifts its build by a different amount.</b> The harness sites test plots
     * twelve blocks apart and this building is thirty-eight wide, so two tests that both built at plot
     * height would build through each other - the roof-integrity assertion first failed on two cells
     * that the NEXT test's clearing pass had emptied, which a trace of this method's own pieces proved
     * were concrete when it finished. Forty blocks of lift is the building's height plus its clearing
     * reach plus a margin, so no two tests share a band whatever plot they land in.
     */
    private static BlockPos build(GameTestHelper helper, int lift) {
        var level = helper.getLevel();
        BlockPos o = helper.absolutePos(new BlockPos(0, 1 + lift, 0));
        BoundingBox all = AquariumStructure.footprint(o.getX(), o.getY(), o.getZ());
        BoundingBox limit = new BoundingBox(all.minX() - 8, all.minY() - 8, all.minZ() - 8,
            all.maxX() + 8, all.maxY() + AquariumStructure.CLEAR_ABOVE + 8, all.maxZ() + 8);
        for (StructurePiece piece : AquariumStructure.pieces(o.getX(), o.getY(), o.getZ())) {
            BoundingBox box = piece.getBoundingBox();
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(7L), limit, new ChunkPos(box.minX() >> 4, box.minZ() >> 4),
                new BlockPos(box.minX(), box.minY(), box.minZ()));
        }
        return o;
    }

    private static boolean passable(BlockState s) {
        return s.isAir() || s.is(Blocks.WATER) || s.is(RCBlocks.LEACHATE.get());
    }

    static void register() {

        RCGameTests.test("the_municipal_aquarium_is_registered_and_aimed_at_the_yard", 20, helper -> {
            var access = helper.getLevel().registryAccess();
            var structures = access.lookupOrThrow(Registries.STRUCTURE);
            helper.assertTrue(structures.get(ResourceKey.create(Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "municipal_aquarium"))).isPresent(),
                "recompile:municipal_aquarium did not load - the structure JSON is wrong or the type is unregistered");
            var sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
            helper.assertTrue(sets.get(ResourceKey.create(Registries.STRUCTURE_SET,
                    Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "municipal_aquariums"))).isPresent(),
                "the structure set did not load, so the building is registered and never placed");
            var biomes = access.lookupOrThrow(Registries.BIOME);
            TagKey<Biome> tag = TagKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "has_structure/municipal_aquarium"));
            boolean yard = biomes.get(tag).map(set -> set.stream().anyMatch(h -> h.is(
                ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "demolition_yard")))))
                .orElse(false);
            helper.assertTrue(yard, "the has_structure tag does not name the demolition yard (ruling 8.5)");
            helper.succeed();
        });

        // RULE 1 OF THE ROOM GRAPH, IN THE WORLD. The layout test walks doors as arithmetic; this walks
        // ON FOOT, from the forecourt, through whatever the pieces actually wrote. A door one piece cut
        // and the other walled back over fails here, and so does a door that opens into an exhibit
        // bay's glass - which the first version of this test could not see, because it flooded air in
        // six directions and simply climbed over the glass. A walker needs headroom, steps up one,
        // falls any distance, and swims through fluid.
        RCGameTests.test("every_room_is_reachable_from_the_forecourt_on_foot", 200, helper -> {
            BlockPos o = build(helper, 0);
            var level = helper.getLevel();
            int ox = o.getX();
            int base = o.getY();
            int oz = o.getZ();
            BoundingBox all = AquariumStructure.footprint(ox, base, oz);

            BoundingBox fore = Room.FORECOURT.interior(ox, base, oz);
            BlockPos start = new BlockPos((fore.minX() + fore.maxX()) / 2, fore.minY(), (fore.minZ() + fore.maxZ()) / 2);
            helper.assertTrue(passable(level.getBlockState(start)), "the forecourt's own floor air is not air at " + start);

            Set<BlockPos> seen = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                BlockPos here = queue.poll();
                List<BlockPos> next = new ArrayList<>();
                boolean swimming = level.getBlockState(here).getFluidState().isSource()
                    || !level.getBlockState(here).getFluidState().isEmpty();
                if (swimming && passable(level.getBlockState(here.above()))) {
                    next.add(here.above());
                }
                for (BlockPos t : new BlockPos[]{here.north(), here.south(), here.east(), here.west()}) {
                    if (passable(level.getBlockState(t)) && passable(level.getBlockState(t.above()))) {
                        next.add(t);
                    } else if (passable(level.getBlockState(t.above())) && passable(level.getBlockState(t.above(2)))
                            && passable(level.getBlockState(here.above(2)))) {
                        next.add(t.above());
                    }
                }
                for (BlockPos n : next) {
                    // Fall until something holds.
                    while (all.isInside(n.below()) && passable(level.getBlockState(n.below()))
                            && level.getBlockState(n).getFluidState().isEmpty()) {
                        n = n.below();
                    }
                    if (all.isInside(n) && seen.add(n)) {
                        queue.add(n);
                    }
                }
            }

            List<String> unreached = new ArrayList<>();
            for (Room room : Room.values()) {
                BoundingBox in = room.interior(ox, base, oz);
                boolean any = false;
                for (int x = in.minX(); x <= in.maxX() && !any; x++) {
                    for (int y = in.minY(); y <= in.maxY() && !any; y++) {
                        for (int z = in.minZ(); z <= in.maxZ() && !any; z++) {
                            any = seen.contains(new BlockPos(x, y, z)) && passable(level.getBlockState(new BlockPos(x, y, z)));
                        }
                    }
                }
                if (!any) {
                    unreached.add(room.name());
                }
            }
            helper.assertTrue(unreached.isEmpty(),
                "rooms a player cannot walk into from the forecourt without breaking a block: " + unreached);
            helper.succeed();
        });

        // RULE 4: exactly one room holds water. The two fluids are one block id apart and a swap fails
        // silently in both directions, so this reads every cell in the footprint.
        RCGameTests.test("only_the_guardian_tank_holds_water_and_the_rest_is_leachate", 200, helper -> {
            BlockPos o = build(helper, 40);
            var level = helper.getLevel();
            int ox = o.getX();
            int base = o.getY();
            int oz = o.getZ();
            BoundingBox all = AquariumStructure.footprint(ox, base, oz);
            BoundingBox water = AquariumStructure.guardianWater(ox, base, oz);

            List<String> wrong = new ArrayList<>();
            int leachate = 0;
            int waterCells = 0;
            for (int x = all.minX(); x <= all.maxX(); x++) {
                for (int y = all.minY(); y <= all.maxY(); y++) {
                    for (int z = all.minZ(); z <= all.maxZ(); z++) {
                        BlockPos at = new BlockPos(x, y, z);
                        BlockState s = level.getBlockState(at);
                        // FLUID STATE, not block: a waterlogged coral fan is a bucketable water source
                        // and is not Blocks.WATER. Eighteen of them shipped past the block check.
                        if (s.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                            waterCells++;
                            if (!water.isInside(at)) {
                                wrong.add("water outside the guardian tank at " + at);
                            }
                        } else if (s.is(RCBlocks.LEACHATE.get())) {
                            leachate++;
                            if (Room.GUARDIAN_TANK.box(ox, base, oz).isInside(at)) {
                                wrong.add("leachate in the guardian tank at " + at);
                            }
                        } else if (water.isInside(at) && !s.is(Blocks.SPAWNER) && !s.is(Blocks.WATER)) {
                            wrong.add("the guardian tank holds " + s + " rather than water at " + at);
                        }
                    }
                }
            }
            helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
            helper.assertTrue(waterCells > 0, "no water anywhere, so there is no guardian tank");
            helper.assertTrue(leachate > 0, "no leachate anywhere, so the pools and the sump were not placed");
            helper.succeed();
        });

        // Both spawners, configured rather than merely present: LandmarkSpawnerTests records three
        // separate silent spawner defects that shipped through a green suite.
        RCGameTests.test("the_guardian_and_the_drowned_spawners_are_configured", 200, helper -> {
            BlockPos o = build(helper, 80);
            assertSpawner(helper, AquariumStructure.guardianSpawner(o.getX(), o.getY(), o.getZ()),
                "minecraft:guardian", AquariumStructure.GUARDIAN_SPAWN_RANGE);
            assertSpawner(helper, AquariumStructure.drownedSpawner(o.getX(), o.getY(), o.getZ()),
                "minecraft:drowned", 4);
            helper.succeed();
        });

        // THE EXEMPTION THAT LETS A GUARDIAN SPAWN ALSO REMOVES ITS WATER CHECK, so the range is the only
        // thing keeping one in the tank. Asserted as geometry rather than as a number: every cell the
        // spawner can reach must be inside the water, or guardians appear flopping on the yard outside.
        RCGameTests.test("the_guardian_spawner_cannot_reach_outside_its_tank", 20, helper -> {
            BlockPos o = helper.absolutePos(new BlockPos(0, 1, 0));
            BoundingBox reach = AquariumStructure.guardianSpawnReach(o.getX(), o.getY(), o.getZ());
            BoundingBox water = AquariumStructure.guardianWater(o.getX(), o.getY(), o.getZ());
            List<String> outside = new ArrayList<>();
            for (int x = reach.minX(); x <= reach.maxX(); x++) {
                for (int y = reach.minY(); y <= reach.maxY(); y++) {
                    for (int z = reach.minZ(); z <= reach.maxZ(); z++) {
                        BlockPos at = new BlockPos(x, y, z);
                        // y below the waterline is the tank floor: solid, so noCollision refuses it.
                        if (!water.isInside(at) && y > water.minY() - 1) {
                            outside.add(at.toString());
                        }
                    }
                }
            }
            helper.assertTrue(outside.isEmpty(),
                "the guardian spawner can place a mob outside its own water at " + outside);
            helper.succeed();
        });

        RCGameTests.test("the_chest_the_silt_and_the_pedestal_carry_their_contents", 200, helper -> {
            BlockPos o = build(helper, 120);
            var level = helper.getLevel();
            int ox = o.getX();
            int base = o.getY();
            int oz = o.getZ();

            ResourceKey<LootTable> chestLoot = loot("chests/aquarium_curator");
            ResourceKey<LootTable> siltLoot = loot("archaeology/aquarium_silt");
            helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(chestLoot) != LootTable.EMPTY,
                "chests/aquarium_curator did not load");
            helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(siltLoot) != LootTable.EMPTY,
                "archaeology/aquarium_silt did not load");

            BlockPos chest = AquariumStructure.chest(ox, base, oz);
            helper.assertTrue(level.getBlockEntity(chest) instanceof RandomizableContainer c
                    && chestLoot.equals(c.getLootTable()),
                "the curator's chest at " + chest + " does not carry chests/aquarium_curator");

            BoundingBox silt = AquariumStructure.silt(ox, base, oz);
            int brushables = 0;
            List<String> bare = new ArrayList<>();
            for (int x = silt.minX(); x <= silt.maxX(); x++) {
                for (int z = silt.minZ(); z <= silt.maxZ(); z++) {
                    BlockPos at = new BlockPos(x, silt.minY(), z);
                    if (level.getBlockEntity(at) instanceof BrushableBlockEntity be) {
                        brushables++;
                        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                        if (!tag.toString().contains("archaeology/aquarium_silt")) {
                            bare.add(at.toString());
                        }
                    }
                }
            }
            helper.assertTrue(brushables > 0, "the silt bed placed no brushable at all");
            helper.assertTrue(bare.isEmpty(),
                "brushables with no loot table, which brush away to nothing: " + bare);

            BlockPos plinth = AquariumStructure.pedestal(ox, base, oz);
            helper.assertTrue(level.getBlockEntity(plinth) instanceof DisplayPedestalBlockEntity p
                    && p.getDisplayed().is(Items.HEART_OF_THE_SEA),
                "the centrepiece pedestal at " + plinth + " does not hold the heart of the sea");
            helper.succeed();
        });

        // ALL FIFTEEN DEAD CORALS ARE ACTUALLY IN THE BUILDING, read off placed blocks at two origins of
        // opposite parity. The bays are the only dead coral in the game and the bay revival is the only
        // live coral, so a form this loop never places is a form no world contains - which is what
        // shipped, and which every existing test was blind to because they read the tag and the data map
        // rather than the world. Two parities because the bug was parity-dependent: thirteen forms at an
        // even origin and twelve at an odd one.
        RCGameTests.test("the_bays_hold_every_one_of_the_fifteen_dead_corals", 200, helper -> {
            List<String> missing = new ArrayList<>();
            for (int parity = 0; parity < 2; parity++) {
                var level = helper.getLevel();
                BlockPos o = helper.absolutePos(new BlockPos(parity, 1 + 200 + parity * 40, 0));
                for (StructurePiece piece : AquariumStructure.pieces(o.getX(), o.getY(), o.getZ())) {
                    BoundingBox pb = piece.getBoundingBox();
                    BoundingBox lim = new BoundingBox(pb.minX() - 8, pb.minY() - 8, pb.minZ() - 8,
                        pb.maxX() + 8, pb.maxY() + AquariumStructure.CLEAR_ABOVE + 8, pb.maxZ() + 8);
                    piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                        RandomSource.create(7L), lim, new ChunkPos(pb.minX() >> 4, pb.minZ() >> 4),
                        new BlockPos(pb.minX(), pb.minY(), pb.minZ()));
                }
                Set<Block> seen = new HashSet<>();
                BoundingBox g = Room.GALLERY.box(o.getX(), o.getY(), o.getZ());
                for (int x = g.minX(); x <= g.maxX(); x++) {
                    for (int y = g.minY(); y <= g.maxY(); y++) {
                        for (int z = g.minZ(); z <= g.maxZ(); z++) {
                            seen.add(level.getBlockState(new BlockPos(x, y, z)).getBlock());
                        }
                    }
                }
                for (String colour : List.of("tube", "brain", "bubble", "fire", "horn")) {
                    for (String form : List.of("coral", "coral_fan", "coral_block")) {
                        Block dead = BuiltInRegistries.BLOCK.getValue(
                            Identifier.withDefaultNamespace("dead_" + colour + "_" + form));
                        if (!seen.contains(dead)) {
                            missing.add("origin parity " + parity + ": " + dead);
                        }
                    }
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "dead corals the bays never place, so neither they nor their live forms exist in any "
                    + "world: " + missing);
            helper.succeed();
        });

        // BOTH MOSSES ARE ACTUALLY PLACED. Moss was purchasable from a wandering trader and nothing
        // else before this building; the filtration hall and the centrepiece tank are what make it a
        // find rather than a trade, so a refactor that stopped placing either would quietly take a route
        // back out of the game.
        RCGameTests.test("the_building_grows_moss_and_pale_moss", 200, helper -> {
            BlockPos o = build(helper, 240);
            var level = helper.getLevel();
            BoundingBox all = AquariumStructure.footprint(o.getX(), o.getY(), o.getZ());
            Set<Block> seen = new HashSet<>();
            for (int x = all.minX(); x <= all.maxX(); x++) {
                for (int y = all.minY(); y <= all.maxY(); y++) {
                    for (int z = all.minZ(); z <= all.maxZ(); z++) {
                        seen.add(level.getBlockState(new BlockPos(x, y, z)).getBlock());
                    }
                }
            }
            List<String> missing = new ArrayList<>();
            for (Block b : List.of(Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK)) {
                if (!seen.contains(b)) {
                    missing.add(b.toString());
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "the aquarium places no " + missing + ", so mining one is not a route after all");
            helper.succeed();
        });

        // The revival chain (spec section 4): fifteen dead forms, each growable in the bay and each
        // yielding its own live counterpart. Data only, so this is the whole proof it is wired.
        RCGameTests.test("every_dead_coral_revives_into_its_own_colour_in_the_bay", 20, helper -> {
            List<String> wrong = new ArrayList<>();
            for (String colour : List.of("tube", "brain", "bubble", "fire", "horn")) {
                for (String form : List.of("coral", "coral_fan", "coral_block")) {
                    Item dead = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("dead_" + colour + "_" + form));
                    Item live = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(colour + "_" + form));
                    if (!HydroponicsBayBlockEntity.isGrowable(new ItemStack(dead))) {
                        wrong.add(dead + " is not in #recompile:hydroponic");
                    } else if (HydroponicsBayBlockEntity.yieldOf(dead) != live) {
                        wrong.add(dead + " yields " + HydroponicsBayBlockEntity.yieldOf(dead) + " rather than " + live);
                    }
                }
            }
            helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
            helper.succeed();
        });

        // WHAT A FEATURE LEFT STANDING OVER THE BUILDING IS CLEARED. The first aquarium generated with a
        // Building Husk lattice rising through its forecourt and over its roof: the shell overwrote
        // everything inside the boxes, and the lattice above them was outside every box. Steel beams
        // are planted over three rooms before the build and must be gone after it.
        RCGameTests.test("a_lattice_standing_over_the_building_is_cleared", 200, helper -> {
            var level = helper.getLevel();
            BlockPos o = helper.absolutePos(new BlockPos(0, 1 + 160, 0));
            int ox = o.getX();
            int base = o.getY();
            int oz = o.getZ();
            List<BlockPos> planted = new ArrayList<>();
            for (Room room : List.of(Room.FORECOURT, Room.GALLERY, Room.FILTRATION_HALL)) {
                BoundingBox b = room.box(ox, base, oz);
                for (int y = b.maxY() + 1; y <= base + AquariumStructure.CLEAR_ABOVE; y += 5) {
                    planted.add(new BlockPos((b.minX() + b.maxX()) / 2, y, (b.minZ() + b.maxZ()) / 2));
                }
            }
            for (BlockPos p : planted) {
                level.setBlock(p, RCBlocks.STEEL_I_BEAM.get().defaultBlockState(), 3);
            }
            build(helper, 160);
            List<String> standing = new ArrayList<>();
            for (BlockPos p : planted) {
                if (!level.getBlockState(p).isAir()) {
                    standing.add(p.toString());
                }
            }
            helper.assertTrue(standing.isEmpty(), "feature blocks still standing over the building: " + standing);

            // AND THE CLEARING TOOK NOTHING THAT WAS OURS. Every roofed room keeps a solid roof over its
            // whole box after every piece has run - a lower room's clearing must not open a taller
            // neighbour's wall top or roof edge on the plane they share.
            List<String> holes = new ArrayList<>();
            for (Room room : Room.values()) {
                if (!room.roofed()) {
                    continue;
                }
                BoundingBox b = room.box(ox, base, oz);
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        if (level.getBlockState(new BlockPos(x, b.maxY(), z)).isAir()) {
                            holes.add(room + "@" + x + "," + z);
                        }
                    }
                }
            }
            helper.assertTrue(holes.isEmpty(), "roof cells that ended up air: " + holes.size() + " e.g. "
                + holes.subList(0, Math.min(6, holes.size())));
            helper.succeed();
        });

        // The manufactured half of the prismarine route (spec 8.6): the grit separates into a shard.
        RCGameTests.test("prismarine_grit_separates_into_a_prismarine_shard", 20, helper -> {
            boolean found = false;
            for (RecipeHolder<SeparatingRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.SEPARATING.get())) {
                if (holder.value().matches(new SingleRecipeInput(new ItemStack(RCItems.PRISMARINE_GRIT.get())), helper.getLevel())) {
                    // OR, not assign: a second recipe matching the grit and iterating after this one
                    // would otherwise flip the answer to false with the shipped route intact.
                    found |= holder.value().results().stream().anyMatch(r -> r.item() == Items.PRISMARINE_SHARD);
                }
            }
            helper.assertTrue(found, "no separating recipe turns Prismarine Grit into a prismarine shard");
            helper.succeed();
        });

        registerManifest();
    }

    /**
     * The guard on {@link AquariumStructure#VANILLA_PLACED}, and it runs in BOTH directions on
     * purpose.
     *
     * <p>The declaration exists so {@code tools/resource_checklist} can know what this procedural
     * building puts in the world; a list nothing checks is exactly the silent-drift shape that
     * produced #366 in the first place. Missing a member makes the checklist call a reachable
     * resource unreachable. Carrying a member the building no longer places makes it call an
     * unreachable one reachable, which is the worse direction because nothing in the game will ever
     * contradict it.
     */
    static void registerManifest() {
        RCGameTests.test("the_aquarium_places_exactly_the_vanilla_blocks_it_declares", 40, helper -> {
            BlockPos o = build(helper, 280);
            BoundingBox box = AquariumStructure.footprint(o.getX(), o.getY(), o.getZ());
            var level = helper.getLevel();

            java.util.Set<Block> seen = new java.util.HashSet<>();
            for (BlockPos at : BlockPos.betweenClosed(
                    new BlockPos(box.minX(), box.minY(), box.minZ()),
                    new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
                Block block = level.getBlockState(at).getBlock();
                if (block == Blocks.AIR) {
                    continue;
                }
                // Vanilla only: the mod's own blocks are not what the checklist tracks.
                if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")) {
                    seen.add(block);
                }
            }

            java.util.List<String> missing = new java.util.ArrayList<>();
            for (Block declared : AquariumStructure.VANILLA_PLACED) {
                // Position-hashed decoration is exempt from this half: see VANILLA_PLACED_SPARSE.
                if (AquariumStructure.VANILLA_PLACED_SPARSE.contains(declared)) {
                    continue;
                }
                if (!seen.contains(declared)) {
                    missing.add(BuiltInRegistries.BLOCK.getKey(declared).toString());
                }
            }
            java.util.List<String> undeclared = new java.util.ArrayList<>();
            for (Block block : seen) {
                if (!AquariumStructure.VANILLA_PLACED.contains(block)) {
                    undeclared.add(BuiltInRegistries.BLOCK.getKey(block).toString());
                }
            }
            java.util.Collections.sort(missing);
            java.util.Collections.sort(undeclared);

            helper.assertTrue(missing.isEmpty(), "AquariumStructure.VANILLA_PLACED declares blocks the "
                + "building does not place, so the resource checklist credits it with sources it does "
                + "not have: " + missing + ". If the block is position-hashed decoration, it belongs "
                + "in VANILLA_PLACED_SPARSE rather than being deleted.");
            helper.assertTrue(undeclared.isEmpty(), "the building places vanilla blocks that "
                + "AquariumStructure.VANILLA_PLACED does not declare, so the resource checklist will "
                + "call them unreachable (this is #366's failure mode): " + undeclared);

            // The item half. A block sweep cannot see what sits IN a block entity, and the heart of
            // the sea is the whole reason that matters: it is on the centrepiece pedestal and
            // nowhere else in the game.
            for (net.minecraft.world.item.Item declared : AquariumStructure.VANILLA_ITEMS_PLACED) {
                BlockPos plinth = AquariumStructure.pedestal(o.getX(), o.getY(), o.getZ());
                helper.assertTrue(
                    level.getBlockEntity(plinth) instanceof DisplayPedestalBlockEntity p
                        && p.getDisplayed().is(declared),
                    "AquariumStructure.VANILLA_ITEMS_PLACED declares "
                        + BuiltInRegistries.ITEM.getKey(declared) + " but the centrepiece pedestal at "
                        + plinth + " does not hold it, so the checklist credits a source that is not "
                        + "there");
            }
            helper.succeed();
        });
    }

    private static void assertSpawner(GameTestHelper helper, BlockPos at, String mob, int expectedRange) {
        var level = helper.getLevel();
        helper.assertTrue(level.getBlockState(at).is(Blocks.SPAWNER) && level.getBlockEntity(at) instanceof SpawnerBlockEntity,
            "no spawner at " + at + " for " + mob);
        CompoundTag tag = ((SpawnerBlockEntity) level.getBlockEntity(at)).getUpdateTag(level.registryAccess());
        String text = tag.toString();
        helper.assertTrue(text.contains(mob), "the spawner at " + at + " does not name " + mob + "; it holds " + text);
        helper.assertTrue(text.contains("custom_spawn_rules"),
            "the spawner at " + at + " has no custom_spawn_rules, so it is back on the vanilla placement check - "
                + "which for a guardian means it never spawns at all, water or no water");
        int range = tag.getIntOr("SpawnRange", 4);
        helper.assertTrue(range == expectedRange, "the spawner at " + at + " has a range of " + range
            + " rather than " + expectedRange);
    }
}
