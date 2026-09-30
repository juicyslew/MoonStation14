package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Camera-space projection and bounded, screen-space edge intersection. No world queries. */
public final class DirectionalCueGeometry {
    private DirectionalCueGeometry() { }

    public record Point(double x, double y, boolean comfortablyVisible, int turn,
                        boolean centeredFront, boolean rear) { }

    public static Vec3 bearing(int sector, int band) {
        if (sector < 0 || sector >= LocalSpeechPayload.HORIZONTAL_SECTORS
                || band < LocalSpeechPayload.BAND_LOW || band > LocalSpeechPayload.BAND_DOWN) return null;
        if (band == LocalSpeechPayload.BAND_UP) return sector == 0 ? new Vec3(0, 1, 0) : null;
        if (band == LocalSpeechPayload.BAND_DOWN) return sector == 0 ? new Vec3(0, -1, 0) : null;
        double angle = sector * Math.PI * 2 / LocalSpeechPayload.HORIZONTAL_SECTORS;
        return new Vec3(Math.sin(angle), band == LocalSpeechPayload.BAND_HIGH ? .55
                : band == LocalSpeechPayload.BAND_LOW ? -.55 : 0, Math.cos(angle)).normalize();
    }

    /** Projection is copied from the level event, not reconstructed from a guessed FOV. */
    public static Point project(Vec3 direction, Quaternionf cameraRotation, Matrix4f projection,
                                int width, int height, boolean verticalOnly) {
        if (direction == null || cameraRotation == null || projection == null || width < 48 || height < 90
                || !finite(direction.x) || !finite(direction.y) || !finite(direction.z)
                || direction.lengthSqr() < 1.0e-12 || !projection.isFinite()
                || !Float.isFinite(projection.determinant()) || Math.abs(projection.determinant()) < 1.0e-12f
                || !finite(cameraRotation.x) || !finite(cameraRotation.y)
                || !finite(cameraRotation.z) || !finite(cameraRotation.w)) return null;
        Vector3f view = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)
                .rotate(new Quaternionf(cameraRotation).conjugate());
        if (!Float.isFinite(view.x) || !Float.isFinite(view.y) || !Float.isFinite(view.z)) return null;
        boolean inFront = view.z < -1.0e-5;
        Vector4f clip = new Vector4f(view.x, view.y, view.z, 1).mul(projection);
        double nx = inFront && finite(clip.w) && clip.w > 1.0e-5 ? clip.x / clip.w : Double.NaN;
        double ny = inFront && finite(clip.w) && clip.w > 1.0e-5 ? clip.y / clip.w : Double.NaN;
        // For a rear source the projected point reverses direction. Use its camera-space
        // lateral/up components instead, including a deterministic bottom fallback at 180°.
        if (!finite(nx) || !finite(ny)) {
            nx = view.x;
            ny = view.y;
            double scale = Math.max(Math.abs(nx), Math.abs(ny));
            if (scale < 1.0e-6) { nx = 0; ny = -1; }
            else { nx /= scale; ny /= scale; }
        }
        if (verticalOnly) { nx = 0; ny = direction.y > 0 ? 1 : -1; }
        boolean comfortable = inFront && Math.abs(nx) < .78 && Math.abs(ny) < .72 && !verticalOnly;
        double magnitude = Math.max(Math.abs(nx), Math.abs(ny));
        boolean centeredFront = inFront && !verticalOnly && magnitude < 1.0e-8;
        if (centeredFront) return new Point(width / 2.0, height / 2.0 - 14,
                comfortable, 0, true, false);
        if (magnitude < 1.0e-8) { nx = 0; ny = -1; magnitude = 1; }
        // Intersect the physical GUI boundary; attenuation over HUD/input is handled by the mesh.
        double halfWidth = Math.max(8, width / 2.0 - 16);
        double top = 0, bottom = height;
        double vertical = ny >= 0 ? height / 2.0 - top : bottom - height / 2.0;
        if (vertical <= 0) return null;
        double factor = Math.min(halfWidth / Math.max(1.0e-9, Math.abs(nx)),
                vertical / Math.max(1.0e-9, Math.abs(ny)));
        double x = Math.max(16, Math.min(width - 16, width / 2.0 + nx * factor));
        double y = Math.max(top, Math.min(bottom, height / 2.0 - ny * factor));
        if (!finite(x) || !finite(y)) return null;
        int turn = !inFront ? 2 : magnitude > 1.4 ? 2 : magnitude > .8 ? 1 : 0;
        return new Point(x, y, comfortable, turn, false, !inFront && !verticalOnly);
    }

    /** True only when the projected point is in front of the camera and inside the real viewport. */
    public static boolean inViewport(Vec3 direction, Quaternionf cameraRotation, Matrix4f projection) {
        if (direction == null || cameraRotation == null || projection == null || !projection.isFinite()
                || !finite(direction.x) || !finite(direction.y) || !finite(direction.z)) return false;
        Vector3f view = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)
                .rotate(new Quaternionf(cameraRotation).conjugate());
        if (!Float.isFinite(view.x) || !Float.isFinite(view.y) || !Float.isFinite(view.z) || view.z >= -1.0e-5)
            return false;
        Vector4f clip = new Vector4f(view.x, view.y, view.z, 1).mul(projection);
        if (!finite(clip.w) || clip.w <= 1.0e-5) return false;
        double nx = clip.x / clip.w, ny = clip.y / clip.w;
        return finite(nx) && finite(ny) && nx >= -1 && nx <= 1 && ny >= -1 && ny <= 1;
    }

    /** Tests feet, torso, and head against the camera viewport without querying the world. */
    public static boolean bodyInViewport(Vec3 feet, double height, Vec3 camera,
                                         Quaternionf cameraRotation, Matrix4f projection) {
        if (feet == null || camera == null || !finite(height) || height <= 0) return false;
        for (double fraction : new double[]{0, .5, .95}) {
            Vec3 point = new Vec3(feet.x, feet.y + height * fraction, feet.z).subtract(camera);
            if (inViewport(point, cameraRotation, projection)) return true;
        }
        return false;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
