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
        emit(pose,out,frame,alpha,unshaded,light,0xffffffff);
    }
    public static void emit(PoseStack pose, VertexConsumer out, ProjectileSpriteFrames.Frame frame,
                            float alpha, boolean unshaded, int light, int color) {
        var current=pose.last(); var matrix=current.pose(); float size=.5f;
        int a=Math.max(0,Math.min(255,Math.round(((color>>>24)&255)*alpha))), vertexLight=unshaded?LightTexture.FULL_BRIGHT:light;
        int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;
        out.addVertex(matrix,-size,-size,0).setColor(r,g,b,a).setUv(frame.u0(),1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(vertexLight).setNormal(current,0,0,1);
        out.addVertex(matrix,size,-size,0).setColor(r,g,b,a).setUv(frame.u1(),1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(vertexLight).setNormal(current,0,0,1);
        out.addVertex(matrix,size,size,0).setColor(r,g,b,a).setUv(frame.u1(),0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(vertexLight).setNormal(current,0,0,1);
        out.addVertex(matrix,-size,size,0).setColor(r,g,b,a).setUv(frame.u0(),0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(vertexLight).setNormal(current,0,0,1);
    }
}
