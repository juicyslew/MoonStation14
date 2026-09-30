package com.juicyslew.moonstation14.ms14.atmos.world;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReactionWorkQueueStressTest {
    private static final int CELLS = 800;

    private static BlockPos position(int index) {
        return new BlockPos((index / 16) * 16 + index % 16, 64, 0);
    }

    @Test void overflowAndContinuouslyHotCellsDoNotStarveColdChemistry() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        Set<BlockPos> offered = new HashSet<>();
        for (int i = 0; i < CELLS; i++) {
            BlockPos pos = position(i);
            assertTrue(offered.add(pos));
            queue.offer(pos);
            queue.offer(pos); // duplicate heat/gas notification
        }
        assertEquals(ReactionWorkQueue.ACTIVE_CAPACITY, queue.activeSize());
        assertEquals(CELLS - ReactionWorkQueue.ACTIVE_CAPACITY, queue.backlogSize());

        // First four positions model persistent hot fires; the remaining positions
        // include cold FrezonProduction, N2O and ammonia candidates.
        Set<BlockPos> serviced = new HashSet<>();
        // Place three cold chemistries in overflow, not in the initially active 256.
        BlockPos frezonProduction = position(300);
        BlockPos n2oDecomposition = position(500);
        BlockPos ammoniaOxygen = position(799);
        int frezonPass = -1, n2oPass = -1, ammoniaPass = -1;
        int inspectedTotal = 0, evaluatedTotal = 0, hotReoffers = 0, duePasses = 0;
        while (serviced.size() < CELLS && duePasses < 240) {
            int inspected = 0, evaluated = 0;
            while (inspected < AtmosphereService.MAX_REACTION_INSPECTIONS_PER_TICK
                    && evaluated < AtmosphereService.MAX_REACTION_EVALUATIONS_PER_TICK) {
                BlockPos pos = queue.poll();
                if (pos == null) break;
                inspected++;
                // All 800 are eligible in this synthetic workload; each evaluation
                // corresponds to one detached candidate, never a chunk/world scan.
                evaluated++;
                serviced.add(pos);
                if (pos.equals(frezonProduction) && frezonPass < 0) frezonPass = duePasses;
                if (pos.equals(n2oDecomposition) && n2oPass < 0) n2oPass = duePasses;
                if (pos.equals(ammoniaOxygen) && ammoniaPass < 0) ammoniaPass = duePasses;
                if (pos.equals(position(0)) || pos.equals(position(1))
                        || pos.equals(position(2)) || pos.equals(position(3))) {
                    queue.offer(pos);
                    hotReoffers++;
                }
            }
            assertTrue(inspected <= 32);
            assertTrue(evaluated <= 8);
            assertTrue(queue.activeSize() <= ReactionWorkQueue.ACTIVE_CAPACITY);
            inspectedTotal += inspected;
            evaluatedTotal += evaluated;
            duePasses++;
        }
        assertEquals(offered, serviced, "all finite candidates, including overflow, must be serviced");
        assertTrue(frezonPass >= 0 && frezonPass < 240);
        assertTrue(n2oPass >= 0 && n2oPass < 240);
        assertTrue(ammoniaPass >= 0 && ammoniaPass < 240);
        assertTrue(hotReoffers > 4, "hot work must actually compete with cold backlog");
        assertTrue(duePasses <= 240);
        assertEquals(inspectedTotal, evaluatedTotal);
        assertTrue(inspectedTotal <= duePasses * 32);
        assertTrue(evaluatedTotal <= duePasses * 8);
        assertEquals(0, queue.backlogSize());
    }

    @Test void resumableSavedOverrideDiscoveryChargesEveryCursorAttempt() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        // 50 chunks x 16 saved overrides; cursors are the only discovery source.
        for (int chunk = 0; chunk < 50; chunk++) queue.startLoad(new ChunkPos(chunk, 0));
        Set<BlockPos> discovered = new HashSet<>();
        int[] attempts = {0};
        int passes = 0;
        while (discovered.size() < CELLS && passes < 50) {
            int before = attempts[0];
            int charged = queue.drainLoads(AtmosphereService.MAX_REACTION_LOAD_STEPS_PER_TICK,
                    AtmosphereService.MAX_REACTION_LOAD_STEPS_PER_TICK, chunk -> {
                        var cursor = queue.cursor(chunk);
                        int index = cursor == null ? 0 : cursor.x();
                        assertTrue(discovered.add(position(chunk.x * 16 + index)));
                        attempts[0]++;
                        if (index < 15) queue.continueLoad(chunk,
                                new AtmosphereChunkData.CellPosition(index + 1, 64, 0));
                        return true;
                    });
            assertEquals(charged, attempts[0] - before);
            assertTrue(charged <= 32);
            passes++;
        }
        assertEquals(CELLS, discovered.size());
        assertEquals(CELLS, attempts[0]);
        assertEquals(25, passes);
        assertEquals(0, queue.drainLoads(32, 32, chunk -> fail("no undiscovered chunks")));
    }
}
