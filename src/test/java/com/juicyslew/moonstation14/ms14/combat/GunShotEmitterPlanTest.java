package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GunShotEmitterPlanTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
    private static ProjectileProfile profile(int count, float degrees, ResourceLocation child) {
        return new ProjectileProfile(Map.of(), false, true, 5, null, null, null, null, null, null,
                new ProjectileProfile.SpreadData(count, degrees, Optional.ofNullable(child)), 0, true);
    }
    private static Map<ResourceLocation, ProjectileProfile> catalog(int count) {
        return Map.of(id("root"), profile(count, 20, id("child")), id("child"), profile(9, 100, id("missing")));
    }

    @Test void sourcePatternFansOutRootThenChildrenWithoutRecursion() {
        for (int count : new int[]{6, 4, 3}) {
            var planned = GunShotEmitter.plan(catalog(count), id("root"), new Vec3(0, 0, 1), Vec3.ZERO, 20, 1).orElseThrow();
            assertEquals(count, planned.size());
            assertEquals(id("root"), planned.get(0).id());
            for (int i = 1; i < count; i++) assertEquals(id("child"), planned.get(i).id());
        }
    }

    @Test void missingChildInvalidatesEntirePlan() {
        assertTrue(GunShotEmitter.plan(Map.of(id("root"), profile(3, 20, id("absent"))), id("root"), new Vec3(0, 0, 1), Vec3.ZERO, 20, 1).isEmpty());
    }

    @Test void spreadSamplesThreeDimensionalConeForHorizontalVerticalAndDiagonalAim() {
        for (Vec3 aim : new Vec3[]{new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(1, 2, 3)}) {
            for (int count : new int[]{3, 4, 6}) {
                var planned = GunShotEmitter.plan(catalog(count), id("root"), aim, Vec3.ZERO, 20, .6f).orElseThrow();
                assertEquals(count, planned.size());
                Vec3 forward = aim.normalize();
                assertEquals(1, planned.get(0).direction().dot(forward), 1e-9);
                boolean hasRight = false, hasUp = false;
                Vec3 helper = Math.abs(forward.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                Vec3 right = helper.cross(forward).normalize(), up = forward.cross(right).normalize();
                for (var p : planned) {
                    assertEquals(1, p.direction().length(), 1e-9);
                    assertTrue(Math.acos(Math.max(-1, Math.min(1, forward.dot(p.direction())))) <= Math.toRadians(6) + 1e-8);
                    hasRight |= Math.abs(p.direction().dot(right)) > 1e-6;
                    hasUp |= Math.abs(p.direction().dot(up)) > 1e-6;
                }
                assertTrue(hasRight && hasUp);
            }
        }
    }

    @Test void zeroConeAndSingleCountRemainValid() {
        for (int count : new int[]{1, 3, 4, 6}) {
            var shots = GunShotEmitter.plan(Map.of(id("root"), profile(count, 0, null)), id("root"), new Vec3(0, 1, 0), Vec3.ZERO, 20, 1).orElseThrow();
            assertEquals(count, shots.size());
            shots.forEach(p -> assertEquals(new Vec3(0, 1, 0), p.direction()));
        }
    }

    @Test void momentumIsAddedOnceAndDoesNotSkewDirection() {
        Vec3 aim = new Vec3(0, 0, 1), motion = new Vec3(2, 0, 0);
        var shot = GunShotEmitter.plan(Map.of(id("root"), profile(1, 0, null)), id("root"), aim, motion, 40, 1).orElseThrow().get(0);
        assertEquals(new Vec3(2, 0, 2), shot.velocity());
        assertEquals(aim, shot.direction());
    }
}
