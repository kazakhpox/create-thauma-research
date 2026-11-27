package net.createthaumaresearch.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.createthaumaresearch.gate.ResearchGates;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
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
 * The crafting grid (the crafting table's and the inventory's): once the game has found the recipe and its result, a gated recipe
 * the player has not opened leaves the result slot empty, on the server and on the player's screen.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void createthaumaresearch$gate(AbstractContainerMenu menu, Level level, Player player, CraftingContainer grid,
                                                  ResultContainer slots, @Nullable RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        ItemStack result = slots.getItem(0);
        RecipeHolder<?> used = slots.getRecipeUsed();
        if (level.isClientSide || result.isEmpty() || used == null || ResearchGates.mayUse(player, used, result)) {
            return;
        }
        slots.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        if (player instanceof ServerPlayer server) {
            server.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
        }
    }
}
