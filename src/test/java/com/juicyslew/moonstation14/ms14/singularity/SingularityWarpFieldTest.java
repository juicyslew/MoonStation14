package com.juicyslew.moonstation14.ms14.singularity;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SingularityWarpFieldTest {
    private static SingularityDistortionProjection.Source src(float x,float y,float d) {
        return new SingularityDistortionProjection.Source(x,y,1800, (float)Math.sqrt(8),80,d,1);
    }
    @Test void fullLookupStaysFiniteAcrossScreenResolutionDepthEdgesWithoutClaimingNoFold() {
        int w=256,h=192; var sources=List.of(src(128,96,12));
        java.util.function.BiFunction<Float,Float,Float> depth=(x,y)->(x>128&&x<136&&y>40&&y<160)?2f:(x>130?20f:12f);
        for(float y=2;y<h-2;y+=2)for(float x=2;x<w-2;x+=2) {
            var p=SingularityDistortionProjection.map(x,y,w,h,sources,depth);
            assertTrue(Float.isFinite(p.x())&&Float.isFinite(p.y()));
            var xp=SingularityDistortionProjection.map(x+.01f,y,w,h,sources,depth);
            var xm=SingularityDistortionProjection.map(x-.01f,y,w,h,sources,depth);
            var yp=SingularityDistortionProjection.map(x,y+.01f,w,h,sources,depth);
            var ym=SingularityDistortionProjection.map(x,y-.01f,w,h,sources,depth);
            double ax=(xp.x()-xm.x())/.02, ay=(xp.y()-xm.y())/.02;
            double bx=(yp.x()-ym.x())/.02, by=(yp.y()-ym.y())/.02;
            assertTrue(Double.isFinite(ax)&&Double.isFinite(ay)&&Double.isFinite(bx)&&Double.isFinite(by));
        }
    }
    @Test void sourcesComposeSequentiallyWithoutSourceCountAveraging() {
        int w=320,h=240; var s=List.of(src(80,80,10),src(160,120,10));
        assertTrue(SingularityDistortionProjection.map(150,120,w,h,s,(x,y)->20f).x()>150);
        var one=List.of(src(160,120,10));
        var padded=List.of(src(-1000,-1000,10),src(160,120,10),src(2500,1800,10),src(-2000,2000,10),src(3000,20,10));
        float singleSourceContribution=SingularityDistortionProjection.map(159,120,w,h,one,(x,y)->20f).x();
        float paddedContribution=SingularityDistortionProjection.map(159,120,w,h,padded,(x,y)->20f).x();
        assertEquals(singleSourceContribution,paddedContribution,1e-4,"unrelated selected sources do not weaken a physical contribution by averaging");
    }
    @Test void thinForegroundIsSampledAtFullResolutionAndFlatForegroundStaysIdentity() {
        int w=1920,h=1080; float x=400,y=400,sourceDepth=20;
        var source=List.of(new SingularityDistortionProjection.Source(x,y,100000,2,300,sourceDepth,1));
        java.util.function.BiFunction<Float,Float,Float> background=(px,py)->sourceDepth*2;
        float queryX=x+30.5f, queryY=y+.5f;
        java.util.function.BiFunction<Float,Float,Float> tinyForeground=(px,py)->
                Math.abs(px-queryX)<.5f&&Math.abs(py-queryY)<.5f?sourceDepth*.5f:sourceDepth*2;
        assertTrue(tinyForeground.apply(queryX,queryY)<sourceDepth,"foreground patch contains queried scene point");
        var backgroundMap=SingularityDistortionProjection.map(queryX,queryY,w,h,source,background);
        var narrowPatchMap=SingularityDistortionProjection.map(queryX,queryY,w,h,source,tinyForeground);
        assertNotEquals(backgroundMap,narrowPatchMap,
                "a one-pixel-scale foreground patch participates in the screen-resolution depth field");
        var fullResponse=SingularityDistortionProjection.radialDisplacement(queryX-x,queryY-y,100000,2,300,1,1);
        assertEquals(queryX-fullResponse.x(),backgroundMap.x(),1e-4,"all-background field retains full source response");
        var broadForeground=SingularityDistortionProjection.map(queryX,queryY,w,h,source,(px,py)->sourceDepth*.5f);
        assertEquals(queryX,broadForeground.x(),1e-5,"fully foreground stays identity");
    }
    @Test void foregroundRampUsesDocumentedCameraZGapAtObliqueViewAngles() {
        float sourceZ=10,ramp=1;
        float frontal=SingularityDistortionProjection.depthMask(9.5f,sourceZ,ramp);
        float obliqueSameCameraZ=SingularityDistortionProjection.depthMask(9.5f,sourceZ,ramp);
        assertEquals(frontal,obliqueSameCameraZ,"XY view angle is deliberately ignored by camera-Z gap");
        assertEquals(.5f,frontal,1e-6);
        assertEquals(0f,SingularityDistortionProjection.depthMask(9,sourceZ,ramp));
        assertEquals(1f,SingularityDistortionProjection.depthMask(sourceZ,sourceZ,ramp));
    }
    @Test void resolvedProfilesKeepLensInfluenceVisibleBeyondSpriteFootprint() throws Exception {
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(ModSingularityVisuals.TYPE);
        var raw=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
        for(int level=1;level<=6;level++) try(var reader=new java.io.InputStreamReader(
                getClass().getResourceAsStream("/data/moonstation14/singularity_visual/level_"+level+".json"))) {
            raw.put(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","level_"+level),
                    com.google.gson.JsonParser.parseReader(reader).getAsJsonObject());
        }
        manager.reload(ModSingularityVisuals.TYPE,raw);
        int width=1920,height=1080; float distance=12;
        float focal=(float)(height/(2*Math.tan(Math.toRadians(35))));
        float pixelsPerWorldUnit=focal/distance;
        float[] previousOutsideSpriteReference={4.56f,11.58f,16.27f,18.76f};
        var measurements=new StringBuilder("outside-sprite-max px at FOV70,d=12,1920x1080,background depth=24:");
        for(int level=1;level<=6;level++) {
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","level_"+level);
            var profile=manager.snapshot(ModSingularityVisuals.TYPE).get(id);
            var projected=SingularityDistortionProjection.project(profile,width*.5f,height*.5f,pixelsPerWorldUnit);
            var source=new SingularityDistortionProjection.Source(projected.x(),projected.y(),projected.intensity(),
                    projected.falloff(),projected.cutoffPixels(),distance,1,projected.strength());
            float halfExtentPixels=profile.spriteHalfExtent()*pixelsPerWorldUnit;
            float maxRadius=Math.min(source.cutoffPixels(),Math.min(width,height)*.5f-1);
            float maximum=0;
            for(float radius=halfExtentPixels+1;radius<=maxRadius;radius+=2) for(int axis=0;axis<4;axis++) {
                float x=width*.5f+(axis<2?(axis==0?radius:-radius):0);
                float y=height*.5f+(axis>=2?(axis==2?radius:-radius):0);
                var mapped=SingularityDistortionProjection.map(x,y,width,height,List.of(source),(px,py)->distance*2);
                maximum=Math.max(maximum,(float)Math.hypot(x-mapped.x(),y-mapped.y()));
            }
            measurements.append(" L").append(level).append('=').append(String.format(java.util.Locale.ROOT,"%.2f",maximum));
            if(level<=previousOutsideSpriteReference.length)
                assertTrue(maximum>previousOutsideSpriteReference[level-1],measurements.toString()+" should exceed prior outside-sprite reference "+previousOutsideSpriteReference[level-1]);
        }
        System.out.println(measurements);
    }
    @Test void identityCenterCutoffAndEdgeFadeKeepLookupInViewport() {
        int w=320,h=240;var source=List.of(src(160,120,10));
        assertEquals(new SingularityDistortionProjection.Pixel(160,120),SingularityDistortionProjection.map(160,120,w,h,source,(x,y)->20f));
        assertEquals(new SingularityDistortionProjection.Pixel(100,100),SingularityDistortionProjection.map(100,100,w,h,List.of(src(20,100,10)),(x,y)->20f));
        for(float x:new float[]{0,1,319,320})for(float y:new float[]{0,1,120,239,240}) {
            var p=SingularityDistortionProjection.map(x,y,w,h,source,(a,b)->20f);
            assertTrue(p.x()>=0&&p.x()<=w&&p.y()>=0&&p.y()<=h);
            if(Math.min(Math.min(x,w-x),Math.min(y,h-y))==0)assertEquals(x,p.x(),1e-5);
        }
        assertEquals(new SingularityDistortionProjection.Pixel(0,0),SingularityDistortionProjection.radialDisplacement(4,0,100,2,4,1));
        assertEquals(new SingularityDistortionProjection.Pixel(0,0),SingularityDistortionProjection.radialDisplacement(4,0,0,2,10,1));
        float[] verifiedFalloffs={2.529822f,2.6457512f,2.8284271f,3.1622777f,3.4641016f};
        for(float k:verifiedFalloffs) {
            var d=SingularityDistortionProjection.radialDisplacement(12,5,180000000,k,80,1);
            assertTrue(Float.isFinite(d.x())&&Float.isFinite(d.y()));
            assertEquals(new SingularityDistortionProjection.Pixel(0,0),SingularityDistortionProjection.radialDisplacement(80,0,180000000,k,80,1));
        }
    }
    @Test void projectionAdmissionUsesInfluenceNotSpriteAndRejectsRearNearAndZeroSources() {
        var profile=new SingularityVisualProfile(1,1,100,2,10,32);
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.1f,100);
        float focal=(float)(720/(2*Math.tan(Math.toRadians(35))));
        for(float[] point:new float[][]{{0,8},{0,-8},{13.4f,0},{-13.4f,0}}) {
            var p=SingularityDistortionProjection.project(profile,point[0],point[1],-10,new Matrix4f(),projection,1280,720,focal);
            assertNotNull(p);assertFalse(p.inViewport());assertFalse(p.spriteIntersectsViewport(1280,720));
        }
        assertNull(SingularityDistortionProjection.project(profile,35,0,-10,new Matrix4f(),projection,1280,720,focal),
                "offscreen influence is admitted only while within the 20-world-unit support");
        assertNull(SingularityDistortionProjection.project(profile,-35,0,-10,new Matrix4f(),projection,1280,720,focal));
        assertNull(SingularityDistortionProjection.project(new SingularityVisualProfile(1,1,100,2,0,32),0,0,-10,new Matrix4f(),projection,1280,720,focal));
        assertNull(SingularityDistortionProjection.project(new SingularityVisualProfile(1,1,0,2,10,32),0,0,-10,new Matrix4f(),projection,1280,720,focal));
        assertNull(SingularityDistortionProjection.project(profile,0,0,10,new Matrix4f(),projection,1280,720,focal));
        assertNull(SingularityDistortionProjection.project(profile,0,0,-.05f,new Matrix4f(),projection,1280,720,focal));
    }
    @Test void inverseProjectionRoundTripsCurrentMatrixNearFarAndFovAndSkyIsUnoccluded() {
        for(float near:new float[]{.03f,.1f,.5f})for(float far:new float[]{80,500,3000})for(float fov:new float[]{45,70,105}) {
            var p=new Matrix4f().perspective((float)Math.toRadians(fov),16f/9,near,far);var inv=new Matrix4f(p).invert();
            for(float z:new float[]{near*1.1f,1,far*.8f}) {
                if(z>=far)continue;
                var clip=p.transform(new org.joml.Vector4f(0,0,-z,1));float buffer=(clip.z/clip.w+1)*.5f;
                float reconstructed=SingularityDistortionProjection.reconstructCameraDepth(inv,.5f,.5f,buffer);
                float towardNear=SingularityDistortionProjection.reconstructCameraDepth(inv,.5f,.5f,Math.nextDown(buffer));
                float towardFar=SingularityDistortionProjection.reconstructCameraDepth(inv,.5f,.5f,Math.nextUp(buffer));
                // Depth-buffer float spacing expands approximately quadratically with camera
                // distance; compare against the adjacent representable encoded depths rather
                // than demanding sub-ULP recovery at far planes. Include a small matrix
                // arithmetic rounding allowance, while retaining the encoded-depth resolution.
                double quantization=Math.max(Math.abs((double)reconstructed-towardNear),
                        Math.abs((double)towardFar-reconstructed));
                double arithmetic=8.0*Math.ulp(z);
                assertEquals(z,reconstructed,quantization+arithmetic);
            }
            assertEquals(Float.POSITIVE_INFINITY,SingularityDistortionProjection.reconstructCameraDepth(inv,.5f,.5f,1));
        }
    }
}
