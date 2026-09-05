package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Jade's server data must actually SAY something, which nothing checked before.
 *
 * <p><b>Why this class exists.</b> A coverage pass across both test layers found
 * {@code compat/jade} at <b>0 of 260 branches</b> - every provider's enum declaration and
 * {@code getUid()} were touched and not one line of an {@code appendServerData} body. The one test
 * that mentioned Jade asserted that a class of the right NAME exists, by reflection, which a class
 * whose method body is empty passes just as well.
 *
 * <p>That gap matters more here than it would in most mods. The Separator, the Trommel and the
 * Pulverizer have <b>no screen at all</b>: Jade is the only way a player can see what a machine is
 * holding or why it is not running. Two separate silent stalls turned up in one session - a queue
 * starved by an under-count head, and a machine that quietly ran its 4000 FE buffer dry two thirds of
 * the way through a job - and Jade is precisely the instrument meant to explain both.
 *
 * <p><b>Reflection, not imports, and that is deliberate.</b> Jade is an optional dependency; the mod
 * ships and runs without it. A GameTest class is registered unconditionally at mod init, so importing
 * {@code snownee.jade.api.BlockAccessor} here would throw {@code NoClassDefFoundError} on a Jade-less
 * install and take the whole mod down. {@link MachineParityTests} already reaches for Jade by name for
 * the same reason. When Jade is absent these tests pass trivially rather than failing, which is the
 * correct behaviour for a check on an integration that is not installed.
 */
final class JadeDataTests {

    private static final String JADE_PKG = "com.flatts.recompile.compat.jade.";
    private static final String ACCESSOR = "snownee.jade.api.BlockAccessor";

    private JadeDataTests() {
    }

    static void register() {

        // EVERY POWERED MACHINE'S DATA PROVIDER WRITES SOMETHING, and something with a real number in
        // it. Derived from the registry the same way the rest of MachineParityTests' rules are, so a
        // machine added next year is covered the day its core is registered rather than the day
        // somebody remembers this file.
        //
        // The bar is deliberately low and still catches the failure that matters: a provider that
        // writes nothing, or that reports a zero-capacity battery, leaves a screenless machine with no
        // way to explain itself. The Pulverizer once shipped with no providers at all and an audit
        // found it, not a test.
        RCGameTests.test("every_powered_machine_reports_real_numbers_to_jade", 60, helper -> {
            helper.assertTrue(jadePresent(),
                "Jade is not on the GameTest classpath, so this test would pass without checking "
                    + "anything and compat/jade would silently return to zero coverage. It is "
                    + "runtimeOnly in build.gradle and every dev and CI run has it.");
            List<Block> machines = poweredMachines(helper);
            helper.assertTrue(machines.size() >= 3,
                "only " + machines.size() + " powered machine cores found - discovery is broken, so "
                    + "this test would pass by checking nothing");

            List<String> gaps = new ArrayList<>();
            for (Block machine : machines) {
                String name = idOf(machine);
                BlockPos probe = new BlockPos(1, 1, 1);
                helper.setBlock(probe, machine);
                BlockPos abs = helper.absolutePos(probe);
                BlockEntity be = helper.getLevel().getBlockEntity(abs);
                if (be == null) {
                    gaps.add(name + " has no BlockEntity to report from");
                    helper.setBlock(probe, net.minecraft.world.level.block.Blocks.AIR);
                    continue;
                }

                CompoundTag data = serverData(camel(name) + "DataProvider", helper.getLevel(), abs, be);
                if (data == null) {
                    gaps.add(name + " has no usable " + camel(name) + "DataProvider");
                } else if (data.isEmpty()) {
                    gaps.add(name + "'s Jade provider wrote nothing at all, so the machine cannot "
                        + "explain itself and it has no screen to fall back on");
                } else if (data.getIntOr("capacity", 0) <= 0) {
                    gaps.add(name + " reports a battery capacity of " + data.getIntOr("capacity", 0)
                        + ", so its power bar can only ever render empty");
                }
                helper.setBlock(probe, net.minecraft.world.level.block.Blocks.AIR);
            }

            helper.assertTrue(gaps.isEmpty(),
                "Jade is the ONLY feedback surface these machines have, and these are silent: " + gaps);
            helper.succeed();
        });

        // THE NUMBERS TRACK THE MACHINE, rather than being constants that happen to be non-zero. The
        // sweep above would pass against a provider that hardcoded a capacity and nothing else, which
        // is the shape a stub takes. This one changes the machine and watches the report change with
        // it - the property a player is actually relying on when they look at a stalled Separator.
        RCGameTests.test("jade_follows_the_separators_power_down", 60, helper -> {
            helper.assertTrue(jadePresent(),
                "Jade is not on the GameTest classpath, so this test would pass without checking "
                    + "anything and compat/jade would silently return to zero coverage. It is "
                    + "runtimeOnly in build.gradle and every dev and CI run has it.");
            BlockPos probe = new BlockPos(1, 1, 1);
            helper.setBlock(probe, com.flatts.recompile.registry.RCBlocks.SEPARATOR.get());
            BlockPos abs = helper.absolutePos(probe);
            if (!(helper.getLevel().getBlockEntity(abs)
                    instanceof com.flatts.recompile.content.block.entity.SeparatorBlockEntity sep)) {
                helper.fail("the Separator has no BlockEntity");
                return;
            }

            CompoundTag empty = serverData("SeparatorDataProvider", helper.getLevel(), abs, sep);
            helper.assertTrue(empty != null, "no usable SeparatorDataProvider");
            helper.assertTrue(empty.getIntOr("stored", -1) == 0,
                "a machine with no power must report none, got " + empty.getIntOr("stored", -1));

            try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                    net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                sep.battery().insert(1_000, tx);
                tx.commit();
            }

            CompoundTag charged = serverData("SeparatorDataProvider", helper.getLevel(), abs, sep);
            helper.assertTrue(charged != null, "no usable SeparatorDataProvider");
            helper.assertTrue(charged.getIntOr("stored", -1) == 1_000,
                "Jade must follow the buffer it is reporting on: put 1000 FE in and it says "
                    + charged.getIntOr("stored", -1) + ". A power readout that does not move is worse "
                    + "than none, because a player trusts it to explain a stall");
            helper.assertTrue(charged.getIntOr("capacity", 0) >= charged.getIntOr("stored", 0),
                "capacity " + charged.getIntOr("capacity", 0) + " is below stored "
                    + charged.getIntOr("stored", 0) + ", so the bar would render over-full");
            helper.succeed();
        });

