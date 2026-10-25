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

    @Test void minecraftPlayerLethalCoreIsSmallerWithoutChangingGeneralHorizonGeometry() {
        var profile = profileWithHorizon(1);
        assertEquals(.35, profile.eventHorizon().playerLethalRadiusScale());
        assertEquals(.35, profile.eventHorizon().playerLethalRadiusMax());
        assertEquals(.35, profile.playerLethalRadiusAtLevel(4), 1e-9);
        var body = new net.minecraft.world.phys.AABB(2, 0, 0, 2.5, 1, 1);
        var center = net.minecraft.world.phys.Vec3.ZERO;
        assertTrue(SingularityHorizonGeometry.touches(center, profile.eventHorizonRadiusAtLevel(4), body));
        assertFalse(SingularityHorizonGeometry.touches(center, profile.playerLethalRadiusAtLevel(4), body));
        assertEquals(3.5, profile.eventHorizonRadiusAtLevel(4), 1e-12, "NPC and terrain horizon remain on the upstream-derived radius");
        assertFalse(SingularityHorizonGeometry.touches(center, profile.playerLethalRadiusAtLevel(4),
                new net.minecraft.world.phys.AABB(.9, 0, 0, 1.4, 1, 1)), "body survives outside the capped player core");
        assertTrue(SingularityHorizonGeometry.touches(center, profile.playerLethalRadiusAtLevel(4),
                new net.minecraft.world.phys.AABB(.2, 0, 0, .7, 1, 1)), "body contact inside capped core is lethal");
        var invalid = new SingularityGameplayProfile.EventHorizon(true, true, .5, 1, 1, .01, 0);
        var base = profileWithHorizon(1);
        var invalidProfile = new SingularityGameplayProfile(base.initialEnergy(), base.initialLevel(), base.radsPerLevel(), base.energyLoss(),
                base.gravityAcceleration(), base.gravityRange(), base.minSpeed(), base.maxSpeed(), base.levelThresholds(), base.drainPerSecond(),
                base.radiation(), base.sounds(), invalid, base.randomWalk());
        assertThrows(IllegalArgumentException.class, invalidProfile::validate);
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
        assertTrue(profile.eventHorizon().consumeTiles());
        assertEquals(.5, profile.eventHorizon().consumePeriodSeconds());
        assertEquals(1, profile.eventHorizon().terrainEnergy());
        assertEquals(.01, profile.eventHorizon().lowYieldTerrainEnergy());
        assertEquals(.7, profile.randomWalk().verticalScale());
        assertEquals(200, profile.randomWalk().initialEscapeTicks());
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
        var base = profileWithHorizon(1);
        var invalidPeriod = new SingularityGameplayProfile.EventHorizon(true, true, Double.MAX_VALUE, 1, 1, .01);
        var p = new SingularityGameplayProfile(base.initialEnergy(), base.initialLevel(), base.radsPerLevel(), base.energyLoss(),
                base.gravityAcceleration(), base.gravityRange(), base.minSpeed(), base.maxSpeed(), base.levelThresholds(),
                base.drainPerSecond(), base.radiation(), base.sounds(), invalidPeriod, base.randomWalk());
        assertThrows(IllegalArgumentException.class, p::validate);
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
            assertEquals(SingularityGameplayProfile.RandomWalk.DEFAULT, resolved.randomWalk());
            assertEquals(2.5, resolved.eventHorizonRadiusAtLevel(3));
        }
    }

    @Test void horizonWaveAndWalkCompositionResolveIndependently() {
        var horizon = SingularityGameplayProfile.EventHorizon.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new JsonObject()).getOrThrow();
        assertTrue(horizon.consumeTiles());
        assertEquals(.5, horizon.consumePeriodSeconds());
        var noEntities = new JsonObject(); noEntities.addProperty("consumeEntities", false);
        var decoded = SingularityGameplayProfile.EventHorizon.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, noEntities).getOrThrow();
        assertFalse(decoded.consumeEntities());
        assertTrue(decoded.consumeTiles(), "disabling entity contact must not disable terrain waves");
        var walk = SingularityGameplayProfile.RandomWalk.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new JsonObject()).getOrThrow();
        assertEquals(SingularityGameplayProfile.RandomWalk.DEFAULT, walk);
    }

    @Test void gravityStrengthAdaptationDefaultsOverridesAndRejectsInvalidValues() {
        try (var stream = SingularityGameplayProfileTest.class.getResourceAsStream(
                "/data/moonstation14/singularity_gameplay/singularity.json")) {
            var authored = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var profile = SingularityGameplayProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, authored).getOrThrow();
            assertEquals(15, profile.gravityStrengthMultiplier());
            assertEquals(3, profile.gravityRangeMultiplier());
            assertEquals(2, profile.gravityRampFullStrengthRadiusScale());
            assertEquals(.35, profile.eventHorizon().playerLethalRadiusMax());
            authored.remove("gravityStrengthMultiplier");
            assertEquals(15, SingularityGameplayProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, authored)
                    .getOrThrow().gravityStrengthMultiplier(), "prototype omission inherits the adaptation default");
            authored.addProperty("gravityStrengthMultiplier", 20);
            assertEquals(20, SingularityGameplayProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, authored)
                    .getOrThrow().gravityStrengthMultiplier());
            authored.addProperty("gravityStrengthMultiplier", Double.POSITIVE_INFINITY);
            var invalid = SingularityGameplayProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, authored).getOrThrow();
            assertThrows(IllegalArgumentException.class, invalid::validate);
            authored.addProperty("gravityStrengthMultiplier", 15);
            authored.getAsJsonObject("gravityRamp").addProperty("rangeMultiplier", 0);
            var invalidRange = SingularityGameplayProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, authored).getOrThrow();
            assertThrows(IllegalArgumentException.class, invalidRange::validate);
            authored.getAsJsonObject("gravityRamp").addProperty("rangeMultiplier", 3);
            authored.getAsJsonObject("eventHorizon").addProperty("playerLethalRadiusMax", Double.NaN);
            var invalidCore = SingularityGameplayProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, authored).getOrThrow();
            assertThrows(IllegalArgumentException.class, invalidCore::validate);
        } catch (Exception failure) { throw new AssertionError(failure); }
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
