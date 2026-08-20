package com.flatts.recompile.gametest;

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
