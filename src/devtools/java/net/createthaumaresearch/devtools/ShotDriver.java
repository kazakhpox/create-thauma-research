package net.createthaumaresearch.devtools;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;



/**
 * Builds a flat test world, dresses the player in Knights & Heraldry armour and looks at their first-person arms (Punchy!), saving screenshots along the way to the folder in the system property {@code handson.shots}. Then quits.
 */
@Mod(value = "createthaumaresearch_devtools", dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ShotDriver {
    private static final String WORLD = "handson_shots";
    
    private final Deque<Step> steps = new ArrayDeque<>();
    private int wait;
    
    private record Step(String name, BooleanSupplier until, Runnable action) {
    }

    public ShotDriver(IEventBus modBus) {
        if (System.getProperty("handson.shots") == null) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        plan();
    }

    private void then(String name, int ticks, Runnable action) {
        steps.add(new Step(name, () -> true, action));
        steps.add(new Step(name + " wait", null, () -> wait = ticks));
    }

    private void when(String name, BooleanSupplier until, Runnable action) {
        steps.add(new Step(name, until, action));
    }

    private static void command(ServerPlayer player, String command) {
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
    }

    /** Lays two andesite alloys in a crafting table's grid (the shaft recipe) and says what the result slot holds. */
    private static void craftShaft(ServerPlayer player, String when) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> new net.minecraft.world.inventory.CraftingMenu(id,
                inventory, net.minecraft.world.inventory.ContainerLevelAccess.create(p.level(), p.blockPosition())),
                net.minecraft.network.chat.Component.literal("test")));
        var menu = player.containerMenu;
        menu.getSlot(2).set(item("create:andesite_alloy"));
        menu.getSlot(5).set(item("create:andesite_alloy"));
        menu.slotsChanged(null);
        System.out.println("[shots] crafting shaft " + when + ": " + menu.getSlot(0).getItem());
    }

    /** Uses andesite alloy on a stripped log (Create's andesite casing) and says what the block became. */
    private static void applyCasing(ServerPlayer player, String when) {
        ServerLevel level = player.serverLevel();
        BlockPos at = new BlockPos(0, -60, 2);
        level.setBlockAndUpdate(at, block("minecraft:stripped_oak_log"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item("create:andesite_alloy"));
        var hit = new net.minecraft.world.phys.BlockHitResult(new Vec3(0.5, -59, 2.5), Direction.UP, at, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        System.out.println("[shots] casing " + when + ": " + level.getBlockState(at).getBlock());
    }

    /** Lays two andesite alloys (the shaft recipe) in the grid of the crafting table the player has open, as a player would. */
    private static void fillGrid(ServerPlayer player) {
        var menu = player.containerMenu;
        System.out.println("[shots] open menu on the server: " + menu.getClass().getSimpleName());
        menu.getSlot(2).set(item("create:andesite_alloy"));
        menu.getSlot(5).set(item("create:andesite_alloy"));
        menu.slotsChanged(null);
        menu.broadcastChanges();
        System.out.println("[shots] server result slot: " + menu.getSlot(0).getItem());
    }

    private static void clientResult(String when) {
        var mc = Minecraft.getInstance();
        System.out.println("[shots] client screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                + ", result slot " + when + ": " + mc.player.containerMenu.getSlot(0).getItem());
    }

    private void plan() {
        when("title", () -> Minecraft.getInstance().screen instanceof TitleScreen, this::createWorld);
        when("in world", () -> Minecraft.getInstance().player != null && Minecraft.getInstance().getSingleplayerServer() != null
                && Minecraft.getInstance().screen == null, () -> wait = 80);
        then("table", 40, () -> server(player -> {
            player.serverLevel().setBlockAndUpdate(new BlockPos(0, -60, 2), block("minecraft:crafting_table"));
            player.getInventory().clearContent();
            player.teleportTo(player.serverLevel(), 0.5, -60, 0.3, 0.0F, 35.0F);
        }));
        then("open table", 30, ShotDriver::use);
        then("fill", 20, () -> server(ShotDriver::fillGrid));
        then("locked client", 1, () -> clientResult("before research"));
        then("locked shot", 5, () -> shot("00_crafting_before_research"));
        then("close", 20, () -> server(player -> {
            player.closeContainer();
            player.getInventory().clearContent();
            command(player, "thaumaturge research @a everything");
        }));
        then("reopen", 60, ShotDriver::use);
        then("refill", 20, () -> server(ShotDriver::fillGrid));
        then("unlocked client", 1, () -> clientResult("after research"));
        then("unlocked shot", 5, () -> shot("01_crafting_after_research"));
        then("quit", 20, () -> {
            server(player -> {
                player.closeContainer();
                player.getInventory().clearContent();
            });
        });
        then("stop", 5, () -> Minecraft.getInstance().stop());
    }

    private static ItemStack item(String id) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)));
    }

    private static BlockState block(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(id)).defaultBlockState();
    }

    private void tick() {
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = steps.peek();
        if (step == null) {
            return;
        }
        if (step.until() == null) {
            steps.poll();
            step.action().run();
            return;
        }
        if (step.until().getAsBoolean()) {
            steps.poll();
            System.out.println("[shots] " + step.name());
            step.action().run();
        }
    }

    private void createWorld() {
        Minecraft mc = Minecraft.getInstance();
        Path saves = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        try {
            if (Files.exists(saves)) {
                try (var walk = Files.walk(saves)) {
                    walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(), mc.screen);
    }

    /** A right click at what the crosshair is on, with an empty hand: the player uses the anvil as anybody would. */
    private static void use() {
        Minecraft mc = Minecraft.getInstance();
        mc.player.getInventory().selected = 4;
        System.out.println("[shots] looking at " + mc.hitResult);
        try {
            Method method = Minecraft.class.getDeclaredMethod("startUseItem");
            method.setAccessible(true);
            method.invoke(mc);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void server(java.util.function.Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        File dir = new File(System.getProperty("handson.shots"));
        dir.mkdirs();
        Screenshot.grab(dir, name + ".png", mc.getMainRenderTarget(), message -> System.out.println("[shots] " + message.getString()));
    }
}
