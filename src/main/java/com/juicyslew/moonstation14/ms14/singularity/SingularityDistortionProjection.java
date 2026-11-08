package com.juicyslew.moonstation14.ms14.singularity;

import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** Perspective conversion of an upstream orthographic pixel distortion profile. */
public final class SingularityDistortionProjection {
    private SingularityDistortionProjection() { }

    public record Source(float x, float y, float intensity, float falloff, float cutoffPixels,
                         float depth, float visibility, float strength, float foregroundRampWorld) {
        public Source(float x, float y, float intensity, float falloff, float cutoffPixels) {
            this(x, y, intensity, falloff, cutoffPixels, 1, 1, 1, 1);
        }

        public Source(float x, float y, float intensity, float falloff, float cutoffPixels,
                      float depth, float visibility) {
            this(x, y, intensity, falloff, cutoffPixels, depth, visibility, 1, 1);
        }

        public Source(float x, float y, float intensity, float falloff, float cutoffPixels,
                      float depth, float visibility, float strength) {
            this(x, y, intensity, falloff, cutoffPixels, depth, visibility, strength, 1);
        }
    }

    public record Projected(Source source, float depth, float depthBuffer, boolean inViewport,
                            float spriteHalfExtentPixels) {
        public boolean spriteIntersectsViewport(int width, int height) {
            return nearestFootprintPixel(source.x(), source.y(), spriteHalfExtentPixels, width, height) != null;
        }
    }

    public record Pixel(float x, float y) { }

    /** Foreground attenuation in positive camera-space world units. */
    public static float depthMask(float sceneDepth, float sourceDepth) {
        return depthMask(sceneDepth, sourceDepth, 1f);
    }

    public static float depthMask(float sceneDepth, float sourceDepth, float rampWorld) {
        if (!Float.isFinite(sceneDepth) || !Float.isFinite(sourceDepth) || !Float.isFinite(rampWorld)
                || sceneDepth <= 0 || sourceDepth <= 0 || rampWorld <= 0) {
            return 1;
        }
        return smooth(clamp(1 - (sourceDepth - sceneDepth) / rampWorld, 0, 1));
    }

    public static Pixel radialDisplacement(float dx, float dy, float intensity, float falloff,
                                           float cutoff, float visibility) {
        return radialDisplacement(dx, dy, intensity, falloff, cutoff, visibility, 1f);
    }

