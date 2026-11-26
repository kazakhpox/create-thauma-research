package net.createthaumaresearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.createthaumaresearch.CreateThaumaResearch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side guard for ordinary and shift-click actions in the vanilla crafting menu.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void createthaumaresearch$preventLockedCraftTake(int slotId, int button, ClickType clickType,
                                                               Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (!(menu instanceof CraftingMenu) || slotId != 0) {
            return;
        }

        ItemStack result = menu.getSlot(slotId).getItem();
        if (!result.isEmpty() && !CreateThaumaResearch.mayMake(player, result)) {
            ci.cancel();
        }
    }
}
