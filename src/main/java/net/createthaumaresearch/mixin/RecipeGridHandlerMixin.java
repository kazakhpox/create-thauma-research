package net.createthaumaresearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;

import net.createthaumaresearch.gate.CrafterContext;
import net.createthaumaresearch.gate.Owners;
import net.createthaumaresearch.gate.ResearchGates;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Create's mechanical crafters: a recipe is allowed only if the crafter's owner (the player who placed the crafter that checks it)
 * has opened its gates. Covers both crafting-table and mechanical-crafting recipes.
 */
@Mixin(value = RecipeGridHandler.class, remap = false)
public abstract class RecipeGridHandlerMixin {
    @Inject(method = "isRecipeAllowed", at = @At("RETURN"), cancellable = true)
    private static void createthaumaresearch$gate(RecipeHolder<CraftingRecipe> recipe, CraftingInput input, CallbackInfoReturnable<Boolean> cir) {
        var crafter = CrafterContext.CURRENT.get();
        if (!cir.getReturnValueZ() || crafter == null || crafter.getLevel() == null) {
            return;
        }
        var output = recipe.value().getResultItem(crafter.getLevel().registryAccess());
        if (!ResearchGates.mayUse(crafter.getLevel(), Owners.ownerOf(crafter), recipe, output)) {
            cir.setReturnValue(false);
        }
    }
}
