package com.juicyslew.moonstation14.ms14.loose_objects;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Geometry-only check against the shipped item model and its pinned vanilla parent geometry. */
class LooseWorldVisualGeometryTest {
    @Test
    void grenadeModelFloorPivotMatchesCocoaStageTwoGeometry() throws Exception {
        String model = read("/assets/moonstation14/models/item/grenade.json");
        assertTrue(model.contains("minecraft:block/cocoa_stage2"));
        String profile = read("/data/moonstation14/moonstation14/loose_object_profile/grenade.json");
        double scale = number(profile, "model_scale");
        double floorOffset = number(profile, "model_floor_offset");

        // Vanilla 1.21.1 model source: https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.1/assets/minecraft/models/block/cocoa_stage2.json
        // Body bounds [4,3,7]-[12,12,15], stem bounds [8,12,12]-[8,16,16].
        // ItemRenderer NONE centers model coordinates by -8/16 on every axis.
        double centeredModelMinY = 3.0 / 16.0 - 8.0 / 16.0;
        double transformedMinY = centeredModelMinY * scale + floorOffset;
        assertEquals(0.0, transformedMinY, 1e-9, "grenade model must meet the floor");
        assertEquals(0.65, 13.0 / 16.0 * scale, 1e-9,
                "profile model scale determines overall body-and-stem height");

        double minZ = (7.0 / 16.0 - .5) * scale + number(profile, "sprite_offset_y");
        double maxZ = (16.0 / 16.0 - .5) * scale + number(profile, "sprite_offset_y");
        assertEquals(-.225, minZ, 1e-9, "overall cocoa geometry is centered in world Z");
        assertEquals(.225, maxZ, 1e-9, "overall cocoa geometry is centered in world Z");
        assertEquals(.45, number(profile, "physical_width"), 1e-9);
        assertEquals(.65, number(profile, "physical_height"), 1e-9);
        assertTrue(minZ >= -.45 / 2 - 1e-9 && maxZ <= .45 / 2 + 1e-9,
                "physical box contains the full body-and-stem width");
        assertTrue(transformedMinY >= 0 && transformedMinY + .65 <= number(profile, "physical_height"),
                "physical box contains the full body-and-stem height");
    }

    private static String read(String path) throws Exception {
        try (InputStream input = LooseWorldVisualGeometryTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "missing shipped asset " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static double number(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?[0-9.]+)").matcher(json);
        assertTrue(matcher.find(), "missing numeric field " + key);
        return Double.parseDouble(matcher.group(1));
    }
}
