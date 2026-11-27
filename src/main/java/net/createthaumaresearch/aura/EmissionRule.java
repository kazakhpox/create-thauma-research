package net.createthaumaresearch.aura;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * One emission rule, from a datapack file {@code data/<namespace>/createthaumaresearch/aura_emissions/<name>.json}:
 *
 * <pre>
 * {
 *   "type": "block",                       // or "fan_processing"
 *   // block rules: a block (or a tag of blocks) that pollutes while it is in a given state
 *   "blocks": ["create:blaze_burner", "#mymod:furnaces"],
 *   "state": {"blaze": ["kindled", "seething"]},   // optional: block state properties that must match
 *   "min_speed": 1,                        // optional: a kinetic block must turn at least this fast (rpm, either way)
 *   "scale_with_speed": false,             // optional: multiply the flux by rpm / 64
 *   "interval": 100,                       // optional: ticks between tries (default 100)
 *   // fan_processing rules: an encased fan processing items through a medium
 *   "processing": ["create:splashing"],    // Create fan processing types: splashing, blasting, haunting, smoking
 *   "items": ["#create:crushed_raw_materials"],   // optional: only for these items (or tags)
 *   // both
 *   "chance": 0.5,                         // optional: chance per try / per processed item (default 1)
 *   "flux": 0.25,                          // flux put into the aura of the chunk
 *   "spill_chance": 0.0,                   // optional: chance to also spill physical flux (goo) beside it
 *   "effect": true                         // optional: show the pollution particles (default true)
 * }
 * </pre>
 */
public final class EmissionRule {
    public enum Type { BLOCK, FAN_PROCESSING }

    public final ResourceLocation id;
    public final Type type;
    private final List<ResourceLocation> blockIds = new ArrayList<>();
    private final List<TagKey<Block>> blockTags = new ArrayList<>();
    private final Map<String, List<String>> state = new HashMap<>();
    public final float minSpeed;
    public final boolean scaleWithSpeed;
    public final int interval;
    public final List<ResourceLocation> processing = new ArrayList<>();
    private final List<ResourceLocation> itemIds = new ArrayList<>();
    private final List<TagKey<Item>> itemTags = new ArrayList<>();
    public final float chance;
    public final float flux;
    public final float spillChance;
    public final boolean effect;

    public EmissionRule(ResourceLocation id, JsonObject json) {
        this.id = id;
        this.type = "fan_processing".equals(json.get("type").getAsString()) ? Type.FAN_PROCESSING : Type.BLOCK;
        for (String entry : strings(json.get("blocks"))) {
            if (entry.startsWith("#")) {
                blockTags.add(TagKey.create(Registries.BLOCK, ResourceLocation.parse(entry.substring(1))));
            } else {
                blockIds.add(ResourceLocation.parse(entry));
            }
        }
        if (json.has("state")) {
            json.getAsJsonObject("state").entrySet().forEach(e -> state.put(e.getKey(), strings(e.getValue())));
        }
        minSpeed = json.has("min_speed") ? json.get("min_speed").getAsFloat() : 0;
        scaleWithSpeed = json.has("scale_with_speed") && json.get("scale_with_speed").getAsBoolean();
        interval = json.has("interval") ? Math.max(1, json.get("interval").getAsInt()) : 100;
        strings(json.get("processing")).forEach(p -> processing.add(ResourceLocation.parse(p)));
        for (String entry : strings(json.get("items"))) {
            if (entry.startsWith("#")) {
                itemTags.add(TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.substring(1))));
            } else {
                itemIds.add(ResourceLocation.parse(entry));
            }
        }
        chance = json.has("chance") ? json.get("chance").getAsFloat() : 1;
        flux = json.get("flux").getAsFloat();
        spillChance = json.has("spill_chance") ? json.get("spill_chance").getAsFloat() : 0;
        effect = !json.has("effect") || json.get("effect").getAsBoolean();
    }

    private static List<String> strings(JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element == null) {
            return out;
        }
        if (element.isJsonArray()) {
            for (JsonElement e : (JsonArray) element) {
                out.add(e.getAsString());
            }
        } else {
            out.add(element.getAsString());
        }
        return out;
    }

    /** The blocks this rule names directly; tagged ones are checked with {@link #matchesBlock}. */
    public List<Block> directBlocks() {
        List<Block> out = new ArrayList<>();
        blockIds.forEach(id -> BuiltInRegistries.BLOCK.getOptional(id).ifPresent(out::add));
        return out;
    }

    public boolean hasBlockTags() {
        return !blockTags.isEmpty();
    }

    public boolean matchesBlock(BlockState block) {
        if (blockIds.contains(BuiltInRegistries.BLOCK.getKey(block.getBlock()))) {
            return true;
        }
        for (TagKey<Block> tag : blockTags) {
            if (block.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the block's state has every property value this rule asks for. */
    public boolean matchesState(BlockState block) {
        for (Map.Entry<String, List<String>> e : state.entrySet()) {
            Property<?> property = block.getBlock().getStateDefinition().getProperty(e.getKey());
            if (property == null || !e.getValue().contains(valueName(block, property))) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> String valueName(BlockState block, Property<T> property) {
        return property.getName(block.getValue(property));
    }

    public boolean matchesItem(ItemStack stack) {
        if (itemIds.isEmpty() && itemTags.isEmpty()) {
            return true;
        }
        if (itemIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return true;
        }
        for (TagKey<Item> tag : itemTags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
