package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Small bounded admission counter for the diagnostic fixture's committed body. */
final class FixtureSettling {
    enum Result { WAIT, READY, ABORT }
    private int elapsed, consecutive;
    private Vec3 previous;

    void seed(Vec3 position) { previous=position; }

    Result observe(boolean supported, Vec3 position) {
        elapsed++;
        boolean stable=supported && position!=null && previous!=null && position.distanceToSqr(previous)<=.0001;
        consecutive=stable?consecutive+1:0;
        previous=position;
        if(consecutive>=3)return Result.READY;
        return elapsed>=40?Result.ABORT:Result.WAIT;
    }

    static BlockPos floorOrigin(Vec3 feet) {
        return BlockPos.containing(feet.x,Math.ceil(feet.y-1e-5)-1,feet.z);
    }
    record Candidate(BlockPos origin, Vec3 center, double distance) { }
    static List<Candidate> candidates(Vec3 feet,float yaw) {
        return candidates(feet,yaw,16);
    }
    static List<Candidate> candidates(Vec3 feet,float yaw,int width) {
        Vec3 forward=forward(yaw), right=new Vec3(Math.cos(Math.toRadians(yaw)),0,Math.sin(Math.toRadians(yaw)));
        List<Candidate> result=new ArrayList<>(18);
        for(double distance:new double[]{13,17}) for(double lateral:new double[]{0,-4,4}) for(int rise=0;rise<=2;rise++) {
            Vec3 center=feet.add(forward.scale(distance)).add(right.scale(lateral)).add(0,rise,0);
            BlockPos origin=floorOrigin(center).offset(-(width/2),0,-(width/2));
            // Keep the committed body's horizontal footprint outside the enclosure with a one-block gap.
            double dx=feet.x-(origin.getX()+width/2.0), dz=feet.z-(origin.getZ()+width/2.0);
            double exclusion=width/2.0+0.5;
            if(Math.abs(dx)<=exclusion && Math.abs(dz)<=exclusion) continue;
            result.add(new Candidate(origin,center,distance));
        }
        return List.copyOf(result);
    }
    static Candidate firstPassing(List<Candidate> candidates, Predicate<Candidate> safe) {
        for(Candidate candidate:candidates) if(safe.test(candidate)) return candidate;
        return null;
    }
    static Vec3 centerAhead(Vec3 feet,float yaw) { return feet.add(forward(yaw).scale(13)); }
    static Vec3 forward(float yaw) {
        double radians=Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians),0,Math.cos(radians));
    }
    static BlockPos spawnCell(int index) { return new BlockPos(3+index%10,0,3+index/10); }
    static BlockPos spawnCell(int index, int width) {
        return spawnCell(index,width,1);
    }
    static BlockPos spawnCell(int index, int width, int spacing) {
        int columns=Math.min(10,width-2);
        if(width==24 && spacing==3) return new BlockPos(3+(index%7)*3,0,3+(index/7)*3);
        return new BlockPos(width==16?3+index%10:1+index%columns,0,
                width==16?3+(index/10)%10:1+(index/columns)%(width-2));
    }
}
