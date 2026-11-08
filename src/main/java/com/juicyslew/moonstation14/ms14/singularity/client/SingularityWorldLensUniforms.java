package com.juicyslew.moonstation14.ms14.singularity.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL20;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Frame-scoped uniform bridge for the shared singularity vertex lens. */
public final class SingularityWorldLensUniforms {
    public static final int MAX_SOURCES = 5;
    public static final String CORE_PROGRAM = "moonstation14:singularity_core";
    private static final Map<ShaderInstance, Locations> LOCATIONS = new WeakHashMap<>();
    private static Frame frame;
    private static int suspensionDepth;

    private SingularityWorldLensUniforms() { }

    public record Source(float eyeX, float eyeY, float eyeZ, float rawIntensity, float falloff,
                         float cutoffSsPixels, float strengthTimesVisibility, float foregroundRampWorld) {
        public Source {
            if (!Float.isFinite(eyeX) || !Float.isFinite(eyeY) || !Float.isFinite(eyeZ)
                    || !Float.isFinite(rawIntensity) || !Float.isFinite(falloff)
                    || !Float.isFinite(cutoffSsPixels) || !Float.isFinite(strengthTimesVisibility)
                    || !Float.isFinite(foregroundRampWorld)) throw new IllegalArgumentException("non-finite lens source field");
        }
    }

    private record Frame(List<Source> sources, float[] projection, float[] inverseProjection) { }
    private record Locations(int count, int projection, int inverseProjection, int[] source, int[] meta) { }

    public static void activate(Matrix4fc projection, List<Source> sources) {
        if (projection == null || sources == null) throw new NullPointerException();
        if (sources.size() > MAX_SOURCES) throw new IllegalArgumentException("at most five lens sources are supported");
        float[] p = new float[16], inv = new float[16];
        projection.get(p);
        new Matrix4f(projection).invert().get(inv);
        frame = new Frame(List.copyOf(sources), p, inv);
    }

    public static void deactivate() { frame = null; }

    /** Pure state inspection used by diagnostics and focused packing tests. */
    public static int activeSourceCount() { return frame == null ? 0 : frame.sources().size(); }
    static float[] packedProjection() { return frame == null ? null : frame.projection().clone(); }
    static float[] packedInverseProjection() { return frame == null ? null : frame.inverseProjection().clone(); }
    public static boolean isSuspended() { return suspensionDepth != 0; }

    /** Suspends lens application across immediate draws; pair with try-with-resources. */
    public static Suspension suspend() {
        suspensionDepth++;
        return new Suspension();
    }

    public static final class Suspension implements AutoCloseable {
        private boolean closed;
        private Suspension() { }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (suspensionDepth > 0) suspensionDepth--;
        }
    }

    public static void apply(ShaderInstance shader) {
        int program = shader.getId();
        Locations loc = LOCATIONS.computeIfAbsent(shader, ignored -> locations(program));
        Frame active = frame;
        boolean enabled = active != null && suspensionDepth == 0 && !CORE_PROGRAM.equals(shader.getName())
                && !active.sources().isEmpty();
        GL20.glUniform1i(loc.count(), enabled ? active.sources().size() : 0);
        if (!enabled) return;
        GL20.glUniformMatrix4fv(loc.projection(), false, active.projection());
        GL20.glUniformMatrix4fv(loc.inverseProjection(), false, active.inverseProjection());
        for (int i = 0; i < active.sources().size(); i++) {
            Source s = active.sources().get(i);
            GL20.glUniform4f(loc.source()[i], s.eyeX(), s.eyeY(), s.eyeZ(), s.rawIntensity());
            GL20.glUniform4f(loc.meta()[i], s.falloff(), s.cutoffSsPixels(), s.strengthTimesVisibility(), s.foregroundRampWorld());
        }
    }

    public static void forget(ShaderInstance shader) { LOCATIONS.remove(shader); }

    private static Locations locations(int program) {
        int[] source = new int[MAX_SOURCES], meta = new int[MAX_SOURCES];
        for (int i = 0; i < MAX_SOURCES; i++) {
            source[i] = GL20.glGetUniformLocation(program, "MS14LensSource" + i);
            meta[i] = GL20.glGetUniformLocation(program, "MS14LensMeta" + i);
        }
        return new Locations(GL20.glGetUniformLocation(program, "MS14LensCount"),
                GL20.glGetUniformLocation(program, "MS14LensProjection"),
                GL20.glGetUniformLocation(program, "MS14LensInverseProjection"), source, meta);
    }
}
