package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.TrommelBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * Jade (server): the Trommel's queue as an <b>item grid</b>, exactly as the Separator shows its own.
 *
 * <p><b>This does not make the Trommel a container.</b> Jade's item-storage extension is a view, so the
 * machine still exposes no {@code Container} and no item handler: nothing can insert, nothing can
 * extract, no pipe can connect. Showing what is inside and letting something reach inside are different
 * things, and only the second is the door this machine keeps shut.
 * {@code the_trommel_is_unreachable_by_pipe_and_hopper} still passes with this in place, which is the
 * assertion that the distinction is real rather than intended.
 */
public enum TrommelStorageProvider implements IServerExtensionProvider<ItemStack> {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "trommel_storage");

    @Override
    public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof TrommelBlockEntity trommel)) {
            return null;
        }
        List<ItemStack> queued = new ArrayList<>();
        for (ItemStack stack : trommel.queued()) {
            if (!stack.isEmpty()) {
                queued.add(stack.copy());
            }
        }
        // Null rather than an empty group: an empty grid draws a row of blank slots, which reads as
        // "nine slots, all empty" when the honest answer is "nothing to show".
        return queued.isEmpty() ? null : List.of(new ViewGroup<>(queued));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
