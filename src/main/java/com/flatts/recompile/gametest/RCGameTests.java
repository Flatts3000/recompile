package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world GameTest registrar. Each test is a headless scenario run inside a real
 * server via {@code ./gradlew runGameTestServer} on the shared 5x5x5 plot
 * ({@code data/recompile/structure/empty_5x5x5.nbt}), asserting behaviour via
 * {@link GameTestHelper}.
 *
 * <p>26.1 registration: a test is a body ({@code Consumer<GameTestHelper>} in
 * {@link Registries#TEST_FUNCTION}) plus metadata (a {@link TestData} carried by a
 * {@link FunctionGameTestInstance} registered at {@link RegisterGameTestsEvent}).
 * {@link #test} hides that two-step so each domain class is just bodies plus one
 * registration line. Mirrors the productive-frogs suite shape.
 */
public final class RCGameTests {

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, Recompile.MOD_ID);

    /** The default plot most tests run on. */
    static final String DEFAULT_STRUCTURE = "empty_5x5x5";

    private record Spec(ResourceKey<Consumer<GameTestHelper>> fn, Identifier structure, Rotation rotation, int maxTicks) {}

    private static final List<Spec> SPECS = new ArrayList<>();

    private RCGameTests() {
    }

    /** Register one test on the default {@code empty_5x5x5} plot. {@code name} must be lower_snake_case. */
    static void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
        DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> holder =
            FUNCTIONS.register(name, () -> body);
        SPECS.add(new Spec(holder.getKey(),
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, DEFAULT_STRUCTURE), Rotation.NONE, maxTicks));
    }

    /** Wire up from the {@code Recompile} constructor. */
    public static void register(IEventBus modEventBus) {
        SortingTests.register();
        SortingTarpTests.register();
        ForageTests.register();
        CraftingTableTests.register();

        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RCGameTests::onRegisterGameTests);
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "default"));
        for (Spec spec : SPECS) {
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                env, spec.structure(), spec.maxTicks(),
                0,            // setupTicks
                true,         // required (run in CI)
                spec.rotation(),
                false,        // manualOnly (false -> auto CI batch)
                1,            // maxAttempts
                1,            // requiredSuccesses
                false,        // skyAccess
                1);           // padding between batched instances
            event.registerTest(spec.fn().identifier(), new FunctionGameTestInstance(spec.fn(), data));
        }
    }
}
