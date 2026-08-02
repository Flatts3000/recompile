package com.flatts.recompile.compat.jei;

import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.network.FillGridPayload;
import com.flatts.recompile.network.ScrapNetworkContentsPayload;
import com.flatts.recompile.registry.RCMenus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * JEI's transfer button for the Scrap Crafting Table (#95).
 *
 * <p><b>Why the stock handler is not enough.</b> JEI's built-in transfer moves items between slots of
 * the open container, and this table's materials mostly are not in it - they are in the Scrap Barrel
 * and Scrap Bins wired to it, which is the whole reason the connected-storage panel exists. A player
 * looking at a recipe whose every ingredient sat in the barrel beside them was told "Missing Items".
 *
 * <p><b>The client can answer this without asking the server.</b> The panel's contents are already
 * synced for rendering, so availability is computed here from the player's inventory plus that
 * snapshot, and only the placements are sent. The server still finds each item itself before moving
 * anything, so a stale snapshot costs a half-filled grid rather than an item from nowhere.
 *
 * <p><b>One class, registered for every category this table can run.</b> It reads the recipe through
 * JEI's slot views rather than the recipe object, so it does not care whether it is looking at a
 * blueprint recipe, a fragment assembly or an ordinary vanilla one.
 */
public class ScrapTableTransfer<R> implements IRecipeTransferHandler<ScrapCraftingStationMenu, R> {

    private final IRecipeType<R> recipeType;
    private final IRecipeTransferHandlerHelper helper;

    public ScrapTableTransfer(IRecipeType<R> recipeType, IRecipeTransferHandlerHelper helper) {
        this.recipeType = recipeType;
        this.helper = helper;
    }

    @Override
    public Class<? extends ScrapCraftingStationMenu> getContainerClass() {
        return ScrapCraftingStationMenu.class;
    }

    @Override
    public Optional<MenuType<ScrapCraftingStationMenu>> getMenuType() {
        return Optional.of(RCMenus.SCRAP_CRAFTING_STATION.get());
    }

    @Override
    public IRecipeType<R> getRecipeType() {
        return recipeType;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(ScrapCraftingStationMenu menu, R recipe,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);

        // What the player can reach: their own inventory, plus everything the table's cluster holds.
        Map<Item, Integer> available = new HashMap<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                available.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        ScrapNetworkContentsPayload contents = menu.contents();
        for (ScrapNetworkContentsPayload.Material material : contents.materials()) {
            available.merge(material.item(), material.count(), Integer::sum);
        }

        List<Integer> placements = new ArrayList<>();
        for (int i = 0; i < FillGridPayload.SLOTS; i++) {
            placements.add(FillGridPayload.EMPTY);
        }
        List<IRecipeSlotView> missing = new ArrayList<>();

        // Only INPUT slots, which is what keeps the blueprint out of this: the category declares it as
        // a CRAFTING_STATION because it is required and never consumed. It was an INPUT once, and this
        // loop dutifully demanded it as a tenth ingredient - reporting "missing items" for a sheet the
        // player had filed in a cabinet next door.
        //
        // The category declares all nine cells in grid order, empty ones included, so the index here is
        // the grid position.
        int index = 0;
        for (IRecipeSlotView view : inputs) {
            if (index >= FillGridPayload.SLOTS) {
                break;
            }
            List<ItemStack> options = view.getItemStacks().toList();
            if (options.isEmpty()) {
                index++;
                continue;
            }
            // First option the player can actually reach, so a tag ingredient uses whatever colour of
            // wool is to hand rather than insisting on the one JEI happened to draw.
            Item chosen = null;
            for (ItemStack option : options) {
                Item item = option.getItem();
                if (available.getOrDefault(item, 0) > 0) {
                    chosen = item;
                    break;
                }
            }
            if (chosen == null) {
                missing.add(view);
            } else {
                available.merge(chosen, -1, Integer::sum);
                placements.set(index, Item.getId(chosen));
            }
            index++;
        }

        if (!missing.isEmpty()) {
            return helper.createUserErrorForMissingSlots(
                Component.translatable("jei.recompile.transfer_missing"), missing);
        }
        if (doTransfer) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new FillGridPayload(List.copyOf(placements)));
        }
        return null;
    }
}
