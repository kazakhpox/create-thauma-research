package net.createthaumaresearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;

import net.createthaumaresearch.aura.Emissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Items on belts and depots in an encased fan's air current: the fan hands each one to processing in a lambda that knows the belt
 * or depot; each item processed there is reported for aura emissions at its place.
 */
@Mixin(value = AirCurrent.class, remap = false)
public abstract class AirCurrentMixin {
    private static final ThreadLocal<ItemStack> createthaumaresearch$input = new ThreadLocal<>();

    @Inject(method = "lambda$tickAffectedHandlers$0", at = @At("HEAD"), require = 0)
    private void createthaumaresearch$before(Level level, FanProcessingType type, TransportedItemStackHandlerBehaviour handler,
                                                    TransportedItemStack transported,
                                                    CallbackInfoReturnable<TransportedItemStackHandlerBehaviour.TransportedResult> cir) {
        createthaumaresearch$input.set(transported.stack.copy());
    }

    @Inject(method = "lambda$tickAffectedHandlers$0", at = @At("RETURN"), require = 0)
    private void createthaumaresearch$after(Level level, FanProcessingType type, TransportedItemStackHandlerBehaviour handler,
                                                   TransportedItemStack transported,
                                                   CallbackInfoReturnable<TransportedItemStackHandlerBehaviour.TransportedResult> cir) {
        ItemStack input = createthaumaresearch$input.get();
        createthaumaresearch$input.remove();
        var result = cir.getReturnValue();
        if (input != null && result != null && !result.doesNothing()) {
            Emissions.fanProcessed(level, handler.getPos(), CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type), input);
        }
    }
}
