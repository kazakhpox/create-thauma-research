package net.createthaumaresearch.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.createthaumaresearch.CreateThaumaResearch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/**
 * Hides locked results and blocks shift-click extraction. ResultSlotMixin additionally guards
 * ordinary result-slot pickups on the server.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @ModifyVariable(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"), ordinal = 0)
    private static ItemStack createthaumaresearch$lockDisplayedResult(ItemStack result, AbstractContainerMenu menu, Level level,
                                                                       Player player, CraftingContainer grid,
                                                                       ResultContainer slots,
                                                                       @Nullable RecipeHolder<CraftingRecipe> recipe) {
        return CreateThaumaResearch.mayMake(player, result) ? result : ItemStack.EMPTY;
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void createthaumaresearch$preventLockedShiftClick(Player player, int slotIndex,
                                                               CallbackInfoReturnable<ItemStack> cir) {
        if (slotIndex != 0) {
            return;
        }
        ItemStack result = ((CraftingMenu) (Object) this).getSlot(slotIndex).getItem();
        if (!result.isEmpty() && !CreateThaumaResearch.mayMake(player, result)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
