package com.juicyslew.moonstation14.ms14.combat.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MeleeQuadBufferSourceTest {
    private static final class Recorder implements VertexConsumer {
        private List<String> events;
        final List<float[]> positions = new ArrayList<>();
        final List<int[]> colors=new ArrayList<>(), uv1=new ArrayList<>(), uv2=new ArrayList<>();
        final List<float[]> uv=new ArrayList<>(), normals=new ArrayList<>();
        int r,g,b,a,u1,v1,u2,v2; float tu,tv,nx,ny,nz;
        @Override public VertexConsumer addVertex(float x,float y,float z){if(events!=null)events.add("vertex");positions.add(new float[]{x,y,z});colors.add(new int[]{r,g,b,a});uv.add(new float[]{tu,tv});uv1.add(new int[]{u1,v1});uv2.add(new int[]{u2,v2});normals.add(new float[]{nx,ny,nz});return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;if(!colors.isEmpty())colors.set(colors.size()-1,new int[]{r,g,b,a});return this;}
        @Override public VertexConsumer setUv(float u,float v){tu=u;tv=v;if(!uv.isEmpty())uv.set(uv.size()-1,new float[]{u,v});return this;}
        @Override public VertexConsumer setUv1(int u,int v){u1=u;v1=v;if(!uv1.isEmpty())uv1.set(uv1.size()-1,new int[]{u,v});return this;}
        @Override public VertexConsumer setUv2(int u,int v){u2=u;v2=v;if(!uv2.isEmpty())uv2.set(uv2.size()-1,new int[]{u,v});return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){nx=x;ny=y;nz=z;if(!normals.isEmpty())normals.set(normals.size()-1,new float[]{x,y,z});return this;}
    }
    @Test void retainedBaseAndGlintConsumersBothFinishFourthVertex() {
        Recorder base=new Recorder(), glint=new Recorder(); int[] requests={0};
        MultiBufferSource source=type -> requests[0]++==0 ? base : glint;
        MeleeQuadBufferSource buffers=new MeleeQuadBufferSource(source,1,0,0,0,0,null);
        VertexConsumer b=buffers.getBuffer(null), g=buffers.getBuffer(null);
        for(int i=0;i<4;i++){ b.addVertex(2+i,0,0); g.addVertex(2+i,1,0); }
        buffers.finish();
        assertEquals(4,base.positions.size()); assertEquals(4,glint.positions.size());
    }
    @Test void attributesPreservedForInsideAndInterpolatedAtCrossing() {
        Recorder recorder=new Recorder(); MultiBufferSource source=type->recorder;
        MeleeQuadBufferSource buffers=new MeleeQuadBufferSource(source,1,0,0,0,0,null);
        VertexConsumer c=buffers.getBuffer(null);
        c.addVertex(1,0,0).setColor(10,20,30,40).setUv(.2f,.3f).setUv1(100,200).setUv2(300,400).setNormal(0,0,1);
        c.addVertex(2,0,0).setColor(20,30,40,50).setUv(.4f,.5f).setUv1(200,300).setUv2(400,500).setNormal(0,0,1);
        c.addVertex(2,1,0).setColor(20,30,40,50).setUv(.4f,.5f).setUv1(200,300).setUv2(400,500).setNormal(0,0,1);
        c.addVertex(1,1,0).setColor(10,20,30,40).setUv(.2f,.3f).setUv1(100,200).setUv2(300,400).setNormal(0,0,1);
        buffers.finish();
        assertArrayEquals(new int[]{10,20,30,40},recorder.colors.get(0));
        assertArrayEquals(new float[]{.2f,.3f},recorder.uv.get(0));
        assertArrayEquals(new int[]{100,200},recorder.uv1.get(0));
        assertArrayEquals(new int[]{300,400},recorder.uv2.get(0));
        assertArrayEquals(new float[]{0,0,1},recorder.normals.get(0));
    }
    @Test void finishCommitsLastVertexOnceAndClipsCrossingQuad() {
        Recorder recorder=new Recorder(); int[] count={0};
        MultiBufferSource src=type -> recorder;
        MeleeQuadBufferSource buffers=new MeleeQuadBufferSource(src,1,0,0,0,0,count);
        VertexConsumer c=buffers.getBuffer(null);
        for(float[] p:new float[][]{{-1,0,0},{1,0,0},{1,1,0},{-1,1,0}})
            c.addVertex(p[0],p[1],p[2]).setColor(255,64,32,255).setUv(.5f,.25f).setUv1(3,4).setUv2(5,6).setNormal(0,0,1);
        assertEquals(0,recorder.positions.size());
        buffers.finish(); buffers.finish();
        assertEquals(4,recorder.positions.size()); assertEquals(4,count[0]);
        assertTrue(recorder.positions.stream().allMatch(p->p[0]>=0));
    }
    @Test void fullOutsideQuadIsDroppedAndFullInsideEmitted() {
        Recorder recorder=new Recorder(); MultiBufferSource src=type -> recorder;
        MeleeQuadBufferSource buffers=new MeleeQuadBufferSource(src,1,0,0,0,0,null);
        VertexConsumer c=buffers.getBuffer(null);
        for(float x:new float[]{-3,-2,-2,-3}) c.addVertex(x,0,0);
        for(float x:new float[]{2,3,3,2}) c.addVertex(x,0,0);
        buffers.finish(); assertEquals(4,recorder.positions.size());
    }
    @Test void switchFlushesBeforeUnderlyingBufferRequest() {
        List<String> events=new ArrayList<>();
        Recorder target=new Recorder(); target.events=events;
        RenderType one=org.mockito.Mockito.mock(RenderType.class), two=org.mockito.Mockito.mock(RenderType.class);
        MultiBufferSource src=t->{events.add("getBuffer");return target;};
        MeleeQuadBufferSource buffers=new MeleeQuadBufferSource(src,1,0,0,0,0,null);
        VertexConsumer c=buffers.getBuffer(one);
        for(float x:new float[]{2,3,3,2}) c.addVertex(x,0,0);
        buffers.getBuffer(two);
        assertEquals(4,events.stream().filter("vertex"::equals).count());
        assertTrue(events.indexOf("vertex")<events.lastIndexOf("getBuffer"));
    }
    @Test void crossingVertexAttributesAreInterpolatedByAdapter() {
        Recorder r=new Recorder(); MeleeQuadBufferSource buffers=new MeleeQuadBufferSource(t->r,1,0,0,0,0,null);
        VertexConsumer c=buffers.getBuffer(null);
        c.addVertex(-1,0,0).setColor(0,20,40,60).setUv(0,0).setUv1(0,0).setUv2(0,0).setNormal(1,0,0);
        c.addVertex(1,0,0).setColor(100,120,140,160).setUv(1,1).setUv1(100,200).setUv2(300,400).setNormal(0,1,0);
        c.addVertex(1,1,0).setColor(100,120,140,160).setUv(1,1).setUv1(100,200).setUv2(300,400).setNormal(0,1,0);
        c.addVertex(-1,1,0).setColor(0,20,40,60).setUv(0,0).setUv1(0,0).setUv2(0,0).setNormal(1,0,0);
        buffers.finish(); int i=0; while(r.positions.get(i)[0]!=0) i++;
        assertArrayEquals(new int[]{50,70,90,110},r.colors.get(i)); assertArrayEquals(new float[]{.5f,.5f},r.uv.get(i));
        assertArrayEquals(new int[]{50,100},r.uv1.get(i)); assertArrayEquals(new int[]{150,200},r.uv2.get(i));
        assertEquals(Math.sqrt(.5),r.normals.get(i)[0],1e-6); assertEquals(Math.sqrt(.5),r.normals.get(i)[1],1e-6);
    }
}
