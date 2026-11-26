package net.createthaumaresearch;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Create: Thauma Research. A Thaumonomicon chapter, Mechanoturgy, whose research (plain Thaumaturge data) opens Create's machines:
 * until a player has completed the research that opens an item, the crafting grid gives nothing for it and its casing cannot be
 * applied to a log. Which research opens which item is {@code data/createthaumaresearch/research_locks.json}, written with the
 * chapter by {@code tools/gen.py}.
 */
@Mod(CreateThaumaResearch.MOD_ID)
public final class CreateThaumaResearch {
    public static final String MOD_ID = "createthaumaresearch";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Item, ResourceLocation> LOCKS = new HashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> RAW = new HashMap<>();
    private static boolean resolved;
    private static Method knowledgeOf;
    private static Method isResearchComplete;
    private static boolean reflectionFailed;

    public CreateThaumaResearch(IEventBus modBus) {
        try (InputStream in = CreateThaumaResearch.class.getResourceAsStream("/data/" + MOD_ID + "/research_locks.json")) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            json.entrySet().forEach(e -> RAW.put(ResourceLocation.parse(e.getKey()), ResourceLocation.parse(e.getValue().getAsString())));
        } catch (Exception e) {
            LOGGER.error("Create: Thauma Research could not read its research locks", e);
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.RightClickBlock.class, CreateThaumaResearch::onUseOnBlock);
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, CreateThaumaResearch::onTooltip);
    }

    /** The research that opens this item, or null if it is free. */
    public static ResourceLocation lockOf(ItemStack stack) {
        if (!resolved) {
            RAW.forEach((item, research) -> BuiltInRegistries.ITEM.getOptional(item).ifPresent(found -> LOCKS.put(found, research)));
            resolved = true;
        }
        return stack.isEmpty() ? null : LOCKS.get(stack.getItem());
    }

    /** Whether the player may make this item: it is free, or they have completed its research. */
    public static boolean mayMake(Player player, ItemStack stack) {
        ResourceLocation research = lockOf(stack);
        return research == null || player == null || completed(player, research);
    }

    /** Thaumaturge's own record of the player's research, reached by name (this mod compiles without Thaumaturge). */
    public static boolean completed(Player player, ResourceLocation research) {
        if (player == null || research == null) {
            return false;
        }

        if (knowledgeOf == null && !reflectionFailed) {
            try {
                Class<?> knowledgeAccess = Class.forName("com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess");
                knowledgeOf = knowledgeAccess.getMethod("of", Player.class);

                for (Method method : knowledgeOf.getReturnType().getMethods()) {
                    if (method.getName().equals("isResearchComplete") && method.getParameterCount() == 1) {
                        isResearchComplete = method;
                        break;
                    }
                }
                if (isResearchComplete == null) {
                    throw new NoSuchMethodException("IPlayerKnowledge.isResearchComplete");
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                reflectionFailed = true;
                LOGGER.error("Create: Thauma Research could not access Thaumaturge research knowledge", e);
            }
        }

        if (reflectionFailed || knowledgeOf == null || isResearchComplete == null) {
            return false;
        }

        try {
            Object knowledge = knowledgeOf.invoke(null, player);
            Object researchId = convertResearchId(isResearchComplete.getParameterTypes()[0], research);
            return (boolean) isResearchComplete.invoke(knowledge, researchId);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Create: Thauma Research failed to query completed research: {}", research, e);
            return false;
        }
    }

    private static Object convertResearchId(Class<?> targetType, ResourceLocation research) throws ReflectiveOperationException {
        if (targetType.isInstance(research)) {
            return research;
        }

        try {
            return targetType.getMethod("parse", String.class).invoke(null, research.toString());
        } catch (NoSuchMethodException ignored) {
        }

        try {
            return targetType.getMethod("fromNamespaceAndPath", String.class, String.class)
                    .invoke(null, research.getNamespace(), research.getPath());
        } catch (NoSuchMethodException ignored) {
        }

        try {
            return targetType.getConstructor(String.class, String.class)
                    .newInstance(research.getNamespace(), research.getPath());
        } catch (NoSuchMethodException e) {
            throw new ReflectiveOperationException("No compatible research identifier constructor or parser for " + targetType.getName(), e);
        }
    }

    public static Component researchName(ResourceLocation research) {
        return Component.translatable("research." + research.getNamespace() + "." + research.getPath() + ".title");
    }

    /** Create's casings are made by using an alloy or ingot on a log (item application): refused while the casing is locked. */
    private static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (held.isEmpty()) {
            return;
        }
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.fromNamespaceAndPath("create", "item_application"));
        if (type == null) {
            return;
        }
        ItemStack block = new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock().asItem());
        for (RecipeHolder<?> holder : event.getLevel().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.getType() != type || recipe.getIngredients().size() < 2) {
                continue;
            }
            Ingredient target = recipe.getIngredients().get(0);
            Ingredient applied = recipe.getIngredients().get(1);
            if (!target.test(block) || !applied.test(held)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(event.getLevel().registryAccess());
            if (!mayMake(player, result)) {
                player.displayClientMessage(Component.translatable("tooltip." + MOD_ID + ".locked", researchName(lockOf(result)))
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                event.setCanceled(true);
                return;
            }
        }
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ResourceLocation research = lockOf(event.getItemStack());
        if (research != null && event.getEntity() != null && !completed(event.getEntity(), research)) {
            event.getToolTip().add(Component.translatable("tooltip." + MOD_ID + ".locked", researchName(research))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
