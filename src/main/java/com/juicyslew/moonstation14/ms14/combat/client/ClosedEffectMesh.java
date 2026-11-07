package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LightTexture;

import java.util.ArrayList;
import java.util.List;

/** CPU-only closed, world-oriented effect volume. Faces use frame UVs, not sheet margins. */
public final class ClosedEffectMesh {
    private ClosedEffectMesh() { }
    public record Vertex(Vec3 position, float u, float v) { }
    public static final int NATIVE_ALPHA_CUTOFF=26;
    public record FrameVolume(List<Vertex> mesh,float opaqueU0,float opaqueU1,float opaqueV0,float opaqueV1,int opaqueShortSpan,float coreU,float coreV) { }

    /** Builds live frame geometry and alpha-derived metadata from the same source mask. */
    public static FrameVolume frameVolume(int width,int height,byte[] alpha,float u0,float u1,float v0,float v1) {
        int minX=width,minY=height,maxX=-1,maxY=-1;
        if(width>0&&height>0&&alpha!=null&&alpha.length==width*height)
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((alpha[y*width+x]&255)>=NATIVE_ALPHA_CUTOFF){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        if(maxX<minX)return new FrameVolume(List.of(),(u0+u1)/2,(u0+u1)/2,(v0+v1)/2,(v0+v1)/2,0,(u0+u1)/2,(v0+v1)/2);
        int span=Math.min(maxX-minX+1,maxY-minY+1);
        int coreX=minX,coreY=minY,best=-1;double cx=(minX+maxX)/2.0,cy=(minY+maxY)/2.0;
        for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){int a=alpha[y*width+x]&255;double d=(x-cx)*(x-cx)+(y-cy)*(y-cy);double old=(coreX-cx)*(coreX-cx)+(coreY-cy)*(coreY-cy);if(a>best||(a==best&&d<old)){best=a;coreX=x;coreY=y;}}
        return new FrameVolume(extrudeFrame(width,height,alpha,span/32.0,u0,u1,v0,v1,1.0/32.0),
                u0+(u1-u0)*minX/width,u0+(u1-u0)*(maxX+1)/width,
                v0+(v1-v0)*minY/height,v0+(v1-v0)*(maxY+1)/height,span,
                u0+(u1-u0)*(coreX+.5f)/width,v0+(v1-v0)*(coreY+.5f)/height);
    }

    /** Emits our triangle-list topology to Minecraft's QUADS consumers as [a,b,c,c]. */
    public static void emitQuads(PoseStack.Pose pose, VertexConsumer out,List<Vertex> triangles,
                                 int color,int alpha,int light,boolean unshaded) {
        int a=Math.max(0,Math.min(255,alpha*((color>>>24)&255)/255));
        int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;
        int vertexLight=unshaded?LightTexture.FULL_BRIGHT:light;
        for(int i=0;i+2<triangles.size();i+=3){
            Vertex va=triangles.get(i),vb=triangles.get(i+1),vc=triangles.get(i+2);
            Vec3 normal=vb.position().subtract(va.position()).cross(vc.position().subtract(va.position()));
            if(normal.lengthSqr()<1e-20)continue;normal=normal.normalize();
            vertex(out,pose,va,r,g,b,a,vertexLight,normal);vertex(out,pose,vb,r,g,b,a,vertexLight,normal);
            vertex(out,pose,vc,r,g,b,a,vertexLight,normal);vertex(out,pose,vc,r,g,b,a,vertexLight,normal);
        }
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vertex v,int r,int g,int b,int a,int light,Vec3 n){
        out.addVertex(pose.pose(),(float)v.position().x,(float)v.position().y,(float)v.position().z)
                .setColor(r,g,b,a).setUv(v.u(),v.v()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose,(float)n.x,(float)n.y,(float)n.z);
    }

