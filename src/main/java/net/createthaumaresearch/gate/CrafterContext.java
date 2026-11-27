package net.createthaumaresearch.gate;

import net.minecraft.world.level.block.entity.BlockEntity;

/** The mechanical crafter checking its recipe on this thread, if any. */
public final class CrafterContext {
    public static final ThreadLocal<BlockEntity> CURRENT = new ThreadLocal<>();

    private CrafterContext() {
    }
}
