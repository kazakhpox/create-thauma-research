package net.createthaumaresearch.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

/** The level's ticking block entities: the working machines, looked at for aura emissions. */
@Mixin(Level.class)
public interface LevelAccessor {
    @Accessor("blockEntityTickers")
    List<TickingBlockEntity> createthaumaresearch$tickers();
}
