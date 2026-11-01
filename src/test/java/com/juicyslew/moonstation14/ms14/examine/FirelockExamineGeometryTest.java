package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.doors.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the exact pure bounds/ray seam called by ExamineWorldSelector's door branches. */
class FirelockExamineGeometryTest {
    private static List<AABB> cover(BlockPos root,Direction facing,double q) { return FirelockGeometry.worldBounds(root,facing,q); }

    @Test void authoredRayAndOutlineHonorBothFacesFourFacingsAndPaddedReturns() {
        BlockPos root=new BlockPos(7,4,-3);
        for(Direction facing:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)) {
            var pieces=cover(root,facing,0);
            for(double edgeX:new double[]{-.25/16,16.25/16}) for(double side:new double[]{-1,1}) {
                double x=edgeX, z=.2;
                Vec3 point=world(root,facing,x,1.5,z);
                Vec3 normal=new Vec3(facing.getStepX(),0,facing.getStepZ());
                Vec3 origin=point.add(normal.scale(side));
                var hit=DoorExamineGeometry.ray(pieces,origin,point.subtract(normal.scale(side)));
                assertNotNull(hit,"facing="+facing+" edge="+edgeX+" face="+side);
            }
        }
    }

    @Test void qEndpointsAndMidpointUseAuthoredMotionAndOpenApertureHasNoPlaneHit() {
        for(double q:new double[]{0,.5,1}) {
            var boxes=FirelockGeometry.localBoxes(q);
            assertTrue(boxes.stream().allMatch(b->b.x0()>=-.25/16-1e-9&&b.x1()<=16.25/16+1e-9&&b.y0()>=0&&b.y1()<=2));
        }
        var open=FirelockGeometry.worldBounds(BlockPos.ZERO,Direction.NORTH,1);
        Vec3 origin=new Vec3(.5,1,-1), end=new Vec3(.5,1,2);
        assertNull(DoorExamineGeometry.ray(open,origin,end),"open aperture must not select a full door plane");
        var nearOpen=FirelockGeometry.worldBounds(BlockPos.ZERO,Direction.NORTH,1-1e-6);
        assertFalse(nearOpen.isEmpty());
        AABB target=nearOpen.get(0);
        Vec3 rayCenter=new Vec3((target.minX+target.maxX)/2,target.minY-1,(target.minZ+target.maxZ)/2);
        assertNotNull(DoorExamineGeometry.ray(nearOpen,rayCenter,new Vec3(rayCenter.x,target.maxY+1,rayCenter.z)),
                "near-open cover is selectable through the real ray path");
    }

    @Test void rootsAndAttachedVisibilityUseCanonicalRootAndOnlyHideLatchedClosedBase() {
        BlockPos upper=new BlockPos(3,9,2), lower=upper.below();
        assertEquals(lower,DoorExamineGeometry.canonicalLowerRoot(upper,true,true));
        assertEquals(upper,DoorExamineGeometry.canonicalLowerRoot(upper,true,false));
        assertEquals(lower,DoorExamineGeometry.canonicalLowerRoot(lower,false,true));
        var base=List.of(new AABB(0,0,0,1,2,1));
        var cover=List.of(new AABB(2,0,0,3,2,1));
        assertEquals(cover,DoorExamineGeometry.visiblePieces(base,cover,true,true,0));
        assertEquals(List.of(base.get(0),cover.get(0)),DoorExamineGeometry.visiblePieces(base,cover,true,true,.5));
        assertEquals(List.of(base.get(0),cover.get(0)),DoorExamineGeometry.visiblePieces(base,cover,true,false,0));
        assertEquals(base,DoorExamineGeometry.visiblePieces(base,cover,false,false,1),"ordinary airlock remains unchanged");
    }

    @Test void exactOpenAttachedAndStandaloneSelectionHaveNoCoverButRetainAttachedBase() {
        var root = new BlockPos(0, 0, 0);
        var cover = FirelockGeometry.worldBounds(root, Direction.NORTH, 1.0);
        assertTrue(cover.isEmpty(), "strictly clipped zero-volume boundary boxes produce no full-open cover outline");
        var standalonePieces = DoorExamineGeometry.visiblePieces(List.of(), cover, false, false, 1.0);
        assertTrue(standalonePieces.isEmpty());

        var base = List.of(new AABB(0, 0, 0, 1, 2, 1));
        var attachedPieces = DoorExamineGeometry.visiblePieces(base, cover, true, false, 1.0);
        assertEquals(base, attachedPieces, "open attached firelock leaves base airlock selectable");
        assertNotNull(DoorExamineGeometry.ray(attachedPieces,
                new Vec3(.5, 1, -1), new Vec3(.5, 1, 2)), "base remains on the real authored ray-hit path");
        assertTrue(DoorExamineGeometry.visiblePieces(base, cover, true, false, 1.0).stream()
                .noneMatch(box -> box.equals(cover.stream().findAny().orElse(null))));
    }

    private static Vec3 world(BlockPos root,Direction facing,double x,double y,double z) {
        Direction right=facing.getCounterClockWise();
        return new Vec3(root.getX()+.5+right.getStepX()*(x-.5)+facing.getStepX()*(z-.5),root.getY()+y,
                root.getZ()+.5+right.getStepZ()*(x-.5)+facing.getStepZ()*(z-.5));
    }
}
