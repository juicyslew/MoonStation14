package com.juicyslew.moonstation14.entities.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProjectileBillboardTest {
    @Test void longitudinalTextureAxisFollowsAllSixCardinalFlightDirections() {
        for (Vec3 direction : new Vec3[]{new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1)}) {
            Vector3f longitudinal = ProjectileBillboard.flightRotation(direction).transform(new Vector3f(0, 1, 0));
            assertEquals(direction.x, longitudinal.x, 1.0e-6);
            assertEquals(direction.y, longitudinal.y, 1.0e-6);
            assertEquals(direction.z, longitudinal.z, 1.0e-6);
        }
    }
}
