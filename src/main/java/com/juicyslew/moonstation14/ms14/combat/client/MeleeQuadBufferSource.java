package com.juicyslew.moonstation14.ms14.combat.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

/** Melee-scoped stream adapter which clips complete attribute-rich quads before forwarding. */
public final class MeleeQuadBufferSource implements MultiBufferSource {
    private final MultiBufferSource source;
    private final double dx, dy, dz, clearance, epsilon;
    private final int[] emitted;
    private final List<Stream> streams = new ArrayList<>();
    private final Map<VertexConsumer, Stream> wrappers = new IdentityHashMap<>();
    private RenderType activeType;

    public MeleeQuadBufferSource(MultiBufferSource source, double dx, double dy, double dz,
                                 double clearance, double epsilon, int[] emitted) {
        this.source = source;
        this.dx = dx; this.dy = dy; this.dz = dz;
        this.clearance = clearance; this.epsilon = epsilon; this.emitted = emitted;
    }

    @Override public VertexConsumer getBuffer(RenderType type) {
        if (activeType != null && activeType != type) flushPending();
        activeType = type;
        VertexConsumer delegate = source.getBuffer(type);
        return wrappers.computeIfAbsent(delegate, key -> { Stream s = new Stream(key); streams.add(s); return s; });
    }

    /** Completes any pending final vertex and flushes all complete quads. */
    public void finish() { flushPending(); activeType = null; }
    private void flushPending() { for (Stream stream : streams) stream.finish(); }

    private final class Stream implements VertexConsumer {
        private final VertexConsumer out;
        private final List<MeleeQuadClipper.Vertex> quad = new ArrayList<>(4);
        private double x,y,z; private float r=1,g=1,b=1,a=1,u,v,nx,ny,nz;
        private int light,overlay; private boolean pending;
        Stream(VertexConsumer out) { this.out=out; }
        @Override public VertexConsumer addVertex(float x,float y,float z) {
            if(pending) complete(); this.x=x;this.y=y;this.z=z;pending=true;return this;
        }
        private void complete() {
            quad.add(new MeleeQuadClipper.Vertex(x,y,z,r,g,b,a,u,v,light,overlay,nx,ny,nz)); pending=false;
            if(quad.size()==4) flush();
        }
        private void flush() {
            for(var vertex:MeleeQuadClipper.clipQuad(quad,dx,dy,dz,clearance,epsilon)) {
                out.addVertex((float)vertex.x(),(float)vertex.y(),(float)vertex.z())
                        .setColor(Math.round(vertex.red()*255),Math.round(vertex.green()*255),Math.round(vertex.blue()*255),Math.round(vertex.alpha()*255))
                        .setUv(vertex.u(),vertex.v()).setUv1(vertex.overlay()&0xffff,vertex.overlay()>>>16)
                        .setUv2(vertex.light()&0xffff,vertex.light()>>>16).setNormal(vertex.nx(),vertex.ny(),vertex.nz());
                if(emitted!=null) emitted[0]++;
            }
            quad.clear();
        }
        void finish() { if(pending) complete(); }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { this.r=r/255f;this.g=g/255f;this.b=b/255f;this.a=a/255f;return this; }
        @Override public VertexConsumer setUv(float u,float v) { this.u=u;this.v=v;return this; }
        @Override public VertexConsumer setUv1(int u,int v) { overlay=(v<<16)|(u&65535);return this; }
        @Override public VertexConsumer setUv2(int u,int v) { light=(v<<16)|(u&65535);return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { nx=x;ny=y;nz=z;return this; }
    }
}
