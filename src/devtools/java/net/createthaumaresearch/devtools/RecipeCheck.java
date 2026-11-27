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
            emissionTest(server);
        });
    }

    private static int ticks;

    /**
     * Machines in three chunks by spawn: a seething blaze burner, a creative motor (a test rule with min_speed), and an encased fan
     * blowing through water onto crushed iron. After a while the flux of each chunk's aura must have risen.
     */
    private static void emissionTest(net.minecraft.server.MinecraftServer server) {
        var level = server.overworld();
        var spawn = level.getSharedSpawnPos();
        net.minecraft.core.BlockPos[] places = new net.minecraft.core.BlockPos[3];
        float[] before = new float[3];
        for (int i = 0; i < 3; i++) {
            var base = spawn.offset(-16 + 16 * i, 0, 0);
            base = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, base).above(3);
            level.setChunkForced(base.getX() >> 4, base.getZ() >> 4, true);
            places[i] = base;
        }
        var burner = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:blaze_burner")).defaultBlockState();
        var heat = burner.getBlock().getStateDefinition().getProperty("blaze");
        burner = setNamed(burner, heat, "seething");
        level.setBlockAndUpdate(places[0], burner);
        try {
            // A blaze cake: the burner turns seething, as a player feeding it would make it.
            var entity = level.getBlockEntity(places[0]);
            var feed = entity.getClass().getDeclaredMethod("tryUpdateFuel", net.minecraft.world.item.ItemStack.class, boolean.class, boolean.class);
            feed.setAccessible(true);
            System.out.println("[createthaumaresearch.check] blaze cake fed: " + feed.invoke(entity, new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:blaze_cake"))), false, false)
                    + ", " + level.getBlockState(places[0]));
        } catch (ReflectiveOperationException e) {
            System.out.println("[createthaumaresearch.check] " + e);
        }
        var motor = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:creative_motor")).defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST);
        level.setBlockAndUpdate(places[1], motor);
        level.setBlockAndUpdate(places[1].east(), net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:shaft"))
                .defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
        var fanMotor = places[2];
        var fan = fanMotor.east();
        level.setBlockAndUpdate(fanMotor, motor);
        level.setBlockAndUpdate(fan, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:encased_fan"))
                .defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST));
        level.setBlockAndUpdate(fan.east(), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        for (int i = 1; i < 6; i++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(fan.east(i).below().south(dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
        }
        for (int i = 0; i < 3; i++) {
            before[i] = flux(level, places[i]);
        }
        craftTest(level);
        System.out.println("[createthaumaresearch.check] emission test started at " + places[0] + ", " + places[1] + ", " + places[2]);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, event -> {
            ticks++;
            if (ticks % 40 == 0 && ticks <= 400) {
                var crushed = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.parse("create:crushed_raw_iron")), 4);
                var entity = new net.minecraft.world.entity.item.ItemEntity(level, fan.getX() + 2.5, fan.getY() + 0.2, fan.getZ() + 0.5, crushed);
                entity.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(entity);
            }
            if (ticks == 600) {
                String[] names = {"seething blaze burner", "turning shaft (test rule)", "fan washing crushed iron"};
                for (int i = 0; i < 3; i++) {
                    System.out.println("[createthaumaresearch.check] flux " + names[i] + ": " + before[i] + " -> " + flux(level, places[i]));
                }
                server.halt(false);
            }
        });
    }

    /** A player without research crafts sticks (free) and then a shaft (locked) in a crafting table. */
    private static void craftTest(net.minecraft.server.level.ServerLevel level) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var control = new net.minecraft.world.inventory.CraftingMenu(0, player.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(level, player.blockPosition()));
        control.getSlot(2).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        control.getSlot(5).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        control.slotsChanged(null);
        System.out.println("[createthaumaresearch.check] crafting sticks (free, the same grid): " + control.getSlot(0).getItem());
        var menu = new net.minecraft.world.inventory.CraftingMenu(0, player.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(level, player.blockPosition()));
        var alloy = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:andesite_alloy"));
        menu.getSlot(2).set(new net.minecraft.world.item.ItemStack(alloy));
        menu.getSlot(5).set(new net.minecraft.world.item.ItemStack(alloy));
        menu.slotsChanged(null);
        System.out.println("[createthaumaresearch.check] crafting shaft without research: " + menu.getSlot(0).getItem());
    }

    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState setNamed(
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property, String name) {
        return state.setValue(property, property.getValue(name).orElseThrow());
    }

    private static float flux(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        try {
            return (float) Class.forName("com.leclowndu93150.thaumaturge.api.aura.AuraHelper")
                    .getMethod("getFlux", net.minecraft.world.level.Level.class, net.minecraft.core.BlockPos.class).invoke(null, level, pos);
        } catch (ReflectiveOperationException e) {
            return Float.NaN;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<Object> registry(net.minecraft.server.MinecraftServer server, String name) {
        ResourceKey key = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("thaumaturge", name));
        return (Registry<Object>) server.registryAccess().registry(key).orElse(null);
    }
}
