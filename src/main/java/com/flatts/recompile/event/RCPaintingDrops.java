package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps a recovered painting recognisable after it is broken (#99).
 *
 * <p><b>The problem.</b> {@code Painting.dropItem} spawns a bare {@code Items.PAINTING} and throws the
 * variant away, so a player who breaks their Mona Lisa gets a blank canvas and re-hanging it rolls
 * whatever the {@code placeable} tag offers. Three of the four acceptance criteria hold for free -
 * vanilla's {@code HangingEntityItem} already names the work in its tooltip and places the right image
 * from the component - and this is the fourth: that all of it survives a break.
 *
 * <p><b>Why an event pair and not an override.</b> {@code dropItem} is not overridable anywhere useful,
 * NeoForge does not patch it (its only {@code HangingEntity} patch is support-box logic), and this mod
 * has no mixins. What every destruction path does share is that an {@link ItemEntity} appears: punched,
 * shot, blown up, or the wall behind it removed. Catching the item covers all of them at once, where an
 * attack hook would only cover the first.
 *
 * <p><b>Why it has to remember rather than just look around.</b> The obvious version searches for the
 * Painting beside the dropped item, and it does not work: {@code BlockAttachedEntity.hurtServer} calls
 * {@code kill(level)} <em>before</em> {@code dropItem}, so by the time the item exists the painting has
 * already left the level and no entity search can find it. Written that way first, and the round-trip
 * test caught it. So the variant is recorded on the way out and claimed on the way in.
 *
 * <p>The record is <b>transient by design</b>: an in-memory map, never serialized, holding an entry for
 * the moment between the two events. That keeps the mod's no-saved-state grain, the same reason
 * encroachment has no memory and the scrap network has no core.
 *
 * <p>This is the same problem {@code CLAUDE.md} records for the Rain Collector, state lost because
 * breaking destroys the thing holding it, with an entity in place of a BlockEntity.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCPaintingDrops {

    /**
     * Variant of the last recovered painting removed at each position, awaiting its dropped item.
     *
     * <p>Stamped with the game time it was recorded, because an entry that outlives its tick is a bug
     * waiting to mislabel an unrelated painting item. Server-thread only: both events fire there, so no
     * synchronisation is needed and adding some would imply otherwise.
     */
    private static final Map<BlockPos, Pending> PENDING = new HashMap<>();

    private record Pending(Holder<PaintingVariant> variant, long gameTime) { }

    private RCPaintingDrops() {
    }

    @SubscribeEvent
    public static void onPaintingRemoved(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Painting painting)) {
            return;
        }
        Holder<PaintingVariant> variant = painting.getVariant();
        // Only ours. A vanilla painting keeps vanilla behaviour, so a world that never sees this
        // feature is unaffected by it.
        boolean recovered = variant.unwrapKey()
            .map(key -> Recompile.MOD_ID.equals(key.identifier().getNamespace()))
            .orElse(false);
        if (recovered) {
            PENDING.put(painting.blockPosition(),
                new Pending(variant, event.getLevel().getGameTime()));
        }
    }

    @SubscribeEvent
    public static void onItemSpawned(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !(event.getEntity() instanceof ItemEntity dropped)) {
            return;
        }
        ItemStack stack = dropped.getItem();
        // Only a BARE painting item. One that already carries a variant came from a loot table or a
        // player's inventory and is nobody's business here.
        if (!stack.is(Items.PAINTING) || stack.has(DataComponents.PAINTING_VARIANT)) {
            return;
        }

        Pending pending = PENDING.remove(dropped.blockPosition());
        if (pending == null || pending.gameTime() != level.getGameTime()) {
            // No recovered painting died here this tick, so this item is unrelated: a player dropped it,
            // or a vanilla painting was broken. Leave it exactly as vanilla made it.
            return;
        }

        stack.set(DataComponents.PAINTING_VARIANT, pending.variant());
        // The tooltip comes from the variant, but the item's NAME does not - so it is restored too, or
        // the recovered item would read "Painting" while its own tooltip said Mona Lisa.
        pending.variant().value().title()
            .ifPresent(title -> stack.set(DataComponents.ITEM_NAME, title));
        dropped.setItem(stack);
    }

    /**
     * Drop anything left over from an earlier tick.
     *
     * <p>A painting removed without dropping an item - cleared by a command, or in a world with entity
     * drops off - would otherwise sit here forever. The map is tiny and short-lived, but "tiny and
     * short-lived" is how leaks start.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        PENDING.entrySet().removeIf(e -> e.getValue().gameTime() != now);
    }
}
