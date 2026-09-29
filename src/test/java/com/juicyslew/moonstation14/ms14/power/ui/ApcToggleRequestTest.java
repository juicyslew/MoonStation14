package com.juicyslew.moonstation14.ms14.power.ui;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApcToggleRequestTest {
    @Test
    void boundedIntentContainsOnlyMenuSessionAndExpectedRevision() {
        UUID session = UUID.randomUUID();
        ApcToggleRequest request = new ApcToggleRequest(7, session, 12L, 3L, false);
        assertEquals(7, request.containerId());
        assertEquals(session, request.session());
        assertEquals(12L, request.expectedRevision());
        assertEquals(3L, request.requestId());
        assertEquals(false, request.desiredClosed());
    }

    @Test
    void rejectsInvalidMenuAndRevision() {
        UUID session = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new ApcToggleRequest(-1, session, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new ApcToggleRequest(1, session, -1, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new ApcToggleRequest(1, session, 0, -1, true));
    }

    @Test
    void batterySnapshotIsBoundedToDeviceCapacity() {
        assertEquals(0, ApcMenu.batteryPermille(0));
        assertEquals(500, ApcMenu.batteryPermille(PowerDeviceRules.APC_CAPACITY_JOULES / 2));
        assertEquals(0, ApcMenu.batteryPermille(Double.POSITIVE_INFINITY));
        assertEquals(1000, ApcMenu.batteryPermille(PowerDeviceRules.APC_CAPACITY_JOULES * 2));
    }

    @Test
    void longRevisionRoundTripsThroughFourSignedShortMenuSlots() {
        long revision = 0x7fff8123fedcba98L;
        int[] words = new int[4];
        for (int word = 0; word < words.length; word++) {
            // Vanilla's menu-data wire value is a signed short; decode must treat it as unsigned.
            words[word] = (short) ApcMenu.revisionWord(revision, word);
        }
        assertEquals(revision, ApcMenu.assembleRevision(words));
    }

    @Test
    void toggleResponseCarriesAuthoritativeTripLatchSnapshot() {
        UUID session = UUID.randomUUID();
        ApcToggleResponse response = new ApcToggleResponse(4, session, 2, true, true,
                false, 9L, 500, true);
        assertEquals(true, response.tripLatched());
        assertEquals(false, response.breakerClosed());
    }
}
