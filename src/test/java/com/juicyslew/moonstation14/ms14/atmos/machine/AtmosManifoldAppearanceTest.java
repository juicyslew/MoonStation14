package com.juicyslew.moonstation14.ms14.atmos.machine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosManifoldAppearanceTest {
    @Test
    void endpointsShareExplicitBitAndModelOrdering() {
        List<String> ids = List.of("north0", "north1", "north2", "south0", "south1", "south2");
        List<String> suffixes = List.of("north_primary", "north_secondary", "north_tertiary",
                "south_primary", "south_secondary", "south_tertiary");
        assertEquals(6, AtmosManifoldAppearance.endpointCount());
        for (int bit = 0; bit < ids.size(); bit++) {
            assertEquals(bit, AtmosManifoldAppearance.portBit(ids.get(bit)));
            assertEquals(suffixes.get(bit), AtmosManifoldAppearance.endpointSuffix(bit));
        }
        assertEquals(-1, AtmosManifoldAppearance.portBit("unknown"));
        assertEquals(null, AtmosManifoldAppearance.endpointSuffix(-1));
        assertEquals(null, AtmosManifoldAppearance.endpointSuffix(6));
    }
}
