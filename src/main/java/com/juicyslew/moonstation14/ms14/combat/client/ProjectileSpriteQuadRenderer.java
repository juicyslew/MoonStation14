package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.ProjectileSpriteFrames;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Shared single-frame sprite quad emission for projectile and muzzle-flash presentations. */
public final class ProjectileSpriteQuadRenderer {
    private ProjectileSpriteQuadRenderer() { }
    public static void emit(PoseStack pose, VertexConsumer out, ProjectileSpriteFrames.Frame frame,
                            float alpha, boolean unshaded, int light) {
        var current=pose.last(); var matrix=current.pose(); float size=.5f;
        int a=Math.max(0,Math.min(255,Math.round(255*alpha))), vertexLight=unshaded?LightTexture.FULL_BRIGHT:light;
        out.addVertex(matrix,-size,-size,0).setColor(255,255,255,a).setUv(frame.u0(),1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(vertexLight).setNormal(current,0,0,1);
        out.addVertex(matrix,size,-size,0).setColor(255,255,255,a).setUv(frame.u1(),1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(vertexLight).setNormal(current,0,0,1);
        out.addVertex(matrix,size,size,0).setColor(255,255,255,a).setUv(frame.u1(),0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(vertexLight).setNormal(current,0,0,1);
        out.addVertex(matrix,-size,size,0).setColor(255,255,255,a).setUv(frame.u0(),0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(vertexLight).setNormal(current,0,0,1);
    }
}
