package com.juicyslew.moonstation14.ms14.singularity.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

import java.util.List;

/** The active world-render lens frame and its immutable CPU-culling source snapshot. */
public final class SingularityWorldLensClient {
    private static Object world;
    private static Frame frame;
    private static Object lastFrameWorld;
    private static boolean lastFrameActive;
    private static boolean occlusionInvalidationPending;
    private SingularityWorldLensClient() { }

    private record Frame(Object world, Vec3 cameraPosition, List<SingularityVisualPreview.WorldSource> sources) { }

    public static void begin(DeltaTracker deltaTracker, Camera camera, Matrix4fc view, Matrix4fc projection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || camera == null || view == null || projection == null
                || SingularityVisualPreview.reducedMotion()) {
            end();
            if (mc.level == null || mc.player == null || SingularityVisualPreview.reducedMotion()) SingularityVisualPreview.resetVisibility();
            if (mc.level != world) world = mc.level;
            recordFrameState(mc.level);
            return;
        }
        if (mc.level != world) {
            SingularityVisualPreview.resetVisibility();
            world = mc.level;
        }
        List<SingularityVisualPreview.WorldSource> sources = SingularityVisualPreview.collectResolvedWorldSources(
                mc, camera, deltaTracker.getGameTimeDeltaPartialTick(false), view);
        if (sources.isEmpty()) { end(); recordFrameState(mc.level); return; }
        SingularityWorldLensUniforms.activate(projection, sources.stream().map(SingularityVisualPreview.WorldSource::uniform).toList());
        frame = new Frame(mc.level, camera.getPosition(), sources);
        recordFrameState(mc.level);
    }

    public static void end() {
        SingularityWorldLensUniforms.deactivate();
        frame = null;
    }

    public static boolean isActive() { return frame != null; }
    public static List<SingularityVisualPreview.WorldSource> worldSources() { return frame == null ? List.of() : frame.sources(); }
    public static Vec3 cameraPosition() { return frame == null ? null : frame.cameraPosition(); }
    public static Object worldIdentity() { return frame == null ? null : frame.world(); }

    public static boolean consumeOcclusionInvalidation() {
        boolean pending = occlusionInvalidationPending;
        occlusionInvalidationPending = false;
        return pending;
    }

    private static void recordFrameState(Object currentWorld) {
        boolean active = frame != null;
        if (shouldInvalidateForFrameTransition(lastFrameActive, active, lastFrameWorld, currentWorld))
            occlusionInvalidationPending = true;
        lastFrameActive = active;
        lastFrameWorld = currentWorld;
    }

    public static boolean shouldInvalidateForFrameTransition(boolean previousActive, boolean active,
                                                              Object previousWorld, Object currentWorld) {
        return previousActive != active || previousWorld != currentWorld;
    }
}
