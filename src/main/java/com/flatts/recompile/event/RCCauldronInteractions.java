package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * Rehydrating things in a water cauldron: a Dry Clay Body into clay (#115), and a Dried Bouquet into
 * one of the tall plants this world has no other source for (#331, #335).
 *
 * <p><b>Why a cauldron and not a machine.</b> Grog plus bentonite plus water is three inputs, and a
 * {@link CauldronInteraction} is one held item against a cauldron state. The dry blend collapses that to
 * one solid plus water, which a cauldron can express - so no new block, no GUI, nothing stored. The
 * cauldron does only what a cauldron does, which is supply water. That is the Sorting Tarp idiom, and
 * the reason this feature adds no machine at all.
 *
 * <p><b>The chemistry is why the blend has to exist first.</b> Grog is crushed fired ceramic and firing
 * is irreversible - above roughly 550-600 C kaolinite dehydroxylates and the bound hydroxyls leave the
 * lattice permanently, so there is nothing left to rehydrate. Grinding a sherd and adding water cannot
 * make clay, which was the original proposal and is the one link in the chain that could not be made
 * honest. The plasticity comes from the bentonite instead.
 *
 * <p><b>The bouquet is the same interaction pointed the other way.</b> The clay chain puts back what
 * firing drove off; a dried flower lost nothing but its water, so water is the whole of what goes back.
 * Which one it turns out to be is a loot table ({@link #BOUQUET_TABLE}), the same shape as the
 * Hydroponics Bay's seedling lottery - a find that resolves into one of several things when used, with
 * the odds in JSON where a pack can retune them.
 *
 * <p><b>Both cost a level of water</b>, the way every other cauldron recipe does. Water is the P1.10
 * economy's scarce input and a chain that drained nothing would be quietly free.
 */
public final class RCCauldronInteractions {

    /** What a Dried Bouquet rehydrates into; one draw per bouquet. */
    public static final ResourceKey<LootTable> BOUQUET_TABLE = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/dried_bouquet"));

    private RCCauldronInteractions() {
    }

    /**
     * Registered during {@code FMLCommonSetupEvent}, not from a data file and NOT from the mod
     * constructor - this resolves {@code RCItems.DRY_CLAY_BODY.get()}, and a DeferredItem is not
     * resolved while the constructor is still running, so calling it there throws.
     *
     * <p>{@code CauldronInteractions.WATER} is a plain map keyed by item, so this is a put rather than
     * anything reloadable - which also means a pack cannot add a hydration recipe without Java. That is
     * a real limit and an accepted one: the interaction is a behaviour, not a recipe, and the mod has
     * two of them. What a pack CAN retune is what the bouquet becomes, because that half is a table.
     */
    public static void register() {
        CauldronInteractions.WATER.put(RCItems.DRY_CLAY_BODY.get(),
            (state, level, pos, player, hand, held) ->
                hydrate(state, level, pos, player, held, new ItemStack(Items.CLAY_BALL)));
        CauldronInteractions.WATER.put(RCItems.DRIED_BOUQUET.get(),
            (state, level, pos, player, hand, held) ->
                hydrate(state, level, pos, player, held,
                    level instanceof ServerLevel server ? rollBouquet(server) : ItemStack.EMPTY));
    }

    /**
     * One plant from the bouquet table, or empty if the table is missing or rolled nothing.
     *
     * <p>Deliberately not a pull stream and not recorded by {@code RCAnalytics}: it is what a find
     * turns out to contain rather than a conversion of the player's time into materials, which is the
     * same reason the bay's seedling roll is not recorded either.
     */
    private static ItemStack rollBouquet(ServerLevel server) {
        LootTable table = server.getServer().reloadableRegistries().getLootTable(BOUQUET_TABLE);
        List<ItemStack> rolled = table.getRandomItems(
            new LootParams.Builder(server).create(LootContextParamSets.EMPTY));
        return rolled.isEmpty() ? ItemStack.EMPTY : rolled.get(0);
    }

    /**
     * Swap the held item for {@code product}, at the cost of one level of water.
     *
     * <p>An EMPTY product refuses rather than consuming - the only way to get one is a bouquet table
     * that a pack has emptied, and a cauldron that ate the item and the water for nothing would read
     * as a bug with no message. PASS lets the click fall through to whatever else wants it.
     */
    private static InteractionResult hydrate(BlockState state, Level level, BlockPos pos, Player player,
                                             ItemStack held, ItemStack product) {
        if (level.isClientSide()) {
            // SUCCESS on the client so the arm swings; the server does the work. Returning the server's
            // answer here would swing on a failure too.
            return InteractionResult.SUCCESS;
        }
        if (product.isEmpty()) {
            return InteractionResult.PASS;
        }
        // Give the product BEFORE lowering the level, so a full inventory cannot lose the water as well
        // as the input - addItem returning false is why this is not fire-and-forget.
        if (!player.getInventory().add(product)) {
            player.drop(product, false);
        }
        held.consume(1, player);
        player.awardStat(Stats.USE_CAULDRON);
        LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4F, 1.4F);
        return InteractionResult.SUCCESS;
    }
}
