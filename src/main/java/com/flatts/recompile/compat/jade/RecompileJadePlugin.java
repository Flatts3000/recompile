package com.flatts.recompile.compat.jade;

import com.flatts.recompile.content.block.BulkyWasteBlock;
import com.flatts.recompile.content.block.ManholeBlock;
import com.flatts.recompile.content.block.CompostCageBlock;
import com.flatts.recompile.content.block.CompostHeapCoreBlock;
import com.flatts.recompile.content.block.RecompileWorkbenchBlock;
import com.flatts.recompile.content.block.ScrapBinBlock;
import com.flatts.recompile.content.block.AnimalBaitBlock;
import com.flatts.recompile.content.block.BurnerGeneratorBlock;
import com.flatts.recompile.content.block.SolarPanelBlock;
import com.flatts.recompile.content.block.SeparatorCoreBlock;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.SteelBeamBlock;
import com.flatts.recompile.content.block.TreeNurseryCoreBlock;
import com.flatts.recompile.content.block.TreeNurseryTankBlock;
import com.flatts.recompile.content.block.PulverizerCoreBlock;
import com.flatts.recompile.content.block.PulverizerPartBlock;
import com.flatts.recompile.content.block.SeparatorPartBlock;
import com.flatts.recompile.content.block.TrommelCoreBlock;
import com.flatts.recompile.content.block.TrommelDrumBlock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
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
 *   <li>{@link WorkbenchHintProvider} - the GUI-free workbench's state and next step.</li>
 *   <li>{@link MachineStatusProvider} - whether a machine is assembled, what it still needs,
 *       and the Grass Spreader's radius.</li>
 * </ul>
 */
@WailaPlugin
public class RecompileJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // Server side: send the workbench's racked-tool durability to the client on hover. This is
        // a separate provider from the client component - since MC 1.21.6 one class cannot be both.
        registration.registerBlockDataProvider(WorkbenchDataProvider.INSTANCE, RecompileWorkbenchBlock.class);
        registration.registerBlockDataProvider(ScrapBinDataProvider.INSTANCE, ScrapBinBlock.class);
        // The heap's layer state is on the core BE; the cage variant lets any hovered cell resolve it.
        registration.registerBlockDataProvider(CompostHeapDataProvider.INSTANCE, CompostHeapCoreBlock.class);
        registration.registerBlockDataProvider(CompostHeapDataProvider.INSTANCE, CompostCageBlock.class);
        // The nursery's state is on the core BE; the clad tank cell lets a hovered tank resolve it too.
        registration.registerBlockDataProvider(TreeNurseryDataProvider.INSTANCE, TreeNurseryCoreBlock.class);
        registration.registerBlockDataProvider(TreeNurseryDataProvider.INSTANCE, TreeNurseryTankBlock.class);
        // The power tier (#72): a generator's buffer is server-only, and the Solar Panel's current rate
        // depends on sky exposure the client can lag on - so both cross with the hover.
        registration.registerBlockDataProvider(SeparatorDataProvider.INSTANCE, SeparatorCoreBlock.class);
        registration.registerBlockDataProvider(TrommelDataProvider.INSTANCE, TrommelCoreBlock.class);
        registration.registerBlockDataProvider(PulverizerDataProvider.INSTANCE, PulverizerCoreBlock.class);
        // The queue as an item grid. A view, not a container: the machine still exposes no
        // handler, so nothing can insert or extract - showing what is inside and letting
        // something reach inside are different doors.
        registration.registerItemStorage(SeparatorStorageProvider.INSTANCE,
            com.flatts.recompile.content.block.entity.SeparatorBlockEntity.class);
        registration.registerItemStorage(TrommelStorageProvider.INSTANCE,
            com.flatts.recompile.content.block.entity.TrommelBlockEntity.class);
        registration.registerItemStorage(PulverizerStorageProvider.INSTANCE,
            com.flatts.recompile.content.block.entity.PulverizerBlockEntity.class);
        registration.registerBlockDataProvider(GeneratorDataProvider.INSTANCE, SolarPanelBlock.class);
        registration.registerBlockDataProvider(GeneratorDataProvider.INSTANCE, BurnerGeneratorBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ToolHintProvider.INSTANCE, SortableBlock.class);

        // Recovered paintings name themselves on the wall (#99). An ENTITY component, not a block
        // one - a hung painting is an entity, which is also why it needs no server data provider:
        // the variant is already synced so the client can draw it.
        registration.registerEntityComponent(PaintingNameProvider.INSTANCE,
            net.minecraft.world.entity.decoration.painting.Painting.class);
        registration.registerBlockComponent(ToolHintProvider.INSTANCE, BulkyWasteBlock.class);
        // The manhole reuses the Bulky Waste gate, so it reuses the hint that explains it. A player
        // who finds the pad and cannot act on it reads the whole sewer as scenery.
        registration.registerBlockComponent(ToolHintProvider.INSTANCE, ManholeBlock.class);
        registration.registerBlockComponent(ToolHintProvider.INSTANCE, SteelBeamBlock.class);
        registration.registerBlockComponent(SortProgressProvider.INSTANCE, SortableBlock.class);
        registration.registerBlockComponent(WorkbenchHintProvider.INSTANCE, RecompileWorkbenchBlock.class);
        registration.registerBlockComponent(MachineStatusProvider.INSTANCE, MultiblockCoreBlock.class);
        registration.registerBlockComponent(TrommelFeedProvider.INSTANCE, TrommelCoreBlock.class);
        registration.registerBlockComponent(TrommelFeedProvider.INSTANCE, TrommelDrumBlock.class);
        registration.registerBlockComponent(PulverizerFeedProvider.INSTANCE, PulverizerCoreBlock.class);
        registration.registerBlockComponent(PulverizerFeedProvider.INSTANCE, PulverizerPartBlock.class);
        registration.registerBlockComponent(SeparatorFeedProvider.INSTANCE, SeparatorCoreBlock.class);
        registration.registerBlockComponent(SeparatorFeedProvider.INSTANCE, SeparatorPartBlock.class);
        registration.registerBlockComponent(ScrapBinProvider.INSTANCE, ScrapBinBlock.class);
        registration.registerItemStorageClient(SeparatorStorageClientProvider.INSTANCE);
        registration.registerItemStorageClient(TrommelStorageClientProvider.INSTANCE);
        registration.registerItemStorageClient(PulverizerStorageClientProvider.INSTANCE);
        registration.registerBlockComponent(CompostHeapProvider.INSTANCE, CompostHeapCoreBlock.class);
        registration.registerBlockComponent(CompostHeapProvider.INSTANCE, CompostCageBlock.class);
        registration.registerBlockComponent(SeparatorProvider.INSTANCE, SeparatorCoreBlock.class);
        registration.registerBlockComponent(TrommelProvider.INSTANCE, TrommelCoreBlock.class);
        registration.registerBlockComponent(PulverizerProvider.INSTANCE, PulverizerCoreBlock.class);
        registration.registerBlockComponent(GeneratorProvider.INSTANCE, SolarPanelBlock.class);
        registration.registerBlockComponent(GeneratorProvider.INSTANCE, BurnerGeneratorBlock.class);
        registration.registerBlockComponent(TreeNurseryProvider.INSTANCE, TreeNurseryCoreBlock.class);
        registration.registerBlockComponent(TreeNurseryProvider.INSTANCE, TreeNurseryTankBlock.class);
        registration.registerBlockComponent(AnimalBaitProvider.INSTANCE, AnimalBaitBlock.class);
    }
}
