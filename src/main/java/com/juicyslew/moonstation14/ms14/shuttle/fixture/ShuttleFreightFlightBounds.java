package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import org.joml.Vector3dc;

/** Small disengagement-only excursion envelope for the admin freight profile. */
public final class ShuttleFreightFlightBounds {
    public static final double MAX_HORIZONTAL_METERS=64.0;
    public static final double MAX_VERTICAL_METERS=8.0;
    private ShuttleFreightFlightBounds() { }

    public static boolean within(Vector3dc origin,Vector3dc current) {
        if(!finite(origin)||!finite(current)) return false;
        double dx=current.x()-origin.x(),dz=current.z()-origin.z(),dy=current.y()-origin.y();
        return Double.isFinite(dx*dx+dz*dz)&&dx*dx+dz*dz<=MAX_HORIZONTAL_METERS*MAX_HORIZONTAL_METERS
                &&Math.abs(dy)<=MAX_VERTICAL_METERS;
    }

    private static boolean finite(Vector3dc p) {
        return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());
    }
}
