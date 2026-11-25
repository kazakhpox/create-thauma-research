package net.createthaumaresearch.devtools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Dev only: at server start, the Mechanoturgy chapter and every one of its research entries must have loaded into Thaumaturge's
 * registries, and every recipe and parent they name must exist; then the server stops.
 */
@Mod("createthaumaresearch_devtools")
public final class RecipeCheck {
    private static final String NS = "createthaumaresearch";

    public RecipeCheck() {
        if (System.getProperty("createthaumaresearch.check") == null) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> {
            var server = event.getServer();
            List<String> problems = new ArrayList<>();
            Registry<Object> categories = registry(server, "research_category");
            Registry<Object> entries = registry(server, "research_entry");
            if (categories == null || entries == null) {
                problems.add("no research registries");
            } else {
                if (!categories.containsKey(ResourceLocation.fromNamespaceAndPath(NS, "mechanoturgy"))) {
                    problems.add("category missing");
                }
                int ours = 0;
                for (ResourceLocation id : entries.keySet()) {
                    if (!id.getNamespace().equals(NS)) {
                        continue;
                    }
                    ours++;
                    var file = ResourceLocation.fromNamespaceAndPath(NS, "thaumaturge/research_entry/" + id.getPath() + ".json");
                    try (var in = server.getResourceManager().getResourceOrThrow(file).open()) {
                        var json = JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
                        for (JsonElement parent : json.getAsJsonArray("parents")) {
                            if (!entries.containsKey(ResourceLocation.parse(parent.getAsString()))) {
                                problems.add(id + ": parent " + parent.getAsString());
                            }
                        }
                        for (JsonElement stage : json.getAsJsonArray("stages")) {
                            var recipes = stage.getAsJsonObject().getAsJsonArray("recipes");
                            if (recipes == null) {
                                continue;
                            }
                            for (JsonElement recipe : recipes) {
                                if (server.getRecipeManager().byKey(ResourceLocation.parse(recipe.getAsString())).isEmpty()) {
                                    problems.add(id + ": recipe " + recipe.getAsString());
                                }
                            }
                        }
                    } catch (Exception e) {
                        problems.add(id + ": " + e);
                    }
                }
                System.out.println("[createthaumaresearch.check] research entries loaded: " + ours);
            }
            System.out.println("[createthaumaresearch.check] problems " + problems.size() + " " + problems);
            server.halt(false);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<Object> registry(net.minecraft.server.MinecraftServer server, String name) {
        ResourceKey key = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("thaumaturge", name));
        return (Registry<Object>) server.registryAccess().registry(key).orElse(null);
    }
}
