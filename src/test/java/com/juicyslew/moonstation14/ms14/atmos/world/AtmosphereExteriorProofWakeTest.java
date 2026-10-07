package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereExteriorProofWakeTest {
    @Test void onlyLoadedFiniteFaceNeighborsAreDedupeCandidates() {
        BlockPos open = new BlockPos(0, 64, 0), finite = open.east(), closed = open.west();
        Set<BlockPos> result = AtmosphereService.finiteBoundaryCandidates(List.of(open, open.east()),
                p -> p.equals(finite));
        assertEquals(Set.of(finite), result);
        assertFalse(result.contains(closed));
    }
}
