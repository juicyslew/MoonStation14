package com.juicyslew.moonstation14.ms14.singularity;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingularityCameraProjectionTest {
    private static final SingularityVisualProfile PROFILE = new SingularityVisualProfile(1,1,3645,6.4f,20,32);

    @Test void projectsPerspectiveCenterAtSeveralDepthsAndFovs() {
        for (float fov : new float[]{55,70,95}) for (float depth : new float[]{5,10,20}) {
            float focal=(float)(720/(2*Math.tan(Math.toRadians(fov/2))));
            var result=SingularityDistortionProjection.project(PROFILE,0,0,(float)-depth,
                    new Matrix4f(),new Matrix4f().perspective((float)Math.toRadians(fov),16f/9,.05f,2048),1280,720,focal);
            assertNotNull(result,"depth="+depth+" fov="+fov);
            assertEquals(640,result.source().x(),.01);
            assertEquals(360,result.source().y(),.01);
            assertEquals(depth,result.depth(),.01);
            assertEquals(result.depth(),result.source().depth(),.001);
            float expected=(float)(2048.0/(2048.0-.05) * (1.0-.05/depth));
            assertEquals(expected,result.depthBuffer(),.00001);
        }
    }

    @Test void clipsNearRearAndDistantOffscreenSources() {
        Matrix4f projection=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,2048);
        float focal=(float)(720/(2*Math.tan(Math.toRadians(35))));
        assertNull(SingularityDistortionProjection.project(PROFILE,0,0,-.01f,new Matrix4f(),projection,1280,720,focal));
        assertNull(SingularityDistortionProjection.project(PROFILE,0,0,5,new Matrix4f(),projection,1280,720,focal));
        assertNull(SingularityDistortionProjection.project(PROFILE,100,0,-10,new Matrix4f(),projection,1280,720,focal));
    }

    @Test void reportsOffscreenProjectedCenterForProductionDepthAdmission() {
        Matrix4f projection=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,2048);
        float focal=(float)(720/(2*Math.tan(Math.toRadians(35))));
        // x=13 remains inside the larger distortion cutoff, but its projected footprint is wholly offscreen.
        var outside=SingularityDistortionProjection.project(PROFILE,13,0,-10,new Matrix4f(),projection,1280,720,focal);
        assertNotNull(outside);
        assertFalse(outside.inViewport());
        // At x=13 the projected center is hundreds of pixels beyond the screen; a ~14px half-width cannot reach it.
        assertFalse(outside.spriteIntersectsViewport(1280,720));
        float halfExtentWorld=PROFILE.spriteHalfExtent();
        float horizontalViewportHalfWorld=(1280f/(2*focal))*10f;
        float verticalViewportHalfWorld=(720f/(2*focal))*10f;
        for(float[] center:new float[][]{
                {horizontalViewportHalfWorld+halfExtentWorld*.5f,0},
                {-horizontalViewportHalfWorld-halfExtentWorld*.5f,0},
                {0,verticalViewportHalfWorld+halfExtentWorld*.5f},
                {0,-verticalViewportHalfWorld-halfExtentWorld*.5f}}) {
            var nearEdge=SingularityDistortionProjection.project(PROFILE,center[0],center[1],-10,new Matrix4f(),projection,1280,720,focal);
            assertNotNull(nearEdge);
            assertFalse(nearEdge.inViewport());
            assertTrue(nearEdge.spriteIntersectsViewport(1280,720));
        }
        var beyond=SingularityDistortionProjection.project(PROFILE,100,0,-10,new Matrix4f(),projection,1280,720,focal);
        assertNull(beyond);
    }

    @Test void projectedSpriteFootprintUsesViewDepthAndDetectsAllViewportEdges() {
        Matrix4f projection=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,2048);
        float focal=(float)(720/(2*Math.tan(Math.toRadians(35))));
        var center=SingularityDistortionProjection.project(PROFILE,0,0,-10,new Matrix4f(),projection,1280,720,focal);
        assertEquals(focal/10*PROFILE.spriteHalfExtent(),center.spriteHalfExtentPixels(),.001);
        float horizontalEdgeWorld=1280f/(2*focal)*10f;
        float verticalEdgeWorld=720f/(2*focal)*10f;
        float halfExtentWorld=PROFILE.spriteHalfExtent();
        for(float[] point:new float[][]{{-horizontalEdgeWorld-halfExtentWorld*.5f,0},
                {horizontalEdgeWorld+halfExtentWorld*.5f,0},{0,verticalEdgeWorld+halfExtentWorld*.5f},
                {0,-verticalEdgeWorld-halfExtentWorld*.5f}}) {
            var edge=SingularityDistortionProjection.project(PROFILE,point[0],point[1],-10,new Matrix4f(),projection,1280,720,focal);
            assertNotNull(edge);
            assertFalse(edge.inViewport());
            assertTrue(edge.spriteIntersectsViewport(1280,720));
            var beyond=point[0]!=0
                    ? SingularityDistortionProjection.project(PROFILE,Math.copySign(horizontalEdgeWorld+2*halfExtentWorld,point[0]),0,-10,new Matrix4f(),projection,1280,720,focal)
                    : SingularityDistortionProjection.project(PROFILE,0,Math.copySign(verticalEdgeWorld+2*halfExtentWorld,point[1]),-10,new Matrix4f(),projection,1280,720,focal);
            assertNotNull(beyond);
            assertFalse(beyond.spriteIntersectsViewport(1280,720));
        }
    }

    @Test void footprintPixelPolicyHandlesOffscreenTinyAndDegenerateViewports() {
        int w=11,h=9; float extent=1.5f;
        for(float[] center:new float[][]{{-.5f,4.5f},{11.5f,4.5f},{5.5f,-.5f},{5.5f,9.5f}}) {
            var pixel=SingularityDistortionProjection.nearestFootprintPixel(center[0],center[1],extent,w,h);
            assertNotNull(pixel);
            assertTrue(pixel.x()>=.5f&&pixel.x()<=w-.5f&&pixel.y()>=.5f&&pixel.y()<=h-.5f);
        }
        assertNull(SingularityDistortionProjection.nearestFootprintPixel(-2,4,extent,w,h));
        // Integer-centered subpixel footprint lies between pixel centers (n+.5), so no legal depth texel exists.
        assertNull(SingularityDistortionProjection.nearestFootprintPixel(5,4,.49f,w,h));
        assertNull(SingularityDistortionProjection.nearestFootprintPixel(Float.NaN,4,5,4,1,w,h));
        assertNull(SingularityDistortionProjection.nearestFootprintPixel(5.5f,4.5f,Float.POSITIVE_INFINITY,4,1,w,h));
        var fractional=SingularityDistortionProjection.nearestFootprintPixel(-.2f,4.1f,0,4.5f,1,w,h);
        assertEquals(.5f,fractional.x());
        assertEquals(4.5f,fractional.y());
        assertNull(SingularityDistortionProjection.nearestFootprintPixel(1,1,1,0,h));
        assertNull(SingularityDistortionProjection.nearestFootprintPixel(1,1,1,w,0));
        assertEquals(.5f,SingularityDistortionProjection.nearestFootprintPixel(0,0,1,1,1).x());
        assertEquals(.5f,SingularityDistortionProjection.nearestFootprintPixel(0,0,1,1,1).y());
    }

}
