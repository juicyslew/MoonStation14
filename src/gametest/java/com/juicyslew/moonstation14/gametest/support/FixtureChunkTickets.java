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
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;

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
        if (maxWaitTicks < 1 || positions.length == 0) throw new IllegalArgumentException("invalid ticket readiness wait");
        long startGameTime = helper.getLevel().getGameTime();
        BoundedGameTestWait.await(helper, maxWaitTicks,
                () -> timeoutDiagnostic(helper, lease, positions, maxWaitTicks, startGameTime,
                        additionalReady, condition), () -> {
            boolean readyNow = true;
            for (BlockPos position : positions) readyNow &= lease.entityTicking(position);
            return readyNow && additionalReady.getAsBoolean();
        }, ready);
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
        public boolean covers(net.minecraft.world.level.ChunkPos chunk) { return chunks.contains(chunk); }
        public Set<ChunkPos> chunkPositions() { return chunks; }
        public String diagnostic(BlockPos position) {
            return position + " chunk=" + new ChunkPos(position) + " covers=" + covers(new ChunkPos(position))
                    + " loaded=" + level.hasChunkAt(position)
                    + " entityTicking=" + entityTicking(position);
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
