package com.juicyslew.moonstation14.gametest.support;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** GameTest-owned entity-ticking tickets for fixtures extending beyond the root chunk. */
public final class FixtureChunkTickets {
    private static final TicketType<UUID> TYPE = TicketType.create("moonstation14_gametest_fixture",
            Comparator.<UUID>naturalOrder());

    private FixtureChunkTickets() { }

    /** Add exact, uniquely-owned level-31 region tickets to each chunk in the fixture footprint. */
    public static Lease acquire(GameTestHelper helper, AABB bounds) {
        ServerLevel level = helper.getLevel();
        int minX = net.minecraft.util.Mth.floor(bounds.minX) >> 4;
        int maxX = net.minecraft.util.Mth.floor(bounds.maxX) >> 4;
        int minZ = net.minecraft.util.Mth.floor(bounds.minZ) >> 4;
        int maxZ = net.minecraft.util.Mth.floor(bounds.maxZ) >> 4;
        UUID key = UUID.randomUUID();
        Set<ChunkPos> chunks = new HashSet<>();
        try {
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                chunks.add(chunk);
                level.getChunkSource().addRegionTicket(TYPE, chunk, 2, key);
                level.getChunk(chunk.x, chunk.z);
            }
        } catch (RuntimeException | Error failure) {
            for (ChunkPos chunk : chunks)
                level.getChunkSource().removeRegionTicket(TYPE, chunk, 2, key);
            throw failure;
        }
        Lease lease = new Lease(level, chunks, key);
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { lease.close(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { lease.close(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
                lease.close();
            }
        });
        return lease;
    }

