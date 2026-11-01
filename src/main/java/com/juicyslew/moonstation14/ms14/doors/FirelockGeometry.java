package com.juicyslew.moonstation14.ms14.doors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/** Authored R4 moving firelock geometry; deliberately independent of physical collision. */
public final class FirelockGeometry {
    private static final String ROOT="/assets/moonstation14/models/block/firelock/standard/components/";
    private static final List<AirlockGeometry.Box> UPPER=AuthoredDoorGeometry.load(ROOT+"upper_leaf.json");
    private static final List<AirlockGeometry.Box> LOWER=AuthoredDoorGeometry.load(ROOT+"lower_leaf.json");
    private static final List<AirlockGeometry.Box> INDICATOR=AuthoredDoorGeometry.load(ROOT+"indicator_dark.json");
    private FirelockGeometry() { }

    public static List<AirlockGeometry.Box> upperLeaf() { return UPPER; }
    public static List<AirlockGeometry.Box> lowerLeaf() { return LOWER; }
    /** One four-housing indicator choice, moving with the upper leaf. */
    public static List<AirlockGeometry.Box> indicator() { return INDICATOR; }

    public static List<AirlockGeometry.Box> localBoxes(double q) {
        double progress=Math.max(0,Math.min(1,q));
        // AuthoredDoorGeometry has already converted model pixels to block units.
        // Match FirelockFramePlanner's one-block travel at full openness.
        double travel=progress;
        var boxes=new ArrayList<AirlockGeometry.Box>();
        boxes.addAll(moveAndClip(UPPER,travel));
        boxes.addAll(moveAndClip(INDICATOR,travel));
        boxes.addAll(moveAndClip(LOWER,-travel));
        return List.copyOf(boxes);
    }

    public static List<AABB> worldBounds(BlockPos root, Direction facing, double q) {
        int rx=facing.getCounterClockWise().getStepX(), rz=facing.getCounterClockWise().getStepZ();
        int fx=facing.getStepX(), fz=facing.getStepZ();
        var bounds=new ArrayList<AABB>();
        for (var b:localBoxes(q)) {
            double minX=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY;
            double maxX=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
            for(double x:new double[]{b.x0(),b.x1()}) for(double z:new double[]{b.z0(),b.z1()}) {
                double wx=root.getX()+.5+rx*(x-.5)+fx*(z-.5), wz=root.getZ()+.5+rz*(x-.5)+fz*(z-.5);
                minX=Math.min(minX,wx); maxX=Math.max(maxX,wx); minZ=Math.min(minZ,wz); maxZ=Math.max(maxZ,wz);
            }
            bounds.add(new AABB(minX,root.getY()+b.y0(),minZ,maxX,root.getY()+b.y1(),maxZ));
        }
        return List.copyOf(bounds);
    }

    private static List<AirlockGeometry.Box> moveAndClip(List<AirlockGeometry.Box> source,double y) {
        return source.stream().map(b->new AirlockGeometry.Box(Math.max(-.25/16,b.x0()),Math.max(0,b.y0()+y),Math.max(0,b.z0()),
                Math.min(16.25/16,b.x1()),Math.min(2,b.y1()+y),Math.min(1,b.z1())))
                .filter(b->b.x0()<b.x1()&&b.y0()<b.y1()&&b.z0()<b.z1()).toList();
    }
}
