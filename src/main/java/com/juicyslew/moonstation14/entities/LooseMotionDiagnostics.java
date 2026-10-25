package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Bounded, observational diagnostics for recently spawned loose objects. */
public final class LooseMotionDiagnostics {
    private static final int CAPACITY = 32;
    private static final long STALL_TICKS = 10;
    private static final ArrayDeque<Watch> WATCHES = new ArrayDeque<>();

    private LooseMotionDiagnostics() { }

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(LooseMotionDiagnostics::onJoin);
        bus.addListener(LooseMotionDiagnostics::onServerTick);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof LooseItemEntity item)) return;
        WATCHES.removeIf(watch -> watch.entity == item);
        if (WATCHES.size() == CAPACITY) WATCHES.removeFirst();
        WATCHES.addLast(new Watch(level, item, level.getGameTime(), item.position()));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Watch> iterator = WATCHES.iterator();
        while (iterator.hasNext()) {
            Watch watch = iterator.next();
            LooseItemEntity item = watch.entity;
            if (item.isRemoved() || item.level() != watch.level || !watch.level.isLoaded(item.blockPosition())) {
                iterator.remove();
                continue;
            }
            long now = watch.level.getGameTime();
            if (now == watch.lastObservedTick) continue;
            watch.lastObservedTick = now;
            double moved = item.position().distanceToSqr(watch.lastPosition);
            if (moved > 1.0e-8) {
                if (watch.stallSince >= 0) {
                    MoonStation14.LOGGER.info("Loose motion resumed uuid={} stalledTicks={} pos={} velocity={} tickCount={}",
                            item.getUUID(), now - watch.stallSince, item.position(), item.getDeltaMovement(), item.tickCount);
                    watch.stallSince = -1;
                }
                watch.lastPosition = item.position();
                watch.stillSince = now;
                continue;
            }
            watch.lastPosition = item.position();
            boolean shouldMove = !item.settled() && item.getDeltaMovement().lengthSqr() > 1.0e-6;
            if (!shouldMove) { watch.stillSince = now; continue; }
            if (now - watch.stillSince < STALL_TICKS) continue;
            if (watch.stallSince < 0) {
                watch.stallSince = now;
                BlockPos pos = item.blockPosition();
                MoonStation14.LOGGER.warn("Loose motion stalled uuid={} pos={} velocity={} tickCount={} settled={} unloadedSweepBlocked={} chunkPresent={} entityTickEligible={}",
                        item.getUUID(), item.position(), item.getDeltaMovement(), item.tickCount, item.settled(),
                        item.motionBlockedByUnloaded(), watch.level.hasChunkAt(pos), watch.level.isPositionEntityTicking(pos));
            }
        }
    }

    private static final class Watch {
        final ServerLevel level;
        final LooseItemEntity entity;
        long lastObservedTick;
        long stillSince;
        long stallSince = -1;
        net.minecraft.world.phys.Vec3 lastPosition;

        Watch(ServerLevel level, LooseItemEntity entity, long tick, net.minecraft.world.phys.Vec3 pos) {
            this.level = level; this.entity = entity; this.lastObservedTick = tick;
            this.stillSince = tick; this.lastPosition = pos;
        }
    }
}

