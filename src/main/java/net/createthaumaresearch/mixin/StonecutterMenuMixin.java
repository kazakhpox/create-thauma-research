package net.createthaumaresearch.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.createthaumaresearch.gate.ResearchGates;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/** The stonecutter: a gated recipe the player has not opened can be chosen, but gives nothing. */
@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
    @Shadow @Final ResultContainer resultContainer;
    @Shadow private List<RecipeHolder<StonecutterRecipe>> recipes;
    @Shadow @Final private DataSlot selectedRecipeIndex;
    @Unique private Player createthaumaresearch$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void createthaumaresearch$remember(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        createthaumaresearch$player = inventory.player;
    }

    @Inject(method = "setupResultSlot", at = @At("TAIL"))
    private void createthaumaresearch$gate(CallbackInfo ci) {
        int index = selectedRecipeIndex.get();
        ItemStack result = resultContainer.getItem(0);
        if (result.isEmpty() || index < 0 || index >= recipes.size() || createthaumaresearch$player == null
                || createthaumaresearch$player.level().isClientSide) {
            return;
        }
        if (!ResearchGates.mayUse(createthaumaresearch$player, recipes.get(index), result)) {
            resultContainer.setItem(0, ItemStack.EMPTY);
        }
    }
}
