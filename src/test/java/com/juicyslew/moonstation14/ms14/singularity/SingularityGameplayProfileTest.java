package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SingularityGameplayProfileTest {
    @Test void levelFourHorizonFitsOnlyTheEnlargedFixtureCenterCorridor() {
        var profile = profileWithHorizon(1);
        double radius = profile.eventHorizonRadiusAtLevel(4);
        assertEquals(3.5, radius);
        assertEquals(1, 8 - 2 * radius, 1e-9);
        assertTrue(8 - 2 * radius > 0);
        assertTrue(4 - 2 * profile.eventHorizonRadiusAtLevel(3) < 0,
                "old four-block compact enclosure cannot fit the stage-3 diameter of five");
    }

    @Test void horizonUsesThreeDimensionalSphereAgainstBodyBoxNotBoxInflation() {
        var center = new net.minecraft.world.phys.Vec3(0, 0, 0);
        var corner = new net.minecraft.world.phys.AABB(.29, .29, .29, .8, .8, .8);
        var boundary = new net.minecraft.world.phys.AABB(.5, 0, 0, 1, 1, 1);
        var outside = new net.minecraft.world.phys.AABB(.51, .51, 0, 1, 1, 1);
        assertTrue(SingularityHorizonGeometry.touches(center, .5, boundary));
        assertFalse(SingularityHorizonGeometry.touches(center, .5, corner));
        assertFalse(SingularityHorizonGeometry.touches(center, .5, outside));
        assertFalse(SingularityHorizonGeometry.touches(center, -.1, boundary));
    }

    @Test void resolvedCatalogDrivesEnergyAndLevelTransitions() throws Exception {
        var manager = new PrototypeManager(); manager.register(ModSingularityGameplay.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        String path = "/data/moonstation14/" + ModSingularityGameplay.TYPE.resourceDirectory() + "/singularity.json";
        try (var stream = SingularityGameplayProfileTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity"), json);
        }
        manager.reload(ModSingularityGameplay.TYPE, raw);
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity");
        var profile = manager.snapshot(ModSingularityGameplay.TYPE).get(id);
        assertNotNull(profile);
        assertEquals(180, profile.initialEnergy());
        assertEquals(1, profile.initialLevel());
        assertEquals(2, profile.radsPerLevel());
        assertEquals(10, profile.gravityAcceleration());
        assertEquals(4, profile.gravityRange());
        assertEquals(2, profile.radiation().intensity());
        assertEquals(.2, profile.radiation().slope());
        assertTrue(profile.eventHorizon().consumeEntities());
        assertEquals(1, profile.eventHorizon().entityEnergy());
        double[] boundaries = {200, 500, 1000, 2000, 5000};
        for (int i = 0; i < boundaries.length; i++) {
            assertEquals(i + 1, profile.levelForEnergy(boundaries[i] - .001));
            assertEquals(i + 2, profile.levelForEnergy(boundaries[i]));
        }
        assertEquals(0, profile.levelForEnergy(0));
        assertEquals(0, profile.levelForEnergy(-1));
        assertEquals(1, profile.levelForEnergy(180));
        assertEquals(179.95, profile.energyAfterSeconds(180, 1, .05), 1e-9);
        assertEquals(179, profile.energyAfterSeconds(180, 1, 1), 1e-9);
        assertEquals(180, profile.energyAfterSeconds(180, 5, 1));
        assertEquals(profile.levelForEnergy(500), profile.levelForEnergy(500));
        assertEquals(1.875, profile.minSpeedAtLevel(1));
        assertEquals(2.5, profile.maxSpeedAtLevel(1));
    }

    @Test void horizonSettingsDecodeUpstreamDefaultsAndCanDisableContact() {
        var horizonCodec = SingularityGameplayProfile.EventHorizon.CODEC;
        assertEquals(SingularityGameplayProfile.EventHorizon.DEFAULT,
                horizonCodec.parse(com.mojang.serialization.JsonOps.INSTANCE, new JsonObject()).getOrThrow());
        var disabled = new JsonObject(); disabled.addProperty("consumeEntities", false); disabled.addProperty("entityEnergy", 2.5);
        var decoded = horizonCodec.parse(com.mojang.serialization.JsonOps.INSTANCE, disabled).getOrThrow();
        assertFalse(decoded.consumeEntities());
        assertEquals(2.5, decoded.entityEnergy());
        assertTrue(horizonCodec.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.JsonPrimitive("bad")).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> profileWithHorizon(-1).validate());
    }

    @Test void resolvedPrototypeUsesNestedHorizonDefaultsWhenCompositionOmitsIt() throws Exception {
        var manager = new PrototypeManager(); manager.register(ModSingularityGameplay.TYPE);
        try (var stream = SingularityGameplayProfileTest.class.getResourceAsStream(
                "/data/moonstation14/singularity_gameplay/singularity.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            json.remove("eventHorizon");
            var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
            var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity");
            raw.put(id, json);
            manager.reload(ModSingularityGameplay.TYPE, raw);
            assertEquals(SingularityGameplayProfile.EventHorizon.DEFAULT,
                    manager.snapshot(ModSingularityGameplay.TYPE).get(id).eventHorizon());
            var resolved = manager.snapshot(ModSingularityGameplay.TYPE).get(id);
            assertEquals(2.5, resolved.eventHorizonRadiusAtLevel(3));
        }
    }

    private static SingularityGameplayProfile profileWithHorizon(double energy) {
        // The base profile is fully authored in the canonical resource; use it as the immutable source.
        try (var stream = SingularityGameplayProfileTest.class.getResourceAsStream(
                "/data/moonstation14/singularity_gameplay/singularity.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var parsed = SingularityGameplayProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            return new SingularityGameplayProfile(parsed.initialEnergy(), parsed.initialLevel(), parsed.radsPerLevel(), parsed.energyLoss(),
                    parsed.gravityAcceleration(), parsed.gravityRange(), parsed.minSpeed(), parsed.maxSpeed(), parsed.levelThresholds(),
                    parsed.drainPerSecond(), parsed.radiation(), parsed.sounds(),
                    new SingularityGameplayProfile.EventHorizon(true, energy));
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
}
