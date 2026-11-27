package net.createthaumaresearch.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;

import net.createthaumaresearch.gate.ResearchGates;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

/** Create's deployer applies recipes for the player who placed it: a gated recipe its owner has not opened is not found. */
@Mixin(value = DeployerBlockEntity.class, remap = false)
public abstract class DeployerMixin {
    @Shadow protected UUID owner;

    @Inject(method = "getRecipe", at = @At("RETURN"), cancellable = true)
    private void createthaumaresearch$gate(ItemStack stack,
                                           CallbackInfoReturnable<RecipeHolder<? extends Recipe<? extends RecipeInput>>> cir) {
        RecipeHolder<?> recipe = cir.getReturnValue();
        var self = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        if (recipe == null || self.getLevel() == null || self.getLevel().isClientSide) {
            return;
        }
        ItemStack output = recipe.value().getResultItem(self.getLevel().registryAccess());
        if (!ResearchGates.mayUse(self.getLevel(), owner, recipe, output)) {
            cir.setReturnValue(null);
        }
    }
}
