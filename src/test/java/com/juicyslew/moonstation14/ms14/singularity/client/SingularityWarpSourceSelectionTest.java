package com.juicyslew.moonstation14.ms14.singularity.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingularityWarpSourceSelectionTest {
    @Test
    void liveSourcesTakePriorityWithoutLimitingSpriteEligibility() {
        List<String> previews = List.of("preview1", "preview2", "preview3", "preview4", "preview5");
        List<String> live = List.of("entity1", "entity2", "entity3", "entity4", "entity5", "entity6");

        List<String> warp = SingularityVisualPreview.selectWarpSources(live, previews);

        assertEquals(5, warp.size());
        assertTrue(warp.contains("entity1"));
        assertEquals(List.of("entity1", "entity2", "entity3", "entity4", "entity5"), warp);
        assertEquals(6, live.size(), "sprite source enumeration remains independent of the five-source warp cap");
    }

    @Test
    void previewsFillOnlyUnoccupiedWarpSlots() {
        assertEquals(List.of("entity1", "preview1", "preview2", "preview3", "preview4"),
                SingularityVisualPreview.selectWarpSources(List.of("entity1"),
                        List.of("preview1", "preview2", "preview3", "preview4", "preview5")));
    }
}