        // THE MACHINES THE SWEEP ABOVE CANNOT REACH, which is most of them.
        //
        // `every_powered_machine_reports_real_numbers_to_jade` derives its list from powered multiblock
        // cores, so it covers exactly three providers - Separator, Trommel, Pulverizer - and those three
        // are the only ones in compat/jade at 100%. Five more IServerDataProvider classes exist and none
        // of them was ever run: the Scrap Bin, the Workbench, the generators, the Compost Heap and the
        // Tree Nursery. A derived list is the right shape and it still only derives the set somebody
        // thought of, which is the same failure the Pulverizer's missing providers were.
        //
        // These are the blocks with the LEAST to fall back on. A Separator at least has a power bar to
        // look at; a Scrap Bin's contents and a Workbench's racked tools exist nowhere else in the UI at
        // all, so a provider that writes nothing is a block that cannot be read.
        RCGameTests.test("every_server_data_provider_has_a_subject_and_writes", 80, helper -> {
            helper.assertTrue(jadePresent(),
                "Jade is not on the GameTest classpath, so this test would pass without checking "
                    + "anything");

            record Subject(String provider, Block block, boolean formed, String why) { }
            List<Subject> subjects = List.of(
                new Subject("GeneratorDataProvider", RCBlocks.BURNER_GENERATOR.get(), false,
                    "burn time remaining, which nothing else reports"),
                new Subject("GeneratorDataProvider", RCBlocks.SEQUENCER.get(), false,
                    "the Sequencer is the provider's third branch and shares none of the other two"),
                new Subject("ScrapBinDataProvider", RCBlocks.SCRAP_BIN.get(), false,
                    "a bound bin's contents and fill level are on no other surface"),
                // NOT the Workbench - see the loop below. An empty bench correctly reports nothing,
                // so it needs a tool racked first and cannot be driven by a bare setBlock.

                new Subject("GeneratorDataProvider", RCBlocks.SOLAR_PANEL.get(), false,
                    "a solar panel has no screen, so its charge is Jade or nothing"),
                new Subject("CompostHeapDataProvider", RCBlocks.COMPOST_HEAP.get(), true,
                    "layer count is the whole state of a compost heap"),
                new Subject("TreeNurseryDataProvider", RCBlocks.TREE_NURSERY.get(), true,
                    "water and fertiliser levels, which its screen shows only while open"),
                new Subject("HaulerDepotDataProvider", RCBlocks.HAULER_DEPOT.get(), false,
                    "whether the Hauler is docked or out, and its charge either way, which nothing "
                        + "but the screen shows and the screen shows only while open"));

            List<String> gaps = new ArrayList<>();
            for (Subject subject : subjects) {
                BlockPos probe = new BlockPos(1, 1, 1);
                BlockState state = subject.block().defaultBlockState();
                if (subject.formed()) {
                    // FORMED IS A BLOCKSTATE, NOT A STRUCTURE - see MultiblockCoreBlock. Both of these
                    // providers bail on an unformed core, deliberately, so without this they would be
                    // "covered" by a test that only ever exercised the early return.
                    state = state.setValue(MultiblockCoreBlock.FORMED, true);
                }
                helper.setBlock(probe, state);
                BlockPos abs = helper.absolutePos(probe);
                BlockEntity be = helper.getLevel().getBlockEntity(abs);
                if (be == null) {
                    gaps.add(subject.provider() + ": " + idOf(subject.block()) + " has no BlockEntity");
                    helper.setBlock(probe, net.minecraft.world.level.block.Blocks.AIR);
                    continue;
                }

                // BIND THE BIN BEFORE READING IT. Its subject line says "a bound bin's contents", and
                // an unbound bin has boundMaterial null - so the branch that names the material, which
                // exists precisely because the blockstate cannot express a modded binding, was the one
                // thing about this provider not being exercised.
                if (be instanceof com.flatts.recompile.content.block.entity.ScrapBinBlockEntity bin) {
                    bin.deposit(new net.minecraft.world.item.ItemStack(RCItems.SCRAP_METAL.get(), 8));
                }

                CompoundTag data = serverData(subject.provider(), helper.getLevel(), abs, be);
                if (data == null) {
                    gaps.add(subject.provider() + " is missing or does not implement the interface");
                } else if (data.isEmpty()) {
                    gaps.add(subject.provider() + " wrote nothing, so " + idOf(subject.block())
                        + " cannot explain itself - " + subject.why());
                } else if (be instanceof com.flatts.recompile.content.block.entity.ScrapBinBlockEntity
                        && !data.contains("material")) {
                    gaps.add("a bound Scrap Bin reported no material, so a bin holding a modded item "
                        + "is unreadable - the blockstate cannot name it and this is the only thing "
                        + "that can");
                }
                helper.setBlock(probe, net.minecraft.world.level.block.Blocks.AIR);
            }

            // THE WORKBENCH, SEPARATELY, BECAUSE AN EMPTY ONE IS SUPPOSED TO SAY NOTHING.
            //
            // WorkbenchDataProvider writes a durability pair per racked tool and skips a slot that is
            // empty or holds something undamageable - so a bare bench reports an empty tag, correctly.
            // The first version of this test swept it with the others and failed, which was the test
            // being wrong rather than the provider: what it had actually proved was that the empty
            // path returns nothing, which is the one behaviour not worth asserting. Racking a knife
            // exercises the branch that has something to say.
            BlockPos benchPos = new BlockPos(3, 1, 1);
            helper.setBlock(benchPos, RCBlocks.RECOMPILE_WORKBENCH.get());
            BlockPos benchAbs = helper.absolutePos(benchPos);
            if (helper.getLevel().getBlockEntity(benchAbs)
                    instanceof com.flatts.recompile.content.block.entity
                        .RecompileWorkbenchBlockEntity bench) {
                CompoundTag bare = serverData("WorkbenchDataProvider", helper.getLevel(), benchAbs,
                    bench);
                // TWO FAILURES, TWO MESSAGES. Folding these together made a missing provider class
                // report as "an empty Workbench reported null", which sends the reader looking for an
                // over-reporting provider when the class is simply gone. The loop above already
                // separates them.
                helper.assertTrue(bare != null,
                    "WorkbenchDataProvider is missing or does not implement IServerDataProvider");
                helper.assertTrue(bare.isEmpty(),
                    "an empty Workbench reported " + bare + ". Nothing is racked, so there is nothing "
                        + "to say, and inventing a number here would draw a durability bar for a tool "
                        + "that is not there");

                var player = helper.makeMockServerPlayerInLevel();
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                bench.rackTool(helper.getLevel(), player,
                    new net.minecraft.world.item.ItemStack(RCItems.SCRAP_KNIFE.get()));
                CompoundTag racked = serverData("WorkbenchDataProvider", helper.getLevel(), benchAbs,
                    bench);
                helper.assertTrue(racked != null && racked.getIntOr("knife_max", 0) > 0,
                    "a Workbench with a Scrap Knife racked reported " + racked + ", so the one surface "
                        + "that shows which tools are on the bench shows nothing. Which tools are "
                        + "racked decides which teardowns will run");
                helper.assertTrue(racked.getIntOr("knife_rem", -1) >= 0
                        && racked.getIntOr("knife_rem", -1) <= racked.getIntOr("knife_max", 0),
                    "the knife's remaining durability (" + racked.getIntOr("knife_rem", -1)
                        + ") is outside 0.." + racked.getIntOr("knife_max", 0)
                        + ", so the bar it draws is meaningless");
            } else {
                gaps.add("the Workbench has no BlockEntity to report from");
            }
            helper.setBlock(benchPos, net.minecraft.world.level.block.Blocks.AIR);

            // THE resolve() FALLBACK, WHICH IS THE HALF THE SWEEP CANNOT REACH.
            //
            // CompostHeapDataProvider and TreeNurseryDataProvider both carry a resolve() that walks
            // from a hovered DUMMY cell back to the core via MultiblockDummyBlock.findCore, so that
            // hovering any face of an assembled machine reads the machine rather than only the one
            // core cell. The sweep above hands the proxy the core's own BlockEntity, so resolve()
            // short-circuits on its first instanceof and that walk never runs - it sat at zero while
            // the provider read as covered. Pointing the proxy at a cell with no BlockEntity is what
            // makes it take the branch.
            BlockPos corePos = new BlockPos(1, 1, 3);
            BlockState coreState = RCBlocks.COMPOST_HEAP.get().defaultBlockState()
                .setValue(MultiblockCoreBlock.FORMED, true);
            helper.setBlock(corePos, coreState);
            // THE CELL COMES OFF THE BLUEPRINT, not off a guess. findCore only accepts a position the
            // core's own blueprint claims, so a cage placed at an arbitrary offset is not a dummy
            // cell as far as it is concerned - which is what the first version of this did, and it
            // failed for that reason rather than for the one it reported.
            MultiblockCoreBlock core = (MultiblockCoreBlock) RCBlocks.COMPOST_HEAP.get();
            BlockPos coreAbs = helper.absolutePos(corePos);
            BlockPos cellAbs = core.blueprint().cells().get(0)
                .at(coreAbs, core.rotationFor(coreState));
            CompoundTag viaDummy = serverData("CompostHeapDataProvider", helper.getLevel(),
                cellAbs, null);
            helper.assertTrue(viaDummy != null,
                "CompostHeapDataProvider is missing or does not implement IServerDataProvider");
            helper.assertTrue(!viaDummy.isEmpty(),
                "hovering a Compost Cage reported nothing. resolve() is supposed to walk from a dummy "
                    + "cell back to the core so any face of the heap reads the heap - if that walk is "
                    + "broken, five of the machine's six faces go blank and the core still works, "
                    + "which is exactly the shape that ships unnoticed");
            helper.setBlock(corePos, net.minecraft.world.level.block.Blocks.AIR);

            // AND EVERY PROVIDER THAT EXISTS IS IN THAT LIST, derived rather than trusted.
            //
            // The list above is hand-written, and the failure this whole test was added for is that
            // the OTHER sweep's derived list only derived the set somebody had thought of. A
            // hand-written one is worse at exactly the same thing, so it does not get to be the last
            // word: this walks the compiled compat.jade package and fails on any IServerDataProvider
            // with no subject. Add a tenth provider and the build tells you, rather than the class
            // sitting at zero percent with everything green.
            List<String> unclaimed = new ArrayList<>();
            for (String provider : serverDataProviders()) {
                boolean claimed = subjects.stream().anyMatch(sub -> sub.provider().equals(provider))
                    || COVERED_ELSEWHERE.contains(provider);
                if (!claimed) {
                    unclaimed.add(provider);
                }
            }
            helper.assertTrue(!serverDataProviders().isEmpty(),
                "no IServerDataProvider classes were discovered at all, so this check is vacuous - "
                    + "the package scan is broken, not the providers");
            // AND THE OTHER DIRECTION, which matters more. The check above catches a registered
            // provider nobody drives; this catches a provider we drive that is no longer REGISTERED -
            // a class that still works when a test calls it by name and never runs in the game,
            // because nothing wires it to a block. Unregistering ScrapBinDataProvider passed
            // everything until this existed.
            List<String> unwired = new ArrayList<>();
            for (Subject subject : subjects) {
                if (!serverDataProviders().contains(subject.provider())
                        && !unwired.contains(subject.provider())) {
                    unwired.add(subject.provider());
                }
            }
            helper.assertTrue(unwired.isEmpty(),
                unwired + " are driven by this test but registered against no block, so they run here "
                    + "and never in the game. RecompileJadePlugin.register is what wires them");

            helper.assertTrue(unclaimed.isEmpty(),
                unclaimed + " implement IServerDataProvider and no test drives them. Either add a "
                    + "Subject row or, if another test already covers it, name it in COVERED_ELSEWHERE "
                    + "with the reason");

            helper.assertTrue(gaps.isEmpty(),
                "Jade is the only feedback these blocks have, and these are silent: " + gaps);
            helper.succeed();
        });
    }

    /**
     * Providers driven by {@code every_powered_machine_reports_real_numbers_to_jade} instead.
     *
     * <p>Named rather than skipped, so the sweep below stays a complete account of what is covered.
     */
    private static final List<String> COVERED_ELSEWHERE = List.of(
        // Driven by every_powered_machine_reports_real_numbers_to_jade, which derives its subjects
        // from powered multiblock cores.
        "SeparatorDataProvider", "TrommelDataProvider", "PulverizerDataProvider",
        // Driven by the Workbench block further down this same test rather than by the subject loop:
        // an empty bench correctly writes nothing, so it needs a tool racked first and cannot be
        // driven by a bare setBlock.
        "WorkbenchDataProvider");

    /**
     * Every {@code IServerDataProvider} the mod actually registers, asked of the plugin itself.
     *
     * <p><b>Not a package scan, which was tried first and does not work here.</b> Listing the compiled
     * {@code compat/jade} directory needs the classloader to hand back a {@code file:} URL, and
     * moddev's does not - the sweep found nothing and only the "this cannot be vacuous" guard caught
     * it. This is better anyway: registration is what decides whether a provider ever runs, so a class
     * that exists and is never registered is exactly as dead as one that does not exist, and only this
     * can see that.
     *
     * <p>The registration object is a {@link Proxy} that records what it is handed. It is the same
     * trick as {@code serverData}'s accessor, for the same reason - the plugin asks it for one method
     * and Jade's own implementation would drag in the rest of the mod's client half.
     */
    private static List<String> serverDataProviders() {
        List<String> found = new ArrayList<>();
        try {
            Class<?> registrationType = Class.forName("snownee.jade.api.IWailaCommonRegistration");
            Class<?> providerType = Class.forName("snownee.jade.api.IServerDataProvider");
            Object recorder = Proxy.newProxyInstance(
                JadeDataTests.class.getClassLoader(), new Class<?>[]{registrationType},
                (InvocationHandler) (proxy, method, args) -> {
                    if ("registerBlockDataProvider".equals(method.getName()) && args != null
                            && args.length > 0 && providerType.isInstance(args[0])) {
                        String simple = args[0].getClass().getSimpleName();
                        if (!found.contains(simple)) {
                            found.add(simple);
                        }
                    }
                    return defaultFor(method.getReturnType());
                });
            Class<?> plugin = Class.forName(
                "com.flatts.recompile.compat.jade.RecompileJadePlugin");
            plugin.getMethod("register", registrationType)
                .invoke(plugin.getDeclaredConstructor().newInstance(), recorder);
        } catch (ReflectiveOperationException absent) {
            return found;
        }
        return found;
    }

    // ---------------------------------------------------------------- plumbing

    /**
     * Whether Jade is on the classpath.
     *
     * <p><b>Absent is a FAILURE, not a quiet pass</b>, and that is a deliberate reversal of how this
     * started. Skipping silently means that if the Jade coordinate is ever bumped or broken, or the mod
     * stops loading in the GameTest JVM, this file's coverage returns to zero while the suite stays
     * green - which is precisely the "a covered line is not a tested one" failure the whole coverage
     * pass was written against. The dev and CI runs always have Jade: {@code runtimeOnly} in
     * build.gradle, and {@link MachineParityTests} already depends on it being there.
     *
     * <p>Reflection is still how Jade is reached, for a different reason: a GameTest class is registered
     * unconditionally at mod init, so importing {@code snownee.jade.api.BlockAccessor} here would throw
     * {@code NoClassDefFoundError} on a Jade-less install and take the mod down with it. Class-loading
     * safety and test strictness are separate concerns and this file wants both.
     */
    private static boolean jadePresent() {
        try {
            Class.forName(ACCESSOR);
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }

    /**
     * Run one {@code IServerDataProvider} and hand back what it wrote, or null if there is no such
     * provider.
     *
     * <p>The accessor is a {@link Proxy}, not Jade's own builder, because these providers ask it for
     * exactly three things - the BlockEntity, the Level and the position - and a proxy answering those
     * needs no Jade internals and cannot break when Jade's impl package moves.
     */
    private static CompoundTag serverData(String provider, Level level, BlockPos pos, BlockEntity be) {
        try {
            Class<?> accessorType = Class.forName(ACCESSOR);
            Object accessor = Proxy.newProxyInstance(
                JadeDataTests.class.getClassLoader(), new Class<?>[]{accessorType},
                (InvocationHandler) (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockEntity" -> be;
                    case "getLevel" -> level;
                    case "getPosition" -> pos;
                    // Everything else a provider might reach for: answer harmlessly rather than throw,
                    // so a provider that grows a new call fails on its own assertion instead of here.
                    default -> defaultFor(method.getReturnType());
                });

            Class<?> type = Class.forName(JADE_PKG + provider);
            Object instance = type.getEnumConstants() != null && type.getEnumConstants().length > 0
                ? type.getEnumConstants()[0]
                : type.getDeclaredConstructor().newInstance();

            Method append = type.getMethod("appendServerData", CompoundTag.class, accessorType);
            CompoundTag data = new CompoundTag();
            try {
                append.invoke(instance, data, accessor);
            } catch (java.lang.reflect.InvocationTargetException thrown) {
                // NOT swallowed as "absent". InvocationTargetException is a ReflectiveOperationException,
                // so catching that alone reported a provider which THREW - an NPE on a null battery, an
                // accessor call the proxy answers with null - as "has no usable XDataProvider", pointing
                // the reader at a missing class and discarding the stack trace that says what actually
                // broke. A crash is the most interesting thing this test can find; it must surface.
                throw new IllegalStateException(
                    provider + ".appendServerData threw " + thrown.getCause(), thrown.getCause());
            }
            return data;
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            // Genuinely not there: no such provider class, or it does not implement the interface.
            return null;
        } catch (ReflectiveOperationException broken) {
            // Present but unusable - a non-public constructor, say. Also worth surfacing rather than
            // being filed under "missing".
            throw new IllegalStateException("cannot invoke " + provider, broken);
        }
    }

    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }

    /** Every multiblock core that accepts power - the same definition MachineParityTests uses. */
    private static List<Block> poweredMachines(GameTestHelper helper) {
        List<Block> machines = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock)) {
                continue;
            }
            BlockPos probe = new BlockPos(1, 1, 1);
            helper.setBlock(probe, block);
            boolean powered = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                helper.absolutePos(probe), null) != null;
            helper.setBlock(probe, net.minecraft.world.level.block.Blocks.AIR);
            if (powered) {
                machines.add(block);
            }
        }
        return machines;
    }

    private static String idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    /** {@code burner_generator} to {@code BurnerGenerator}, to find its provider by name. */
    private static String camel(String path) {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("_")) {
            if (!part.isEmpty()) {
                out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return out.toString();
    }
}
