package com.juicyslew.moonstation14.ms14.disposal.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisposalPowerParticipationTest {
    @Test void productionDemandRequiresSwitchAndNoIsolatedTestOverride() {
        assertTrue(DisposalUnitBlockEntity.participatesInPowerLoad(true, null), "ordinary switched-on unit participates");
        assertFalse(DisposalUnitBlockEntity.participatesInPowerLoad(false, null), "switched-off unit has no APC demand");
        assertFalse(DisposalUnitBlockEntity.participatesInPowerLoad(true, true), "isolated mechanics power override is not production load");
        assertFalse(DisposalUnitBlockEntity.participatesInPowerLoad(true, false), "isolated unpowered override is not production load");
    }
}
