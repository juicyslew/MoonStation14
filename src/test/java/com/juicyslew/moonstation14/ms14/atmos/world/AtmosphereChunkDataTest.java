package com.juicyslew.moonstation14.ms14.atmos.world;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereChunkDataTest {
    @Test
    void absentStateIsSparseAndMutationsOnlyReportChanges() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture ambient = GasMixture.breathableAir();
        assertNull(data.get(3, -64, 4));
        assertEquals(0, data.size());
        assertFalse(data.put(3, -64, 4, ambient, GasMixture.breathableAir()));
        GasMixture changed = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 310.0);
        assertTrue(data.put(3, -64, 4, changed, ambient));
        assertFalse(data.put(3, -64, 4, changed, ambient));
        assertEquals(changed.gasMoles(), data.get(3, -64, 4).gasMoles());
        assertTrue(data.put(3, -64, 4, ambient, ambient));
        assertNull(data.get(3, -64, 4));
        assertEquals(0, data.size());
    }

    @Test
    void roundTripsSparseOverridesWithStableGasIdsAndSignedY() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        data.put(15, -20, 0, new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 0.75, GasType.FREZON, 1.25), 290.5), GasMixture.vacuum());
        var encoded = AtmosphereChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow();
        String json = encoded.toString();
        assertTrue(json.contains("carbon_dioxide"));
        assertTrue(json.contains("frezon"));
        AtmosphereChunkData decoded = AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
        var restored = decoded.get(15, -20, 0);
        assertEquals(data.get(15, -20, 0).gasMoles(), restored.gasMoles());
        assertEquals(data.get(15, -20, 0).temperatureKelvin(), restored.temperatureKelvin());
        assertEquals(-20, decoded.entries().keySet().iterator().next().y());
    }

    @Test
    void codecRejectsUnsupportedVersionsInvalidValuesAndDuplicateCoordinatesOrGasIds() {
        assertInvalid("{version:2,cells:[]}");
        assertInvalid("{version:1,cells:[{x:0,y:0,z:0,temperature:-1,gases:[]}]}" );
        assertInvalid("{version:1,cells:[{x:0,y:0,z:0,temperature:300,gases:[{id:'unknown',moles:1}]}]}" );
        assertInvalid("{version:1,cells:[{x:0,y:0,z:0,temperature:300,gases:[{id:'oxygen',moles:1},{id:'oxygen',moles:2}]}]}" );
        assertInvalid("{version:1,cells:[{x:0,y:0,z:0,temperature:300,gases:[]},{x:0,y:0,z:0,temperature:300,gases:[]}]}" );
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereChunkData().get(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereChunkData().get(0, 0, 16));
    }

    private static void assertInvalid(String json) {
        assertTrue(AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
    }
}
