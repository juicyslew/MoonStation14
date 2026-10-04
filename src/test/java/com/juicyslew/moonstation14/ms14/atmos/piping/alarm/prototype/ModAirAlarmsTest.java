package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolutionException;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ModAirAlarmsTest {
    private static JsonObject resource(String name) {
        String path = "/data/moonstation14/moonstation14/air_alarm/" + name + ".json";
        try (var stream = ModAirAlarmsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static Map<ResourceLocation, JsonObject> definitions() {
        return new HashMap<>(Map.of(ModAirAlarms.WALLMOUNT, resource("base_wallmount"),
                ModAirAlarms.METALLIC, resource("base_wallmount_metallic"),
                ModAirAlarms.MACHINE, resource("base_wallmount_machine"),
                ModAirAlarms.AIR_ALARM, resource("air_alarm")));
    }

    private static PrototypeManager manager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModAirAlarms.AIR_ALARM_TYPE);
        return manager;
    }

    @Test void resolvesAllInheritedDefaultsAndWireRoundTrip() {
        PrototypeManager manager = manager();
        manager.reload(ModAirAlarms.AIR_ALARM_TYPE, definitions());
        var catalog = manager.snapshot(ModAirAlarms.AIR_ALARM_TYPE);
        assertEquals(1, catalog.asMap().size());
        AirAlarmData alarm = catalog.get(ModAirAlarms.AIR_ALARM);
        assertTrue(alarm.wallMount().anchored());
        assertTrue(alarm.wallMount().nonColliding());
        assertEquals(100, alarm.apcPowerReceiver().requestedWatts());
        assertEquals(3, alarm.extensionCableReceiver().localReachBlocks());
        assertEquals(32, alarm.deviceList().maxDevices());
        JsonObject encoded = manager.encodePublishedCatalogs().get(ModAirAlarms.AIR_ALARM_TYPE.typeId())
                .get(ModAirAlarms.AIR_ALARM);
        assertEquals(alarm, AirAlarmData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertFalse(encoded.has("parent"));
        assertFalse(encoded.has("deviceNetwork"));
    }

    @Test void rejectsMissingEntryParentAndMalformedDefaultsWithoutReplacingPublishedCatalog() {
        PrototypeManager manager = manager();
        manager.reload(ModAirAlarms.AIR_ALARM_TYPE, definitions());
        var original = manager.snapshot(ModAirAlarms.AIR_ALARM_TYPE);
        Map<ResourceLocation, JsonObject> missing = definitions();
        missing.remove(ModAirAlarms.AIR_ALARM);
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModAirAlarms.AIR_ALARM_TYPE, missing));
        Map<ResourceLocation, JsonObject> missingParent = definitions();
        missingParent.remove(ModAirAlarms.MACHINE);
        assertThrows(PrototypeResolutionException.class, () -> manager.reload(ModAirAlarms.AIR_ALARM_TYPE, missingParent));
        Map<ResourceLocation, JsonObject> malformed = definitions();
        malformed.get(ModAirAlarms.MACHINE).getAsJsonObject("apcPowerReceiver").addProperty("requestedWatts", 5);
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModAirAlarms.AIR_ALARM_TYPE, malformed));
        Map<ResourceLocation, JsonObject> unsupported = definitions();
        unsupported.get(ModAirAlarms.AIR_ALARM).add("deviceNetwork", new JsonObject());
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModAirAlarms.AIR_ALARM_TYPE, unsupported));
        assertEquals(original.asMap(), manager.snapshot(ModAirAlarms.AIR_ALARM_TYPE).asMap());
    }
}
