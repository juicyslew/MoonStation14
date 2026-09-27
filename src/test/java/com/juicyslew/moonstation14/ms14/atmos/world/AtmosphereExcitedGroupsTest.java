package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.ExcitedAtmosphereGroups;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.LindaGasSharing;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereExcitedGroupsTest {
    @Test
    void worldSnapshotContainsOnlyTrackerAcceptedMembersAndSamplesOncePerStage() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> mixtures = new HashMap<>();
        for (int x = 0; x < 801; x++)
            mixtures.put(new BlockPos(x, 0, 0), new GasMixture(Map.of(GasType.OXYGEN, x + 1.0), 293.15));
        groups.onShare(LindaGasSharing.Pair.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)), 1.0, mixtures);
        for (int x = 1; x < 799; x++)
            groups.onShare(LindaGasSharing.Pair.of(new BlockPos(x, 0, 0), new BlockPos(x + 1, 0, 0)), 1.0, mixtures);

        BlockPos rejected = new BlockPos(800, 0, 0);
        groups.onShare(LindaGasSharing.Pair.of(new BlockPos(799, 0, 0), rejected), 1.0, mixtures);
        assertFalse(groups.sameGroupPair(LindaGasSharing.Pair.of(new BlockPos(799, 0, 0), rejected)));

        AtomicInteger sampleCount = new AtomicInteger();
        Map<BlockPos, GasMixture> snapshot = AtmosphereService.trackedFiniteSnapshot(groups, pos -> {
            sampleCount.incrementAndGet();
            return mixtures.get(pos);
        });
        assertNotNull(snapshot);
        assertEquals(800, snapshot.size());
        assertEquals(800, sampleCount.get());
        assertFalse(snapshot.containsKey(rejected));

        assertNull(AtmosphereService.trackedFiniteSnapshot(groups, pos ->
                pos.equals(new BlockPos(400, 0, 0)) ? null : mixtures.get(pos)),
                "an unavailable tracked member fails closed rather than making a partial physics snapshot");
    }
}
