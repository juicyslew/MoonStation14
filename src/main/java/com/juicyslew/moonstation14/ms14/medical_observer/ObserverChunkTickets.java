package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps the roaming observer camera's ordinary view area available without moving its player/body. */
@EventBusSubscriber
public final class ObserverChunkTickets {
    private static final TicketType<UUID> TICKET = TicketType.create("moonstation14_observer_view", UUID::compareTo);
    private static final Map<UUID, Ticket> OWNED = new HashMap<>();

    private ObserverChunkTickets() { }

    private record Ticket(ServerLevel level, ChunkPos center, int radius, UUID key) { }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var players = event.getServer().getPlayerList().getPlayers();
        Map<UUID, Ticket> wanted = new HashMap<>();
        for (ServerPlayer player : players) {
            ObserverSession session = ObserverSystem.query(player);
            if (session == null || session.mode() != ObserverSession.Mode.GHOST
                    || !session.ownerId().equals(player.getUUID())
                    || !session.corpseBodyId().equals(player.getUUID())) continue;
            var ghost = player.serverLevel().getEntity(session.ghostId());
            if (ghost == null || ghost.isRemoved() || !ghost.getUUID().equals(session.ghostId())) continue;
            ChunkPos center = ghost.chunkPosition();
            int radius = Math.max(player.serverLevel().getServer().getPlayerList().getViewDistance(),
                    player.getChunkTrackingView() instanceof net.minecraft.server.level.ChunkTrackingView.Positioned view
                            ? view.viewDistance() : 0);
            UUID key = UUID.nameUUIDFromBytes((session.ownerId() + ":" + session.epoch() + ":" + session.ghostId())
                    .getBytes(StandardCharsets.UTF_8));
            wanted.put(player.getUUID(), new Ticket(player.serverLevel(), center, radius, key));
        }

        for (var entry : Map.copyOf(OWNED).entrySet()) {
            Ticket next = wanted.get(entry.getKey()), old = entry.getValue();
            if (next == null || old.level() != next.level() || !old.center().equals(next.center())
                    || old.radius() != next.radius() || !old.key().equals(next.key())) remove(old);
        }
        OWNED.entrySet().removeIf(entry -> !wanted.containsKey(entry.getKey()));
        for (var entry : wanted.entrySet()) {
            Ticket next = entry.getValue(), old = OWNED.get(entry.getKey());
            if (old == null || old.level() != next.level() || !old.center().equals(next.center())
                    || old.radius() != next.radius() || !old.key().equals(next.key())) {
                next.level().getChunkSource().addRegionTicket(TICKET, next.center(), next.radius(), next.key());
                OWNED.put(entry.getKey(), next);
            }
        }
    }

    private static void remove(Ticket ticket) {
        ticket.level().getChunkSource().removeRegionTicket(TICKET, ticket.center(), ticket.radius(), ticket.key());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        OWNED.values().forEach(ObserverChunkTickets::remove);
        OWNED.clear();
    }
}
