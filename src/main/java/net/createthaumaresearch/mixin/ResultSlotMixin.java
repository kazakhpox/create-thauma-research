package net.createthaumaresearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.createthaumaresearch.CreateThaumaResearch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-side guard for ordinary clicks on a crafting result slot. */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void createthaumaresearch$preventLockedPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack result = ((Slot) (Object) this).getItem();
        if (!result.isEmpty() && !CreateThaumaResearch.mayMake(player, result)) {
            cir.setReturnValue(false);
        }
    }
}
