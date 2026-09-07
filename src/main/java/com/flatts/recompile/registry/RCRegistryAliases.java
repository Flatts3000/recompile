package com.flatts.recompile.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Old registry ids kept alive so a rename does not eat what players already hold.
 *
 * <p><b>A registry id is save data.</b> An item in a chest, a hotbar, or a Filing Cabinet is stored by
 * its id, so renaming one turns every existing copy into the missing-registry prompt on next load and
 * then into nothing. v0.19.0 is on CurseForge, so worlds holding these items exist right now; the
 * rename in #390 would have deleted them silently on upgrade.
 *
 * <p><b>The mechanism is {@code addAlias}, NOT {@code MissingMappingsEvent}.</b> Every 1.20/1.21-era
 * guide reaches for that event and it does not exist in 26.1 - the whole class is gone, and a handler
 * written against it fails to compile with a bare {@code cannot find symbol} that names no
 * replacement. What 26.1 has instead is {@code IRegistryExtension.addAlias(from, to)} on the registry
 * itself, which rewrites the id at load time rather than reacting after the lookup already failed.
 *
 * <p>Called from the mod constructor, because an alias has to be in place before any world loads.
 *
 * <p><b>Entries here are permanent.</b> Removing one re-breaks every save that was upgraded through
 * it, so this list only ever grows.
 */
public final class RCRegistryAliases {

    private RCRegistryAliases() {
    }

    public static void register() {
        // #390, 2026-09-06. Idea Fragment -> Spawn Egg Fragment. Once teardown stopped teaching, amber
        // read by the Sequencer was the only source and a creature Blueprint the only destination, so
        // the general name described a mechanic that no longer existed.
        alias("idea_fragment", "spawn_egg_fragment");
    }

    private static void alias(String from, String to) {
        BuiltInRegistries.ITEM.addAlias(
            Identifier.fromNamespaceAndPath("recompile", from),
            Identifier.fromNamespaceAndPath("recompile", to));
    }
}
