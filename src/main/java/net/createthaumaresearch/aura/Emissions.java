package net.createthaumaresearch.aura;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.createthaumaresearch.mixin.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Machines polluting the aura, the way Thaumaturge's crucible does: flux put into the chunk's aura with
 * {@code AuraHelper.polluteAura}, and sometimes physical flux spilled beside the machine. What emits is the datapack rules
 * ({@link EmissionRule}); how much and how often, the config ({@link EmissionConfig}).
 */
public final class Emissions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String P = "com.leclowndu93150.thaumaturge.";
    private static final boolean DEBUG = Boolean.getBoolean("createthaumaresearch.debugEmissions");
    private static List<EmissionRule> rules = List.of();
    private static Map<Block, List<EmissionRule>> byBlock;
    private static List<EmissionRule> tagged;
    private static Method pollute;
    private static Method spill;
    private static boolean reflectionFailed;

    private Emissions() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, event -> event.addListener(new Loader()));
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                tick(level);
            }
        });
    }

    /** The rules, read from every datapack's {@code createthaumaresearch/aura_emissions} folder. */
    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new com.google.gson.Gson(), "createthaumaresearch/aura_emissions");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<EmissionRule> loaded = new ArrayList<>();
            files.forEach((id, json) -> {
                try {
                    loaded.add(new EmissionRule(id, (JsonObject) json));
                } catch (RuntimeException e) {
                    LOGGER.error("Aura emission rule {} is broken: {}", id, e.toString());
                }
            });
            rules = loaded;
            byBlock = null;
            LOGGER.info("Create: Thauma Research: {} aura emission rules", loaded.size());
        }
    }

    public static List<EmissionRule> rules() {
        return rules;
    }

    private static void index() {
        byBlock = new IdentityHashMap<>();
        tagged = new ArrayList<>();
        for (EmissionRule rule : rules) {
            if (rule.type != EmissionRule.Type.BLOCK) {
                continue;
            }
            rule.directBlocks().forEach(block -> byBlock.computeIfAbsent(block, b -> new ArrayList<>()).add(rule));
            if (rule.hasBlockTags()) {
                tagged.add(rule);
            }
        }
    }

    /** Every scan interval: each ticking machine in the level that a block rule names, tried against its rules. */
    private static void tick(ServerLevel level) {
        int every = EmissionConfig.SCAN_INTERVAL.get();
        long time = level.getGameTime();
        if (!EmissionConfig.ENABLED.get() || rules.isEmpty() || time % every != 0) {
            return;
        }
        if (byBlock == null) {
            index();
        }
        if (byBlock.isEmpty() && tagged.isEmpty()) {
            return;
        }
        List<TickingBlockEntity> tickers = new ArrayList<>(((LevelAccessor) level).createthaumaresearch$tickers());
        for (TickingBlockEntity ticker : tickers) {
            BlockPos pos = ticker.getPos();
            if (ticker.isRemoved() || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            List<EmissionRule> candidates = byBlock.get(state.getBlock());
            if (candidates != null) {
                for (EmissionRule rule : candidates) {
                    tryBlock(level, pos, state, rule, time, every);
                }
            }
            for (EmissionRule rule : tagged) {
                if (rule.matchesBlock(state) && (candidates == null || !candidates.contains(rule))) {
                    tryBlock(level, pos, state, rule, time, every);
                }
            }
        }
    }

    private static void tryBlock(ServerLevel level, BlockPos pos, BlockState state, EmissionRule rule, long time, int every) {
        long period = Math.max(every, rule.interval);
        // Spread the machines over the period, so a hundred engines do not all emit on the same tick.
        if (Math.floorMod(time + pos.asLong(), period) >= every || !rule.matchesState(state)) {
            return;
        }
        float scale = 1;
        if (rule.minSpeed > 0 || rule.scaleWithSpeed) {
            float speed = Math.abs(speedOf(level.getBlockEntity(pos)));
            if (speed < rule.minSpeed || speed == 0) {
                return;
            }
            if (rule.scaleWithSpeed) {
                scale = speed / 64F;
            }
        }
        emit(level, pos, rule, scale);
    }

    private static final Map<Class<?>, Method> SPEED = new HashMap<>();

    /**
     * A kinetic block's speed in rpm: Create's {@code getSpeed()}, found by name so any addon's kinetic block works too (0 for a
     * block with none).
     */
    private static float speedOf(BlockEntity entity) {
        if (entity == null) {
            return 0;
        }
        Method method = SPEED.computeIfAbsent(entity.getClass(), type -> {
            try {
                Method found = type.getMethod("getSpeed");
                return found.getReturnType() == float.class ? found : null;
            } catch (NoSuchMethodException e) {
                return null;
            }
        });
        try {
            return method == null ? 0 : (float) method.invoke(entity);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /** An encased fan processed this item through this medium (Create's fan processing type id) at this place. */
    public static void fanProcessed(Level level, BlockPos pos, ResourceLocation processing, ItemStack input) {
        if (!(level instanceof ServerLevel server) || !EmissionConfig.ENABLED.get()) {
            return;
        }
        for (EmissionRule rule : rules) {
            if (rule.type == EmissionRule.Type.FAN_PROCESSING && rule.processing.contains(processing) && rule.matchesItem(input)) {
                emit(server, pos, rule, Math.max(1, input.getCount()) / 1F);
            }
        }
    }

    private static void emit(ServerLevel level, BlockPos pos, EmissionRule rule, float scale) {
        RandomSource random = level.getRandom();
        if (random.nextFloat() >= rule.chance) {
            return;
        }
        float amount = (float) (rule.flux * scale * EmissionConfig.MULTIPLIER.get());
        if (DEBUG) {
            LOGGER.info("[emission] {} at {}: {} flux", rule.id, pos, amount);
        }
        if (amount > 0) {
            pollute(level, pos, amount, rule.effect);
        }
        if (EmissionConfig.SPILL.get() && rule.spillChance > 0 && random.nextFloat() < rule.spillChance) {
            spill(level, pos, random);
        }
    }

    private static boolean reflect() {
        if (pollute == null && !reflectionFailed) {
            try {
                pollute = Class.forName(P + "api.aura.AuraHelper").getMethod("polluteAura", Level.class, BlockPos.class, float.class, boolean.class);
                spill = Class.forName(P + "content.taint.flux.PhysicalFlux").getMethod("spill", ServerLevel.class, BlockPos.class, RandomSource.class);
            } catch (ReflectiveOperationException e) {
                reflectionFailed = true;
                LOGGER.error("Create: Thauma Research: this Thaumaturge has no aura to pollute", e);
            }
        }
        return !reflectionFailed;
    }

    public static void pollute(Level level, BlockPos pos, float amount, boolean effect) {
        if (reflect()) {
            try {
                pollute.invoke(null, level, pos, amount, effect);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Polluting the aura failed", e);
            }
        }
    }

    private static void spill(ServerLevel level, BlockPos pos, RandomSource random) {
        if (reflect()) {
            try {
                spill.invoke(null, level, pos, random);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Spilling flux failed", e);
            }
        }
    }

}
