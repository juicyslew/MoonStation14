package com.juicyslew.moonstation14.ms14.devicenet;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DevicePortDefaultLinksParityTest {
    private static final String ROOT = "/data/moonstation14/moonstation14/device_port/";

    private List<String> defaults(String port) throws Exception {
        try (var stream = getClass().getResourceAsStream(ROOT + port + ".json")) {
            assertNotNull(stream, port);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("defaultLinks").asList().stream()
                    .map(element -> element.getAsString()).toList();
        }
    }

    @Test void sourcePortCatalogRetainsPinnedDefaultLinksMetadata() throws Exception {
        // PARITY: pinned SS14 Resources/Prototypes/DeviceLinking/source_ports.yml.
        assertEquals(List.of("Toggle", "Trigger", "Timer"), defaults("pressed"));
        assertEquals(List.of("On", "Open", "Forward", "Trigger", "Timer"), defaults("on"));
        assertEquals(List.of("Off", "Close"), defaults("off"));
        assertEquals(List.of("Toggle"), defaults("status"));
        assertEquals(List.of("DoorBolt"), defaults("door_status"));
    }

    @Test void airlockStillUsesSupportedDoorBoltDefault() throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/moonstation14/moonstation14/airlock/base_airlock.json")) {
            assertNotNull(stream);
            var airlock = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var sourcePorts = airlock.getAsJsonObject("deviceComponents").getAsJsonObject("deviceLinkSource").getAsJsonArray("ports");
            assertEquals("moonstation14:door_status", sourcePorts.get(0).getAsString());
            assertEquals(List.of("DoorBolt"), defaults("door_status"));
        }
    }
}
