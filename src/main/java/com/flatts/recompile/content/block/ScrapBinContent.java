package com.flatts.recompile.content.block;

import com.flatts.recompile.Recompile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

/**
 * What a Scrap Bin is bound to, as a blockstate value - the finite, known-material half of the
 * design (acceptance is the open {@code #recompile:binnable} tag; this enum is the colored set).
 *
 * <p>The color lives here and is applied at render by the block color handler keyed on this state -
 * the {@code tintindex} mechanism vanilla grass and water use, not a BlockEntityRenderer. Colors are
 * material-matched, so a bin is the color of what is inside. {@link #EMPTY} and {@link #GENERIC}
 * return white (no tint), so the neutral base texture shows through - the empty bin and the
 * binnable-but-uncolored modded-scrap fallback both look neutral, named only by Jade.
 */
public enum ScrapBinContent implements StringRepresentable {

    EMPTY("empty", 0xFFFFFF),
    SCRAP_METAL("scrap_metal", 0x8A8D93),
    PLASTIC_SCRAP("plastic_scrap", 0xB0685F),
    GLASS_SHARDS("glass_shards", 0x7FA898),
    ORGANIC_MUCK("organic_muck", 0x5B5A2E),
    FIBER_SCRAP("fiber_scrap", 0xB8A57E),
    E_SCRAP("e_scrap", 0x3E7A4E),
    JUNK("junk", 0x7A6E5C),
    // The demolition yard's shards, one content each. They are tinted to read apart at 16px by
    // design (see texgen.toml), so collapsing them to a single stone look would throw away a
    // distinction the art deliberately makes - granite bins look like granite. Colours are the
    // lead stop of each shard's own procedural palette, so bin and item agree.
    STONE_SHARD("stone_shard", 0x707070),
    GRANITE_SHARD("granite_shard", 0x895B58),
    DIORITE_SHARD("diorite_shard", 0xC2C1C1),
    ANDESITE_SHARD("andesite_shard", 0x605E5D),
    DEEPSLATE_SHARD("deepslate_shard", 0x343543),
    TUFF_SHARD("tuff_shard", 0x5C5D4B),
    CALCITE_SHARD("calcite_shard", 0xD1CAC1),
    GENERIC("generic", 0xFFFFFF);

    private final String name;
    private final int color;

    ScrapBinContent(String name, int color) {
        this.name = name;
        this.color = color;
    }

    /** The material's signature tint (0xRRGGBB), applied at tintindex 0 by the block color handler. */
    public int color() {
        return color;
    }

    /**
     * The state for a bound material. Our own vocabulary maps to its colored value by registry path
     * (the enum name is the item id); anything else binnable maps to {@link #GENERIC}, which is held
     * and named but uncolored.
     */
    public static ScrapBinContent forItem(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (Recompile.MOD_ID.equals(id.getNamespace())) {
            for (ScrapBinContent content : values()) {
                if (content != EMPTY && content != GENERIC && content.name.equals(id.getPath())) {
                    return content;
                }
            }
        }
        return GENERIC;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