    public static Pixel radialDisplacement(float dx, float dy, float intensity, float falloff,
                                           float cutoff, float visibility, float strength) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy) || !Float.isFinite(intensity) || !Float.isFinite(falloff)
                || !Float.isFinite(cutoff) || !Float.isFinite(visibility) || !Float.isFinite(strength)
                || intensity <= 0 || falloff <= 0 || cutoff <= 0 || strength <= 0) {
            return new Pixel(0, 0);
        }

        double r = Math.hypot(dx, dy);
        if (r >= cutoff || r == 0) {
            return new Pixel(0, 0);
        }

        double safeR = Math.max(r, 1e-6);
        double coefficient = SingularityRadialResponse.evaluate(safeR, intensity, falloff, cutoff).value();

        double scale = strength * coefficient * Math.max(0, Math.min(1, visibility));
        return new Pixel((float) (dx * scale), (float) (dy * scale));
    }

    public static float reconstructCameraDepth(Matrix4fc inverseProjection, float u, float v, float depthBuffer) {
        if (inverseProjection == null || !Float.isFinite(u) || !Float.isFinite(v) || !Float.isFinite(depthBuffer)
                || depthBuffer >= 1) {
            return Float.POSITIVE_INFINITY;
        }
        Vector4f eye = inverseProjection.transform(new Vector4f(2 * u - 1, 2 * v - 1, 2 * depthBuffer - 1, 1));
        return Float.isFinite(eye.w) && Math.abs(eye.w) > 1e-20f && Float.isFinite(eye.z)
                ? -eye.z / eye.w
                : Float.POSITIVE_INFINITY;
    }

    public static Pixel map(float x, float y, int width, int height, java.util.List<Source> sources,
                            java.util.function.BiFunction<Float, Float, Float> cameraDepthAtNode) {
        if (width <= 0 || height <= 0 || !Float.isFinite(x) || !Float.isFinite(y)
                || sources == null || sources.isEmpty()) {
            return new Pixel(x, y);
        }

        float px = x;
        float py = y;
        for (Source source : sources) {
            Pixel full = radialDisplacement(px - source.x(), py - source.y(), source.intensity(),
                    source.falloff(), source.cutoffPixels(), source.visibility(), source.strength());
            float weight = sampleWeight(px, py, width, height, source, cameraDepthAtNode);
            px -= weight * full.x();
            py -= weight * full.y();
        }
        return new Pixel(px, py);
    }

    private static float sampleWeight(float x, float y, int width, int height, Source source,
                                      java.util.function.BiFunction<Float, Float, Float> depth) {
        float cx = clamp(x, .5f, width - .5f);
        float cy = clamp(y, .5f, height - .5f);
        float x0 = (float) Math.floor(cx - .5f) + .5f;
        float y0 = (float) Math.floor(cy - .5f) + .5f;
        float x1 = Math.min(width - .5f, x0 + 1);
        float y1 = Math.min(height - .5f, y0 + 1);
        float fx = cx - x0;
        float fy = cy - y0;

        float a = depthMask(depth.apply(x0, y0), source.depth(), source.foregroundRampWorld());
        float b = depthMask(depth.apply(x1, y0), source.depth(), source.foregroundRampWorld());
        float c = depthMask(depth.apply(x0, y1), source.depth(), source.foregroundRampWorld());
        float d = depthMask(depth.apply(x1, y1), source.depth(), source.foregroundRampWorld());
        return mix(mix(a, b, fx), mix(c, d, fx), fy);
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    /** Nearest valid pixel center in the intersection of the projected square sprite and viewport. */
    public static Pixel nearestFootprintPixel(float x, float y, float halfExtent, int width, int height) {
        return nearestFootprintPixel(x, y, x, y, halfExtent, width, height);
    }

    public static Pixel nearestFootprintPixel(float x, float y, float sourceX, float sourceY,
                                              float halfExtent, int width, int height) {
        if (width <= 0 || height <= 0 || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(sourceX) || !Float.isFinite(sourceY)
                || !Float.isFinite(halfExtent) || halfExtent < 0) {
            return null;
        }

        float minX = Math.max(.5f, (float) Math.ceil(sourceX - halfExtent - .5f) + .5f);
        float maxX = Math.min(width - .5f, (float) Math.floor(sourceX + halfExtent - .5f) + .5f);
        float minY = Math.max(.5f, (float) Math.ceil(sourceY - halfExtent - .5f) + .5f);
        float maxY = Math.min(height - .5f, (float) Math.floor(sourceY + halfExtent - .5f) + .5f);
        if (minX > maxX || minY > maxY) {
            return null;
        }

        float queryX = (float) Math.floor(x) + .5f;
        float queryY = (float) Math.floor(y) + .5f;
        return new Pixel(Math.max(minX, Math.min(maxX, queryX)), Math.max(minY, Math.min(maxY, queryY)));
    }

    /** Projects camera-relative world coordinates using the exact matrices supplied by the level-stage event. */
    public static Projected project(SingularityVisualProfile profile, float x, float y, float z,
                                    Matrix4fc modelView, Matrix4fc projection, int width, int height,
                                    float verticalFocalPixels) {
        if (profile == null || modelView == null || projection == null || width <= 0 || height <= 0
                || !Float.isFinite(verticalFocalPixels) || verticalFocalPixels <= 0) {
            return null;
        }

        Vector4f view = modelView.transform(new Vector4f(x, y, z, 1));
        if (!Float.isFinite(view.z) || view.z >= 0) {
            return null;
        }
        Vector4f clip = projection.transform(new Vector4f(view.x, view.y, view.z, view.w));
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || !Float.isFinite(clip.z)
                || !Float.isFinite(clip.w) || clip.w <= 0) {
            return null;
        }

        float nx = clip.x / clip.w;
        float ny = clip.y / clip.w;
        float nz = clip.z / clip.w;
        if (!Float.isFinite(nx) || !Float.isFinite(ny) || !Float.isFinite(nz) || nz < -1 || nz > 1) {
            return null;
        }
        float px = (nx * .5f + .5f) * width;
        float py = (ny * .5f + .5f) * height;
        Source projected = project(profile, px, py, verticalFocalPixels / -view.z);
        if (projected == null || projected.cutoffPixels <= 0 || projected.intensity() <= 0) {
            return null;
        }

        double ox = Math.max(0, Math.max(-px, px - width));
        double oy = Math.max(0, Math.max(-py, py - height));
        if (Math.hypot(ox, oy) > projected.cutoffPixels) {
            return null;
        }
        float halfExtent = profile.spriteHalfExtent() * verticalFocalPixels / -view.z;
        Source source = new Source(projected.x(), projected.y(), projected.intensity(), projected.falloff(),
                projected.cutoffPixels(), -view.z, 1, projected.strength(), profile.foregroundRampWorldUnits());
        return new Projected(source, -view.z, nz * .5f + .5f, px >= 0 && px <= width && py >= 0 && py <= height,
                halfExtent);
    }

    /** Pixel scale is the projection derivative (vertical focal pixels / source camera-space depth). */
    public static Source project(SingularityVisualProfile profile, float xPixels, float yPixels,
                                 float pixelsPerWorldUnit) {
        if (profile == null || !Float.isFinite(profile.cutoffWorldUnits()) || profile.cutoffWorldUnits() <= 0
                || !Float.isFinite(xPixels) || !Float.isFinite(yPixels)
                || !Float.isFinite(pixelsPerWorldUnit) || pixelsPerWorldUnit <= 0) {
            return null;
        }

        double pixelsPerSs14Meter = pixelsPerWorldUnit / SingularityVisualProfile.PIXELS_PER_METER;
        double intensity = profile.intensity() * Math.pow(pixelsPerSs14Meter, profile.falloff());
        double cutoff = profile.cutoffWorldUnits() * pixelsPerWorldUnit;
        if (!Double.isFinite(intensity) || !Double.isFinite(cutoff)
                || intensity > Float.MAX_VALUE || cutoff > Float.MAX_VALUE) {
            return null;
        }
        return new Source(xPixels, yPixels, (float) intensity, profile.falloff(), (float) cutoff,
                1, 1, profile.lensStrength(), profile.foregroundRampWorldUnits());
    }
}
