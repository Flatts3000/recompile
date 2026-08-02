package com.flatts.recompile.content.item;

import com.flatts.recompile.registry.RCDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * An Idea Fragment: part of working out how something used to be made (#95, spec
 * {@code docs/blueprints_spec.md}).
 *
 * <p><b>It is an idea, not a piece of paper.</b> The first art derived it from the Blueprint so the two
 * would read as one document torn in half; the owner rejected that for a better reason than the picture
 * (2026-08-02): a fragment is an arbitrary idea, not an item. The blueprint is the finished artifact and
 * this is the half-formed thought before it, which is why it is deliberately colourless and
 * unremarkable. Next to the blue sheet it should read as <i>not yet a document</i> rather than as half
 * of one.
 *
 * <p>It carries the same {@link RCDataComponents#BLUEPRINT} component the Blueprint does, so a fragment
 * toward the Clean Mattress and a fragment toward something else are different items rather than one
 * generic scrap. That is what stops a player grinding one easy teardown to unlock everything.
 *
 * <p><b>Fragments stack</b>, unlike blueprints. Here a count is exactly the information you want - the
 * whole mechanic is watching a pile grow toward a threshold - which is the opposite of the blueprint,
 * where presence is the question and a count would be noise.
 */
public class IdeaFragmentItem extends Item {

    public IdeaFragmentItem(Properties properties) {
        super(properties);
    }

    /** Which blueprint this fragment leads to, or null if it names none. */
    public static @Nullable Identifier towards(ItemStack stack) {
        return stack.getItem() instanceof IdeaFragmentItem
            ? stack.get(RCDataComponents.BLUEPRINT.get())
            : null;
    }

    /** A fragment stack pointing at the given blueprint set. */
    public static ItemStack of(Item fragment, Identifier set, int count) {
        ItemStack stack = new ItemStack(fragment, count);
        stack.set(RCDataComponents.BLUEPRINT.get(), set);
        return stack;
    }

    /**
     * Say what it is an idea about.
     *
     * <p>Without this a stack of fragments is unreadable: they all render the same and the component is
     * invisible, so a player holding two different sets sees one pile. The recovered paintings had this
     * exact bug before they carried their titles.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        Identifier set = towards(stack);
        if (set == null) {
            tooltip.accept(Component.translatable("tooltip.recompile.fragment_blank")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        String key = "blueprint." + set.getNamespace() + "." + set.getPath();
        Component name = Component.translatable(key);
        tooltip.accept(Component.translatable("tooltip.recompile.fragment_towards",
                name.getString().equals(key) ? Component.literal(set.toString()) : name)
            .withStyle(ChatFormatting.DARK_AQUA));
    }
}
