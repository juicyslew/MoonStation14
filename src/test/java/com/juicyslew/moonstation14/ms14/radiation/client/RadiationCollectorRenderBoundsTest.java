package com.juicyslew.moonstation14.ms14.radiation.client;

import net.minecraft.client.renderer.block.model.BlockModel;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RadiationCollectorRenderBoundsTest {
    private static final String ROOT = "/assets/moonstation14/models/block/reactor_bindings/collector/";

    @Test void coversAllAdapterElementCornersAndContinuousPanelExtrema() throws Exception {
        for (int tank=0;tank<=1;tank++) for(int pressure=0;pressure<4;pressure++)
            checkModel("shell_tank"+tank+"_pressure"+pressure, false);
        for(int pressure=0;pressure<4;pressure++) checkModel("pressure_overlay_p"+pressure, false);
        for(String panel : new String[]{"panel_active", "panel_inactive"}) checkModel(panel, true);
        var moved=RadiationCollectorRenderBounds.at(17,-3,29);
        assertEquals(RadiationCollectorRenderBounds.LOCAL.minX+17, moved.minX);
        assertEquals(RadiationCollectorRenderBounds.LOCAL.minY-3, moved.minY);
        assertEquals(RadiationCollectorRenderBounds.LOCAL.minZ+29, moved.minZ);
    }

    private static void checkModel(String name, boolean moving) throws Exception {
        BlockModel model;
        try(var stream=RadiationCollectorRenderBoundsTest.class.getResourceAsStream(ROOT+name+".json")) {
            assertNotNull(stream,name);
            model=BlockModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        var bounds=RadiationCollectorRenderBounds.LOCAL;
        for(var element:model.getElements()) for(int ix=0;ix<2;ix++) for(int iy=0;iy<2;iy++) for(int iz=0;iz<2;iz++) {
            double x=(ix==0?element.from.x:element.to.x)/16.0;
            double y=(iy==0?element.from.y:element.to.y)/16.0;
            double z=(iz==0?element.from.z:element.to.z)/16.0;
            if(!moving) { inside(bounds,x,y,z,name); continue; }
            double py=10.0/16,pz=11.5/16,dy=y-py,dz=z-pz;
            // Actual renderer uses positive X rotation: y'=dy*cos-dz*sin; z'=dy*sin+dz*cos.
            checkAngle(bounds,x,py,pz,dy,dz,0,name); checkAngle(bounds,x,py,pz,dy,dz,Math.PI/2,name);
            roots(Math.atan2(dy,dz), angle -> checkAngle(bounds,x,py,pz,dy,dz,angle,name)); // y'=0
            roots(Math.atan2(-dz,dy), angle -> checkAngle(bounds,x,py,pz,dy,dz,angle,name)); // z'=0
        }
    }
    private interface AngleCheck { void check(double angle); }
    private static void roots(double root, AngleCheck check) {
        for(int k=-2;k<=2;k++) { double a=root+k*Math.PI; if(a>=0&&a<=Math.PI/2) check.check(a); }
    }
    private static void checkAngle(net.minecraft.world.phys.AABB b,double x,double py,double pz,double dy,double dz,double a,String model) {
        inside(b,x,py+dy*Math.cos(a)-dz*Math.sin(a),pz+dy*Math.sin(a)+dz*Math.cos(a),model);
    }
    private static void inside(net.minecraft.world.phys.AABB b,double x,double y,double z,String model) {
        assertTrue(x>=b.minX-1e-9&&x<=b.maxX+1e-9&&y>=b.minY-1e-9&&y<=b.maxY+1e-9&&z>=b.minZ-1e-9&&z<=b.maxZ+1e-9,
                model+" vertex outside: "+x+","+y+","+z);
    }
}
