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

/** Jade (client): render the Trommel's queue as item sprites with counts, as the Separator does. */
public enum TrommelStorageClientProvider
        implements IClientExtensionProvider<ItemStack, ItemView> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "trommel_storage");

    @Override
    public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor,
                                                           List<ViewGroup<ItemStack>> groups) {
        // Null-guarded: the server half returns null on an empty queue and map() streams
        // unconditionally. An NPE here crashes the client for hovering a block.
        return groups == null ? List.of() : ClientViewGroup.map(groups, ItemView::new, null);
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
