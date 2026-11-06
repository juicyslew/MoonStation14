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

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** GameTest-owned entity-ticking tickets for fixtures extending beyond the root chunk. */
public final class FixtureChunkTickets {
    private static final boolean FORCE_TICKS = true;
    // ChunkMap's entity-ticking promotion warms a FULL-status neighborhood with radius two.
    private static final int FULL_NEIGHBOR_RADIUS = 2;
    private static final TicketType<UUID> TYPE = TicketType.create("moonstation14_gametest_fixture",
            Comparator.<UUID>naturalOrder());

    private FixtureChunkTickets() { }

    /** Add exact, uniquely-owned level-31 region tickets and request force ticking. */
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
                level.getChunkSource().addRegionTicket(TYPE, chunk, 2, key, FORCE_TICKS);
            }
            // Complete the FULL-neighbor promotion before fixture readiness polling begins.
            // Only the requested footprint owns tickets; halo chunks are synchronously loaded
            // through the normal server chunk pipeline, without adding fixture ticket ownership.
            for (int x = minX - FULL_NEIGHBOR_RADIUS; x <= maxX + FULL_NEIGHBOR_RADIUS; x++)
                for (int z = minZ - FULL_NEIGHBOR_RADIUS; z <= maxZ + FULL_NEIGHBOR_RADIUS; z++)
                    level.getChunk(x, z);
        } catch (RuntimeException | Error failure) {
            for (ChunkPos chunk : chunks)
                level.getChunkSource().removeRegionTicket(TYPE, chunk, 2, key, FORCE_TICKS);
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
        awaitEntityTicking(helper,lease,maxWaitTicks,additionalReady,()->condition,ready,positions);
    }

    /** Variant whose bounded timeout detail is evaluated only when the wait actually fails. */
    public static void awaitEntityTicking(GameTestHelper helper, Lease lease, int maxWaitTicks,
                                          java.util.function.BooleanSupplier additionalReady,
                                          java.util.function.Supplier<String> condition,
                                          Runnable ready, BlockPos... positions) {
        if (maxWaitTicks < 1 || positions.length == 0) throw new IllegalArgumentException("invalid ticket readiness wait");
        BoundedGameTestWait.await(helper, maxWaitTicks,
                () -> {
                    String details = "fixture ticket did not become entity-ticking within " + maxWaitTicks + " ticks: "
                        + java.util.Arrays.stream(positions).map(lease::diagnostic).toList()
                        + "; additionalReady[" + condition.get() + "]=" + additionalReady.getAsBoolean();
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

    public static final class Lease implements AutoCloseable {
        private final ServerLevel level;
        private final Set<ChunkPos> chunks;
        private final UUID key;
        private static final int TICKET_LEVEL = 31;
        private boolean closed;

        private Lease(ServerLevel level, Set<ChunkPos> chunks, UUID key) {
            this.level = level;
            this.chunks = Set.copyOf(chunks);
            this.key = key;
        }

        public boolean entityTicking(BlockPos position) { return level.isPositionEntityTicking(position); }
        /** Whether this fixture lease explicitly covers the chunk hosting a position. */
        public boolean covers(BlockPos position) { return chunks.contains(new ChunkPos(position)); }
        public String diagnostic(BlockPos position) {
            ChunkPos chunk = new ChunkPos(position);
            var loadedChunk = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
            return position + " chunk=" + chunk + " requested=" + chunks.contains(chunk)
                    + " ticket=" + TYPE + "/" + key + " level=" + TICKET_LEVEL + " closed=" + closed
                    + " loaded=" + (loadedChunk != null) + " requestedFullStatus=" + (loadedChunk == null ? "unavailable" : loadedChunk.getFullStatus())
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
                        matching.add("type=" + ticketType + ",key=" + ticketKey + ",level=" + invoke(ticket, "getTicketLevel")
                                + ",forceTicks=" + invoke(ticket, "isForceTicks"));
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
                level.getChunkSource().removeRegionTicket(TYPE, chunk, 2, key, FORCE_TICKS);
        }
    }
}
