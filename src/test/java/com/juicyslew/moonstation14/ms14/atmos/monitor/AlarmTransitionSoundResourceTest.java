package com.juicyslew.moonstation14.ms14.atmos.monitor;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AlarmTransitionSoundResourceTest {
    @Test void registeredEventHasPackagedSourceAndLicense() throws Exception {
        var loader = AlarmTransitionSoundResourceTest.class.getClassLoader();
        var json = new String(loader.getResourceAsStream("assets/moonstation14/sounds.json").readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"atmos/fire_alarm\""));
        assertTrue(json.contains("\"moonstation14:ss14/atmos/alarm\""));
        assertNotNull(loader.getResource("assets/moonstation14/sounds/ss14/atmos/alarm.ogg"));
        var license = new String(loader.getResourceAsStream("assets/moonstation14/sounds/ss14/atmos/alarm.ogg.license.txt").readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(license.contains("Baystation12"));
        assertTrue(license.contains("CC-BY-SA"));
    }
}
