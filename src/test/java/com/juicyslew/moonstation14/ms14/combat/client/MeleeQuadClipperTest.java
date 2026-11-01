package com.juicyslew.moonstation14.ms14.combat.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MeleeQuadClipperTest {
    private static MeleeQuadClipper.Vertex v(double x,double y) {
        return new MeleeQuadClipper.Vertex(x,y,0,(float)(x+2),.2f,.3f,1,(float)(x+2),(float)y,
                packed((int)((x+2)*100),(int)(y*100)),packed((int)((x+2)*200),(int)(y*200)),1,0,0);
    }
    private static int packed(int lo,int hi) { return (hi<<16)|lo; }
    private static List<MeleeQuadClipper.Vertex> q(double a,double b) { return List.of(v(a,0),v(b,0),v(b,1),v(a,1)); }
    private static List<MeleeQuadClipper.Vertex> clip(List<MeleeQuadClipper.Vertex> q,double eps) {
        return MeleeQuadClipper.clipQuad(q,1,0,0,0,eps);
    }
    @Test void allInAllOutAndCrossingStayBoundedAndPreserveWinding() {
        assertSameVertices(q(2,3),clip(q(2,3),0));
        assertTrue(clip(q(-3,-2),0).isEmpty());
        var clipped=clip(q(-1,1),0);
        assertEquals(4,clipped.size());
        assertTrue(clipped.stream().allMatch(p -> p.x()>=0 && p.x()<=1));
        assertTrue(clipped.stream().anyMatch(p -> p.x()==0));
        assertTrue(area(clipped)>0);
    }
    @Test void epsilonCasesIntersectWithinSourceEdgesAndAllowTangent() {
        var near=clip(q(-.5e-8,1),1e-8);
        assertTrue(near.stream().allMatch(p -> p.x()>=-1e-8));
        assertTrue(near.stream().allMatch(p -> p.x()<=1));
        assertTrue(clip(q(-1,0),1e-8).size()>=4);
        assertTrue(clip(q(-1,-.5e-8),0).isEmpty());
    }
    @Test void pentagonClipTriangulatesAsThreeWindingPreservingQuadPrimitives() {
        var pent=List.of(v(-1,0),v(2,0),v(2,1),v(1,2));
        var out=clip(pent,0);
        assertEquals(12,out.size());
        for(int i=0;i<out.size();i+=4) {
            assertTrue(out.get(i).x()>=0 && out.get(i+1).x()>=0 && out.get(i+2).x()>=0);
            assertEquals(out.get(i+2),out.get(i+3));
            assertTrue(cross(out.get(i),out.get(i+1),out.get(i+2))>0);
        }
        assertTrue(out.stream().allMatch(p -> p.x()<=2 && p.y()<=2));
    }
    @Test void intersectionsInterpolateAllAttributesAndNormalizeNormals() {
        var out=clip(q(-1,1),0);
        var p=out.stream().filter(v -> v.x()==0 && v.y()==1).findFirst().orElseThrow();
        assertEquals(2,p.red()); assertEquals(.2f,p.green()); assertEquals(.3f,p.blue()); assertEquals(1,p.alpha());
        assertEquals(2,p.u()); assertEquals(1f,p.v());
        assertEquals(packed(200,100),p.light()); assertEquals(packed(400,200),p.overlay());
        assertEquals(1,p.nx()); assertEquals(0,p.ny()); assertEquals(0,p.nz());
        var mixed=List.of(new MeleeQuadClipper.Vertex(-1,0,0,0,0,0,1,0,0,0,0,1,0,0),
                new MeleeQuadClipper.Vertex(1,0,0,1,1,1,1,1,1,packed(0,0),packed(0,0),0,1,0),v(1,1),v(-1,1));
        var m=clip(mixed,0).stream().filter(x -> x.x()==0 && x.y()==0).findFirst().orElseThrow();
        assertEquals(1,Math.sqrt(m.nx()*m.nx()+m.ny()*m.ny()+m.nz()*m.nz()),1e-6);
        assertEquals(.5f,m.red()); assertEquals(packed(0,0),m.light());
    }
    private static void assertSameVertices(List<MeleeQuadClipper.Vertex> a,List<MeleeQuadClipper.Vertex> b) { assertEquals(a,b); }
    private static double area(List<MeleeQuadClipper.Vertex> q) { double s=0; for(int i=0;i<q.size();i+=4) s+=cross(q.get(i),q.get(i+1),q.get(i+2)); return s; }
    private static double cross(MeleeQuadClipper.Vertex a,MeleeQuadClipper.Vertex b,MeleeQuadClipper.Vertex c) { return (b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x()); }
}
