package net.createthaumaresearch.aura;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's common config (config/createthaumaresearch-common.toml): emissions on or off, how strong, how often machines are looked
 * at. What emits and under which conditions is data: see {@link EmissionRule}.
 */
public final class EmissionConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue MULTIPLIER;
    public static final ModConfigSpec.IntValue SCAN_INTERVAL;
    public static final ModConfigSpec.BooleanValue SPILL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Machines that pollute the aura with flux, as Thaumaturge's crucible does.",
                "Which blocks and processes emit, and when, is set by datapack files in data/<namespace>/createthaumaresearch/aura_emissions/.")
                .push("aura_emissions");
        ENABLED = b.comment("Whether machines emit flux at all.").define("enabled", true);
        MULTIPLIER = b.comment("Every emission's flux is multiplied by this.").defineInRange("flux_multiplier", 1.0, 0.0, 100.0);
        SCAN_INTERVAL = b.comment("Ticks between looks at the loaded machines (block rules). A rule's own interval is rounded up to it.")
                .defineInRange("scan_interval", 20, 1, 1200);
        SPILL = b.comment("Whether rules may spill physical flux (goo) next to the machine, as an overflowing crucible does.")
                .define("allow_spill", true);
        b.pop();
        net.createthaumaresearch.gate.GateConfig.define(b);
        SPEC = b.build();
    }

    private EmissionConfig() {
    }
}
