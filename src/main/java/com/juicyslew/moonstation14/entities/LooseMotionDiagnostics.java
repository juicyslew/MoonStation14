package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Bounded, observational diagnostics for recently spawned loose objects. */
public final class LooseMotionDiagnostics {
    private static final int CAPACITY = 32;
    private static final long STALL_TICKS = 10;
    private static final ArrayDeque<Watch> WATCHES = new ArrayDeque<>();
    static final int PERSISTENCE_CAPACITY = 768;
    private static volatile PersistenceWatch persistenceWatch;

    private LooseMotionDiagnostics() { }

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(LooseMotionDiagnostics::onJoin);
        bus.addListener(LooseMotionDiagnostics::onServerTick);
    }

    /** Exact-level, exact-UUID opt-in observer for natural local serialization callbacks. */
    public static PersistenceWatch watchPersistence(ServerLevel level, Collection<UUID> uuids) {
        return installPersistenceWatch(level, uuids);
    }
    private static PersistenceWatch installPersistenceWatch(Object scope, Collection<UUID> uuids) {
        PersistenceWatch watch = new PersistenceWatch(scope, uuids);
        synchronized (LooseMotionDiagnostics.class) {
            if (persistenceWatch != null) throw new IllegalStateException("natural persistence watch already active");
            persistenceWatch = watch;
        }
        return watch;
    }
    static PersistenceWatch watchPersistenceForTest(Object exactScope, Collection<UUID> ids) {
        return installPersistenceWatch(exactScope, ids);
    }

    public static final class PersistenceWatch implements AutoCloseable {
        public enum Phase { SAVE, READ, JOIN, POST_JOIN, IMPACT_GATE, SPILL }
        public record Event(long sequence, long monotonicNanos, UUID uuid, Phase phase,
                            String position, String velocity, String fields, boolean nonempty,
                            boolean removed, String removalReason) { }
        public record Counts(int saves, int reads, int joins, int postJoin, int impacts, int spills) { }
        public record Snapshot(Set<UUID> watched, List<Event> events, Map<UUID, Counts> counts,
                               boolean overflow, boolean fieldCaptureIncomplete, boolean postJoinCoverageLost,
                               List<String> coverageReasons, Map<UUID, String> postJoinEnd, boolean closed) {
            public boolean coverageComplete() { return !overflow && !fieldCaptureIncomplete && !postJoinCoverageLost; }
        }
        private Object levelScope;
        private final Set<UUID> ids;
        private final ArrayList<Event> events = new ArrayList<>();
        private final Set<UUID> joined = new HashSet<>();
        private long sequence;
        private boolean overflow;
        private boolean fieldCaptureIncomplete;
        private boolean postJoinCoverageLost;
        private boolean closed;
        private final ArrayList<String> coverageReasons = new ArrayList<>();
        private final Map<UUID, String> postJoinEnd = new LinkedHashMap<>();
        private PersistenceWatch(Object level, Collection<UUID> uuids) {
            levelScope = Objects.requireNonNull(level);
            if (uuids == null || uuids.isEmpty() || uuids.size() > 3 || uuids.stream().anyMatch(Objects::isNull)
                    || new HashSet<>(uuids).size() != uuids.size()) throw new IllegalArgumentException("watch requires 1..3 unique UUIDs");
            ids = Set.copyOf(uuids);
        }
        private synchronized boolean accepts(Object level, UUID id) {
            return !closed && level == levelScope && ids.contains(id);
        }
        synchronized boolean capture(Object level, UUID id, Phase phase, String pos, String velocity,
                                         CompoundTag supplied, boolean nonempty, boolean removed, String reason) {
            if (closed || level != levelScope || !ids.contains(id) || phase == null) return false;
            if (phase == Phase.JOIN) joined.add(id);
            if (events.size() == PERSISTENCE_CAPACITY) { overflow = true; return true; }
            FieldCapture capture;
            try { capture = phase == Phase.JOIN ? new FieldCapture("tag=NOT_APPLICABLE", true) : fields(supplied); }
            catch (RuntimeException malformed) { capture = new FieldCapture("capture-error=" + malformed.getClass().getSimpleName(), false); }
            fieldCaptureIncomplete |= !capture.complete();
            events.add(new Event(++sequence, System.nanoTime(), id, phase, pos, velocity, capture.value(), nonempty, removed, reason));
            return true;
        }
        synchronized boolean captureObservation(Object level, UUID id, Phase phase, String pos, String velocity,
                                             String observation, boolean nonempty, boolean removed, String reason) {
            if (closed || level != levelScope || !ids.contains(id)
                    || phase == Phase.POST_JOIN && !joined.contains(id)) return false;
            if (events.size() == PERSISTENCE_CAPACITY) { overflow = true; return false; }
            String copied = observation == null ? "MISSING" : observation;
            boolean complete = copied.length() <= 2048;
            if (!complete) copied = copied.substring(0, 2048) + "<TRUNCATED>";
            fieldCaptureIncomplete |= !complete;
            events.add(new Event(++sequence, System.nanoTime(), id, phase, pos, velocity, copied, nonempty, removed, reason));
            return true;
        }
        public synchronized Snapshot snapshot() {
            Map<UUID, int[]> counts = new LinkedHashMap<>();
            ids.forEach(id -> counts.put(id, new int[Phase.values().length]));
            for (Event event : events) counts.get(event.uuid())[event.phase().ordinal()]++;
            Map<UUID, Counts> immutableCounts = new LinkedHashMap<>();
            counts.forEach((id, c) -> immutableCounts.put(id, new Counts(c[0], c[1], c[2], c[3], c[4], c[5])));
            return new Snapshot(ids, List.copyOf(events), Map.copyOf(immutableCounts), overflow, fieldCaptureIncomplete,
                    postJoinCoverageLost, List.copyOf(coverageReasons), Map.copyOf(postJoinEnd), closed);
        }
        synchronized void finishPostJoin(UUID id, String reason, boolean coverageLost) {
            if (!ids.contains(id)) return;
            postJoinEnd.put(id, reason);
            if (coverageLost) markPostJoinCoverageLost(id, reason);
        }
        synchronized void markPostJoinCoverageLost(UUID id, String reason) {
            postJoinCoverageLost = true;
            if (coverageReasons.size() < 32) coverageReasons.add(id + ":" + reason);
        }
        @Override public void close() {
            synchronized (LooseMotionDiagnostics.class) { if (persistenceWatch == this) persistenceWatch = null; }
            synchronized (this) { closed = true; levelScope = null; }
        }
    }

    private record FieldCapture(String value, boolean complete) { }
    private static FieldCapture fields(CompoundTag tag) {
        if (tag == null) return new FieldCapture("tag=MISSING", true);
        StringBuilder out = new StringBuilder();
        boolean complete = true;
        for (String key : List.of("Pos", "Motion", "Item", "LooseSettled", "LooseFrameOrientation", "LooseFrameStoragePoint", com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchor.KEY)) {
            if (!tag.contains(key)) { out.append(key).append("=MISSING;"); continue; }
            String value;
            try { value = tag.get(key).toString(); } catch (RuntimeException bad) { value = "<capture-error:" + bad.getClass().getSimpleName() + ">"; complete = false; }
            if (value.length() > 2048) { value = value.substring(0, 2048) + "<TRUNCATED>"; complete = false; }
            out.append(key).append("[").append(tag.getTagType(key)).append("]=" ).append(value).append(';');
        }
        return new FieldCapture(out.toString(), complete);
    }

    static void observePersistence(LooseItemEntity entity, CompoundTag tag, PersistenceWatch.Phase phase) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        PersistenceWatch watch = persistenceWatch;
        if (watch != null && watch.accepts(level, entity.getUUID())) watch.capture(level, entity.getUUID(), phase,
                entity.position().toString(), entity.getDeltaMovement().toString(), tag, !entity.getItem().isEmpty(),
                entity.isRemoved(), String.valueOf(entity.getRemovalReason()));
    }

    static boolean persistenceObserves(LooseItemEntity entity) {
        PersistenceWatch watch = persistenceWatch;
        return watch != null && entity.level() instanceof ServerLevel level && watch.accepts(level, entity.getUUID());
    }

    static void observeImpact(LooseItemEntity entity, String witness) {
        observeLocal(entity, PersistenceWatch.Phase.IMPACT_GATE, witness);
    }

    static boolean impactEligible(double approachSpeed, double threshold, boolean breakOnImpact) {
        return breakOnImpact && approachSpeed >= threshold;
    }

    static void observeSpill(LooseItemEntity entity, String witness) {
        observeLocal(entity, PersistenceWatch.Phase.SPILL, witness);
    }

    private static void observeLocal(LooseItemEntity entity, PersistenceWatch.Phase phase, String witness) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        PersistenceWatch watch = persistenceWatch;
        if (watch != null && watch.accepts(level, entity.getUUID()))
            watch.captureObservation(level, entity.getUUID(), phase, entity.position().toString(),
                    entity.getDeltaMovement().toString(), witness, !entity.getItem().isEmpty(),
                    entity.isRemoved(), String.valueOf(entity.getRemovalReason()));
    }

    private static void observePostJoin(Watch motionWatch, LooseItemEntity item) {
        PersistenceWatch observer = motionWatch.persistenceWatch;
        if (observer == null || !observer.accepts(motionWatch.level, item.getUUID())) {
            motionWatch.finishPostJoin("registration-closed-during-post-join", true);
            motionWatch.postJoinFinished = true;
            return;
        }
        try {
            ServerLevel level = motionWatch.level;
            Entity lookup = level.getEntity(item.getUUID());
            String lookupState = lookup == null ? "null" : lookup == item ? "same-ref" : "different-ref";
            String frame = "unknown";
            String point = "unknown";
            String pointStatus = "not-attempted";
            String trackingStatus = "unavailable";
            if (!item.isRemoved()) {
                var actorPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, item);
                pointStatus = actorPoint == null ? "unknown-or-not-resident" : "known";
                if (actorPoint != null) { frame = String.valueOf(actorPoint.frameId()); point = actorPoint.storagePoint().toString(); }
            } else pointStatus = "entity-removed";
            if (item instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement) {
                var tracked = movement.sable$getTrackingSubLevel();
                trackingStatus = tracked == null ? "none" : "id=" + tracked.getUniqueId()
                        + ",removed=" + tracked.isRemoved() + ",live=" + (tracked.getLevel() == level
                        && dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(tracked.getUniqueId()) == tracked);
            }
            var reagent = item.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            String reagentState = reagent == null ? "none" : boundedReagents(reagent);
            long now = level.getGameTime();
            String detail = "gameTime=" + now + " entityTick=" + item.tickCount + " settled=" + item.settled()
                    + " onGround=" + item.onGround() + " lookup=" + lookupState + " actorPoint=" + pointStatus
                    + " frame=" + frame + " storagePoint=" + point + " tracking=" + trackingStatus
                    + " stackNonempty=" + !item.getItem().isEmpty() + " reagents=" + reagentState;
            boolean captured = observer.captureObservation(level, item.getUUID(), PersistenceWatch.Phase.POST_JOIN,
                    item.position().toString(), item.getDeltaMovement().toString(), detail,
                    !item.getItem().isEmpty(), item.isRemoved(), String.valueOf(item.getRemovalReason()));
            motionWatch.postJoinTicks++;
            if (!captured) {
                motionWatch.finishPostJoin("post-join-observer-budget-or-registration", true);
                motionWatch.postJoinFinished = true;
            } else if (item.isRemoved()) {
                motionWatch.finishPostJoin("captured-removal:" + item.getRemovalReason(), false);
                motionWatch.postJoinFinished = true;
            } else if (item.level() != level) {
                motionWatch.finishPostJoin("entity-left-watched-level", true);
                motionWatch.postJoinFinished = true;
            } else if (!level.isLoaded(item.blockPosition())) {
                motionWatch.finishPostJoin("entity-position-no-longer-loaded", true);
                motionWatch.postJoinFinished = true;
            } else if (motionWatch.postJoinTicks > 200) {
                motionWatch.finishPostJoin("200-tick-window-complete", false);
                motionWatch.postJoinFinished = true;
            }
        } catch (RuntimeException failure) {
            observer.finishPostJoin(item.getUUID(), "post-join-capture-error:" + failure.getClass().getSimpleName(), true);
            boolean captured = observer.captureObservation(motionWatch.level, item.getUUID(), PersistenceWatch.Phase.POST_JOIN,
                    safePosition(item), safeVelocity(item), "post-join-capture-error=" + failure.getClass().getSimpleName(),
                    false, item.isRemoved(), String.valueOf(item.getRemovalReason()));
            motionWatch.postJoinTicks++;
            if (!captured || motionWatch.postJoinTicks > 200 || item.isRemoved()) motionWatch.postJoinFinished = true;
        }
    }

    private static String safePosition(LooseItemEntity item) { try { return item.position().toString(); } catch (RuntimeException ignored) { return "unknown"; } }
    private static String safeVelocity(LooseItemEntity item) { try { return item.getDeltaMovement().toString(); } catch (RuntimeException ignored) { return "unknown"; } }
    private static String boundedReagents(com.juicyslew.moonstation14.ms14.reagent.ReagentComponent component) {
        StringBuilder out = new StringBuilder("{");
        int count = 0;
        for (var entry : component.centContents().entrySet()) {
            if (count == 16) { out.append("...more"); break; }
            if (count++ > 0) out.append(',');
            out.append(entry.getKey()).append('=').append(entry.getValue());
            if (out.length() > 900) { out.append("...truncated"); break; }
        }
        return out.append('}').toString();
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof LooseItemEntity item)) return;
        PersistenceWatch watch = persistenceWatch;
        boolean persistenceJoin = watch != null && watch.accepts(level, item.getUUID()) && watch.capture(level, item.getUUID(),
                PersistenceWatch.Phase.JOIN, item.position().toString(), item.getDeltaMovement().toString(),
                null, !item.getItem().isEmpty(), item.isRemoved(), String.valueOf(item.getRemovalReason()));
        WATCHES.removeIf(motionWatch -> {
            if (motionWatch.entity != item) return false;
            motionWatch.markPostJoinCoverageLost("join-watch-replaced"); return true;
        });
        if (WATCHES.size() == CAPACITY) {
            Watch evicted = WATCHES.removeFirst();
            evicted.markPostJoinCoverageLost("motion-watch-capacity-eviction");
        }
        WATCHES.addLast(new Watch(level, item, level.getGameTime(), item.position(), persistenceJoin ? watch : null));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Watch> iterator = WATCHES.iterator();
        while (iterator.hasNext()) {
            Watch watch = iterator.next();
            LooseItemEntity item = watch.entity;
            if (watch.persistenceWatch != null && !watch.postJoinFinished) observePostJoin(watch, item);
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
        final PersistenceWatch persistenceWatch;
        int postJoinTicks;
        boolean postJoinFinished;

        Watch(ServerLevel level, LooseItemEntity entity, long tick, net.minecraft.world.phys.Vec3 pos,
              PersistenceWatch persistenceWatch) {
            this.level = level; this.entity = entity; this.lastObservedTick = tick;
            this.stillSince = tick; this.lastPosition = pos;
            this.persistenceWatch = persistenceWatch;
        }
        void markPostJoinCoverageLost(String reason) {
            finishPostJoin(reason, true);
        }
        void finishPostJoin(String reason, boolean lost) {
            if (persistenceWatch != null) persistenceWatch.finishPostJoin(entity.getUUID(), reason, lost);
        }
    }
}

