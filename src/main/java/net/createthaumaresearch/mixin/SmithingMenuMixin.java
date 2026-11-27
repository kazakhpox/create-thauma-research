package net.createthaumaresearch.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.createthaumaresearch.gate.ResearchGates;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;

/** The smithing table: a gated recipe the player has not opened gives nothing. */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
    @Shadow @Nullable private RecipeHolder<SmithingRecipe> selectedRecipe;

    private SmithingMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
        super(type, id, inventory, access);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void createthaumaresearch$gate(CallbackInfo ci) {
        ItemStack result = resultSlots.getItem(0);
        if (selectedRecipe != null && !result.isEmpty() && !player.level().isClientSide
                && !ResearchGates.mayUse(player, selectedRecipe, result)) {
            resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }
}
