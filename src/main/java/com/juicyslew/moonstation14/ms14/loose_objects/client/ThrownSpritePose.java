package com.juicyslew.moonstation14.ms14.loose_objects.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Maps an item's authored in-plane forward axis onto a world-space travel vector. */
final class ThrownSpritePose {
    private ThrownSpritePose() { }
    static Quaternionf rotation(float forwardDegrees, Vector3f direction) {
        if (direction == null || !Float.isFinite(direction.x) || !Float.isFinite(direction.y)
                || !Float.isFinite(direction.z) || direction.lengthSquared() < 1e-8f) return null;
        double radians = Math.toRadians(forwardDegrees);
        Vector3f source = new Vector3f((float)Math.cos(radians), (float)Math.sin(radians), 0);
        return new Quaternionf().rotationTo(source, new Vector3f(direction).normalize());
    }
}
