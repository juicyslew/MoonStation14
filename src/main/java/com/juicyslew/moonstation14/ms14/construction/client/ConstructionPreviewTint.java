package com.juicyslew.moonstation14.ms14.construction.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Shared preview vertex tint; transparently multiplies model vertex alpha as well as the render pass. */
public final class ConstructionPreviewTint {
    public enum Kind { READY, BLOCKED, INSUFFICIENT }
    public record Rgba(float red,float green,float blue,float alpha) { }
    private ConstructionPreviewTint() { }
    public static Rgba rgba(Kind kind) {
        return switch(kind) {
            case READY -> new Rgba(.15f,1f,.3f,.35f);
            case BLOCKED -> new Rgba(1f,.12f,.12f,.35f);
            case INSUFFICIENT -> new Rgba(1f,.68f,.12f,.35f);
        };
    }
    public static VertexConsumer apply(VertexConsumer delegate,Kind kind) {
        return new AlphaVertexConsumer(delegate,rgba(kind).alpha());
    }

    /** Overrides only the primitive operations: interface default bulk emitters then execute on this wrapper. */
    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;
        private AlphaVertexConsumer(VertexConsumer delegate,float alpha){this.delegate=java.util.Objects.requireNonNull(delegate);this.alpha=alpha;}
        @Override public VertexConsumer addVertex(float x,float y,float z){delegate.addVertex(x,y,z);return this;}
        @Override public VertexConsumer setColor(int red,int green,int blue,int sourceAlpha){delegate.setColor(red,green,blue,Math.round(sourceAlpha*alpha));return this;}
        @Override public VertexConsumer setUv(float u,float v){delegate.setUv(u,v);return this;}
        @Override public VertexConsumer setUv1(int u,int v){delegate.setUv1(u,v);return this;}
        @Override public VertexConsumer setUv2(int u,int v){delegate.setUv2(u,v);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){delegate.setNormal(x,y,z);return this;}
    }
}