    /** Extrudes occupied source pixels as closed pixel prisms. alphaMask is row-major. */
    public static List<Vertex> extrudeFrame(int width,int height,byte[] alphaMask,double depth,
                                            float u0,float u1,float v0,float v1,double worldScale) {
        if(width<=0||height<=0||alphaMask==null||alphaMask.length!=width*height||!Double.isFinite(depth)||depth<=0||!Double.isFinite(worldScale)||worldScale<=0||!finite(u0,u1,v0,v1))return List.of();
        int minX=width,minY=height,maxX=-1,maxY=-1;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((alphaMask[y*width+x]&255)>=NATIVE_ALPHA_CUTOFF){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        if(maxX<minX)return List.of();
        double px=worldScale,py=worldScale,z=depth/2; List<Vertex> out=new ArrayList<>();
        int coreX=minX,coreY=minY,best=-1;double centerX=(minX+maxX)/2.0,centerY=(minY+maxY)/2.0;
        for(int yy=minY;yy<=maxY;yy++)for(int xx=minX;xx<=maxX;xx++){int a=alphaMask[yy*width+xx]&255;double d=(xx-centerX)*(xx-centerX)+(yy-centerY)*(yy-centerY),old=(coreX-centerX)*(coreX-centerX)+(coreY-centerY)*(coreY-centerY);if(a>=NATIVE_ALPHA_CUTOFF&&(a>best||a==best&&d<old)){best=a;coreX=xx;coreY=yy;}}
        float coreU=u0+(u1-u0)*(coreX+.5f)/width,coreV=v0+(v1-v0)*(coreY+.5f)/height;
        for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++)if((alphaMask[y*width+x]&255)>=NATIVE_ALPHA_CUTOFF){
            double l=(x-width/2.0)*px,r=l+px,b=(height/2.0-y-1)*py,t=b+py;
            float ul=u0+(u1-u0)*x/width,ur=u0+(u1-u0)*(x+1)/width;
            float vt=v0+(v1-v0)*y/height,vb=v0+(v1-v0)*(y+1)/height, uc=(ul+ur)/2,vc=(vt+vb)/2;
            quad(out,new Vec3(l,b,z),new Vec3(r,b,z),new Vec3(r,t,z),new Vec3(l,t,z),ul,ur,vt,vb);
            quad(out,new Vec3(l,b,-z),new Vec3(l,t,-z),new Vec3(r,t,-z),new Vec3(r,b,-z),ul,ur,vt,vb);
            if(x==0||!opaque(alphaMask,width,height,x-1,y)) quad(out,new Vec3(l,b,-z),new Vec3(l,b,z),new Vec3(l,t,z),new Vec3(l,t,-z),coreU,coreU,coreV,coreV);
            if(x==width-1||!opaque(alphaMask,width,height,x+1,y)) quad(out,new Vec3(r,b,-z),new Vec3(r,t,-z),new Vec3(r,t,z),new Vec3(r,b,z),coreU,coreU,coreV,coreV);
            if(y==0||!opaque(alphaMask,width,height,x,y-1)) quad(out,new Vec3(l,t,-z),new Vec3(l,t,z),new Vec3(r,t,z),new Vec3(r,t,-z),coreU,coreU,coreV,coreV);
            if(y==height-1||!opaque(alphaMask,width,height,x,y+1)) quad(out,new Vec3(l,b,-z),new Vec3(r,b,-z),new Vec3(r,b,z),new Vec3(l,b,z),coreU,coreU,coreV,coreV);
        }
        return List.copyOf(out);
    }
    private static boolean opaque(byte[] a,int w,int h,int x,int y){return x>=0&&y>=0&&x<w&&y<h&&(a[y*w+x]&255)>=NATIVE_ALPHA_CUTOFF;}
    private static void quad(List<Vertex> o,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u0,float u1,float v0,float v1){tri(o,a,b,c,u0,v1,u1,v1,u1,v0);tri(o,a,c,d,u0,v1,u1,v0,u0,v0);}

    /** Builds an eight-sided tube/prism with closed source and destination caps. */
    public static List<Vertex> tube(Vec3 from, Vec3 to, double radius,
                                    float u0, float u1, float v0, float v1) {
        return tube(from,to,radius,u0,u1,v0,v1,(u0+u1)/2,(v0+v1)/2);
    }
    public static List<Vertex> tube(Vec3 from, Vec3 to, double radius,
                                    float u0, float u1, float v0, float v1,float capU,float capV) {
        if (from == null || to == null || !finite(from) || !finite(to) ||
                !Double.isFinite(radius) || radius <= 0 || !finite(u0,u1,v0,v1)) return List.of();
        if(!Float.isFinite(capU)||!Float.isFinite(capV))return List.of();
        Vec3 delta=to.subtract(from); double length=delta.length();
        if (!Double.isFinite(length) || length < 1e-7) return List.of();
        Vec3 axis=delta.scale(1/length);
        Vec3 helper=Math.abs(axis.y)<.9 ? new Vec3(0,1,0) : new Vec3(1,0,0);
        Vec3 x=axis.cross(helper).normalize(), y=axis.cross(x).normalize();
        List<Vertex> out=new ArrayList<>(8*18);
        for(int i=0;i<8;i++) {
            double a=2*Math.PI*i/8, b=2*Math.PI*(i+1)/8;
            Vec3 r0=x.scale(Math.cos(a)*radius).add(y.scale(Math.sin(a)*radius));
            Vec3 r1=x.scale(Math.cos(b)*radius).add(y.scale(Math.sin(b)*radius));
            Vec3 p0=from.add(r0),p1=from.add(r1),q0=to.add(r0),q1=to.add(r1);
            float vm=(v0+v1)/2;
            tri(out,p0,q1,q0,u0,vm,u1,vm,u1,vm); tri(out,p0,p1,q1,u0,vm,u0,vm,u1,vm);
            // Fan triangles close both ends and sample the opaque frame interior.
            Vec3 c0=from,c1=to; float uc=capU, vc=capV;
            tri(out,c0,p1,p0,uc,vc,u1,v1,u0,v1);
            tri(out,c1,q0,q1,uc,vc,u0,v0,u1,v0);
        }
        return List.copyOf(out);
    }
    private static void tri(List<Vertex> o,Vec3 a,Vec3 b,Vec3 c,float au,float av,float bu,float bv,float cu,float cv){o.add(new Vertex(a,au,av));o.add(new Vertex(b,bu,bv));o.add(new Vertex(c,cu,cv));}
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static boolean finite(float... v){for(float f:v)if(!Float.isFinite(f))return false;return true;}
}
