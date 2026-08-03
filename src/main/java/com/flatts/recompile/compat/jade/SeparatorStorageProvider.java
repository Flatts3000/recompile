package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.SeparatorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * Jade (server): the Separator's queue as an <b>item grid</b>, the way Jade shows any inventory.
 *
 * <p>Sprites and counts rather than a list of names (owner, 2026-08-03). A machine holding several
 * kinds at once is read at a glance from icons and unreadable as prose, and this is the presentation
 * a player already knows from every chest they have hovered.
 *
 * <p><b>This does not make the Separator a container.</b> Jade's item-storage extension is a view, so
 * the machine still exposes no {@code Container} and no item handler: nothing can insert, nothing can
 * extract, and no pipe can connect. Showing what is inside and letting something reach inside are
 * different things, and only the second one is the door the automation policy keeps shut.
 */
public enum SeparatorStorageProvider implements IServerExtensionProvider<ItemStack> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "separator_storage");

    @Override
    public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
        // Registered against SeparatorBlockEntity, so the target IS the machine - no need to go back
        // to the world for it.
        if (!(accessor.getTarget() instanceof SeparatorBlockEntity separator)) {
            return null;
        }
        List<ItemStack> queued = new ArrayList<>();
        for (ItemStack stack : separator.queued()) {
            if (!stack.isEmpty()) {
                queued.add(stack.copy());
            }
        }
        // Null rather than an empty group: an empty grid draws a row of blank slots, which reads as
        // "this machine has nine slots and they are all empty" when the honest answer is "nothing to
        // show". The status line already says the machine is idle.
        return queued.isEmpty() ? null : List.of(new ViewGroup<>(queued));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
