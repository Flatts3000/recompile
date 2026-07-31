package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.BurnerGeneratorBlockEntity;
import com.flatts.recompile.content.block.entity.SolarPanelBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GameTests for the power tier (#72) - the mod's first energy system.
 *
 * <p>The thing actually worth proving is that the mod <b>speaks Forge Energy</b>, because that is what
 * makes every energy mod interoperate for free. So these assert against {@code Capabilities.Energy.BLOCK}
 * rather than against the block entity's own methods wherever they can: the capability is the contract a
 * pipe sees, and a test that only called our own accessors would pass with the capability unregistered.
 */
final class PowerTierTests {

    private PowerTierTests() {
    }

    private static final BlockPos PANEL = new BlockPos(1, 3, 1);
    private static final BlockPos BURNER = new BlockPos(3, 1, 1);

    /**
     * Pin the world to noon so the daylight maths is unambiguous.
     *
     * <p>Through the command, because {@code ServerLevel.setDayTime} does not exist in 26.1 - time is not
     * settable from the level object any more. Without this the test would silently depend on whatever
     * time the gametest server happens to start at, and would pass or fail by luck.
     */
    private static void setNoon(ServerLevel level) {
        level.getServer().getCommands().performPrefixedCommand(
            level.getServer().createCommandSourceStack(), "time set noon");
    }

    private static EnergyHandler energyAt(net.minecraft.gametest.framework.GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), null);
    }

    static void register() {
        // The capability itself. Nothing else in this file means anything if a pipe cannot find the block.
        RCGameTests.test("generators_expose_the_energy_capability", 20, helper -> {
            helper.setBlock(PANEL, RCBlocks.SOLAR_PANEL.get());
            helper.setBlock(BURNER, RCBlocks.BURNER_GENERATOR.get());
            helper.assertTrue(energyAt(helper, PANEL) != null,
                "the Solar Panel must expose Capabilities.Energy.BLOCK, or no pipe can reach it");
            helper.assertTrue(energyAt(helper, BURNER) != null,
                "the Burner Generator must expose Capabilities.Energy.BLOCK");
            helper.succeed();
        });

        // A panel under open sky generates. Asserted through the capability, so this covers the whole
        // path: ticker -> buffer -> what a pipe would actually see.
        RCGameTests.test("solar_panel_generates_under_open_sky", 20, helper -> {
            helper.setBlock(PANEL, RCBlocks.SOLAR_PANEL.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(PANEL);
            setNoon(level);

            for (int i = 0; i < 20; i++) {
                SolarPanelBlockEntity.generateOnce(level, abs);
            }
            EnergyHandler handler = energyAt(helper, PANEL);
            helper.assertTrue(handler != null && handler.getAmountAsInt() > 0,
                "20 ticks of noon must produce energy, got "
                    + (handler == null ? "no handler" : handler.getAmountAsInt()));
            helper.succeed();
        });

        // Roofed over, it makes nothing. The sky check is what stops a panel buried in a base from
        // powering it, and it is easy to lose when the daylight maths is refactored.
        //
        // Asserted after a delay on purpose: immediately after setBlock the heightmap and the light
        // engine have not caught up, so canSeeSky still answers true and sky light still reads 15 with a
        // stone slab visibly sitting on top. Checking at tick 0 measures the world's staleness rather
        // than the panel's rule.
        RCGameTests.test("solar_panel_makes_nothing_under_a_roof", 40, helper -> {
            helper.setBlock(PANEL, RCBlocks.SOLAR_PANEL.get());
            helper.setBlock(PANEL.above(), Blocks.STONE);
            ServerLevel level = helper.getLevel();
            setNoon(level);

            helper.runAfterDelay(5, () -> {
                BlockPos abs = helper.absolutePos(PANEL);
                int rate = SolarPanelBlockEntity.outputAt(level, abs);
                helper.assertTrue(rate == 0, "a roofed panel must have zero output, got " + rate);
                helper.succeed();
            });
        });

        // The Burner turns fuel into energy, and only while it has fuel.
        RCGameTests.test("burner_generator_burns_fuel_into_energy", 40, helper -> {
            helper.setBlock(BURNER, RCBlocks.BURNER_GENERATOR.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(BURNER);

            if (!(level.getBlockEntity(abs) instanceof BurnerGeneratorBlockEntity generator)) {
                helper.fail("the burner generator has no BlockEntity");
                return;
            }
            int idle = BurnerGeneratorBlockEntity.burnOnce(level, abs);
            helper.assertTrue(idle == 0, "an empty generator must make nothing, got " + idle);

            generator.setItem(0, new ItemStack(RCItems.OILY_RAG.get(), 2));
            for (int i = 0; i < 10; i++) {
                BurnerGeneratorBlockEntity.burnOnce(level, abs);
            }
            // The first tick lights the fuel and produces nothing, so 10 ticks is 9 burning ones.
            helper.assertTrue(generator.stored() == BurnerGeneratorBlockEntity.FE_PER_TICK * 9,
                "9 burning ticks must make 9x the per-tick rate, got " + generator.stored());
            helper.assertTrue(generator.getItem(0).getCount() == 1,
                "exactly one rag must have been consumed, " + generator.getItem(0).getCount() + " left");
            helper.succeed();
        });

        // The buffer is what makes it run unattended, and what lets automation fuel it at all. One item
        // is consumed at a time, so a stack is a queue rather than a single wasted insert.
        RCGameTests.test("burner_generator_burns_the_buffer_one_at_a_time", 60, helper -> {
            helper.setBlock(BURNER, RCBlocks.BURNER_GENERATOR.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(BURNER);
            if (!(level.getBlockEntity(abs) instanceof BurnerGeneratorBlockEntity generator)) {
                helper.fail("the burner generator has no BlockEntity");
                return;
            }
            generator.setItem(0, new ItemStack(RCItems.OILY_RAG.get(), 3));
            BurnerGeneratorBlockEntity.burnOnce(level, abs);
            helper.assertTrue(generator.getItem(0).getCount() == 2,
                "lighting must take exactly one, got " + generator.getItem(0).getCount() + " left");
            helper.assertTrue(generator.isLit(), "and the generator must now be lit");
            helper.succeed();
        });

        // Non-fuel cannot be parked in the buffer, by hand or by pipe.
        RCGameTests.test("burner_generator_buffer_takes_only_fuel", 20, helper -> {
            helper.setBlock(BURNER, RCBlocks.BURNER_GENERATOR.get());
            ServerLevel level = helper.getLevel();
            if (!(level.getBlockEntity(helper.absolutePos(BURNER))
                    instanceof BurnerGeneratorBlockEntity generator)) {
                helper.fail("the burner generator has no BlockEntity");
                return;
            }
            helper.assertTrue(generator.canPlaceItem(0, new ItemStack(RCItems.OILY_RAG.get())),
                "an Oily Rag is fuel and must be accepted");
            helper.assertFalse(generator.canPlaceItem(0, new ItemStack(RCItems.SCRAP_METAL.get())),
                "scrap metal is not fuel and must be refused");
            helper.succeed();
        });

        // Fuel in, nothing out: a pipe must not pull the fuel back out of a generator it just filled.
        RCGameTests.test("burner_generator_gives_no_fuel_back", 20, helper -> {
            helper.setBlock(BURNER, RCBlocks.BURNER_GENERATOR.get());
            ServerLevel level = helper.getLevel();
            if (!(level.getBlockEntity(helper.absolutePos(BURNER))
                    instanceof BurnerGeneratorBlockEntity generator)) {
                helper.fail("the burner generator has no BlockEntity");
                return;
            }
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                helper.assertFalse(
                    generator.canTakeItemThroughFace(0, new ItemStack(RCItems.OILY_RAG.get()), side),
                    "no face may give fuel back, " + side + " did");
            }
            helper.succeed();
        });

        // Energy leaves through the capability, which is how a pipe or a machine takes it. Without this
        // the generators would fill up and power nothing.
        RCGameTests.test("energy_can_be_extracted_through_the_capability", 40, helper -> {
            helper.setBlock(BURNER, RCBlocks.BURNER_GENERATOR.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(BURNER);
            if (!(level.getBlockEntity(abs) instanceof BurnerGeneratorBlockEntity generator)) {
                helper.fail("the burner generator has no BlockEntity");
                return;
            }
            generator.setItem(0, new ItemStack(RCItems.OILY_RAG.get(), 1));
            for (int i = 0; i < 11; i++) {
                BurnerGeneratorBlockEntity.burnOnce(level, abs);
            }

            EnergyHandler handler = energyAt(helper, BURNER);
            int drawn;
            try (Transaction transaction = Transaction.openRoot()) {
                drawn = handler.extract(50, transaction);
                transaction.commit();
            }
            helper.assertTrue(drawn == 50, "a consumer must be able to draw 50 FE, got " + drawn);
            helper.assertTrue(generator.stored() == BurnerGeneratorBlockEntity.FE_PER_TICK * 10 - 50,
                "and the buffer must drop by exactly that, got " + generator.stored());
            helper.succeed();
        });
        // Jade and JEI copy is a silent failure: a missing key renders as the raw key itself, which looks
        // like a typo rather than a bug and only shows in a client the tests never run. These are the
        // keys the power tier's panels and tooltips name, so a rename that misses one fails here instead.
        RCGameTests.test("power_tier_lang_keys_resolve", 20, helper -> {
            List<String> missing = new ArrayList<>();
            for (String key : List.of(
                    "jade.recompile.energy_stored", "jade.recompile.energy_rate",
                    "jade.recompile.energy_idle", "jade.recompile.burn_remaining",
                    "jei.recompile.info.solar_panel", "jei.recompile.info.burner_generator",
                    "container.recompile.burner_generator")) {
                String rendered = Component.translatable(key).getString();
                if (rendered.equals(key)) {
                    missing.add(key);
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "these keys render as their own name, so they are missing from en_us.json: " + missing);
            helper.succeed();
        });

    }
}
