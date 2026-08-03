package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

/**
 * Jade (client): render the Separator's queue as item sprites with counts.
 *
 * <p>The plain mapping - no {@code amountText} override - so the count sits on the sprite exactly
 * where it does in an inventory slot. See {@link SeparatorStorageProvider} for why this is a view and
 * not an inventory.
 */
public enum SeparatorStorageClientProvider
        implements IClientExtensionProvider<ItemStack, ItemView> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "separator_storage");

    @Override
    public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor,
                                                           List<ViewGroup<ItemStack>> groups) {
        // Null-guarded: the server half returns null when the queue is empty, and map() streams the
        // list unconditionally. A NPE here is a crash in a tooltip renderer, which takes the client
        // down for hovering a block.
        return groups == null ? List.of() : ClientViewGroup.map(groups, ItemView::new, null);
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
