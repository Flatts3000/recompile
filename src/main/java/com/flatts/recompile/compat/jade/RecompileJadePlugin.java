package com.flatts.recompile.compat.jade;

import com.flatts.recompile.content.block.BulkyWasteBlock;
import com.flatts.recompile.content.block.SortableBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration: hover tooltips for the salvage blocks, whose state is otherwise
 * invisible. Loaded only when Jade is present (Jade scans for {@link WailaPlugin});
 * the API is {@code compileOnly}, so nothing here is referenced without the mod.
 *
 * <ul>
 *   <li>{@link ToolHintProvider} - which tool a block wants (knife / prybar / hand).</li>
 *   <li>{@link SortProgressProvider} - pulls taken, from the {@code sorted} blockstate.</li>
 * </ul>
 */
@WailaPlugin
public class RecompileJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ToolHintProvider.INSTANCE, SortableBlock.class);
        registration.registerBlockComponent(ToolHintProvider.INSTANCE, BulkyWasteBlock.class);
        registration.registerBlockComponent(SortProgressProvider.INSTANCE, SortableBlock.class);
    }
}
