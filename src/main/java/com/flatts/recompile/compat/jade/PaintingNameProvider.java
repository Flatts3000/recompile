package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: name the work on the wall (#99, acceptance criterion 2).
 *
 * <p>Without it every recovered painting hovers as "Painting", because that is the entity's name and
 * Jade has no reason to look further. The whole point of the feature is that the thing on your wall is
 * a specific artwork, and a player standing in front of six of them should be able to tell which is
 * which without counting pixels.
 *
 * <p>Only ours are named. A vanilla painting keeps vanilla behaviour, so a pack that re-adds the
 * default variants does not suddenly get half-labelled walls.
 *
 * <p>No server data provider is needed here, unlike the Scrap Bin and the generators: a painting's
 * variant is synced to the client as entity data already, because the client has to draw it.
 */
public enum PaintingNameProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "painting_name");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof Painting painting)) {
            return;
        }
        Holder<PaintingVariant> variant = painting.getVariant();
        boolean recovered = variant.unwrapKey()
            .map(key -> Recompile.MOD_ID.equals(key.identifier().getNamespace()))
            .orElse(false);
        if (!recovered) {
            return;
        }
        variant.value().title().ifPresent(tooltip::add);
        variant.value().author().ifPresent(author ->
            tooltip.add(Component.literal("").append(author)));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
