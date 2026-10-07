package com.juicyslew.moonstation14.ms14.medical_observer.client;

import com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalCarrierRenderPolicyTest {
    private static final UUID CARRIER = UUID.randomUUID();
    private static final UUID BODY = UUID.randomUUID();

    @Test
    void hidesOnlyLocalCarrierForActiveDistinctCorpseOrGhostRepresentation() {
        for (ObserverPayloads.Mode mode : new ObserverPayloads.Mode[]{ObserverPayloads.Mode.CORPSE, ObserverPayloads.Mode.GHOST})
            assertTrue(LocalCarrierRenderPolicy.hide(true, mode, CARRIER, BODY));

        // Ghost sprite entities are not the local native player model.
        assertFalse(LocalCarrierRenderPolicy.hide(false, ObserverPayloads.Mode.GHOST, BODY, CARRIER));
    }

    @Test
    void keepsCorpseAndAllNonObserverModelsVisible() {
        assertFalse(LocalCarrierRenderPolicy.hide(true, ObserverPayloads.Mode.CORPSE, CARRIER, CARRIER));
        assertFalse(LocalCarrierRenderPolicy.hide(false, ObserverPayloads.Mode.CORPSE, CARRIER, BODY));
        assertFalse(LocalCarrierRenderPolicy.hide(true, null, CARRIER, BODY));
        assertFalse(LocalCarrierRenderPolicy.hide(true, ObserverPayloads.Mode.END, CARRIER, BODY));
    }
}
