package com.juicyslew.moonstation14.ms14.examine.scene;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class CelestialScenePrototypeTest {
    private static JsonObject read(String path) throws Exception {
        try (var in = new InputStreamReader(CelestialScenePrototypeTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }
    @Test void resolvedDefaultsAndInheritedProfilesFeedSharedRayGeometry() throws Exception {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "base"), read("/data/moonstation14/moonstation14/celestial_scene/base.json"));
        raw.put(ModCelestialScenes.SUN, read("/data/moonstation14/moonstation14/celestial_scene/sun.json"));
        raw.put(ModCelestialScenes.EARTH, read("/data/moonstation14/moonstation14/celestial_scene/earth.json"));
        var child = new JsonObject(); child.addProperty("id", "large_sun"); child.addProperty("parent", "sun"); child.addProperty("radius", 12.0);
        var childId = ResourceLocation.fromNamespaceAndPath("moonstation14", "large_sun"); raw.put(childId, child);
        var manager = new PrototypeManager(); manager.register(ModCelestialScenes.TYPE); manager.reload(ModCelestialScenes.TYPE, raw);
        var catalog = manager.snapshot(ModCelestialScenes.TYPE);
        var sun = catalog.get(ModCelestialScenes.SUN); var earth = catalog.get(ModCelestialScenes.EARTH);
        assertEquals("A great big ball of flame.", sun.metadata().description());
        assertEquals("Home to some. Hell to others.", earth.metadata().description());
        assertEquals("minecraft:overworld", sun.dimension());
        assertEquals(9, sun.radius()); assertEquals(80, sun.skyDistance());
        assertEquals(12, catalog.get(childId).radius());
        assertNotNull(CelestialSceneGeometry.intersect(catalog.get(childId), 0,
                new Vec3(0, 0, 0), new Vec3(0, 1, 0)));
        assertNull(CelestialSceneGeometry.intersect(sun, 0, new Vec3(11, 0, 0), new Vec3(0, 1, 0)));
    }
    @Test void rayIntersectionsFollowTimeAndRejectBelowHorizonScenes() throws Exception {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "base"), read("/data/moonstation14/moonstation14/celestial_scene/base.json"));
        raw.put(ModCelestialScenes.SUN, read("/data/moonstation14/moonstation14/celestial_scene/sun.json"));
        raw.put(ModCelestialScenes.EARTH, read("/data/moonstation14/moonstation14/celestial_scene/earth.json"));
        var manager = new PrototypeManager(); manager.register(ModCelestialScenes.TYPE); manager.reload(ModCelestialScenes.TYPE, raw);
        var sun = manager.snapshot(ModCelestialScenes.TYPE).get(ModCelestialScenes.SUN);
        var earth = manager.snapshot(ModCelestialScenes.TYPE).get(ModCelestialScenes.EARTH);
        assertNotNull(CelestialSceneGeometry.intersect(sun, 0, Vec3.ZERO, new Vec3(0, 1, 0)));
        assertNull(CelestialSceneGeometry.intersect(sun, .5, Vec3.ZERO, new Vec3(0, 1, 0)));
        assertNotNull(CelestialSceneGeometry.intersect(earth, .5, Vec3.ZERO, new Vec3(0, 1, 0)));
        assertNull(CelestialSceneGeometry.intersect(earth, 0, Vec3.ZERO, new Vec3(0, 1, 0)));
        Vec3 horizonRay = new Vec3(-80, 0, 0).normalize();
        assertNull(CelestialSceneGeometry.intersect(sun, .25, Vec3.ZERO, horizonRay));
        Vec3 upperDiscRay = new Vec3(-80, 5, 0).normalize();
        assertNotNull(CelestialSceneGeometry.intersect(sun, .25, Vec3.ZERO, upperDiscRay));
    }

    @Test void renderedFanVertexAndEdgeMathMatchTheSharedIntersection() {
        var sun = new CelestialSceneData("minecraft:overworld", CelestialSceneData.Side.SUN, 9, 80,
                255, 255, 245, new com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata("Sun", ""));
        var transform = CelestialSceneGeometry.transform(0);
        org.joml.Vector3f vertex = transform.transformPosition(9, 80, 0, new org.joml.Vector3f());
        Vec3 vertexRay = new Vec3(vertex.x, vertex.y, vertex.z).normalize();
        assertNotNull(CelestialSceneGeometry.intersect(sun, 0, Vec3.ZERO, vertexRay));
        assertEquals(CelestialSceneGeometry.intersectCameraRay(sun, 0, Vec3.ZERO, vertexRay),
                CelestialSceneGeometry.intersectCameraRay(sun, 0, new Vec3(937, 68, -402), vertexRay),
                "camera world translation cannot move the camera-relative rendered sky scene");
        double apothem = 9 * Math.cos(Math.PI / CelestialSceneGeometry.SEGMENTS);
        org.joml.Vector3f edgeInside = transform.transformPosition((float)((apothem - .001) * Math.cos(Math.PI / CelestialSceneGeometry.SEGMENTS)), 80,
                (float)((apothem - .001) * Math.sin(Math.PI / CelestialSceneGeometry.SEGMENTS)),
                new org.joml.Vector3f());
        org.joml.Vector3f edgeOutside = transform.transformPosition((float)((apothem + .001) * Math.cos(Math.PI / CelestialSceneGeometry.SEGMENTS)), 80,
                (float)((apothem + .001) * Math.sin(Math.PI / CelestialSceneGeometry.SEGMENTS)),
                new org.joml.Vector3f());
        assertNotNull(CelestialSceneGeometry.intersect(sun, 0, Vec3.ZERO,
                new Vec3(edgeInside.x, edgeInside.y, edgeInside.z).normalize()));
        assertNull(CelestialSceneGeometry.intersect(sun, 0, Vec3.ZERO,
                new Vec3(edgeOutside.x, edgeOutside.y, edgeOutside.z).normalize()));
        assertEquals(80, CelestialSceneGeometry.localPlaneY(sun));
        var reversed = new CelestialSceneData(sun.dimension(), CelestialSceneData.Side.EARTH, 12, 91,
                1, 2, 3, sun.metadata());
        assertEquals(-91, CelestialSceneGeometry.localPlaneY(reversed));
        assertEquals(-90.9f, CelestialSceneGeometry.cameraFacingArtPlaneY(reversed), 1e-5);
        Vec3 eye = new Vec3(2, 3, 4);
        Vec3 shortSceneEnd = CelestialSceneExamination.rayEndpoint(eye, new Vec3(1, 0, 0));
        var distantDrawing = new CelestialSceneData("minecraft:overworld", CelestialSceneData.Side.SUN,
                9, 300, 1, 2, 3, sun.metadata());
        assertEquals(128, shortSceneEnd.distanceTo(eye));
        assertEquals(shortSceneEnd, CelestialSceneExamination.rayEndpoint(eye, new Vec3(1, 0, 0)),
                "scene world LOS cap is independent of virtual skyDistance");
        assertEquals(300, distantDrawing.skyDistance());
    }
    @Test void removedSceneProfileCanReloadAndIsAbsentForFailClosedConsumers() throws Exception {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "base"), read("/data/moonstation14/moonstation14/celestial_scene/base.json"));
        raw.put(ModCelestialScenes.SUN, read("/data/moonstation14/moonstation14/celestial_scene/sun.json"));
        raw.put(ModCelestialScenes.EARTH, read("/data/moonstation14/moonstation14/celestial_scene/earth.json"));
        var manager = new PrototypeManager(); manager.register(ModCelestialScenes.TYPE); manager.reload(ModCelestialScenes.TYPE, raw);
        raw.remove(ModCelestialScenes.SUN);
        manager.reload(ModCelestialScenes.TYPE, raw);
        assertNull(manager.snapshot(ModCelestialScenes.TYPE).get(ModCelestialScenes.SUN));
        assertFalse(CelestialSceneGeometry.available(null, null, false));
    }
}
