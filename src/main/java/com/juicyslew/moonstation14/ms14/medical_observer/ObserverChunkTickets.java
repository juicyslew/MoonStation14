package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import com.juicyslew.moonstation14.MoonStation14;
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

    static boolean sameTicket(ChunkPos oldCenter, int oldRadius, UUID oldKey,
                              ChunkPos nextCenter, int nextRadius, UUID nextKey) {
        return oldCenter.equals(nextCenter) && oldRadius == nextRadius && oldKey.equals(nextKey);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var players = event.getServer().getPlayerList().getPlayers();
        Map<UUID, Ticket> wanted = new HashMap<>();
        for (ServerPlayer player : players) {
            var target = ControlledViewTarget.resolve(player);
            if (target == null) continue;
            var viewEntity = target.entity();
            ChunkPos center = new ChunkPos(net.minecraft.core.BlockPos.containing(viewEntity.position()));
            int radius = Math.max(player.serverLevel().getServer().getPlayerList().getViewDistance(),
                    player.getChunkTrackingView() instanceof net.minecraft.server.level.ChunkTrackingView.Positioned view
                            ? view.viewDistance() : 0);
            String owner = player.getUUID() + ":" + target.family() + ":" + target.epoch();
            UUID key = UUID.nameUUIDFromBytes((owner + ":" + viewEntity.getUUID())
                    .getBytes(StandardCharsets.UTF_8));
            wanted.put(player.getUUID(), new Ticket(player.serverLevel(), center, radius, key));
        }

        for (var entry : Map.copyOf(OWNED).entrySet()) {
            Ticket next = wanted.get(entry.getKey()), old = entry.getValue();
            if (next == null || old.level() != next.level() || !sameTicket(old.center(), old.radius(), old.key(),
                    next.center(), next.radius(), next.key())) remove(old);
        }
        OWNED.entrySet().removeIf(entry -> !wanted.containsKey(entry.getKey()));
        for (var entry : wanted.entrySet()) {
            Ticket next = entry.getValue(), old = OWNED.get(entry.getKey());
            if (old == null || old.level() != next.level() || !sameTicket(old.center(), old.radius(), old.key(),
                    next.center(), next.radius(), next.key())) {
                MoonStation14.LOGGER.info("[controlled view] ticket owner={} center={} radius={} key={}",
                        entry.getKey(), next.center(), next.radius(), next.key());
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
