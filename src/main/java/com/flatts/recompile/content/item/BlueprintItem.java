package com.flatts.recompile.content.item;

import com.flatts.recompile.registry.RCDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;

/**
 * A Blueprint: a recovered instruction sheet for something this world has forgotten how to make
 * (#95, spec {@code docs/blueprints_spec.md}).
 *
 * <p><b>The item is the knowledge.</b> There is no learned-recipes list on the player and there is not
 * going to be one - encroachment has no memory, the scrap network has no core, and a formed multiblock
 * is a blockstate rather than a BlockEntity. Knowledge follows the same rule: it sits in a slot, it can
 * be lost, and it can be handed to someone else. That is Immersive Engineering's model and it is why it
 * was chosen over a per-player capability.
 *
 * <p><b>Which blueprint it is lives in a data component</b>, so one item covers every blueprint the mod
 * or a pack ever ships. {@link #blueprintOf(ItemStack)} is the only way to read it and returns null for
 * a stack that has none, because a blueprint with no component is a real thing a player can end up
 * holding: {@code /give} without arguments makes one, and so does a pack that removes a recipe out from
 * under an existing save. It must be inert, not a crash.
 *
 * <p><b>It does not stack.</b> A second copy of a thing you already know is worth nothing to you, so a
 * count would be noise; and "do I know this" wants to be answered by presence rather than by reading a
 * number. Vanilla's enchanted book, the closest thing it has to knowledge-as-an-item, is single-stack
 * for the same reason.
 */
public class BlueprintItem extends Item {

    public BlueprintItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Which blueprint this stack is, or null if it carries no component or is not a blueprint. */
    public static @Nullable Identifier blueprintOf(ItemStack stack) {
        return stack.getItem() instanceof BlueprintItem
            ? stack.get(RCDataComponents.BLUEPRINT.get())
            : null;
    }

    /** A blueprint stack for the given set. */
    public static ItemStack of(Item blueprint, Identifier set) {
        ItemStack stack = new ItemStack(blueprint);
        stack.set(RCDataComponents.BLUEPRINT.get(), set);
        return stack;
    }

    /**
     * Name the blueprint on the item itself.
     *
     * <p>Without this every blueprint in an inventory reads "Blueprint" and they are indistinguishable,
     * which is exactly the bug the recovered paintings had before they carried their titles. The key is
     * derived from the id so a pack that adds a blueprint gets a name by adding one lang entry, with the
     * raw id as the fallback rather than nothing at all.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        Identifier set = blueprintOf(stack);
        if (set == null) {
            tooltip.accept(Component.translatable("tooltip.recompile.blueprint_blank")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        String key = "blueprint." + set.getNamespace() + "." + set.getPath();
        Component name = Component.translatable(key);
        tooltip.accept((name.getString().equals(key) ? Component.literal(set.toString()) : name)
            .copy().withStyle(ChatFormatting.AQUA));
    }

    /** Blueprints in the creative tab, one per set the mod ships. */
    public static List<Identifier> shipped() {
        return List.of(CLEAN_MATTRESS, HYDROPONICS_BAY, PUMP, MOTOR, BULB);
    }

    /** The proof of concept: the sheet that turns a filthy mattress into one fit to sleep on. */
    public static final Identifier CLEAN_MATTRESS =
        Identifier.fromNamespaceAndPath("recompile", "clean_mattress");

    /**
     * The second blueprint, and the one that proves the mechanic was worth building.
     *
     * <p>A single gated object is a demo. The spec said as much: "the POC only earns its cost if the
     * bed is not the only one." This is a machine rather than a trinket, and it is learned from the
     * wreck of itself - tearing down a <b>Broken Hydroponics Bay</b>, found in Bulky Waste.
     *
     * <p><i>This javadoc used to say the bay was learned from a washing machine, "which is already
     * where the Pump comes from". That was never what shipped -</i> {@code broken_hydroponics_bay.json}
     * <i>is what carries the</i> {@code teaches} <i>entry, and the washing machine carried none at all
     * until the Pump gained one. Corrected 2026-08-08 (#160).</i>
     */
    public static final Identifier HYDROPONICS_BAY =
        Identifier.fromNamespaceAndPath("recompile", "hydroponics_bay");

    /**
     * The third blueprint, and the first for a <b>component</b> rather than a machine (#160).
     *
     * <p>Owner ruling 2026-08-08. This is a scoped reversal of <b>P2.4-R item 7</b>, which says the
     * Pump is "torn out of a Washing Machine found in Bulky Waste, <b>never crafted</b>" so that
     * reclamation rung 1 sits behind the teardown spine and a find. The literal "never crafted" no
     * longer holds; <b>the reason for it does</b>, and arguably harder than before. Fragments come only
     * from tearing down washing machines, so the blueprint is still behind Bulky Waste, a prybar and
     * the Workbench - and it now takes <i>four</i> of them rather than one lucky find.
     *
     * <p><b>The Pump is therefore not gated the way the other two are.</b> A Clean Mattress and a
     * Hydroponics Bay exist only at the blueprint bench; a Pump is <i>also</i> salvage, and that dual
     * route is the design rather than a leak in it. {@code a_blueprint_result_has_no_other_route}
     * sweeps crafting recipes and is blind to teardown by construction, so it would have passed here
     * without meaning anything - {@code a_pump_is_reachable_by_salvage_and_by_blueprint} pins both
     * halves on purpose.
     */
    public static final Identifier PUMP =
        Identifier.fromNamespaceAndPath("recompile", "pump");

    /**
     * The Motor (#170), and the Bulb (#171) below it - the other two components #160 wanted covered.
     *
     * <p><b>Neither could have the Pump's treatment, and that is why they arrived late.</b> The Pump
     * falls out of a Washing Machine teardown, so its lesson had a recipe to hang on. The Motor came
     * from sorting Mechanical Waste and the Bulb from household sorting, and {@code teaches} lives on
     * {@code recompile:teardown} only - there was nothing to attach. Owner ruling 2026-08-08: give each
     * a found object that tears down into it, which is the Broken Fan and the Light Fixture.
     *
     * <p>All three components are therefore <b>salvage first and blueprint second</b>, unlike the Clean
     * Mattress and the Hydroponics Bay which exist nowhere but the bench.
     */
    public static final Identifier MOTOR =
        Identifier.fromNamespaceAndPath("recompile", "motor");

    public static final Identifier BULB =
        Identifier.fromNamespaceAndPath("recompile", "bulb");
}
