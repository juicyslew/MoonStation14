package com.juicyslew.moonstation14.ms14.disposal.world;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Set;

/** Bounded tube envelope; ports are directions occupied by the tube arms. */
public final class DisposalTubeGeometry {
    private DisposalTubeGeometry() { }
    public static VoxelShape shape(double diameter, Set<Direction> ports) {
        if (!Double.isFinite(diameter) || diameter <= 0 || diameter > 1) throw new IllegalArgumentException("diameter must be in (0,1]");
        double inset=(1-diameter)/2, center=0.5;
        VoxelShape result=Shapes.box(inset,inset,inset,1-inset,1-inset,1-inset);
        for (Direction d : ports) {
            double x0=d.getStepX()<0?0:inset, x1=d.getStepX()>0?1:1-inset;
            double y0=d.getStepY()<0?0:inset, y1=d.getStepY()>0?1:1-inset;
            double z0=d.getStepZ()<0?0:inset, z1=d.getStepZ()>0?1:1-inset;
            VoxelShape arm=Shapes.box(x0,y0,z0,x1,y1,z1);
            result=Shapes.join(result,arm,BooleanOp.OR);
        }
        return result;
    }
    public static double axisCenter() { return 0.5; }
}
