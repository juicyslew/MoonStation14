package com.juicyslew.moonstation14.ms14.singularity;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityVisuals;
import com.juicyslew.moonstation14.ms14.singularity.SingularityDistortionProjection;
import com.juicyslew.moonstation14.ms14.singularity.SingularityVisualProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SingularityVisualProfileTest {
    @Test void resolvesAllUpstreamLevelAppearanceAndDistortionProfiles() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModSingularityVisuals.TYPE);
        var raw = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, com.google.gson.JsonObject>();
        for (int level = 1; level <= 6; level++) {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "level_" + level);
            try (var reader = getClass().getResourceAsStream("/data/moonstation14/singularity_visual/level_" + level + ".json")) {
                assertNotNull(reader, "missing level " + level);
                raw.put(id, JsonParser.parseReader(new java.io.InputStreamReader(reader)).getAsJsonObject());
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        }
        manager.reload(ModSingularityVisuals.TYPE, raw);
        var catalog = manager.snapshot(ModSingularityVisuals.TYPE);
        assertEquals(6, catalog.size());
        assertEquals(1.5F, catalog.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "level_5")).spriteScale());
        assertEquals(.9F, catalog.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "level_6")).spriteScale());
        int[] frameWidths = {32, 96, 160, 224, 288, 352};
        for (int level = 1; level <= 6; level++) {
            var profile = catalog.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "level_" + level));
            assertEquals(frameWidths[level - 1], profile.frameWidthPixels());
            assertEquals(1f,profile.lensStrength(),"optional local lens policy resolves its shared default");
            assertEquals(profile.spriteHalfExtent(),profile.foregroundRampWorldUnits(),1e-6,
                    "foreground ramp is resolved from each level's world sprite half-extent");
            assertEquals(1f,profile.foregroundRampSpriteHalfExtents());
            assertEquals(level==6?16:8,profile.frameDelaysSeconds().size());
            assertEquals(0.1,profile.frameDelaysSeconds().get(0),1e-9);
            assertEquals(frameWidths[level - 1] / 32.0F * profile.spriteScale(), profile.spriteHalfExtent() * 2);
            try (var image = getClass().getResourceAsStream("/assets/moonstation14/textures/singularity/singularity_" + level + ".png")) {
                assertNotNull(image);
                assertEquals(profile.frameWidthPixels(), javax.imageio.ImageIO.read(image).getWidth());
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        }
        assertEquals(103680F, catalog.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "level_2")).intensity());
        assertEquals(Math.sqrt(12), catalog.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "level_6")).falloff(), .00001);
        assertEquals(20F, catalog.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "level_1")).cutoffWorldUnits());
    }

    @Test void resolvedProfileSelectsTheSameManifestFrameForLiveAndPreviewClock() {
        PrototypeManager manager=new PrototypeManager(); manager.register(ModSingularityVisuals.TYPE);
        var raw=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
        for(int level=1;level<=6;level++) try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream(
                "/data/moonstation14/singularity_visual/level_"+level+".json"))) {
            raw.put(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","level_"+level),JsonParser.parseReader(reader).getAsJsonObject());
        } catch(Exception e){throw new AssertionError(e);}
        manager.reload(ModSingularityVisuals.TYPE,raw);
        for(int level=1;level<=6;level++) {
            var profile=manager.snapshot(ModSingularityVisuals.TYPE).get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","level_"+level));
            int count=level==6?16:8;
            assertEquals(count,profile.frameDelaysSeconds().size());
            for(int frame=0;frame<count;frame++) {
                assertEquals(.1,profile.frameDelaysSeconds().get(frame),1e-9);
                double elapsed=frame*.1+.001;
                assertEquals(frame,profile.frameIndex(elapsed));
                assertEquals("moonstation14:textures/singularity/animated/level_"+level+"/frame_"+String.format(java.util.Locale.ROOT,"%02d",frame)+".png",
                        profile.frameTexture(elapsed).toString());
            }
            assertEquals(0,profile.frameIndex(count*.1),"frame cycle wraps at authored duration");
        }
    }

    @Test void frameTimingHonorsNativeTwentyTickClockBoundariesAndPartials() {
        for (int count : new int[]{8, 16}) {
            double[] delays = new double[count]; java.util.Arrays.fill(delays, .1);
            for (int tick = 0; tick <= count * 2; tick++) for (double partial : new double[]{0, .25, .99}) {
                double elapsed = tick / 20d + partial / 20d;
                int expected = (int)Math.floor((tick + partial) / 2) % count;
                assertEquals(expected, SingularityFrameTiming.frameAt(elapsed, delays),
                        "count="+count+" tick="+tick+" partial="+partial);
            }
            assertEquals(3, SingularityFrameTiming.frameAt(.3, delays));
            assertEquals(6, SingularityFrameTiming.frameAt(.6, delays));
            assertEquals(0, SingularityFrameTiming.frameAt(count * .1, delays));
            assertEquals(0, SingularityFrameTiming.frameAt(1_000_000, delays));
        }
        double[] nonuniform = {.1, .2, .05};
        assertEquals(2, SingularityFrameTiming.frameAt(.3, nonuniform));
        assertEquals(1, SingularityFrameTiming.frameAt(.3 - 1e-6, nonuniform));
        assertEquals(2, SingularityFrameTiming.frameAt(.3 + 1e-6, nonuniform));
    }

    @Test void allResolvedFrameSequencesMatchTheImportedManifest() throws Exception {
        com.google.gson.JsonObject manifest;
        try(var input=getClass().getResourceAsStream("/assets/moonstation14/textures/singularity/animated/animation-manifest.json")) {
            assertNotNull(input); manifest=JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject();
        }
        int total=0;
        for(var levelJson:manifest.getAsJsonArray("levels")) {
            var level=levelJson.getAsJsonObject(); int number=level.get("level").getAsInt();
            var frames=level.getAsJsonArray("frames");
            assertEquals(number==6?16:8,frames.size());
            for(int i=0;i<frames.size();i++) {
                var frame=frames.get(i).getAsJsonObject();
                assertEquals(i,frame.get("sourceFrame").getAsInt());
                assertEquals(.1,frame.get("delaySeconds").getAsDouble(),1e-9);
                String path=frame.get("file").getAsString();
                try(var png=getClass().getResourceAsStream("/assets/moonstation14/textures/singularity/animated/"+path)) {
                    assertNotNull(png,path); byte[] bytes=png.readAllBytes();
                    String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                    assertEquals(frame.get("pngSha256").getAsString(),hash,path);
                    var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    assertEquals(level.getAsJsonObject("frameSize").get("width").getAsInt(),decoded.getWidth());
                    assertEquals(decoded.getWidth(),decoded.getHeight());
                }
                total++;
            }
        }
        assertEquals(56,total);
    }

    @Test void projectionConvertsWorldCutoffAndIntensityAtSourceDepth() {
        var profile = new SingularityVisualProfile(2, 1, 100, 2, 20, 96);
        var near = SingularityDistortionProjection.project(profile, 40, 60, 4);
        var far = SingularityDistortionProjection.project(profile, 40, 60, 2);
        assertEquals(1.5625, near.intensity(),1e-5);
        assertEquals(.390625, far.intensity(),1e-6);
        assertEquals(80, near.cutoffPixels());
        assertEquals(40, far.cutoffPixels());
        assertNull(SingularityDistortionProjection.project(profile, 0, 0, Float.NaN));
        assertNull(SingularityDistortionProjection.project(profile, 0, 0, 0));
    }

    @Test void perspectiveWarpPreservesPinnedSs14DeformationForEveryLevelAndDepth() {
        float[] intensities={3645,103680,1113920,16200000,180000000,180000000};
        float[] falloffs={(float)Math.sqrt(6.4),(float)Math.sqrt(7),(float)Math.sqrt(8),
                (float)Math.sqrt(10),(float)Math.sqrt(12),(float)Math.sqrt(12)};
        for(int level=1;level<=6;level++) for(double pixelsPerMeter:new double[]{32,64,100})
            for(double worldDistance:new double[]{.5,1,2,5,10,20}) {
                var profile=new SingularityVisualProfile(level,1,intensities[level-1],falloffs[level-1],20,32);
                var source=SingularityDistortionProjection.project(profile,200,150,(float)pixelsPerMeter);
                assertNotNull(source);
                double pixelDistance=worldDistance*pixelsPerMeter;
                double local=source.intensity()/Math.pow(pixelDistance,profile.falloff());
                double upstream=profile.intensity()/Math.pow(worldDistance*SingularityVisualProfile.PIXELS_PER_METER,profile.falloff());
                assertEquals(upstream,local,Math.abs(upstream)*2e-6+1e-9,
                        "level="+level+" pixelsPerMeter="+pixelsPerMeter+" distance="+worldDistance);
                if(pixelsPerMeter==SingularityVisualProfile.PIXELS_PER_METER)
                    assertEquals(profile.intensity(),source.intensity(),.01f);
            }
    }

    @Test void resolvedLensPolicyDefaultsAndOverridesWithoutChangingRawProfileParameters() {
        var base=new SingularityVisualProfile(2,1,100,2,20,96);
        assertEquals(1f,base.lensStrength());
        assertEquals(20f,base.lensCutoffWorldUnits()); assertEquals(1f,base.foregroundRampSpriteHalfExtents());
        var source=SingularityDistortionProjection.project(base,0,0,32);
        assertEquals(100,source.intensity()); assertEquals(2,source.falloff()); assertEquals(20*32,source.cutoffPixels());
        var extended=new SingularityVisualProfile(2,1,100,2,20,96,java.util.Collections.nCopies(8,.1),.4f,2f);
        assertEquals(20f,extended.lensCutoffWorldUnits()); assertEquals(.4f,extended.lensStrength());
        assertEquals(2f,extended.foregroundRampSpriteHalfExtents());
        assertDoesNotThrow(()->new SingularityVisualProfile(2,1,100,2,20,96,java.util.Collections.nCopies(8,.1),4f,3).validate());
    }

    @Test void depthRampAndCenterGuardAreFiniteAndExactAtEndpoints() {
        assertEquals(0f,SingularityDistortionProjection.depthMask(8,10,1));
        assertEquals(0f,SingularityDistortionProjection.depthMask(9,10,1));
        assertEquals(1f,SingularityDistortionProjection.depthMask(10,10,1));
        assertEquals(1f,SingularityDistortionProjection.depthMask(20,10,1));
        var source=new SingularityDistortionProjection.Source(0,0,100,2,12,4,1,1);
        var map=SingularityDistortionProjection.map(.5f,.5f,1,1,java.util.List.of(source),(x,y)->Float.POSITIVE_INFINITY);
        assertTrue(Float.isFinite(map.x())&&Float.isFinite(map.y()));
        var foreground=SingularityDistortionProjection.map(5,5,10,10,java.util.List.of(source),(x,y)->3f);
        assertEquals(new SingularityDistortionProjection.Pixel(5,5),foreground,"uniform fully foreground remains identity");
    }

    @Test void ss14ThresholdTransitionIsC1NarrowAndStrengthHasNoOldPixelCap() {
        var omittedStrength = SingularityDistortionProjection.radialDisplacement(3, 4, 100, 2, 50, 1);
        var explicitStrengthOne = SingularityDistortionProjection.radialDisplacement(3, 4, 100, 2, 50, 1, 1);
        var oldStrength = SingularityDistortionProjection.radialDisplacement(3, 4, 100, 2, 50, 1, .7f);
        assertEquals(explicitStrengthOne, omittedStrength, "omitted CPU strength uses canonical default 1");
        assertNotEquals(oldStrength, omittedStrength, "six-argument overload must not preserve the retired .7 source");

        float r=1,R=100,k=2;
        java.util.function.DoubleFunction<Float> responseAtRaw=raw->{
            float intensity=(float)(raw*Math.pow(r,k)/(1-Math.pow(r/R,4)));
            var d=SingularityDistortionProjection.radialDisplacement((float)r,0,intensity,k,R,1,1);
            return d.x();
        };
        float before=responseAtRaw.apply(.79499),atLow=responseAtRaw.apply(.795),atHigh=responseAtRaw.apply(.805),after=responseAtRaw.apply(.80501);
        assertTrue(Math.abs(before-atLow)<.0001); assertTrue(Math.abs(after-atHigh)<.001);
        double e=.00001;
        double lowLeft=(atLow-responseAtRaw.apply(.795-e))/e,lowRight=(responseAtRaw.apply(.795+e)-atLow)/e;
        double highLeft=(atHigh-responseAtRaw.apply(.805-e))/e,highRight=(responseAtRaw.apply(.805+e)-atHigh)/e;
        assertEquals(lowLeft,lowRight,.25,"C1 seam approaches raw derivative at lower edge");
        assertEquals(highLeft,highRight,.25,"C1 seam approaches powered derivative at upper edge");
        var low=SingularityDistortionProjection.radialDisplacement(100,0,1e8f,2,500,1,1);
        var high=SingularityDistortionProjection.radialDisplacement(100,0,1e8f,2,500,1,2);
        assertTrue(low.x()>45,"response exceeds former 45-pixel cap at this synthetic scale");
        assertEquals(low.x()*2,high.x(),Math.abs(low.x())*1e-5,"strength scales response without saturation");
        assertEquals(new SingularityDistortionProjection.Pixel(0,0),
                SingularityDistortionProjection.radialDisplacement(0,0,Float.MAX_VALUE,6,500,1,1),"finite center guard");
    }
}
