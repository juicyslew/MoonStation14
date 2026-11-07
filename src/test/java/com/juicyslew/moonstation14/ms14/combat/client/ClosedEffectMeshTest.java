package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClosedEffectMeshTest {
    private record P(long x,long y,long z) { }
    private static P p(Vec3 v){return new P(Math.round(v.x*100000),Math.round(v.y*100000),Math.round(v.z*100000));}
    private static java.util.Map<String,Integer> edges(java.util.List<ClosedEffectMesh.Vertex> m){var result=new java.util.HashMap<String,Integer>();for(int i=0;i<m.size();i+=3)for(int j=0;j<3;j++){P a=p(m.get(i+j).position()),b=p(m.get(i+(j+1)%3).position());String key=a.toString().compareTo(b.toString())<0?a+"/"+b:b+"/"+a;result.merge(key,1,Integer::sum);}return result;}
    private static boolean directedClosed(java.util.List<ClosedEffectMesh.Vertex> m){var count=new java.util.HashMap<String,Integer>();for(int i=0;i<m.size();i+=3)for(int j=0;j<3;j++){P a=p(m.get(i+j).position()),b=p(m.get(i+(j+1)%3).position());count.merge(a+">"+b,1,Integer::sum);}for(var e:count.entrySet()){String[] q=e.getKey().split(">");if(!e.getValue().equals(count.getOrDefault(q[1]+">"+q[0],0)))return false;}return true;}
    @Test void blankAndInvalidInputsAreEmpty(){assertTrue(ClosedEffectMesh.extrudeFrame(2,2,new byte[4],.1,0,1,0,1,1).isEmpty());assertTrue(ClosedEffectMesh.tube(Vec3.ZERO,Vec3.ZERO,.1,0,1,0,1).isEmpty());}
    @Test void pixelPrismHasFrontBackAndOpaqueSideUvs(){
        var mesh=ClosedEffectMesh.extrudeFrame(1,1,new byte[]{(byte)255},.1,.2f,.4f,.3f,.7f,1);
        assertEquals(36,mesh.size());
        assertTrue(mesh.stream().allMatch(v->v.u()>=.2f&&v.u()<=.4f&&v.v()>=.3f&&v.v()<=.7f));
        assertTrue(mesh.stream().anyMatch(v->Math.abs(v.position().z())>.049));
    }
    @Test void tubeClosesBothEndsAndHasVolume(){
        var mesh=ClosedEffectMesh.tube(Vec3.ZERO,new Vec3(0,0,2),.2,0,1,0,1);
        assertEquals(96,mesh.size());
        double volume=0;for(int i=0;i<mesh.size();i+=3){Vec3 a=mesh.get(i).position(),b=mesh.get(i+1).position(),c=mesh.get(i+2).position();volume+=a.dot(b.cross(c))/6;}
        assertTrue(Math.abs(volume)>0.01);
        assertTrue(mesh.stream().anyMatch(v->v.position().z()==0));assertTrue(mesh.stream().anyMatch(v->v.position().z()==2));
        assertTrue(edges(mesh).values().stream().allMatch(n->n==2),"tube must be watertight");
        double signed=0;for(int i=0;i<mesh.size();i+=3){Vec3 a=mesh.get(i).position(),b=mesh.get(i+1).position(),c=mesh.get(i+2).position();signed+=a.dot(b.cross(c))/6;}
        assertTrue(signed>0,"outward-oriented closed tube has positive signed volume");
    }
    @Test void adjacentOpaquePixelsCancelInternalWallsAndKeepBoundaryUv(){
        var mesh=ClosedEffectMesh.extrudeFrame(2,1,new byte[]{-1,-1},.1,0,1,0,1,1);
        assertEquals(60,mesh.size()); // 2 front/back quads + six outer boundary quads
        assertTrue(edges(mesh).values().stream().allMatch(n->n==2));
        assertTrue(mesh.stream().allMatch(v->v.u()>=0&&v.u()<=1&&v.v()>=0&&v.v()<=1));
    }
    @Test void sourcePixelsKeepAspectAndOneThirtySecondBlockScale(){
        var mesh=ClosedEffectMesh.extrudeFrame(4,2,new byte[]{-1,-1,-1,-1,-1,-1,-1,-1},.0625,0,1,0,1,1.0/32.0);
        double minX=mesh.stream().mapToDouble(v->v.position().x).min().orElseThrow(),maxX=mesh.stream().mapToDouble(v->v.position().x).max().orElseThrow();
        double minY=mesh.stream().mapToDouble(v->v.position().y).min().orElseThrow(),maxY=mesh.stream().mapToDouble(v->v.position().y).max().orElseThrow();
        assertEquals(4.0/32,maxX-minX,1e-9);assertEquals(2.0/32,maxY-minY,1e-9);
    }
    @Test void nativeAlphaCutoffExcludesSoftBorderAndSidewallsUseOpaqueCore(){
        byte[] alpha=new byte[9];alpha[0]=10;alpha[4]=(byte)255;
        var frame=ClosedEffectMesh.frameVolume(3,3,alpha,0,1,0,1);
        assertEquals(1,frame.opaqueShortSpan());
        assertEquals(1f/3,frame.opaqueU0(),1e-6f);assertEquals(2f/3,frame.opaqueU1(),1e-6f);
        assertEquals(1f/3,frame.opaqueV0(),1e-6f);assertEquals(2f/3,frame.opaqueV1(),1e-6f);
        assertEquals(.5f,frame.coreU(),1e-6f);assertEquals(.5f,frame.coreV(),1e-6f);
        assertFalse(frame.mesh().isEmpty());
        assertTrue(frame.mesh().stream().filter(v->Math.abs(v.position().z())<1e-9)
                .allMatch(v->v.u()==frame.coreU()&&v.v()==frame.coreV()),"sidewall samples must use opaque core, not alpha-10 border");
        assertTrue(ClosedEffectMesh.frameVolume(1,1,new byte[]{25},0,1,0,1).mesh().isEmpty());
    }
    @Test void beamTubeSamplesLongitudinalOpaqueBandAndOpaqueCapTexel(){
        // Synthetic +X source strip: opaque vertical band occupies only the middle rows.
        var mesh=ClosedEffectMesh.tube(Vec3.ZERO,new Vec3(0,0,4),.1,.1f,.9f,.4f,.6f,.5f,.5f);
        assertEquals(96,mesh.size());
        assertTrue(mesh.stream().allMatch(v->v.u()>=.1f&&v.u()<=.9f&&v.v()>=.4f&&v.v()<=.6f));
        for(int side=0;side<8;side++){
            var tri=mesh.subList(side*12,side*12+6);
            assertTrue(tri.stream().anyMatch(v->v.position().z()==4&&v.u()==.9f));
            assertTrue(tri.stream().anyMatch(v->v.position().z()==0&&v.u()==.1f));
        }
        assertTrue(mesh.stream().anyMatch(v->v.u()==.5f&&v.v()==.5f));
        assertTrue(directedClosed(mesh));
        double signed=0;for(int i=0;i<mesh.size();i+=3){Vec3 a=mesh.get(i).position(),b=mesh.get(i+1).position(),c=mesh.get(i+2).position();signed+=a.dot(b.cross(c))/6;}
        assertTrue(signed>0,"tube faces must point outward");
    }
    @Test void productionEmitterWritesDegenerateQuadsForEachTriangle(){
        int[] vertices={0};float[] normal={0,0,0};var consumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->{
                    if(method.getName().equals("addVertex"))vertices[0]++;
                    if(method.getName().equals("setNormal")){normal[0]=(float)args[1];normal[1]=(float)args[2];normal[2]=(float)args[3];}
                    if(method.getReturnType().isAssignableFrom(com.mojang.blaze3d.vertex.VertexConsumer.class))return proxy;
                    return null;
                });
        var triangle=ClosedEffectMesh.tube(Vec3.ZERO,new Vec3(0,0,1),.1,0,1,0,1).subList(0,3);
        ClosedEffectMesh.emitQuads(new com.mojang.blaze3d.vertex.PoseStack().last(),consumer,triangle,0xffffffff,255,0,false);
        assertEquals(4,vertices[0]);
        Vec3 a=triangle.get(0).position(),b=triangle.get(1).position(),c=triangle.get(2).position();
        Vec3 expected=b.subtract(a).cross(c.subtract(a)).normalize();
        assertEquals(expected.x,normal[0],1e-5);assertEquals(expected.y,normal[1],1e-5);assertEquals(expected.z,normal[2],1e-5);
        Vec3 centroid=a.add(b).add(c).scale(1.0/3);
        assertTrue(new Vec3(normal[0],normal[1],normal[2]).dot(centroid)>0,"sample side normal points away from tube axis");
    }
}
