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

    /** Run every room's own postProcess into the test world, and hand back the origin they were laid out from. */
    private static BlockPos build(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos o = helper.absolutePos(new BlockPos(0, 1, 0));
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
        // AIR, from the forecourt, through whatever the pieces actually wrote - so a door that one
        // piece cut and the other piece walled back over is what fails here, and nothing else can see it.
        RCGameTests.test("every_room_is_reachable_from_the_forecourt_through_placed_air", 200, helper -> {
            BlockPos o = build(helper);
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
                for (BlockPos n : new BlockPos[]{here.above(), here.below(), here.north(), here.south(), here.east(), here.west()}) {
                    if (all.isInside(n) && seen.add(n) && passable(level.getBlockState(n))) {
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
            BlockPos o = build(helper);
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
                        if (s.is(Blocks.WATER)) {
                            waterCells++;
                            if (!water.isInside(at)) {
                                wrong.add("water outside the guardian tank at " + at);
                            }
                        } else if (s.is(RCBlocks.LEACHATE.get())) {
                            leachate++;
                            if (Room.GUARDIAN_TANK.box(ox, base, oz).isInside(at)) {
                                wrong.add("leachate in the guardian tank at " + at);
                            }
                        } else if (water.isInside(at) && !s.is(Blocks.SPAWNER)) {
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
            BlockPos o = build(helper);
            assertSpawner(helper, AquariumStructure.guardianSpawner(o.getX(), o.getY(), o.getZ()), "minecraft:guardian");
            assertSpawner(helper, AquariumStructure.drownedSpawner(o.getX(), o.getY(), o.getZ()), "minecraft:drowned");
            helper.succeed();
        });

        RCGameTests.test("the_chest_the_silt_and_the_pedestal_carry_their_contents", 200, helper -> {
            BlockPos o = build(helper);
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
            BlockPos o = helper.absolutePos(new BlockPos(0, 1, 0));
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
            build(helper);
            List<String> standing = new ArrayList<>();
            for (BlockPos p : planted) {
                if (!level.getBlockState(p).isAir()) {
                    standing.add(p.toString());
                }
            }
            helper.assertTrue(standing.isEmpty(), "feature blocks still standing over the building: " + standing);
            helper.succeed();
        });

        // The manufactured half of the prismarine route (spec 8.6): the grit separates into a shard.
        RCGameTests.test("prismarine_grit_separates_into_a_prismarine_shard", 20, helper -> {
            boolean found = false;
            for (RecipeHolder<SeparatingRecipe> holder : helper.getLevel().recipeAccess()
                    .recipeMap().byType(RCRecipeTypes.SEPARATING.get())) {
                if (holder.value().matches(new SingleRecipeInput(new ItemStack(RCItems.PRISMARINE_GRIT.get())), helper.getLevel())) {
                    found = holder.value().results().stream().anyMatch(r -> r.item() == Items.PRISMARINE_SHARD);
                }
            }
            helper.assertTrue(found, "no separating recipe turns Prismarine Grit into a prismarine shard");
            helper.succeed();
        });
    }

    private static void assertSpawner(GameTestHelper helper, BlockPos at, String mob) {
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
        helper.assertTrue(range == 4, "the spawner at " + at + " has a range of " + range + " rather than 4");
    }
}
