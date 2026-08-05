package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCTags;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Digging a sortable pile without its tool is refused and explained, rather than silently eating the
 * block (owner, 2026-08-05).
 *
 * <p><b>Why cancel rather than let it break and drop nothing.</b> {@code requiresCorrectToolForDrops}
 * suppresses the drop but not the mining, so on its own the wrong tool <em>destroys</em> the pile and
 * says nothing. That is the worst shape a rule can have here: a garbage mound breaks in about a second
 * to a bare hand, so a new player's first instinct - punch it, the universal Minecraft reflex - would
 * permanently delete two to three pulls of material and teach them only that garbage is worthless.
 * They are standing in a world made of it.
 *
 * <p>This is the same call {@link RCTorchFuel} already made for the Cutting Torch, in the same words:
 * silently eating the block is the worse failure, because a player who cannot see why it vanished has
 * no way to learn the rule. Consistency matters more than the small saving of leaving it uncancelled.
 *
 * <p><b>Scoped to {@link SortableBlock}</b>, and derived rather than listed, so a new pile is covered
 * the day it is registered. It is scoped that way because these are the blocks with a second verb: the
 * message can point at sorting, which is both the way out and the mechanic the mod most wants found.
 * Reinforced Concrete and Steel I-Beams are gated too and are deliberately not here - there is no
 * "sort it instead" to offer, and the Torch already speaks for itself.
 *
 * <p>Creative is exempt, which matters beyond convenience: {@code instabuild} skips the harvest check
 * entirely, so without this a creative player would be refused a break the game was going to allow.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCHarvestGate {

    private RCHarvestGate() {
    }

    /**
     * Tool tags to the word for the tool, most specific first.
     *
     * <p>Read off the tags rather than hardcoded per block, so moving a block between tags moves its
     * message with it - which has already happened once, when Mechanical Waste went from the shovel to
     * the pickaxe. The mod-specific tags come first because a block may sit in one of those and in a
     * vanilla one, and the bespoke tool is the more useful thing to name.
     */
    private static final List<ToolWord> TOOL_WORDS = List.of(
        new ToolWord(RCTags.MINEABLE_WITH_KNIFE, "knife"),
        new ToolWord(RCTags.MINEABLE_WITH_PRYBAR, "prybar"),
        new ToolWord(RCTags.MINEABLE_WITH_SLEDGEHAMMER, "sledgehammer"),
        new ToolWord(RCTags.MINEABLE_WITH_CUTTING_TORCH, "cutting_torch"),
        new ToolWord(BlockTags.MINEABLE_WITH_SHOVEL, "shovel"),
        new ToolWord(BlockTags.MINEABLE_WITH_PICKAXE, "pickaxe"),
        new ToolWord(BlockTags.MINEABLE_WITH_AXE, "axe"),
        new ToolWord(BlockTags.MINEABLE_WITH_HOE, "hoe"));

    private record ToolWord(TagKey<Block> tag, String word) {
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        Player player = event.getPlayer();
        if (player == null || !refusesDig(player, event.getState())) {
            return;
        }
        event.setCanceled(true);
        player.sendOverlayMessage(Component.translatable(
            "message.recompile.needs_dig_tool", Component.translatable(toolKey(event.getState()))));
    }

    /**
     * Whether this break is a sortable pile the player has no tool for. The static entry point the
     * GameTests drive, the same way {@link RCTorchFuel#cutCostsFuel} is.
     */
    public static boolean refusesDig(Player player, BlockState state) {
        return !player.getAbilities().instabuild
            && state.getBlock() instanceof SortableBlock
            && state.requiresCorrectToolForDrops()
            && !player.hasCorrectToolForDrops(state);
    }

    /** The lang key naming the tool this block wants, or a generic one if it is in no tool tag. */
    public static String toolKey(BlockState state) {
        for (ToolWord candidate : TOOL_WORDS) {
            if (state.is(candidate.tag())) {
                return "tool.recompile." + candidate.word();
            }
        }
        // No tool tag at all means the block is unharvestable by anything, which is a data bug rather
        // than a player one. Say something true instead of naming a tool that does not exist.
        return "tool.recompile.right_tool";
    }

    /** Every sortable block that is gated, for the tests and for anything that wants to sweep them. */
    public static List<Block> gatedSortables() {
        return BuiltInRegistries.BLOCK.stream()
            .filter(block -> block instanceof SortableBlock)
            .filter(block -> block.defaultBlockState().requiresCorrectToolForDrops())
            .toList();
    }
}
