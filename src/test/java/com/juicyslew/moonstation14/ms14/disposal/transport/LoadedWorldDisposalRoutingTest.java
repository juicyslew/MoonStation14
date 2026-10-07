package com.juicyslew.moonstation14.ms14.disposal.transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadedWorldDisposalRoutingTest {
    @Test void convertsDisposalVelocityFromBlocksPerSecondToMinecraftBlocksPerTick() {
        assertEquals(.5, DisposalsEjection.speedBlocksPerTick(), 1e-12);
        assertEquals(.5, DisposalsEjection.blocksPerSecondToBlocksPerTick(10.0), 1e-12);
        assertEquals(0.0, DisposalsEjection.blocksPerSecondToBlocksPerTick(0.0), 1e-12);
    }
}
