package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ManualPresetAckTrackerTest {
    private static final UUID SCRUBBER = UUID.randomUUID();
    private static final UUID VENT = UUID.randomUUID();

    @Test
    void requiresLatestMatchingAcknowledgementFromBothDevices() {
        var tracker = new ManualPresetAckTracker(Set.of(SCRUBBER, VENT));
        tracker.commandDelivered(SCRUBBER, 10, false);
        tracker.commandDelivered(VENT, 20, false);
        tracker.resultDelivered(SCRUBBER, 10, true, false);
        assertFalse(tracker.ready());

        tracker.commandDelivered(SCRUBBER, 11, false);
        assertFalse(tracker.ready());
        tracker.resultDelivered(SCRUBBER, 10, true, false);
        tracker.resultDelivered(VENT, 20, true, false);
        assertFalse(tracker.ready());
        tracker.resultDelivered(SCRUBBER, 11, false, false);
        assertFalse(tracker.ready());
        tracker.resultDelivered(SCRUBBER, 11, true, true);
        assertFalse(tracker.ready());
        tracker.resultDelivered(SCRUBBER, 11, true, false);
        assertTrue(tracker.ready());
        assertTrue(tracker.diagnostic().contains("latestRequestIds"));
    }

    @Test
    void ignoresUnknownAndEnabledCommandsAndRejectsEmptyExpectation() {
        var tracker = new ManualPresetAckTracker(Set.of(SCRUBBER, VENT));
        tracker.commandDelivered(UUID.randomUUID(), 1, false);
        tracker.commandDelivered(SCRUBBER, 2, true);
        tracker.resultDelivered(SCRUBBER, 2, true, false);
        assertFalse(tracker.ready());
        assertThrows(IllegalArgumentException.class, () -> new ManualPresetAckTracker(Set.of()));
    }
}
