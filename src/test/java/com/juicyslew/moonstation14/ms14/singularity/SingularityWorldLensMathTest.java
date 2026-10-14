package com.juicyslew.moonstation14.ms14.singularity;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import static org.junit.jupiter.api.Assertions.*;

class SingularityWorldLensMathTest {
    @Test void inverseMapIsMonotoneAndHasBoundedForwardResidual() {
        double previous=0;
        for(int n=0;n<=1000;n++) {
            double r=n*640.0/1000;
            double q=SingularityWorldLensMath.forwardRadius(r, .7, 2, 640, 2);
            assertTrue(q>=previous); previous=q;
            if(r>0 && r<640) assertEquals(r, q*(1-2*response(q)), 640.0/16777216.0+1e-8);
        }
    }

    @Test void sixResolvedProfilesRemainOrderedAcrossStrengthVisibilityAndForegroundMatrix() throws Exception {
        double[] strengths={0,.01,.25,.5,1,2,8,100};
        double[] visibilities={.5,1};
        double[] foreground={0,.25,.5,.75,1};
        for(int level=1;level<=6;level++) {
            SingularityVisualProfile profile=resolved(level);
            double cutoff=profile.cutoffWorldUnits()*32;
            for(double strength:strengths) for(double visibility:visibilities) for(double weight:foreground) {
                double a=strength*visibility*weight;
                double maxDerivative=derivativeBound(profile,cutoff,a);
                double[] radii=radiusSweep(profile,cutoff,a);
                double previous=-1;
                for(double r:radii) {
                    double q=SingularityWorldLensMath.forwardRadius(r,profile.intensity(),profile.falloff(),cutoff,a);
                    assertTrue(Double.isFinite(q)&&q>=0,"finite/nonnegative level="+level+" a="+a+" r="+r);
                    assertTrue(q>=previous-2*cutoff/16777216.0,"ordering within adjacent bisection brackets level="+level+" a="+a+" r="+r+" q="+q+" previous="+previous);
                    previous=q;
                    if(a==0) assertEquals(r,q,0,"a=0 is exact identity");
                    else if(r>=SingularityWorldLensMath.ORIGIN_EPSILON_PIXELS && r<cutoff) {
                        double residual=Math.abs(q*(1-a*response(q,profile.intensity(),profile.falloff(),cutoff))-r);
                        double bracketWidth=(cutoff-r)/16777216.0;
                        assertTrue(residual<=maxDerivative*bracketWidth+Math.ulp(r)*4,
                                "inverse residual level="+level+" strength="+strength+" visibility="+visibility+" foreground="+weight+" r="+r+" residual="+residual);
                    }
                }
                double e=SingularityWorldLensMath.ORIGIN_EPSILON_PIXELS;
                double atJoin=SingularityWorldLensMath.forwardRadius(e,profile.intensity(),profile.falloff(),cutoff,a);
                double below=SingularityWorldLensMath.forwardRadius(e*(1-1e-7),profile.intensity(),profile.falloff(),cutoff,a);
                assertTrue(atJoin>0 && below>0,"origin extension stays positive");
                assertEquals(atJoin,below,Math.max(1e-12,atJoin*1e-6),"origin extension joins continuously");
                assertEquals(e,atJoin*(1-a*response(atJoin,profile.intensity(),profile.falloff(),cutoff)),
                        maxDerivative*cutoff/16777216.0+1e-8,"epsilon join is the inverse branch");
            }
        }
    }
    private static double response(double q) {
        double raw=.7*Math.pow(q,-2)*(1-Math.pow(q/640,4));
        if(raw<=.795)return raw;
        if(raw>=.805)return Math.pow(raw,.3);
        double t=(raw-.795)/.01, s=t*t*(3-2*t);
        return raw+(Math.pow(raw,.3)-raw)*s;
    }
    @Test void zeroStrengthAndAxisAreIdentityAndOriginExtensionIsFinite() {
        for(double r:new double[]{0,1e-8,.001,.009,.01,10,639,640,800}) {
            assertEquals(r,SingularityWorldLensMath.forwardRadius(r,.5,2,640,0),1e-12);
            assertTrue(Double.isFinite(SingularityWorldLensMath.forwardRadius(r,.5,2,640,2)));
        }
    }

