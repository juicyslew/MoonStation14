package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;

/** Camera-facing oriented strip geometry for transient beam sprites. */
public final class BeamBillboardGeometry {
    private BeamBillboardGeometry() { }
    public record Basis(Vec3 along, Vec3 across, Vec3 normal, double length) { }
    public record Corner(Vec3 position,float u,float v) { }
    /** Production vertex/UV mapping: longitudinal axis samples the frame width, cross-section its height. */
    public static Corner[] quad(Basis basis,Vec3 end,float halfWidth,float u0,float u1,float v0,float v1){
        Vec3 start=end.subtract(basis.along().scale(basis.length())); Vec3 across=basis.across().scale(halfWidth);
        return new Corner[]{new Corner(start.subtract(across),u0,v1),new Corner(start.add(across),u0,v0),new Corner(end.add(across),u1,v0),new Corner(end.subtract(across),u1,v1)};
    }
    public static Basis basis(Vec3 from,Vec3 to,Vec3 camera){
        if(from==null||to==null||camera==null||!finite(from)||!finite(to)||!finite(camera))return null;
        Vec3 delta=to.subtract(from); double length=delta.length(); if(!Double.isFinite(length)||length<1e-5)return null;
        Vec3 along=delta.scale(1/length), view=camera.subtract(from); if(view.lengthSqr()<1e-12)view=new Vec3(0,0,1);
        Vec3 normal=along.cross(view).cross(along); if(normal.lengthSqr()<1e-12){Vec3 helper=Math.abs(along.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0);normal=along.cross(helper);}
        normal=normal.normalize(); Vec3 across=normal.cross(along).normalize(); return new Basis(along,across,normal,length);
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
