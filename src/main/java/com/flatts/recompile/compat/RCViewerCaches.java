package com.flatts.recompile.compat;

import com.flatts.recompile.Recompile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Drops viewer caches that depend on TAG membership when tags are rebound.
 *
 * <p><b>Why this exists.</b> {@code TeardownData} caches its parsed rows because {@code forInput} is
 * called from a Jade tooltip provider and so runs per frame. That cache was safe while the parse
 * depended only on {@code BuiltInRegistries.ITEM}, which is fixed for the JVM. #275 made a teardown
 * input able to be a TAG, and tag membership is rebound on every datapack load and every server tag
 * sync - so without this the first world joined wins for the whole session, and JEI shows one
 * server's cable set on another.
 *
 * <p>Registered on the game bus for BOTH sides: a dedicated server reloads datapacks too, and the
 * same cache backs {@code TeardownData.all()} there.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCViewerCaches {

    private RCViewerCaches() {
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        TeardownData.invalidate();
    }
}
