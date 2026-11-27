package net.createthaumaresearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;

import net.createthaumaresearch.gate.CrafterContext;

/** While a mechanical crafter checks its recipe, the gates know which crafter (and so whose) it is. */
@Mixin(value = MechanicalCrafterBlockEntity.class, remap = false)
public abstract class MechanicalCrafterMixin {
    @Inject(method = "checkCompletedRecipe", at = @At("HEAD"))
    private void createthaumaresearch$enter(boolean poweredStart, CallbackInfo ci) {
        CrafterContext.CURRENT.set((net.minecraft.world.level.block.entity.BlockEntity) (Object) this);
    }

    @Inject(method = "checkCompletedRecipe", at = @At("RETURN"))
    private void createthaumaresearch$leave(boolean poweredStart, CallbackInfo ci) {
        CrafterContext.CURRENT.remove();
    }
}
