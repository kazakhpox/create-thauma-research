package net.createthaumaresearch.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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
 * The crafting grid (the crafting table's and the inventory's) gives nothing for a Create item whose research the player has not
 * completed: the result is emptied before it is put in the result slot and sent to the client.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @ModifyVariable(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"), ordinal = 0)
    private static ItemStack createthaumaresearch$lockResult(ItemStack result, AbstractContainerMenu menu, Level level, Player player,
                                                             CraftingContainer grid, ResultContainer slots,
                                                             @Nullable RecipeHolder<CraftingRecipe> recipe) {
        return CreateThaumaResearch.mayMake(player, result) ? result : ItemStack.EMPTY;
    }
}
