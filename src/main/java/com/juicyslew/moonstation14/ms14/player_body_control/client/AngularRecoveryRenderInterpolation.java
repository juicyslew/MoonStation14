package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.joml.Quaternionf;

/** Render-only history for the network-tick angular pose. */
public final class AngularRecoveryRenderInterpolation {
    private int tick = Integer.MIN_VALUE;
    private Quaternionf previous;
    private Quaternionf current;

    /** Interpolates the actual immutable movement interval, independent of display-tick history. */
    public static Quaternionf sampleCommittedEndpoints(Quaternionf from,Quaternionf to,float partialTick) {
        Quaternionf a=finiteNormalized(from),b=finiteNormalized(to);
        if(a.dot(b)<0)b.set(-b.x,-b.y,-b.z,-b.w);
        return a.slerp(b,Float.isFinite(partialTick)?Math.max(0f,Math.min(1f,partialTick)):0f).normalize();
    }

    private static Quaternionf finiteNormalized(Quaternionf value) {
        if(value==null||!Float.isFinite(value.x)||!Float.isFinite(value.y)||!Float.isFinite(value.z)||!Float.isFinite(value.w))
            return new Quaternionf();
        float length=value.lengthSquared();
        return !Float.isFinite(length)||length<1e-12f?new Quaternionf():new Quaternionf(value).normalize();
    }

    public Quaternionf sample(int renderTick, Quaternionf pose, float partialTick) {
        Quaternionf target = new Quaternionf(pose).normalize();
        if (current == null || renderTick < tick || renderTick > tick + 1) {
            previous = new Quaternionf(target);
            current = new Quaternionf(target);
        } else if (renderTick == tick) {
            current.set(target);
        } else {
            previous.set(current);
            current.set(target);
        }
        tick = renderTick;
        Quaternionf result = new Quaternionf(previous);
        if (result.dot(current) < 0) result.set(-result.x, -result.y, -result.z, -result.w);
        result.slerp(current, Math.max(0, Math.min(1, partialTick))).normalize();
        return result;
    }

    public void clear() { tick = Integer.MIN_VALUE; previous = current = null; }
}
