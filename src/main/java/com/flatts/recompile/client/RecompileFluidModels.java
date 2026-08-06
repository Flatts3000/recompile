package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCFluids;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/**
 * The client half of leachate (#156).
 *
 * <p><b>Fluid textures are a model in 26.1, not an override, and this is the part every tutorial gets
 * wrong.</b> {@code IClientFluidTypeExtensions} has four members here - two fog hooks and the
 * screen-overlay pair - and the {@code getStillTexture} / {@code getFlowingTexture} /
 * {@code getTintColor} methods that 1.20 and 1.21 guides are built around simply do not exist. A
 * fluid's sprites come from a {@link FluidModel} registered on {@link RegisterFluidModelsEvent}
 * instead.
 *
 * <p>It fails <i>soft</i>, which is the trap: a fluid with no registered model falls back to
 * {@code FluidStateModelSet}'s missing model, so forgetting this produces a pond of pink-and-black
 * rather than an error anybody would notice in a log.
 *
 * <p>The event fires on a worker thread during model loading, so this does no work beyond handing
 * over materials.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class RecompileFluidModels {

    private static final Identifier STILL = Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "block/leachate_still");
    private static final Identifier FLOW = Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "block/leachate_flow");

    private RecompileFluidModels() {
    }

    @SubscribeEvent
    static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        // No tint: the sprites are already the colour leachate is meant to be. Water needs a tint
        // because its texture is greyscale and biome-coloured; ours is not, so tinting it would only
        // be a second place for the colour to disagree with itself.
        FluidModel.Unbaked model = new FluidModel.Unbaked(
            material(STILL),
            material(FLOW),
            null,
            FluidTintSources.constant(-1));

        // Both halves share one model, so a flowing edge cannot drift from the pool it came out of.
        event.register(model, RCFluids.LEACHATE, RCFluids.LEACHATE_FLOWING);
    }

    /**
     * 26.1's {@code Material} takes the sprite id alone - the atlas argument every older snippet
     * passes is gone, and fluid sprites are stitched into the block atlas implicitly.
     */
    private static Material material(Identifier texture) {
        return new Material(texture);
    }
}
