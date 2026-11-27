package net.createthaumaresearch.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One research gate, from a datapack file {@code data/<namespace>/createthaumaresearch/research_gates/<name>.json}: the recipes it
 * locks, and the research that opens them, in the same form as the {@code "research"} of Thaumaturge's own recipes.
 *
 * <pre>
 * {
 *   "research": {"entry": "createthaumaresearch:kinetics", "stage": 1, "negate": false},  // stage and negate optional
 *   "recipes": ["create:crafting/kinetics/shaft"],          // recipe ids
 *   "outputs": ["create:cogwheel", "#c:gears"],             // every recipe that makes one of these items (or tags)
 *   "recipe_types": ["minecraft:smelting"],                 // every recipe of these types
 *   "namespaces": ["create"],                               // every recipe whose id is in these namespaces
 *   "except": ["create:crafting/kinetics/shaft_from_zinc"]  // recipes left free even if the rest matches
 * }
 * </pre>
 *
 * A recipe is locked by a gate if it matches any of {@code recipes}, {@code outputs}, {@code recipe_types} or {@code namespaces}
 * and is not in {@code except}. Lists may be single strings. A recipe under several gates needs all of them.
 */
public final class Gate {
    public final ResourceLocation id;
    public final ResourceLocation entry;
    public final Optional<Integer> stage;
    public final boolean negate;
    final List<ResourceLocation> recipes = new ArrayList<>();
    final List<ResourceLocation> outputItems = new ArrayList<>();
    final List<TagKey<Item>> outputTags = new ArrayList<>();
    final List<ResourceLocation> types = new ArrayList<>();
    final List<String> namespaces = new ArrayList<>();
    final List<ResourceLocation> except = new ArrayList<>();

    public Gate(ResourceLocation id, JsonObject json) {
        this.id = id;
        JsonObject research = json.getAsJsonObject("research");
        entry = ResourceLocation.parse(research.get("entry").getAsString());
        stage = research.has("stage") ? Optional.of(research.get("stage").getAsInt()) : Optional.empty();
        negate = research.has("negate") && research.get("negate").getAsBoolean();
        strings(json.get("recipes")).forEach(s -> recipes.add(ResourceLocation.parse(s)));
        for (String s : strings(json.get("outputs"))) {
            if (s.startsWith("#")) {
                outputTags.add(TagKey.create(Registries.ITEM, ResourceLocation.parse(s.substring(1))));
            } else {
                outputItems.add(ResourceLocation.parse(s));
            }
        }
        strings(json.get("recipe_types")).forEach(s -> types.add(ResourceLocation.parse(s)));
        namespaces.addAll(strings(json.get("namespaces")));
        strings(json.get("except")).forEach(s -> except.add(ResourceLocation.parse(s)));
    }

    static List<String> strings(JsonElement element) {
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

    /** Whether this gate locks the recipe; {@code output} is what it makes (may be empty if unknown). */
    public boolean locks(RecipeHolder<?> holder, ItemStack output) {
        if (except.contains(holder.id())) {
            return false;
        }
        if (recipes.contains(holder.id()) || namespaces.contains(holder.id().getNamespace())) {
            return true;
        }
        if (types.contains(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()))) {
            return true;
        }
        return locksItem(output);
    }

    /** Whether this gate locks the recipes making this item. */
    public boolean locksItem(ItemStack output) {
        if (output.isEmpty()) {
            return false;
        }
        if (outputItems.contains(BuiltInRegistries.ITEM.getKey(output.getItem()))) {
            return true;
        }
        for (TagKey<Item> tag : outputTags) {
            if (output.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
