package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class TegContinuousShutdownTest {
    @Test void passiveAtmosphereSettingsAreSuccessfulNoopAndEnabledSettingsNeedDisable() {
        assertFalse(TegContinuousFixtureService.needsDisable(Optional.empty()));
        assertFalse(TegContinuousFixtureService.needsDisable(Optional.of(false)));
        assertTrue(TegContinuousFixtureService.needsDisable(Optional.of(true)));
    }
}
