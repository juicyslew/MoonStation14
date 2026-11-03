package com.juicyslew.moonstation14.entities;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small swept-contact geometry shared by thrown loose items and focused CPU tests. */
final class ThrownContactPhysics {
    private ThrownContactPhysics() { }

    static AABB sourceBaseSweep(AABB target, double itemWidth, double itemHeight) {
        double half = itemWidth * .5;
        return new AABB(target.minX - half, target.minY - itemHeight, target.minZ - half,
                target.maxX + half, target.maxY, target.maxZ + half);
    }

    static Vec3 normal(AABB bounds, Vec3 point, Vec3 incoming) {
        double[] distances = {Math.abs(point.x - bounds.minX), Math.abs(point.x - bounds.maxX),
                Math.abs(point.y - bounds.minY), Math.abs(point.y - bounds.maxY),
                Math.abs(point.z - bounds.minZ), Math.abs(point.z - bounds.maxZ)};
        Vec3[] normals = {new Vec3(-1,0,0), new Vec3(1,0,0), new Vec3(0,-1,0),
                new Vec3(0,1,0), new Vec3(0,0,-1), new Vec3(0,0,1)};
        int chosen = 0;
        for (int i=1;i<distances.length;i++)
            if (distances[i] < distances[chosen] - 1e-9
                    || (Math.abs(distances[i]-distances[chosen]) <= 1e-9
                    && incoming.dot(normals[i]) < incoming.dot(normals[chosen]))) chosen=i;
        return normals[chosen];
    }

    static Vec3 reflect(Vec3 velocity, Vec3 normal, double restitution) {
        double dot = velocity.dot(normal);
        return dot < 0 ? velocity.subtract(normal.scale((1 + restitution) * dot)) : velocity;
    }

    /** Contact is the source-base coordinate; place it outside the expanded target face. */
    static Vec3 outside(Vec3 contact, Vec3 normal, double width, double height) {
        double eps = 1e-4;
        double x=contact.x, y=contact.y, z=contact.z;
        if (normal.x != 0) x += normal.x * (width*.5 + eps);
        else if (normal.z != 0) z += normal.z * (width*.5 + eps);
        else if (normal.y > 0) y += eps;
        else y -= height + eps;
        return new Vec3(x,y,z);
    }
}
