package com.juicyslew.moonstation14.entities.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared crossed-plane projectile billboard; callers own their size, texture and lighting. */
final class ProjectileBillboard {
    private ProjectileBillboard() { }
    static void renderCrossed(PoseStack pose, MultiBufferSource buffers, RenderType type, int light) {
        renderCrossed(pose,buffers,type,light,0,0,1,1);
    }
    static void renderCrossed(PoseStack pose, MultiBufferSource buffers, RenderType type, int light,
                              float minU,float minV,float maxU,float maxV) {
        VertexConsumer out=buffers.getBuffer(type); PoseStack.Pose matrix=pose.last();
        quad(out,matrix,-1,-1,0,1,-1,0,1,1,0,-1,1,0,light,minU,minV,maxU,maxV);
        quad(out,matrix,0,-1,-1,0,-1,1,0,1,1,0,1,-1,light,minU,minV,maxU,maxV);
    }
    static Quaternionf flightRotation(Vec3 velocity) {
        if (velocity.lengthSqr() < 1.0e-12) return new Quaternionf();
        Vector3f direction = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z).normalize();
        return new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction);
    }
    private static void quad(VertexConsumer out, PoseStack.Pose pose,float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz,int light,
                             float minU,float minV,float maxU,float maxV) {
        vertex(out,pose,ax,ay,az,minU,maxV,light); vertex(out,pose,bx,by,bz,maxU,maxV,light);
        vertex(out,pose,cx,cy,cz,maxU,minV,light); vertex(out,pose,dx,dy,dz,minU,minV,light);
    }
    private static void vertex(VertexConsumer out, PoseStack.Pose pose,float x,float y,float z,float u,float v,int light) {
        out.addVertex(pose.pose(),x,y,z).setColor(255,255,255,255).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,1,0);
    }
}