    @Test void resolvedSixProfilesMapInSs14PixelUnitsAndAreCameraRotationInvariant() throws Exception {
        double[] intensity={3645,103680,1113920,16200000,180000000,180000000};
        double[] exponent={Math.sqrt(6.4),Math.sqrt(7),Math.sqrt(8),Math.sqrt(10),Math.sqrt(12),Math.sqrt(12)};
        for(int level=1;level<=6;level++) {
            SingularityVisualProfile profile;
            try(var input=getClass().getResourceAsStream("/data/moonstation14/singularity_visual/level_"+level+".json")) {
                assertNotNull(input);
                profile=SingularityVisualProfile.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(input))).result().orElseThrow();
            }
            assertEquals(intensity[level-1],profile.intensity(),Math.max(1,intensity[level-1])*1e-6);
            assertEquals(exponent[level-1],profile.falloff(),1e-5);
            double cutoff=profile.cutoffWorldUnits()*32;
            var c=new SingularityWorldLensMath.Point(0,0,-10);
            var p=new SingularityWorldLensMath.Point(1,0,-10);
            var mapped=SingularityWorldLensMath.map(p,c,profile.intensity(),profile.falloff(),cutoff,2,1,profile.foregroundRampWorldUnits());
            assertTrue(Math.abs(mapped.x()-p.x())>1e-5,"level="+level+" response uses 32 SS pixels/world unit");
            var rotatedP=new SingularityWorldLensMath.Point(p.x()*Math.cos(.87)+p.z()*Math.sin(.87),p.y(),-p.x()*Math.sin(.87)+p.z()*Math.cos(.87));
            var rotatedC=new SingularityWorldLensMath.Point(c.x()*Math.cos(.87)+c.z()*Math.sin(.87),c.y(),-c.x()*Math.sin(.87)+c.z()*Math.cos(.87));
            var rotated=SingularityWorldLensMath.map(rotatedP,rotatedC,profile.intensity(),profile.falloff(),cutoff,2,1,profile.foregroundRampWorldUnits());
            assertEquals(Math.hypot(mapped.x()-p.x(),mapped.y()-p.y()),
                    Math.sqrt(Math.pow(rotated.x()-rotatedP.x(),2)+Math.pow(rotated.y()-rotatedP.y(),2)+Math.pow(rotated.z()-rotatedP.z(),2)),1e-5);
            var foreground=new SingularityWorldLensMath.Point(0,0,-(10-profile.foregroundRampWorldUnits()));
            assertSame(foreground,SingularityWorldLensMath.map(foreground,c,profile.intensity(),profile.falloff(),cutoff,2,1,profile.foregroundRampWorldUnits()));
        }
    }

    @Test void axisCutoffAndFullyForegroundAreExactIdentity() {
        var c=new SingularityWorldLensMath.Point(0,0,-10);
        var axis=new SingularityWorldLensMath.Point(0,0,-10);
        var edge=new SingularityWorldLensMath.Point(640.0/32,0,-10);
        var foreground=new SingularityWorldLensMath.Point(2,0,-8);
        assertSame(axis,SingularityWorldLensMath.map(axis,c,100,2,640,3,1,1));
        assertSame(edge,SingularityWorldLensMath.map(edge,c,100,2,640,3,1,1));
        assertSame(foreground,SingularityWorldLensMath.map(foreground,c,100,2,640,3,1,1));
    }

    @Test void resolvedProfileWorldMapPreservesHeadTurnAndStrengthResponse() throws Exception {
        double[] strengths={0,.01,.25,.5,1,2,8,100};
        for(int level=1;level<=6;level++) {
            SingularityVisualProfile profile=resolved(level);
            double cutoff=profile.cutoffWorldUnits()*32;
            var source=new SingularityWorldLensMath.Point(0,0,-10);
            var scene=new SingularityWorldLensMath.Point(1,0,-10);
            for(double strength:strengths) {
                var result=SingularityWorldLensMath.map(scene,source,profile.intensity(),profile.falloff(),cutoff,strength,1,profile.foregroundRampWorldUnits());
                double delta=Math.sqrt(Math.pow(result.x()-scene.x(),2)+Math.pow(result.y()-scene.y(),2)+Math.pow(result.z()-scene.z(),2));
                if(strength==0) assertSame(scene,result); else assertTrue(delta>0,"level="+level+" strength="+strength);
                for(double angle:new double[]{0,Math.toRadians(50),Math.toRadians(70)}) {
                    var c=rotate(source,angle); var p=rotate(scene,angle);
                    var turned=SingularityWorldLensMath.map(p,c,profile.intensity(),profile.falloff(),cutoff,strength,1,profile.foregroundRampWorldUnits());
                    double turnedDelta=Math.sqrt(Math.pow(turned.x()-p.x(),2)+Math.pow(turned.y()-p.y(),2)+Math.pow(turned.z()-p.z(),2));
                    assertEquals(delta,turnedDelta,Math.max(1e-8,delta*1e-7),"level="+level+" angle="+angle+" strength="+strength);
                }
            }
        }
    }

    private static SingularityVisualProfile resolved(int level) throws Exception {
        try(var input=SingularityWorldLensMathTest.class.getResourceAsStream("/data/moonstation14/singularity_visual/level_"+level+".json")) {
            assertNotNull(input);
            return SingularityVisualProfile.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(new InputStreamReader(input))).result().orElseThrow();
        }
    }
    private static SingularityWorldLensMath.Point rotate(SingularityWorldLensMath.Point p,double a) {
        return new SingularityWorldLensMath.Point(p.x()*Math.cos(a)+p.z()*Math.sin(a),p.y(),-p.x()*Math.sin(a)+p.z()*Math.cos(a));
    }
    private static double[] radiusSweep(SingularityVisualProfile p,double R,double amount) {
        java.util.ArrayList<Double> values=new java.util.ArrayList<>();
        for(double r:new double[]{0,1e-8,.001,.009,.009999,.01,.010001})values.add(r);
        for(double threshold:new double[]{.795,.8,.805}) {
            double lo=.01,hi=R;
            for(int n=0;n<80;n++){double mid=(lo+hi)*.5;double raw=p.intensity()*Math.pow(mid,-p.falloff())*(1-Math.pow(mid/R,4));if(raw>threshold)lo=mid;else hi=mid;}
            double q=(lo+hi)*.5; values.add(q*(1-1e-8));values.add(q);values.add(q*(1+1e-8));
            double mapped=q*(1-amount*response(q,p.intensity(),p.falloff(),R));
            if(mapped>0 && mapped<R) {values.add(mapped*(1-1e-8));values.add(mapped);values.add(mapped*(1+1e-8));}
        }
        for(int i=1;i<256;i++) values.add(R*i/256.0);
        values.add(R*(1-1e-10));values.add(R);values.add(R*1.01);
        return values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    }
    private static double response(double q,double I,double k,double R) {
        return SingularityRadialResponse.evaluate(q,I,k,R).value();
    }
    private static double derivativeBound(SingularityVisualProfile p,double R,double a) {
        double max=1;
        for(int n=0;n<=4096;n++) {
            double x=SingularityWorldLensMath.ORIGIN_EPSILON_PIXELS+(R-SingularityWorldLensMath.ORIGIN_EPSILON_PIXELS)*n/4096.0;
            var rd=SingularityRadialResponse.evaluate(x,p.intensity(),p.falloff(),R);
            max=Math.max(max,Math.abs(1-a*(rd.value()+x*rd.derivative())));
        }
        return max*(1+1e-10);
    }
}
