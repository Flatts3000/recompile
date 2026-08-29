package com.flatts.recompile.content.item;

import com.flatts.recompile.registry.RCDataComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Fossilised tree resin with a creature trapped in it (#294).
 *
 * <p><b>The species is stamped when the amber is found</b>, as {@link RCDataComponents#SPECIES}, so a
 * player can read a piece before spending machine time on it. Four fragments of the same species make
 * that species' spawn egg, so sorting a pile of amber by what is inside it IS the mechanic; an
 * unstamped piece would be a lottery ticket instead.
 *
 * <p><b>An unknown or absent species is shown, not hidden.</b> The id is a plain {@link Identifier}
 * so that a datapack may name a creature from a mod that is not installed without taking a loot table
 * down at parse - which means an id that resolves to nothing is an ordinary runtime state, not a bug.
 * It reads as "nothing recognisable" rather than rendering a raw id or, worse, silently looking like
 * empty amber.
 */
public class AmberItem extends Item {

    public AmberItem(Properties properties) {
        super(properties);
    }

    /** The creature in this piece, if it names one that exists in this game. */
    public static Optional<EntityType<?>> speciesOf(ItemStack stack) {
        Identifier id = stack.get(RCDataComponents.SPECIES.get());
        return id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
    }

    /** Whether this piece names a species at all, resolvable or not. */
    public static boolean isStamped(ItemStack stack) {
        return stack.get(RCDataComponents.SPECIES.get()) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        Identifier id = stack.get(RCDataComponents.SPECIES.get());
        if (id == null) {
            lines.accept(Component.translatable("tooltip.recompile.amber.empty")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty()) {
            // A species from a mod that is not installed. Say so plainly: the alternative is a
            // tooltip that prints a raw id at the player, which reads as a bug in the mod.
            lines.accept(Component.translatable("tooltip.recompile.amber.unknown")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.accept(Component.translatable("tooltip.recompile.amber.species",
            Component.translatable(type.get().getDescriptionId()).withStyle(ChatFormatting.AQUA))
            .withStyle(ChatFormatting.GRAY));
    }

    /** The tooltip lines this item can produce, so a test can assert every one is translated. */
    public static List<String> tooltipKeys() {
        return List.of("tooltip.recompile.amber.empty", "tooltip.recompile.amber.unknown",
            "tooltip.recompile.amber.species");
    }
}
