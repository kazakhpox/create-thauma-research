package net.createthaumaresearch;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import net.createthaumaresearch.aura.EmissionConfig;
import net.createthaumaresearch.aura.Emissions;

/**
 * Create: Thauma Research. A Thaumonomicon chapter, Mechanoturgy, whose research (plain Thaumaturge data) opens Create's machines,
 * and Create's machines polluting the aura with flux ({@link Emissions}). The locking is Thaumaturge Research Gates (a separate
 * library): this mod only ships its gates, {@code data/createthaumaresearch/research_gates/}, written with the chapter by
 * {@code tools/gen.py}.
 */
@Mod(CreateThaumaResearch.MOD_ID)
public final class CreateThaumaResearch {
    public static final String MOD_ID = "createthaumaresearch";

    public CreateThaumaResearch(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, EmissionConfig.SPEC);
        Emissions.register();
    }
}
