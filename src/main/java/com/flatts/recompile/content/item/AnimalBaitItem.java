package com.flatts.recompile.content.item;

import com.flatts.recompile.content.block.AnimalBaitBlock;
import com.flatts.recompile.content.block.AnimalBaitBlock.Diet;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A bait item (reclamation rung 5): places an {@link AnimalBaitBlock} carrying this item's diet and
 * grade. The tooltip teaches the deviation - place it on grass and <b>walk away</b> (a nearby player
 * holds it), keep baits apart - because those gates are invisible failure modes otherwise.
 */
public class AnimalBaitItem extends BlockItem {

    private final Diet diet;
    private final boolean rich;

    public AnimalBaitItem(Block block, Properties properties, Diet diet, boolean rich) {
        super(block, properties);
        this.diet = diet;
        this.rich = rich;
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        BlockState base = super.getPlacementState(context);
        return base == null ? null
            : base.setValue(AnimalBaitBlock.DIET, diet).setValue(AnimalBaitBlock.RICH, rich);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.recompile.animal_bait").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.recompile.animal_bait.diet." + diet.getSerializedName())
            .withStyle(ChatFormatting.DARK_GRAY));
        if (rich) {
            builder.accept(Component.translatable("tooltip.recompile.animal_bait.rich")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
