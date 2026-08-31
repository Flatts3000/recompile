package com.flatts.recompile.gametest;

import com.flatts.recompile.content.worldgen.tower.CoolingTowerPiece;
import com.flatts.recompile.content.worldgen.tower.SmokestackPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * What the two landmark spawners are actually configured with (#317).
 *
 * <p><b>This exists because three separate defects shipped through a green suite.</b> The spawner was
 * placed before {@code clearInterior} and silently overwritten with air; its equipment was written into
 * the entity tag, which took that tag to two keys and so skipped {@code finalizeSpawn}, and the Parched
 * spawned without the bow that call hands it; and the cap's loot table wrote a partial
 * {@code equippable} that decoded fine, equipped fine, and rendered nothing. Every one of those
 * generates a structure that looks correct from outside, and not one of them logs a line.
 *
 * <p><b>So this reads the spawner rather than looking for one.</b> {@code SewerLifeTests} already
 * learned that half - a block check alone passed against a decorative empty cage - and the same trap is
 * one layer deeper here: a spawner naming the right mob can still be dead in daylight, sealed to a
 * range of one, or handing out an invisible hat. Configuration is the thing under test.
 *
 * <p>{@code BaseSpawner} keeps its spawn data private and exposes no accessor, so the reading is off
 * {@code SpawnerBlockEntity.getUpdateTag}, which is the full save minus {@code SpawnPotentials}.
 */
final class LandmarkSpawnerTests {

    private LandmarkSpawnerTests() {
    }

    /** Run a piece's own postProcess into the test world and hand back the first spawner's save tag. */
    private static CompoundTag spawnerTagOf(GameTestHelper helper, StructurePiece piece) {
        var level = helper.getLevel();
        BoundingBox box = piece.getBoundingBox();
        BoundingBox limit = new BoundingBox(box.minX() - 8, box.minY() - 8, box.minZ() - 8,
            box.maxX() + 8, box.maxY() + 8, box.maxZ() + 8);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
            RandomSource.create(7L), limit, new ChunkPos(box.minX() >> 4, box.minZ() >> 4),
            new BlockPos(box.minX(), box.minY(), box.minZ()));

        // Only the bottom few courses can hold it, and sweeping the whole shell of a 76 tall tower is
        // hundreds of thousands of positions for a block that is always at the foot.
        for (int y = box.minY(); y <= Math.min(box.maxY(), box.minY() + 4); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos here = new BlockPos(x, y, z);
                    if (level.getBlockState(here).is(Blocks.SPAWNER)
                            && level.getBlockEntity(here) instanceof SpawnerBlockEntity be) {
                        return be.getUpdateTag(level.registryAccess());
                    }
                }
            }
        }
        return null;
    }

    /** The three things that make a landmark spawner a landmark spawner rather than a cage. */
    private static void assertConfigured(GameTestHelper helper, CompoundTag tag, String what,
            String mob) {
        helper.assertTrue(tag != null, "the " + what + " generated no spawner at all. It is placed "
            + "after the passes that clear the column it stands in, and putting it back before them "
            + "writes a spawner and then deletes it - which is what shipped, and nothing caught it");
        String text = tag.toString();

        helper.assertTrue(text.contains(mob),
            "the " + what + "'s spawner does not name " + mob + "; it holds " + text);

        // SPAWNS IN DAYLIGHT. An empty custom_spawn_rules is the whole mechanism, and its ABSENCE is
        // what has to fail here: without the key BaseSpawner falls back to
        // SpawnPlacements.checkSpawnRules, and an ordinary SPAWNER is not exempt from the darkness test
        // (only TRIAL_SPAWNER is). Both landmarks would then sit dead through every afternoon, which is
        // the one symptom a player walking up to one would actually see.
        helper.assertTrue(text.contains("custom_spawn_rules"),
            "the " + what + "'s spawner has no custom_spawn_rules, so it is back on the vanilla light "
                + "check and spawns nothing in daylight. It holds " + text);

        // AND IT REACHES OUT. The range was clamped to 1 once, to keep mobs sealed inside the brick
        // until somebody broke in; the owner reversed that, because it made the structure inert to
        // anyone who merely walked past. A revert is one character and is invisible in-game until you
        // stand next to one at the wrong hour.
        int range = tag.getIntOr("SpawnRange", 4);
        helper.assertTrue(range == 4, "the " + what + "'s spawner has a range of " + range
            + " rather than 4, so it no longer reaches past its own walls");
    }

    static void register() {

        RCGameTests.test("the_cooling_tower_spawner_is_configured", 200, helper -> {
            BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0));
            CoolingTowerPiece piece = new CoolingTowerPiece(RandomSource.create(3L),
                at.getX(), at.getY(), at.getZ());
            assertConfigured(helper, spawnerTagOf(helper, piece), "cooling tower", "minecraft:parched");
            helper.succeed();
        });

        RCGameTests.test("the_smokestack_spawner_is_configured", 200, helper -> {
            // ONE IN THREE STACKS IS FELLED, and a felled one has no flue to stand a spawner in, so the
            // position is searched rather than assumed. The felled roll is derived from the foot
            // coordinates, so stepping x is the whole search.
            CompoundTag tag = null;
            for (int i = 0; i < 8 && tag == null; i++) {
                BlockPos at = helper.absolutePos(new BlockPos(i * 24, 1, 0));
                tag = spawnerTagOf(helper, new SmokestackPiece(RandomSource.create(3L),
                    at.getX(), at.getY(), at.getZ()));
            }
            assertConfigured(helper, tag, "smokestack", "minecraft:husk");
            helper.succeed();
        });

        // THE HAT IS WORN AND IT IS VISIBLE, which are two assertions because they failed separately.
        //
        // The cap shipped with a set_components writing a partial `equippable` naming the head slot.
        // That is enough to be worn - Equippable.CODEC requires only `slot`, and resolveSlot reads it
        // straight - so the Parched stopped burning and the fix looked done. But set_components
        // REPLACES rather than merges, and the replacement had no asset_id, which is the only thing the
        // armour layer draws from. The result was a working, invisible hat, reported as no hat.
        RCGameTests.test("the_sun_cap_is_headgear_the_game_can_draw", 100, helper -> {
            var level = helper.getLevel();
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath("recompile", "equipment/sun_cap"));
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            helper.assertTrue(table != LootTable.EMPTY,
                "recompile:equipment/sun_cap does not resolve, so the tower's spawner equips nothing "
                    + "and its Parched burn in the open basin");

            var rolled = table.getRandomItems(new LootParams.Builder(level)
                .create(LootContextParamSets.EMPTY));
            helper.assertTrue(!rolled.isEmpty(), "recompile:equipment/sun_cap rolled no items");

            for (ItemStack stack : rolled) {
                var equippable = stack.get(DataComponents.EQUIPPABLE);
                helper.assertTrue(equippable != null,
                    stack + " out of the sun cap table carries no equippable component, so "
                        + "EquipmentUser.resolveSlot drops it into MAINHAND and it stops nothing");
                helper.assertTrue(equippable.slot() == EquipmentSlot.HEAD,
                    stack + " equips to " + equippable.slot() + " rather than HEAD. Mob.burnUndead "
                        + "only ever looks at the head slot, so anywhere else is decoration");
                helper.assertTrue(equippable.assetId().isPresent(),
                    stack + " has an equippable with no asset_id. It will be worn and it will stop the "
                        + "burn, and it will render as nothing at all - which is how this shipped, and "
                        + "how it was reported as the mob not wearing a hat");
            }
            helper.succeed();
        });
    }
}
