package net.createthaumaresearch.gate;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The research gates' part of the common config. */
public final class GateConfig {
    public static ModConfigSpec.BooleanValue LOCK_UNOWNED;
    public static ModConfigSpec.BooleanValue LOCK_FURNACES;

    private GateConfig() {
    }

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Research gates: recipes locked until research is done (datapack files in data/<namespace>/createthaumaresearch/research_gates/).")
                .push("research_gates");
        LOCK_UNOWNED = b.comment("Whether machines nobody placed (world generation, commands, other mods) are refused gated recipes.")
                .define("lock_unowned_machines", false);
        LOCK_FURNACES = b.comment("Whether furnaces, smokers and blast furnaces obey gates (as their owner).").define("gate_furnaces", true);
        b.pop();
    }
}
