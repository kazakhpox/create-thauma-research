package net.createthaumaresearch.gate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


import net.createthaumaresearch.CreateThaumaResearch;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Who a machine belongs to: the player who placed it, kept on its block entity. A machine asks gates about its owner: while the
 * owner is online, Thaumaturge is asked; while they are away, the answer from when they were last seen is used (closed if never).
 */
public final class Owners {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CreateThaumaResearch.MOD_ID);
    public static final Supplier<AttachmentType<Optional<UUID>>> OWNER = ATTACHMENTS.register("owner",
            () -> AttachmentType.builder(() -> Optional.<UUID>empty())
                    .serialize(UUIDUtil.CODEC.optionalFieldOf("owner").codec()).build());
    /** Owner -> gate -> passed, as last seen. */
    private static final Map<UUID, Map<net.minecraft.resources.ResourceLocation, Boolean>> SEEN = new ConcurrentHashMap<>();

    private Owners() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class,
                event -> remember(event.getEntity()));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                event -> remember(event.getEntity()));
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, event -> {
            if (event.getEntity() instanceof Player player && !event.getLevel().isClientSide()) {
                BlockEntity entity = event.getLevel().getBlockEntity(event.getPos());
                if (entity != null) {
                    entity.setData(OWNER.get(), Optional.of(player.getUUID()));
                    entity.setChanged();
                }
            }
        });
    }

    /** A player leaving: their answer to every gate is kept for their machines while they are away. */
    static void remember(Player player) {
        Map<net.minecraft.resources.ResourceLocation, Boolean> answers = SEEN.computeIfAbsent(player.getUUID(), o -> new ConcurrentHashMap<>());
        for (Gate gate : ResearchGates.gates()) {
            answers.put(gate.id, ResearchGates.passes(player, gate));
        }
    }

    public static UUID ownerOf(BlockEntity entity) {
        return entity == null ? null : entity.getData(OWNER.get()).orElse(null);
    }

    public static void setOwner(BlockEntity entity, UUID owner) {
        entity.setData(OWNER.get(), Optional.ofNullable(owner));
        entity.setChanged();
    }

    static boolean passes(Level level, UUID owner, Gate gate) {
        Player player = level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            boolean passed = ResearchGates.passes(player, gate);
            SEEN.computeIfAbsent(owner, o -> new ConcurrentHashMap<>()).put(gate.id, passed);
            return passed;
        }
        return SEEN.getOrDefault(owner, Map.of()).getOrDefault(gate.id, false);
    }

}
