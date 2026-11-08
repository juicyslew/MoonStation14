package com.juicyslew.moonstation14.ms14.combat.client;

import java.util.ArrayList;
import java.util.List;

/** Clips observer-melee quad streams against a world-space half-plane. */
public final class MeleeQuadClipper {
    private MeleeQuadClipper() {}

    /** Colors use VertexConsumer float channel units (0..1); light/overlay use two packed 16-bit channels. */
    public record Vertex(double x, double y, double z, float red, float green, float blue, float alpha,
                         float u, float v, int light, int overlay, float nx, float ny, float nz) {}

    /** Output triangles are emitted as quad primitives, duplicating the final vertex for triangles. */
    public static List<Vertex> clipQuad(List<Vertex> quad, double dx, double dy, double dz,
                                        double clearance, double epsilon) {
        if (quad.size() != 4) throw new IllegalArgumentException("Expected exactly four quad vertices");
        if (!Double.isFinite(clearance) || !Double.isFinite(epsilon) || epsilon < 0)
            throw new IllegalArgumentException("Invalid plane parameters");
        double length = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (!(length > 0) || !Double.isFinite(length)) throw new IllegalArgumentException("Direction must be finite and nonzero");
        dx /= length; dy /= length; dz /= length;
        boolean allInside=true, allOutside=true;
        for (Vertex v:quad) {
            double d=dot(v,dx,dy,dz)-clearance;
            allInside &= d + epsilon >= 0;
            allOutside &= d + epsilon < 0;
        }
        if (allInside) return List.copyOf(quad);
        if (allOutside) return List.of();
        List<Vertex> polygon = new ArrayList<>();
        for (int i=0; i<4; i++) {
            Vertex a=quad.get(i), b=quad.get((i+1)%4);
            // epsilon shifts the clipping plane, keeping classifications and intersections consistent.
            double da=dot(a,dx,dy,dz)-clearance+epsilon, db=dot(b,dx,dy,dz)-clearance+epsilon;
            boolean ia=da >= 0, ib=db >= 0;
            if (ia) polygon.add(a);
            if (ia != ib) polygon.add(interpolate(a,b,da/(da-db)));
        }
        if (polygon.size() < 3) return List.of();
        if (polygon.size() == 4) return List.copyOf(polygon);
        List<Vertex> out = new ArrayList<>((polygon.size()-2)*4);
        for (int i=1; i<polygon.size()-1; i++) {
            out.add(polygon.get(0)); out.add(polygon.get(i)); out.add(polygon.get(i+1)); out.add(polygon.get(i+1));
        }
        return List.copyOf(out);
    }

    private static double dot(Vertex v,double x,double y,double z) { return v.x*x+v.y*y+v.z*z; }
    private static Vertex interpolate(Vertex a,Vertex b,double t) {
        float nx=m(a.nx,b.nx,t), ny=m(a.ny,b.ny,t), nz=m(a.nz,b.nz,t);
        double norm=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(norm>1e-12) { nx/= (float)norm; ny/= (float)norm; nz/= (float)norm; }
        return new Vertex(m(a.x,b.x,t),m(a.y,b.y,t),m(a.z,b.z,t),m(a.red,b.red,t),m(a.green,b.green,t),m(a.blue,b.blue,t),m(a.alpha,b.alpha,t),
                m(a.u,b.u,t),m(a.v,b.v,t),packed(a.light,b.light,t),packed(a.overlay,b.overlay,t),nx,ny,nz);
    }
    private static int packed(int a,int b,double t) {
        int lo=channel(a&0xffff,b&0xffff,t), hi=channel((a>>>16)&0xffff,(b>>>16)&0xffff,t);
        return (hi<<16)|lo;
    }
    private static int channel(int a,int b,double t) { return Math.max(0,Math.min(65535,(int)Math.round(a+(b-a)*t))); }
    private static double m(double a,double b,double t) { return a+(b-a)*t; }
    private static float m(float a,float b,double t) { return (float)(a+(b-a)*t); }
}
