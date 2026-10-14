package com.juicyslew.moonstation14.ms14.singularity.client;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, read-only capture of native chunk-layer VBO submissions selected by visual-status. */
public final class SingularityNativeDrawProbe {
    private static final Set<String> ARMED_SHADER_NAMES = ConcurrentHashMap.newKeySet();
    private SingularityNativeDrawProbe() { }
    public static void arm(Set<String> shaderNames) {
        ARMED_SHADER_NAMES.clear();
        ARMED_SHADER_NAMES.addAll(shaderNames);
    }
    public static boolean armed() { return !ARMED_SHADER_NAMES.isEmpty(); }
    public static void capture(ShaderInstance shader) {
        if (shader == null || !ARMED_SHADER_NAMES.contains(shader.getName())) return;
        Uniform modelViewUniform = shader.getUniform("ModelViewMat");
        if (modelViewUniform == null) return;
        if (!ARMED_SHADER_NAMES.remove(shader.getName())) return;
        Matrix4f modelView = readAppliedModelView(modelViewUniform.getFloatBuffer());
        String determinant = modelView == null ? "unavailable" : Float.toString(
                modelView.m00() * (modelView.m11() * modelView.m22() - modelView.m12() * modelView.m21())
                - modelView.m01() * (modelView.m10() * modelView.m22() - modelView.m12() * modelView.m20())
                + modelView.m02() * (modelView.m10() * modelView.m21() - modelView.m11() * modelView.m20()));
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        var main = Minecraft.getInstance().getMainRenderTarget();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "Singularity one-shot native chunk-layer draw (generic matching-pass submission context, not aimed-PA attribution): shader={} depthTest={} depthWrite={} depthFunc={} cull={} frontFace={} cullMode={} readFbo={} drawFbo={} mainFbo={} appliedModelView3x3Det={}",
                shader.getName(), GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC), GL11.glIsEnabled(GL11.GL_CULL_FACE),
                GL11.glGetInteger(GL11.GL_FRONT_FACE), GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), read, draw,
                 main.frameBufferId, determinant);
    }

    static Matrix4f readAppliedModelView(FloatBuffer source) {
        FloatBuffer isolated = source.duplicate();
        if (isolated.remaining() < 16) return null;
        int p = isolated.position();
        // JOML's FloatBuffer path may use Unsafe for direct native buffers. The
        // unit contract deliberately accepts heap buffers too, so copy absolute
        // indexed values through safe Java access before constructing the matrix.
        return new Matrix4f(
                isolated.get(p), isolated.get(p + 1), isolated.get(p + 2), isolated.get(p + 3),
                isolated.get(p + 4), isolated.get(p + 5), isolated.get(p + 6), isolated.get(p + 7),
                isolated.get(p + 8), isolated.get(p + 9), isolated.get(p + 10), isolated.get(p + 11),
                isolated.get(p + 12), isolated.get(p + 13), isolated.get(p + 14), isolated.get(p + 15));
    }
}
