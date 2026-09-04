package com.flatts.recompile.compat;

import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ItemLike;

/**
 * Every item that gets a JEI info panel, and the lang key that fills it.
 *
 * <p><b>This list used to live inside the JEI plugin, and that is why it kept going wrong.</b>
 * {@code RecompileJeiPlugin} only classloads when JEI is present, so nothing in the build could read
 * the list - and a {@code jei.recompile.info.*} string written into {@code en_us.json} without a
 * matching registration is a translation nothing can ever ask for. Silent: the key resolves fine, it
 * is simply never requested.
 *
 * <p>It happened three times before it was caught. {@code leachate_bucket}, then {@code motor} and
 * {@code bulb}, each found by a human reading a diff. When the guard was finally written it
 * immediately found two more that had been dead for months - {@code printer} and
 * {@code broken_hydroponics_bay}, both describing finds a player would actively look up.
 *
 * <p>The reverse defect is equally silent and equally possible: registering a key with no lang entry
 * renders the raw key to the player. {@code jei_info_panels_and_lang_keys_agree} asserts both
 * directions, which is only possible because the list lives out here where a test can see it.
 *
 * <p>Deliberately free of JEI types. This class must be loadable without JEI on the classpath, which
 * is the whole point - it names items and strings and nothing else.
 */
public final class JeiInfoPanels {

    /** One panel: the item it hangs off, and the suffix of its {@code jei.recompile.info.*} key. */
    public record Panel(ItemLike item, String key) {}

    private JeiInfoPanels() {
    }

    /**
     * The panels, in the order they are registered.
     *
     * <p>A panel earns its place when <b>no recipe expresses the mechanic</b> - where a find comes
     * from, that a blueprint is held rather than spent, that the Burn Barrel refuses most of what a
     * furnace accepts. If a recipe already says it, JEI already shows it and a panel would only
     * restate it.
     */
    public static List<Panel> all() {
        return List.of(
            // Sorting and salvage sources: items whose only origin is a block drop, which JEI cannot see.
            new Panel(RCItems.RAW_ROACH.get(), "raw_roach"),
            new Panel(RCItems.STEEL_OFFCUT.get(), "steel_offcut"),
            new Panel(RCItems.MOTOR.get(), "motor"),
            new Panel(RCItems.BULB.get(), "bulb"),
            new Panel(RCItems.DEPLETED_BATTERY.get(), "depleted_battery"),
            new Panel(RCItems.BATTERY.get(), "battery"),
            new Panel(RCItems.DRIED_BOUQUET.get(), "dried_bouquet"),
            new Panel(RCItems.PRISMARINE_GRIT.get(), "prismarine_grit"),
            new Panel(RCItems.RUBBER_SCRAP.get(), "rubber_scrap"),
            new Panel(RCItems.TIRE.get(), "tire"),
            new Panel(RCItems.LEACHATE_BUCKET.get(), "leachate_bucket"),

            // Machines whose restriction or behaviour no recipe states.
            new Panel(RCItems.BURN_BARREL.get(), "burn_barrel"),
            new Panel(RCItems.CUPOLA_FURNACE.get(), "cupola_furnace"),
            new Panel(RCItems.CUTTING_TORCH.get(), "cutting_torch"),
            new Panel(RCItems.GRASS_SPREADER.get(), "grass_spreader"),
            new Panel(RCItems.RAIN_COLLECTOR.get(), "rain_collector"),
            new Panel(RCItems.COMPOST_HEAP.get(), "compost_heap"),
            new Panel(RCItems.FERTILIZER.get(), "fertilizer"),
            new Panel(RCItems.SOLAR_PANEL.get(), "solar_panel"),
            new Panel(RCItems.BURNER_GENERATOR.get(), "burner_generator"),
            new Panel(RCItems.HYDROPONICS_BAY.get(), "hydroponics_bay"),
            new Panel(RCItems.UNKNOWN_SEEDLING.get(), "unknown_seedling"),

            // The blueprint mechanic: three items, and no recipe expresses any of it.
            new Panel(RCItems.BLUEPRINT.get(), "blueprint"),
            new Panel(RCItems.IDEA_FRAGMENT.get(), "idea_fragment"),
            new Panel(RCItems.FILING_CABINET.get(), "filing_cabinet"),
            new Panel(RCItems.cleanMattress(DyeColor.WHITE), "clean_mattress"),

            // Bulky Waste finds. Both of these had lang strings and no registration for months, which
            // is the defect this class exists to make impossible - a player looking up the one find
            // that gates the dye set, or the one that teaches the Hydroponics Bay, got nothing.
            new Panel(RCItems.PRINTER.get(), "printer"),
            new Panel(RCItems.BROKEN_HYDROPONICS_BAY.get(), "broken_hydroponics_bay"),

            // The market: a find, and two terminals whose whole mechanic - a balance that is not an
            // item, a tag that says what sells - no recipe can express.
            new Panel(RCItems.BROKEN_TERMINAL.get(), "broken_terminal"),
            new Panel(RCItems.SELL_TERMINAL.get(), "sell_terminal"),
            new Panel(RCItems.BUY_TERMINAL.get(), "buy_terminal"));
    }
}