    public static Lease around(GameTestHelper helper, BlockPos first, BlockPos second, double padding) {
        return acquire(helper, new AABB(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()), Math.max(first.getX(), second.getX()) + 1,
                Math.max(first.getY(), second.getY()) + 1, Math.max(first.getZ(), second.getZ()) + 1).inflate(padding));
    }

    /** Waits only until every requested position actually reaches entity-ticking status. */
    public static void awaitEntityTicking(GameTestHelper helper, Lease lease, int maxWaitTicks,
                                          Runnable ready, BlockPos... positions) {
        awaitEntityTicking(helper, lease, maxWaitTicks, () -> true, "extra readiness", ready, positions);
    }

    /** Also await a fixture-specific physical precondition without weakening chunk-ticking checks. */
    public static void awaitEntityTicking(GameTestHelper helper, Lease lease, int maxWaitTicks,
                                          java.util.function.BooleanSupplier additionalReady, String condition,
                                          Runnable ready, BlockPos... positions) {
        awaitEntityTicking(helper, lease, maxWaitTicks, additionalReady, () -> condition, ready, positions);
    }

    /** Variant whose bounded timeout detail is evaluated only when the wait actually fails. */
    public static void awaitEntityTicking(GameTestHelper helper, Lease lease, int maxWaitTicks,
                                          java.util.function.BooleanSupplier additionalReady,
                                          java.util.function.Supplier<String> condition,
                                          Runnable ready, BlockPos... positions) {
        if (maxWaitTicks < 1 || positions.length == 0) throw new IllegalArgumentException("invalid ticket readiness wait");
        long startGameTime = helper.getLevel().getGameTime();
        BoundedGameTestWait.await(helper, maxWaitTicks,
                () -> {
                    String details = timeoutDiagnostic(helper, lease, positions, maxWaitTicks, startGameTime,
                            additionalReady, condition.get()) + "; positions="
                            + java.util.Arrays.stream(positions).map(lease::diagnostic).toList();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[fixture-chunk-ticket-timeout] {}", details);
                    BlockPos firstUnticked = java.util.Arrays.stream(positions)
                            .filter(position -> !lease.entityTicking(position)).findFirst().orElse(null);
                    return timeoutSummary(maxWaitTicks, firstUnticked, condition.get(), additionalReady.getAsBoolean());
                }, () -> {
            boolean readyNow = true;
            for (BlockPos position : positions) readyNow &= lease.entityTicking(position);
            return readyNow && additionalReady.getAsBoolean();
        }, ready);
    }

    static String timeoutSummary(int maxWaitTicks, BlockPos firstUnticked, String condition, boolean additionalReady) {
        String cause = firstUnticked == null ? "additionalReady=" + condition + ":" + additionalReady
                : "unticked=" + firstUnticked;
        String summary = "fixture chunk readiness timeout after " + maxWaitTicks + " ticks; see [fixture-chunk-ticket-timeout] log; " + cause;
        return summary.length() <= 600 ? summary : summary.substring(0, 597) + "...";
    }

    private static String timeoutDiagnostic(GameTestHelper helper, Lease lease, BlockPos[] positions,
                                            int maxWaitTicks, long startGameTime,
                                            java.util.function.BooleanSupplier additionalReady, String condition) {
        Map<ChunkPos, BlockPos> unique = new LinkedHashMap<>();
        for (BlockPos position : positions) unique.putIfAbsent(new ChunkPos(position), position);
        Map<ChunkPos, BlockPos> pending = new LinkedHashMap<>();
        for (Map.Entry<ChunkPos, BlockPos> entry : unique.entrySet())
            if (!lease.entityTicking(entry.getValue())) pending.put(entry.getKey(), entry.getValue());
        boolean extra = additionalReady.getAsBoolean();
        StringBuilder message = new StringBuilder("fixture entity-ticking timeout pendingChunks=")
                .append(pending.size()).append('/').append(unique.size())
                .append(" elapsedWorldTicks=").append(helper.getLevel().getGameTime() - startGameTime)
                .append(" maxWaitTicks=").append(maxWaitTicks)
                .append(" additionalReady[").append(condition).append("]=").append(extra).append(" pending=");
        if (pending.isEmpty()) message.append("none");
        else {
            int shown = 0;
            for (Map.Entry<ChunkPos, BlockPos> entry : pending.entrySet()) {
                if (shown++ == 6) break;
                BlockPos pos = entry.getValue();
                message.append("{chunk=").append(entry.getKey()).append(" pos=").append(pos)
                        .append(" covers=").append(lease.covers(entry.getKey()))
                        .append(" closed=").append(lease.isClosed())
                        .append(" loaded=").append(helper.getLevel().hasChunkAt(pos))
                        .append(" entityTicking=false}");
            }
            if (pending.size() > 6) message.append(" +").append(pending.size() - 6).append(" more");
        }
        return message.toString();
    }

    public static final class Lease implements AutoCloseable {
        private static final int TICKET_LEVEL = 31;
        private final ServerLevel level;
        private final Set<ChunkPos> chunks;
        private final UUID key;
        private boolean closed;

        private Lease(ServerLevel level, Set<ChunkPos> chunks, UUID key) {
            this.level = level;
            this.chunks = Set.copyOf(chunks);
            this.key = key;
        }

        public boolean entityTicking(BlockPos position) { return level.isPositionEntityTicking(position); }
        public boolean covers(BlockPos position) { return chunks.contains(new ChunkPos(position)); }
        public boolean covers(ChunkPos chunk) { return chunks.contains(chunk); }
        public Set<ChunkPos> chunkPositions() { return chunks; }

        public String diagnostic(BlockPos position) {
            ChunkPos chunk = new ChunkPos(position);
            var loadedChunk = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
            return position + " chunk=" + chunk + " requested=" + chunks.contains(chunk)
                    + " ticket=" + TYPE + "/" + key + " level=" + TICKET_LEVEL + " closed=" + closed
                    + " loaded=" + (loadedChunk != null) + " requestedFullStatus="
                    + (loadedChunk == null ? "unavailable" : loadedChunk.getFullStatus())
                    + " overallEntityTicking=" + entityTicking(position) + " ticketState=" + ticketState(chunk, position);
        }

        private String ticketState(ChunkPos chunk, BlockPos position) {
            try {
                Object chunkMap = field(level.getChunkSource(), "chunkMap");
                Object distanceManager = invoke(chunkMap, "getDistanceManager");
                Object tickets = field(distanceManager, "tickets");
                Object entries = tickets.getClass().getMethod("get", long.class).invoke(tickets, chunk.toLong());
                var matching = new ArrayList<String>();
                if (entries instanceof Iterable<?> iterable) for (Object ticket : iterable) {
                    Object ticketType = invoke(ticket, "getType");
                    Object ticketKey = readTicketKey(ticket);
                    if (TYPE.equals(ticketType) && key.equals(ticketKey) && matching.size() < 4)
                        matching.add("type=" + ticketType + ",key=" + ticketKey + ",level=" + invoke(ticket, "getTicketLevel"));
                }
                return "rawDistanceInEntityRange=" + invoke(distanceManager, "inEntityTickingRange", long.class, chunk.toLong())
                        + ", rawCanPositionTick=" + invoke(field(level, "entityManager"), "canPositionTick", BlockPos.class, position)
                        + ", ownTickets=" + matching;
            } catch (Throwable failure) {
                return "diagnosticUnavailable=" + failure.getClass().getSimpleName() + ":" + failure.getMessage();
            }
        }

        private static Object readTicketKey(Object ticket) throws ReflectiveOperationException {
            try { return invoke(ticket, "getKey"); }
            catch (ReflectiveOperationException ignored) { return field(ticket, "key"); }
        }

        private static Object field(Object target, String name) throws ReflectiveOperationException {
            Class<?> type = target.getClass();
            while (type != null) {
                try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
                catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
            }
            throw new NoSuchFieldException(name);
        }

        private static Object invoke(Object target, String name, Class<?> parameter, Object argument) throws ReflectiveOperationException {
            return target.getClass().getMethod(name, parameter).invoke(target, argument);
        }

        private static Object invoke(Object target, String name) throws ReflectiveOperationException {
            return target.getClass().getMethod(name).invoke(target);
        }

        public boolean isClosed() { return closed; }

        @Override public void close() {
            if (closed) return;
            closed = true;
            for (ChunkPos chunk : chunks)
                level.getChunkSource().removeRegionTicket(TYPE, chunk, 2, key);
        }
    }
}
