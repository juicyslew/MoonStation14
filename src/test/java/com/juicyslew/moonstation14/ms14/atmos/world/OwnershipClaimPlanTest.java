package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OwnershipClaimPlanTest {
    @Test
    void commitsLargeProvenRegionInDeterministicBoundedBatches() {
        List<BlockPos> positions = positions(500);
        OwnershipClaimPlan plan = new OwnershipClaimPlan(positions);
        List<BlockPos> committed = new ArrayList<>();

        assertEquals(256, plan.advance(256, pos -> { committed.add(pos); return true; }));
        assertEquals(256, plan.cursor());
        assertFalse(plan.isComplete());
        assertEquals(244, plan.advance(256, pos -> { committed.add(pos); return true; }));
        assertTrue(plan.isComplete());
        assertEquals(positions, committed);
        assertEquals(positions.size(), new HashSet<>(committed).size());
    }

    @Test
    void failedValidationCancelsTailWithoutRepeatingCommittedPositions() {
        List<BlockPos> positions = positions(600);
        OwnershipClaimPlan plan = new OwnershipClaimPlan(positions);
        List<BlockPos> committed = new ArrayList<>();
        assertEquals(256, plan.advance(256, pos -> { committed.add(pos); return true; }));

        int nextBatch = plan.advance(256, pos -> {
            if (pos.equals(positions.get(300))) return false;
            committed.add(pos);
            return true;
        });
        assertEquals(44, nextBatch);
        assertTrue(plan.isCancelled());
        assertFalse(plan.isComplete());
        assertEquals(300, plan.cursor());
        assertEquals(300, new HashSet<>(committed).size());
        assertEquals(0, plan.advance(256, pos -> { fail("cancelled tail must not be committed"); return true; }));
    }

    @Test
    void eachAdvanceNeverExceedsItsWriteBudgetAndCancellationIsSticky() {
        OwnershipClaimPlan plan = new OwnershipClaimPlan(positions(700));
        for (int i = 0; i < 3; i++) {
            int[] writes = {0};
            int result = plan.advance(256, pos -> { writes[0]++; return true; });
            assertTrue(result <= 256);
            assertEquals(result, writes[0]);
        }
        assertEquals(700, plan.cursor());
        plan.cancel();
        assertEquals(0, plan.advance(256, pos -> fail("cancelled plan must not write")));
    }

    @Test
    void cancelledStagingPreservesPreviouslyClaimedMixtureAndLeavesTailUnknown() {
        BlockPos saved = BlockPos.ZERO;
        BlockPos tail = new BlockPos(1, 0, 0);
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture ambient = GasMixture.breathableAir();
        GasMixture stored = new GasMixture(Map.of(GasType.OXYGEN, 7.0), 315.0);
        data.put(0, 0, 0, stored, ambient);
        OwnershipClaimPlan plan = new OwnershipClaimPlan(List.of(saved, tail));

        assertEquals(1, plan.advance(256, pos -> {
            if (pos.equals(tail)) return false; // topology/exposure invalidated the staged tail
            return data.claimFinite(pos.getX(), pos.getY(), pos.getZ());
        }));
        assertTrue(plan.isCancelled());
        assertTrue(data.isFiniteClaimed(0, 0, 0));
        assertFalse(data.isFiniteClaimed(1, 0, 0));
        assertEquals(stored.gasMoles(), data.get(0, 0, 0).gasMoles());
        assertEquals(stored.temperatureKelvin(), data.get(0, 0, 0).temperatureKelvin());
    }

    private static List<BlockPos> positions(int count) {
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) positions.add(new BlockPos(i, 64, 0));
        return List.copyOf(positions);
    }
}
