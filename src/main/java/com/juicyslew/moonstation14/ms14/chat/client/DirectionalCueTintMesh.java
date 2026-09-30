package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload.Mode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** Bounded feathered GUI mesh: alpha falls off both inward and tangentially. */
final class DirectionalCueTintMesh {
    static final int MENTION_RGB = 0xffdc5f;
    private DirectionalCueTintMesh() { }

    record Style(double depth, double span, double peak, double lateralPower,
                  int red, int green, int blue) { }
    static Style style(Mode mode, boolean mention, double intensity) {
        double strength = Double.isNaN(intensity) ? 0 : Math.max(0, Math.min(1, intensity));
        boolean whisper = mode == Mode.WHISPER || mode == Mode.W_MUFFLED;
        int red = mention ? 255 : whisper ? 81 : 75;
        int green = mention ? 220 : whisper ? 220 : 204;
        int blue = mention ? 95 : whisper ? 240 : 244;
        if (whisper) return new Style(48 + 105 * strength, 18 + 20 * strength,
                .08 + .82 * Math.pow(strength, 1.1), 8, red, green, blue);
        return new Style(43 + 49 * strength, 80 + 75 * strength,
                .09 + .48 * strength, 2, red, green, blue);
    }

    static double alpha(Style style, double inward, double lateral) {
        if (inward < 0 || inward >= style.depth || Math.abs(lateral) >= style.span) return 0;
        double d = inward / style.depth;
        double t = lateral / style.span;
        return style.peak * Math.pow(1 - d, style.lateralPower >= 5 ? 1.3 : 2)
                * Math.pow(1 - t * t, style.lateralPower);
    }

    static void emit(GuiGraphics graphics, DirectionalCueGeometry.Point point, double intensity, double fade,
                       Mode mode, boolean mention, @Nullable LocalSpeechReviewOverlay.Layout panel) {
        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        emit(graphics.guiWidth(), graphics.guiHeight(), point, intensity, fade, mode, mention, panel,
                (x, y, alpha, style) -> vertex(vertices, matrix, x, y, alpha, style));
    }

    @FunctionalInterface
    interface VertexSink {
        void vertex(double x, double y, double alpha, Style style);
    }

    static void emit(int width, int height, DirectionalCueGeometry.Point point, double intensity, double fade,
                       Mode mode, boolean mention, @Nullable LocalSpeechReviewOverlay.Layout panel, VertexSink vertices) {
        if (width < 180 || height < 140 || intensity <= 0 || fade <= 0) return;
        Style base = style(mode, mention, intensity);
        Style style = new Style(base.depth(), base.span(), base.peak() * Math.min(1, fade), base.lateralPower(),
                base.red(), base.green(), base.blue());
        // An occluded front-center speaker glows near the reticle; rear stays at the bottom.
        if (point.centeredFront()) {
            halo(vertices, point, style);
            return;
        }
        // Pick the nearest physical viewport edge, not the HUD-adjusted projection bounds.
        // A side point near the bottom of a short window must still glow on the side.
        boolean side = Math.min(point.x(), width - point.x())
                <= Math.min(point.y(), height - point.y());
        if (point.rear() && Math.abs(point.x() - width / 2.0) < 30) side = false;
        boolean positive = side ? point.x() > width / 2.0 : point.y() > height / 2.0;
        double edge = side ? (positive ? width : 0) : (positive ? height : 0);
        int depthSteps = 8, lateralSteps = 24;
        for (int i = 0; i < depthSteps; i++) for (int j = 0; j < lateralSteps; j++) {
            double d0 = style.depth * i / depthSteps, d1 = style.depth * (i + 1) / depthSteps;
            double l0 = style.span * (2.0 * j / lateralSteps - 1);
            double l1 = style.span * (2.0 * (j + 1) / lateralSteps - 1);
            double x0 = side ? edge + (positive ? -d0 : d0) : point.x() + l0;
            double x1 = side ? edge + (positive ? -d1 : d1) : point.x() + l1;
            double y0 = side ? point.y() + l0 : edge + (positive ? -d0 : d0);
            double y1 = side ? point.y() + l1 : edge + (positive ? -d1 : d1);
            // GuiGraphics.fillGradient uses ascending x/y with this vertex winding.
            // Derive alpha from the clipped spatial corners, not from traversal order:
            // right/bottom edges traverse inward in the opposite coordinate direction.
            double left = Math.max(0, Math.min(x0, x1));
            double right = Math.min(width, Math.max(x0, x1));
            double top = Math.max(0, Math.min(y0, y1));
            double bottom = Math.min(height, Math.max(y0, y1));
            if (left >= right || top >= bottom) continue;
            quad(vertices, left, top, right, bottom,
                    cornerAlpha(style, side, edge, point, left, top, height, panel),
                    cornerAlpha(style, side, edge, point, left, bottom, height, panel),
                    cornerAlpha(style, side, edge, point, right, bottom, height, panel),
                    cornerAlpha(style, side, edge, point, right, top, height, panel), style);
        }
    }

    private static double cornerAlpha(Style style, boolean side, double edge,
                                      DirectionalCueGeometry.Point point, double x, double y, int height,
                                      @Nullable LocalSpeechReviewOverlay.Layout panel) {
        double attenuation = panel != null && x >= panel.left() && x <= panel.right()
                && y >= panel.top() && y <= panel.bottom() ? .72 : 1;
        // Protect only the input/hotbar strip, not the inward spike of a rear cue.
        // A gentle ramp preserves a nonzero physical-edge peak without a hard seam.
        if (y > height - 18) attenuation *= 1 - .14 * (y - (height - 18)) / 18;
        return alpha(style, Math.abs((side ? x : y) - edge), (side ? y - point.y() : x - point.x()))
                * attenuation;
    }

    private static void halo(VertexSink vertices, DirectionalCueGeometry.Point point, Style style) {
        // Four tiny feathered lobes, away from the crosshair itself.
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2;
            double cx = point.x() + Math.cos(angle) * 18, cy = point.y() + Math.sin(angle) * 18;
            for (int ring = 0; ring < 4; ring++) {
                double radius = 3 + ring * 3;
                double a = style.peak * .14 * (1 - ring / 4.0);
                quad(vertices, cx - radius, cy - radius, cx + radius, cy + radius,
                        0, a, 0, a, style);
            }
        }
    }

    private static void quad(VertexSink vertices, double x0, double y0,
                              double x1, double y1, double a0, double a1, double a2, double a3, Style style) {
        vertices.vertex(x0, y0, a0, style);
        vertices.vertex(x0, y1, a1, style);
        vertices.vertex(x1, y1, a2, style);
        vertices.vertex(x1, y0, a3, style);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, double x, double y,
                               double alpha, Style style) {
        vertices.addVertex(matrix, (float) x, (float) y, 0)
                .setColor(style.red, style.green, style.blue, (int) Math.round(255 * Math.max(0, Math.min(.84, alpha))));
    }
}
