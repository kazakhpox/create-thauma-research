package net.createthaumaresearch.gate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.createthaumaresearch.CreateThaumaResearch;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Research gates for any recipe: the library. Gates are data ({@link Gate}); a recipe is open to a player when every gate that
 * locks it passes Thaumaturge's own check ({@code ResearchGate.passes}, what its arcane workbench and crucible ask), the same
 * entry, stage and negate rules. Machines without a player ask about their owner ({@link Owners}).
 *
 * <p>For other mods: {@link #mayUse(Player, RecipeHolder, ItemStack)} for a player, {@link #mayUse(Level, UUID, RecipeHolder, ItemStack)}
 * for a machine's owner, {@link #gatesFor} for what locks a recipe.
 */
public final class ResearchGates {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static List<Gate> gates = List.of();
    private static Method passes;
    private static Constructor<?> newGate;
    private static boolean thaumaturgeGate = true;

    private ResearchGates() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, event -> event.addListener(new Loader()));
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "createthaumaresearch/research_gates");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<Gate> loaded = new ArrayList<>();
            files.forEach((id, json) -> {
                try {
                    loaded.add(new Gate(id, (JsonObject) json));
                } catch (RuntimeException e) {
                    LOGGER.error("Research gate {} is broken: {}", id, e.toString());
                }
            });
            gates = loaded;
            LOGGER.info("Create: Thauma Research: {} research gates", loaded.size());
        }
    }

    /** Gates arrive on the client with the server's data only in singleplayer; a dedicated server's client sees no gates. */
    public static List<Gate> gates() {
        return gates;
    }

    /** Every gate that locks this recipe. */
    public static List<Gate> gatesFor(RecipeHolder<?> holder, ItemStack output) {
        List<Gate> out = new ArrayList<>();
        for (Gate gate : gates) {
            if (gate.locks(holder, output)) {
                out.add(gate);
            }
        }
        return out;
    }

    /** The first gate locking recipes that make this item and that the player has not passed (null if none): for tooltips. */
    public static Gate closedGateForItem(Player player, ItemStack stack) {
        for (Gate gate : gates) {
            if (gate.locksItem(stack) && !passes(player, gate)) {
                return gate;
            }
        }
        return null;
    }

    /** Whether the player may use this recipe (making {@code output}): every gate on it passes. A null player may not. */
    public static boolean mayUse(Player player, RecipeHolder<?> holder, ItemStack output) {
        for (Gate gate : gatesFor(holder, output)) {
            if (player == null || !passes(player, gate)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a machine owned by this player may use the recipe ({@link Owners}: online, or as last seen; unowned per config). */
    public static boolean mayUse(Level level, UUID owner, RecipeHolder<?> holder, ItemStack output) {
        List<Gate> on = gatesFor(holder, output);
        if (on.isEmpty()) {
            return true;
        }
        if (owner == null) {
            return !GateConfig.LOCK_UNOWNED.get();
        }
        for (Gate gate : on) {
            if (!Owners.passes(level, owner, gate)) {
                return false;
            }
        }
        return true;
    }

    /** Thaumaturge's own gate check for this player; falls back to "research completed" if that API is missing. */
    public static boolean passes(Player player, Gate gate) {
        if (thaumaturgeGate && passes == null) {
            try {
                Class<?> type = Class.forName("com.leclowndu93150.thaumaturge.api.recipe.ResearchGate");
                passes = type.getMethod("passes", Player.class, type);
                newGate = type.getConstructor(ResourceLocation.class, Optional.class, boolean.class);
            } catch (ReflectiveOperationException e) {
                thaumaturgeGate = false;
                LOGGER.warn("Create: Thauma Research: no ResearchGate in this Thaumaturge, using completed research");
            }
        }
        if (thaumaturgeGate) {
            try {
                return (boolean) passes.invoke(null, player, newGate.newInstance(gate.entry, gate.stage, gate.negate));
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.error("Thaumaturge's research gate check failed for {}", gate.entry, e);
                return false;
            }
        }
        boolean done = CreateThaumaResearch.completed(player, gate.entry);
        return gate.negate != done;
    }

    public static Component lockedMessage(Gate gate) {
        return Component.translatable("tooltip." + CreateThaumaResearch.MOD_ID + ".locked", CreateThaumaResearch.researchName(gate.entry))
                .withStyle(ChatFormatting.DARK_PURPLE);
    }

    /** What a gate on this recipe would show the player, or null if they may use it. */
    public static Component refusal(Player player, RecipeHolder<?> holder, ItemStack output) {
        for (Gate gate : gatesFor(holder, output)) {
            if (player == null || !passes(player, gate)) {
                return lockedMessage(gate);
            }
        }
        return null;
    }

}
