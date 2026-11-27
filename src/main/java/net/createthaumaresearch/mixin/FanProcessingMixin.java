package net.createthaumaresearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;

import net.createthaumaresearch.aura.Emissions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Items lying in an encased fan's air current: each one processed (washed, blasted...) is reported for aura emissions. */
@Mixin(value = FanProcessing.class, remap = false)
public abstract class FanProcessingMixin {
    private static final ThreadLocal<ItemStack> createthaumaresearch$input = new ThreadLocal<>();

    @Inject(method = "applyProcessing(Lnet/minecraft/world/entity/item/ItemEntity;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z",
            at = @At("HEAD"))
    private static void createthaumaresearch$before(ItemEntity entity, FanProcessingType type, CallbackInfoReturnable<Boolean> cir) {
        createthaumaresearch$input.set(entity.getItem().copy());
    }

    @Inject(method = "applyProcessing(Lnet/minecraft/world/entity/item/ItemEntity;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z",
            at = @At("RETURN"))
    private static void createthaumaresearch$after(ItemEntity entity, FanProcessingType type, CallbackInfoReturnable<Boolean> cir) {
        ItemStack input = createthaumaresearch$input.get();
        createthaumaresearch$input.remove();
        if (cir.getReturnValueZ() && input != null) {
            Emissions.fanProcessed(entity.level(), entity.blockPosition(), CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type), input);
        }
    }
}
