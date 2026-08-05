package com.flatts.recompile.registry;

import com.flatts.recompile.Recompile;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Leachate: the liquid that drains out of a dump (issue #156, design I-8).
 *
 * <p>Rain falls through refuse, picks up everything soluble on the way down, and comes out the bottom
 * as this. It is the single reason engineered landfills have liners at all, so a dump world without it
 * is a dump world with the consequences edited out.
 *
 * <p><b>It must never irrigate farmland, and the mechanism is not the tag you would guess.</b>
 * {@code FarmlandBlock.isNearWater} (renamed from {@code FarmBlock} in 26.1) reaches
 * {@code FluidType#canHydrate} through {@code BlockState.canBeHydrated} -> {@code FluidState.canHydrate},
 * with a second independent path through {@code FarmlandWaterManager.hasBlockWaterTicket}. The
 * {@code #minecraft:water} fluid tag is not consulted. {@code canHydrate} defaults to {@code false},
 * so this file gets it right by saying nothing - which is exactly why the omission is spelled out
 * below rather than left as a blank line somebody later "completes".
 *
 * <p>That matters because {@code RCEncroachment}'s one blockstate rule is <i>wet farmland holds, dry
 * farmland is taken</i>. A fluid that hydrates would be permanent, free encroachment immunity for
 * every plot within four blocks of a pond, and encroachment defence is meant to be built and
 * maintained. It also keeps the P1.10 water economy honest: the Rain Collector's tank rejects
 * anything that is not water, so leachate is never a shortcut to clean water either.
 */
public final class RCFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Recompile.MOD_ID);

    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, Recompile.MOD_ID);

    /**
     * The fluid type carries the physical behaviour. Deliberate choices, all of them departures from
     * water:
     *
     * <ul>
     *   <li><b>No {@code canHydrate(true)}.</b> See the class note - this is the whole no-irrigation
     *       decision, and it is one word away from being undone.
     *   <li><b>Denser and far more viscous than water</b> (1400 vs 1000, 6000 vs 1000). Leachate is
     *       thick with dissolved solids; mechanically this makes wading through it slow, which is the
     *       only thing the player will actually feel.
     *   <li><b>Not {@code isWaterLike}.</b> That flag opts a fluid into water's special cases across
     *       vanilla, and the point of leachate is that it is not water.
     * </ul>
     */
    public static final Supplier<FluidType> LEACHATE_TYPE = FLUID_TYPES.register("leachate",
        () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid.recompile.leachate")
            .density(1400)
            .viscosity(6000)
            .temperature(300)
            .canSwim(true)
            .canDrown(true)
            .canPushEntity(true)
            .canExtinguish(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    /**
     * Source and flowing halves share one Properties. The spread numbers are tuned so a broken pond
     * edge weeps rather than floods: {@code levelDecreasePerBlock} 2 halves water's reach, and the
     * slower tick rate reads as something thick moving.
     *
     * <p>The block and bucket are passed as suppliers, which is what lets {@link RCBlocks} and
     * {@link RCItems} refer back to the fluid without a circular class-init.
     */
    public static final Supplier<BaseFlowingFluid.Source> LEACHATE = FLUIDS.register("leachate",
        () -> new BaseFlowingFluid.Source(properties()));

    public static final Supplier<BaseFlowingFluid.Flowing> LEACHATE_FLOWING =
        FLUIDS.register("flowing_leachate", () -> new BaseFlowingFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(LEACHATE_TYPE, LEACHATE, LEACHATE_FLOWING)
            .block(RCBlocks.LEACHATE)
            .bucket(RCItems.LEACHATE_BUCKET)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .tickRate(15)
            .explosionResistance(100.0F);
    }

    private RCFluids() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
