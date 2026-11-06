package com.juicyslew.moonstation14.ms14.player_body_control.client;

import java.util.UUID;
import java.util.Optional;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

/** Consumes native-frame yaw changes once for an exact controlled support identity. */
final class ClientSupportedHeading {
    private long epoch;
    private UUID body, frame;
    private Quaterniond previous;
    private boolean bound;
    private Transition transition;

    private record Transition(long epoch, UUID body, long tick) {}

    /** Orientation conversion shared by the committed publication consumer and its live carry. */
    static Quaterniond orientation(dev.ryanhcode.sable.companion.math.Pose3dc pose) {
        if (pose == null) return null;
        var q=pose.orientation();
        return normalizedCopy(new Quaterniond(q.x(),q.y(),q.z(),q.w()));
    }

    /** Freeze the supported pose basis at the completed move boundary. */
    void transition(long currentEpoch, UUID currentBody, UUID currentFrame, long tick) {
        if (currentBody == null || currentFrame == null) return;
        transition = new Transition(currentEpoch,currentBody,tick);
    }

    Optional<Float> transitionRenderOffset(long currentEpoch, UUID currentBody, long tick, float partial) {
        Transition t=transition;
        if (t == null || t.epoch != currentEpoch || !t.body.equals(currentBody) || t.tick != tick
                || !Float.isFinite(partial)) return Optional.empty();
        return Optional.of(0f);
    }

    /** Accepted authority replaces the published interval even when its body tick is unchanged. */
    void authorityRestored() { transition = null; }

    /** Prefer the frozen transition only while it is valid; otherwise use current supported pose. */
    static float presentationOffset(Optional<Float> bridge, float supportedOffset) {
        return bridge.orElse(supportedOffset);
    }

    float carry(long currentEpoch, UUID currentBody, UUID currentFrame, Quaterniondc orientation,
                boolean supported, boolean live) {
        Quaterniond current = normalizedCopy(orientation);
        if (!live || !supported || currentFrame == null || currentBody == null || current == null) {
            reset();
            return 0;
        }
        if (!bound || epoch != currentEpoch || !currentBody.equals(body) || !currentFrame.equals(frame)) {
            epoch = currentEpoch; body = currentBody; frame = currentFrame; previous = current; bound = true;
            return 0;
        }
        Quaterniond change = new Quaterniond(current).mul(new Quaterniond(previous).conjugate()).normalize();
        previous = current;
        return com.juicyslew.moonstation14.ms14.standing.WorldUpTwist.degrees(change);
    }

    /** Establish the completed landing pose as the next live interval's baseline without consuming it. */
    void seed(long currentEpoch, UUID currentBody, UUID currentFrame, Quaterniondc orientation, boolean live) {
        Quaterniond current = normalizedCopy(orientation);
        if (!live || currentFrame == null || currentBody == null || current == null) return;
        if (bound && epoch == currentEpoch && currentBody.equals(body) && currentFrame.equals(frame)) return;
        epoch=currentEpoch; body=currentBody; frame=currentFrame; previous=current; bound=true;
    }

    /** Read-only render offset from the last logical pose consumed for this exact identity. */
    synchronized float renderOffset(long currentEpoch, UUID currentBody, UUID currentFrame,
                                    Quaterniondc renderOrientation, boolean supported) {
        Quaterniond render = normalizedCopy(renderOrientation);
        if (!supported || !bound || epoch != currentEpoch || currentBody == null || currentFrame == null
                || !currentBody.equals(body) || !currentFrame.equals(frame) || render == null || previous == null)
            return 0;
        return com.juicyslew.moonstation14.ms14.standing.WorldUpTwist.degrees(
                new Quaterniond(render).mul(new Quaterniond(previous).conjugate()).normalize());
    }

    private static Quaterniond normalizedCopy(Quaterniondc q) {
        if (q == null) return null;
        double x=q.x(), y=q.y(), z=q.z(), w=q.w();
        double length=Math.sqrt(x*x+y*y+z*z+w*w);
        if (!Double.isFinite(length) || length == 0d) return null;
        return new Quaterniond(x/length,y/length,z/length,w/length);
    }

    void reset() { bound = false; body = frame = null; transition = null; }

    /** Drop the old support basis while the controlled body's camera is unavailable. */
    void resetForOwnershipGap() { reset(); }

    static float normalize(float yaw) {
        yaw %= 360f;
        if (yaw > 180f) yaw -= 360f;
        if (yaw < -180f) yaw += 360f;
        return yaw;
    }
}
