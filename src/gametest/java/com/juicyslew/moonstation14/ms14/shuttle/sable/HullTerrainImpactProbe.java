package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedBlockGetter;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Test-only actual authored collision-shape vs loaded-world-cell SAT sampler. */
final class HullTerrainImpactProbe {
    record HullPart(BlockPos storagePos, BlockState authoredState, AABB localBounds) { }
    record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) { }
    record StateMismatch(long serverTick,BlockPos position,String authoredState,String observedState,
                         List<Box> authoredBoxes,List<Box> observedBoxes,boolean boxesEqual,
                         boolean shapeContextReadComplete) {
        StateMismatch {
            position=position.immutable();
            authoredBoxes=List.copyOf(authoredBoxes);
            observedBoxes=List.copyOf(observedBoxes);
        }
    }
    static final class FirstMismatch {
        private StateMismatch value;
        void capture(StateMismatch mismatch) { if(value==null)value=mismatch; }
        StateMismatch get() { return value; }
    }
    record Observation(long serverTick, Vector3d position, org.joml.Quaterniond orientation, Vector3d linearVelocity,
                       Vector3d angularVelocity, AABB hullBounds, double maxTerrainPenetration,
                       BlockPos contactPos, BlockState contactState, Vector3d contactMtv,
                       boolean sceneComplete, boolean hullUnchanged, boolean fullyBelowSupportSlab) { }

    private final ServerLevel level;
    private final SableFixtureScene.Region scene;
    private final SableFixturePlot plot;
    private final BlockPos storage;
    private final List<HullPart> parts;
    private final LevelReusedVectors scratch = new LevelReusedVectors();
    private final FirstMismatch firstMismatch=new FirstMismatch();

    HullTerrainImpactProbe(ServerLevel level, SableFixtureScene.Region scene, SableFixturePlot plot,
                           BlockPos storage) {
        this.level=level; this.scene=scene; this.plot=plot; this.storage=storage;
        this.parts=collectHull(level,storage);
        if(parts.isEmpty())throw new IllegalStateException("production cabin has no actual collision-shape hull");
    }

    static List<HullPart> collectHull(ServerLevel level,BlockPos storage) {
        var hull=new ArrayList<HullPart>();
        for(int x=0;x<com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.WIDTH;x++)
            for(int y=0;y<com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.HEIGHT;y++)
                for(int z=0;z<com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.DEPTH;z++) {
                    BlockPos pos=storage.offset(x,y,z);
                    BlockState state=level.getBlockState(pos);
                    if(state.isAir())continue;
                    for(AABB shape:state.getCollisionShape(level,pos).toAabbs())
                        hull.add(new HullPart(pos.immutable(),state,new AABB(pos.getX()+shape.minX,
                                pos.getY()+shape.minY,pos.getZ()+shape.minZ,pos.getX()+shape.maxX,
                                pos.getY()+shape.maxY,pos.getZ()+shape.maxZ)));
                }
        return List.copyOf(hull);
    }

    static HullPhysicalSnapshot capturePhysical(ServerLevel level,BlockPos origin,int width,int height,int depth) {
        if(width<1||height<1||depth<1||(long)width*height*depth>HullPhysicalSnapshot.MAX_CELLS)
            throw new IllegalArgumentException("physical snapshot bounds exceed the production cabin limit");
        var cells=new ArrayList<HullPhysicalSnapshot.Cell>();
        for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<depth;z++) {
            BlockPos pos=origin.offset(x,y,z); BlockState state=level.getBlockState(pos);
            if(state.isAir())continue;
            List<HullPhysicalSnapshot.Box> boxes=state.getCollisionShape(level,pos).toAabbs().stream()
                    .map(b->new HullPhysicalSnapshot.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList();
            cells.add(HullPhysicalSnapshot.cellAtWorld(pos.getX(),pos.getY(),pos.getZ(),origin.getX(),origin.getY(),
                    origin.getZ(),state.toString(),boxes));
        }
        if(cells.isEmpty())throw new IllegalStateException("physical hull snapshot is empty");
        return new HullPhysicalSnapshot(cells);
    }

    int hullPartCount(){return parts.size();}
    StateMismatch firstMismatch(){return firstMismatch.get();}
    java.util.UUID frameId(){return plot.engineId();}
    int supportSurfaceY(){return scene.supportSurfaceY();}
    int supportBottomY(){return scene.supportBottomY();}

    /** After evidence leaves known loaded coverage, capture native state only; never query/load world cells. */
    Observation sampleNativeOnly() {
        var pose=plot.subLevel().logicalPose();
        var matrix=pose.bakeIntoMatrix(new Matrix4d());
        var handle=dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel());
        AABB hullBounds=null;
        for(var part:parts) {
            AABB bounds=HullTerrainGeometry.transformedBounds(part.localBounds(),matrix);
            hullBounds=hullBounds==null?bounds:hullBounds.minmax(bounds);
        }
        if(handle==null||!handle.isValid())return new Observation(level.getServer().getTickCount(),
                pose.position().get(new Vector3d()),new org.joml.Quaterniond(pose.orientation()),
                new Vector3d(),new Vector3d(),hullBounds,0,null,null,new Vector3d(),false,false,
                HullTerrainGeometry.fullyBelowSupportSlab(hullBounds,scene.supportBottomY()));
        return new Observation(level.getServer().getTickCount(),pose.position().get(new Vector3d()),
                new org.joml.Quaterniond(pose.orientation()),handle.getLinearVelocity(new Vector3d()),
                handle.getAngularVelocity(new Vector3d()),hullBounds,0,null,null,new Vector3d(),false,false,
                HullTerrainGeometry.fullyBelowSupportSlab(hullBounds,scene.supportBottomY()));
    }

    Observation sample() {
        var pose=plot.subLevel().logicalPose();
        var matrix=pose.bakeIntoMatrix(new Matrix4d());
        var orientation=new org.joml.Quaterniond(pose.orientation());
        var handle=dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel());
        if(handle==null||!handle.isValid())return new Observation(level.getServer().getTickCount(),
                pose.position().get(new Vector3d()),orientation,new Vector3d(),new Vector3d(),null,
                0,null,null,new Vector3d(),false,false,false);
        var position=pose.position().get(new Vector3d());
        var linear=handle.getLinearVelocity(new Vector3d());
        var angular=handle.getAngularVelocity(new Vector3d());
        AABB hullBounds=null;
        boolean complete=true, unchanged=true;
        double deepest=0;
        BlockPos deepestPos=null;
        BlockState deepestState=null;
        Vector3d deepestMtv=new Vector3d();
        for(var part:parts) {
            boolean storageKnownLoaded=scene.tickets.entityTicking(part.storagePos())
                    &&level.getChunkSource().getChunk(part.storagePos().getX()>>4,part.storagePos().getZ()>>4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null;
            if(!storageKnownLoaded) complete=false;
            else {
                BlockState observed=level.getBlockState(part.storagePos());
                if(!observed.equals(part.authoredState())) {
                    unchanged=false;
                    if(firstMismatch.get()==null) {
                        List<Box> authored=canonicalBoxes(parts.stream().filter(p->p.storagePos().equals(part.storagePos()))
                                .map(p->new Box(p.localBounds().minX-part.storagePos().getX(),
                                        p.localBounds().minY-part.storagePos().getY(),p.localBounds().minZ-part.storagePos().getZ(),
                                        p.localBounds().maxX-part.storagePos().getX(),
                                        p.localBounds().maxY-part.storagePos().getY(),
                                        p.localBounds().maxZ-part.storagePos().getZ())).toList());
                        List<Box> current=List.of();
                        boolean shapeContextComplete=true;
                        try {
                            int chunkX=part.storagePos().getX()>>4,chunkZ=part.storagePos().getZ()>>4;
                            var bounded=new LoadedBlockGetter(level,chunkX-1,chunkX+1,chunkZ-1,chunkZ+1);
                            current=canonicalBoxes(observed.getCollisionShape(bounded,part.storagePos()).toAabbs().stream()
                                    .map(b->new Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList());
                        } catch(LoadedBlockGetter.UnknownChunk unknown) {
                            shapeContextComplete=false;
                        }
                        firstMismatch.capture(new StateMismatch(level.getServer().getTickCount(),part.storagePos().immutable(),
                                part.authoredState().toString(),observed.toString(),List.copyOf(authored),List.copyOf(current),
                                shapeContextComplete&&authored.equals(current),shapeContextComplete));
                    }
                }
            }
            AABB broadphase=HullTerrainGeometry.transformedBounds(part.localBounds(),matrix);
            hullBounds=hullBounds==null?broadphase:hullBounds.minmax(broadphase);
            int minX=net.minecraft.core.BlockPos.containing(broadphase.minX,0,0).getX();
            int maxX=net.minecraft.core.BlockPos.containing(Math.nextDown(broadphase.maxX),0,0).getX();
            int minY=net.minecraft.core.BlockPos.containing(0,broadphase.minY,0).getY();
            int maxY=net.minecraft.core.BlockPos.containing(0,Math.nextDown(broadphase.maxY),0).getY();
            int minZ=net.minecraft.core.BlockPos.containing(0,0,broadphase.minZ).getZ();
            int maxZ=net.minecraft.core.BlockPos.containing(0,0,Math.nextDown(broadphase.maxZ)).getZ();
            for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++) {
                BlockPos worldPos=new BlockPos(x,y,z);
                if(!scene.tickets.entityTicking(worldPos)
                        || level.getChunkSource().getChunk(x>>4,z>>4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null
                        || y<level.getMinBuildHeight()||y>=level.getMaxBuildHeight()
                        || !level.getWorldBorder().isWithinBounds(worldPos)) {complete=false;continue;}
                BlockState state=level.getBlockState(worldPos);
                for(AABB shape:state.getCollisionShape(level,worldPos).toAabbs()) {
                    AABB terrain=new AABB(x+shape.minX,y+shape.minY,z+shape.minZ,
                            x+shape.maxX,y+shape.maxY,z+shape.maxZ);
                    if(!intersectsAabb(broadphase,terrain))continue;
                    var collision=HullTerrainGeometry.againstTerrain(part.localBounds(),matrix,terrain,scratch);
                    double depth=collision.penetrationDepth();
                    if(depth>deepest) {deepest=depth;deepestPos=worldPos.immutable();deepestState=state;
                        deepestMtv=collision.mtv();}
                }
            }
        }
        if(hullBounds==null)complete=false;
        AABB horizontalPad=new AABB(scene.min.getX(),level.getMinBuildHeight(),scene.min.getZ(),
                scene.max.getX()+1d,level.getMaxBuildHeight(),scene.max.getZ()+1d);
        if(hullBounds!=null&&(!HullTerrainGeometry.withinHorizontalPad(hullBounds,horizontalPad)
                ||hullBounds.minY<level.getMinBuildHeight()||hullBounds.maxY>=level.getMaxBuildHeight()))complete=false;
        double supportBottom=scene.supportBottomY();
        boolean fullyBelow=HullTerrainGeometry.fullyBelowSupportSlab(hullBounds,supportBottom);
        return new Observation(level.getServer().getTickCount(),position,orientation,linear,angular,hullBounds,
                deepest,deepestPos,deepestState,deepestMtv,complete,unchanged,fullyBelow);
    }

    private static boolean intersectsAabb(AABB a,AABB b) {
        return a.maxX>b.minX&&a.minX<b.maxX&&a.maxY>b.minY&&a.minY<b.maxY&&a.maxZ>b.minZ&&a.minZ<b.maxZ;
    }
    private static int compareBoxes(Box a,Box b) {
        int c=Double.compare(a.minX(),b.minX()); if(c!=0)return c;
        c=Double.compare(a.minY(),b.minY()); if(c!=0)return c;
        c=Double.compare(a.minZ(),b.minZ()); if(c!=0)return c;
        c=Double.compare(a.maxX(),b.maxX()); if(c!=0)return c;
        c=Double.compare(a.maxY(),b.maxY()); return c!=0?c:Double.compare(a.maxZ(),b.maxZ());
    }
    static List<Box> canonicalBoxes(List<Box> boxes) {
        return HullPhysicalSnapshot.canonicalBoxes(boxes.stream()
                .map(b->new HullPhysicalSnapshot.Box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())).toList())
                .stream().map(b->new Box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())).toList();
    }
}
