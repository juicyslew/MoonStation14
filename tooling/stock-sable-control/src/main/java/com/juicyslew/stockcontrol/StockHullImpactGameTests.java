package com.juicyslew.stockcontrol;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.*;

/** Stock-only vanilla shell impact diagnostic. No MoonStation runtime classes are referenced. */
@PrefixGameTestTemplate(false)
public final class StockHullImpactGameTests {
    private static final int Y=-47,PAD=64,LAYERS=4,W=8,H=7,D=13;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("StockSableControl");
    private StockHullImpactGameTests(){}

    @GameTest(templateNamespace="sable_control",template="empty",timeoutTicks=1200)
    public static void twentyMetresPerSecond(GameTestHelper h){run(h,20,4400);}
    @GameTest(templateNamespace="sable_control",template="empty",timeoutTicks=1200)
    public static void eightyMetresPerSecond(GameTestHelper h){run(h,80,4600);}

    private static void run(GameTestHelper h,int speed,int x){
        StockControlIsolation.verify();
        StockControlShutdownDiagnostics.arm(h);
        var level=h.getLevel(); var scene=new Scene(level,h,new BlockPos(x,Y,3600),speed);
        requireTwoSubsteps(level);
        scene.begin(()->{
            BlockPos source=new BlockPos(x+28,Y,3600+28);
            authorVanillaHull(level,source);
            HullPhysicalSnapshot before=snapshot(level,source);
            PropertyCapture beforeProperties=captureProperties(level,source);
            var selection=new ArrayList<BlockPos>();
            for(int a=0;a<W;a++)for(int b=0;b<H;b++)for(int c=0;c<D;c++){
                BlockPos p=source.offset(a,b,c); if(!level.getBlockState(p).isAir())selection.add(p.immutable());
            }
            if(selection.size()!=452||before.cells().size()!=452)throw fail("setup expected 452 authored cells, selected="+selection.size()+" snapshot="+before.cells().size());
            BlockPos max=source.offset(W-1,H-1,D-1);
            ServerSubLevel owner=SubLevelAssemblyHelper.assembleBlocks(level,source,selection,
                    new BoundingBox3i(source.getX(),source.getY(),source.getZ(),max.getX(),max.getY(),max.getZ()));
            scene.owner=owner; // Must precede every operation that can fail after stock returns ownership.
            BlockPos storage=owner.getPlot().getCenterBlock().immutable(); scene.storage=storage;
            scene.authorPad();
            scene.awaitStorage(()->scene.finishAssembly(source,before,beforeProperties,speed));
        });
    }

    private static void authorVanillaHull(ServerLevel l,BlockPos o){
        for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){
            if(x!=0&&x!=W-1&&y!=0&&y!=H-1&&z!=0&&z!=D-1)continue;
            BlockPos p=o.offset(x,y,z);
            l.setBlock(p,p.equals(o.offset(0,2,4))?Blocks.GLASS.defaultBlockState():Blocks.STONE.defaultBlockState(),3);
        }
        for(int x=1;x<=6;x++)for(int z=1;z<=6;z++)l.setBlock(o.offset(x,3,z),Blocks.STONE.defaultBlockState(),3);
        for(int y=1;y<=3;y++)for(int x=1;x<=6;x++)l.setBlock(o.offset(x,y,7),Blocks.STONE.defaultBlockState(),3);
    }
    private static HullPhysicalSnapshot snapshot(ServerLevel l,BlockPos o){
        var cells=new ArrayList<HullPhysicalSnapshot.Cell>();
        for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){
            BlockPos p=o.offset(x,y,z); BlockState s=l.getBlockState(p); if(s.isAir())continue;
            var boxes=s.getCollisionShape(l,p).toAabbs().stream().map(b->new HullPhysicalSnapshot.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList();
            cells.add(HullPhysicalSnapshot.cellAtWorld(p.getX(),p.getY(),p.getZ(),o.getX(),o.getY(),o.getZ(),s.toString(),boxes));
        }
        return new HullPhysicalSnapshot(cells);
    }

    private record PropertyEntry(String blockId,int count,double mass,double friction,double restitution,double volume,Vec3 inertia){}
    private record PropertyCapture(Map<String,PropertyEntry> byState,double totalMass){
        PropertyCapture{byState=Map.copyOf(byState);}
    }
    private static PropertyCapture captureProperties(ServerLevel level,BlockPos origin){
        var counts=new TreeMap<String,Integer>();var states=new TreeMap<String,BlockState>();
        for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){
            BlockPos p=origin.offset(x,y,z);BlockState state=level.getBlockState(p);if(state.isAir())continue;
            String key=state.toString();counts.merge(key,1,Integer::sum);states.putIfAbsent(key,state);
        }
        var values=new TreeMap<String,PropertyEntry>();double total=0;
        for(var entry:counts.entrySet()){
            BlockState state=states.get(entry.getKey());double cellMass=dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(level,
                    findOne(level,origin,state),state);
            double friction=dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getFriction(state);
            double restitution=dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getRestitution(state);
            double volume=dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getVolume(state);
            Vec3 inertia=dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getInertia(level,
                    findOne(level,origin,state),state);
            if(!Double.isFinite(cellMass)||cellMass<=0||!Double.isFinite(friction)||!Double.isFinite(restitution)||!Double.isFinite(volume))
                throw fail("invalid stock-resolved block properties state="+entry.getKey()+" mass="+cellMass+" friction="+friction+" restitution="+restitution+" volume="+volume);
            values.put(entry.getKey(),new PropertyEntry(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),entry.getValue(),cellMass,friction,restitution,volume,inertia));
            total+=cellMass*entry.getValue();
        }
        if(counts.values().stream().mapToInt(Integer::intValue).sum()!=452||!Double.isFinite(total)||total<=0)
            throw fail("stock property capture invalid cells/mass counts="+counts+" total="+total);
        int stone=values.values().stream().filter(v->v.blockId().equals("minecraft:stone")).mapToInt(PropertyEntry::count).sum();
        int glass=values.values().stream().filter(v->v.blockId().equals("minecraft:glass")).mapToInt(PropertyEntry::count).sum();
        if(stone!=451||glass!=1||values.values().stream().mapToInt(PropertyEntry::count).sum()!=452)
            throw fail("vanilla authored composition expected451 stone+1 glass; actual="+values);
        return new PropertyCapture(values,total);
    }
    private static BlockPos findOne(ServerLevel level,BlockPos origin,BlockState target){
        for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){
            BlockPos p=origin.offset(x,y,z);if(level.getBlockState(p).equals(target))return p;
        }
        throw fail("property-state cell disappeared: "+target);
    }

    private record Part(BlockPos pos,BlockState state,AABB box,List<HullPhysicalSnapshot.Box> shape){}
    private static List<Part> parts(ServerLevel l,BlockPos o){
        var out=new ArrayList<Part>();
        for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){
            BlockPos p=o.offset(x,y,z); BlockState s=l.getBlockState(p); if(s.isAir())continue;
            var shape=s.getCollisionShape(l,p).toAabbs().stream().map(b->new HullPhysicalSnapshot.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList();
            if(shape.size()!=1||!shape.getFirst().equals(new HullPhysicalSnapshot.Box(0,0,0,1,1,1)))throw fail("vanilla hull requires one unit-cube collision shape at "+p+" state="+s+" shape="+shape);
            for(var b:s.getCollisionShape(l,p).toAabbs())out.add(new Part(p.immutable(),s,new AABB(p.getX()+b.minX,p.getY()+b.minY,p.getZ()+b.minZ,p.getX()+b.maxX,p.getY()+b.maxY,p.getZ()+b.maxZ),shape));
        }
        if(out.size()!=452)throw fail("expected 452 actual collision parts, got "+out.size());
        return List.copyOf(out);
    }

    /** Exact API shape consumed by the generated, shared HullImpactTraceState. */
    record Observation(long serverTick,Vector3d position,Quaterniond orientation,Vector3d linearVelocity,
                       Vector3d angularVelocity,AABB hullBounds,double maxTerrainPenetration,BlockPos contactPos,
                       BlockState contactState,Vector3d contactMtv,boolean sceneComplete,boolean hullUnchanged,
                       boolean fullyBelowSupportSlab){}
    private record Sample(Observation row,double mass,Vector3d com,Vector3d pivot,String normalizedBounds){}

    private static Observation observe(Scene s,boolean nativeOnly){
        ServerSubLevel owner=s.owner; RigidBodyHandle handle=RigidBodyHandle.of(owner);
        if(!handle.isValid())return null;
        var pose=owner.logicalPose(); Matrix4d matrix=pose.bakeIntoMatrix(new Matrix4d());
        AABB hull=null; boolean complete=!nativeOnly,unchanged=true; double deepest=0; BlockPos contact=null; BlockState contactState=null; Vector3d mtv=new Vector3d();
        var scratch=new dev.ryanhcode.sable.api.math.LevelReusedVectors();
        for(Part part:s.parts){
            AABB projected=HullTerrainGeometry.transformedBounds(part.box,matrix); hull=hull==null?projected:hull.minmax(projected);
            if(nativeOnly)continue;
            if(!s.storageReady(part.pos)){complete=false;continue;}
            BlockState actual=s.level.getBlockState(part.pos);
            if(!actual.equals(part.state)||!fullCubeShape(s.level,part.pos,actual,part.shape))unchanged=false;
            int minX=BlockPos.containing(projected.minX,0,0).getX(),maxX=BlockPos.containing(Math.nextDown(projected.maxX),0,0).getX();
            int minY=BlockPos.containing(0,projected.minY,0).getY(),maxY=BlockPos.containing(0,Math.nextDown(projected.maxY),0).getY();
            int minZ=BlockPos.containing(0,0,projected.minZ).getZ(),maxZ=BlockPos.containing(0,0,Math.nextDown(projected.maxZ)).getZ();
            for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
                BlockPos p=new BlockPos(x,y,z);
                if(y<s.level.getMinBuildHeight()||y>=s.level.getMaxBuildHeight()||!s.level.getWorldBorder().isWithinBounds(p)||!s.physicalReady(p)){complete=false;continue;}
                BlockState state=s.level.getBlockState(p);
                for(AABB shape:state.getCollisionShape(s.level,p).toAabbs()){
                    AABB terrain=new AABB(x+shape.minX,y+shape.minY,z+shape.minZ,x+shape.maxX,y+shape.maxY,z+shape.maxZ);
                    if(projected.maxX<=terrain.minX||projected.minX>=terrain.maxX||projected.maxY<=terrain.minY||projected.minY>=terrain.maxY||projected.maxZ<=terrain.minZ||projected.minZ>=terrain.maxZ)continue;
                    var c=HullTerrainGeometry.againstTerrain(part.box,matrix,terrain,scratch);
                    if(c.penetrationDepth()>deepest){deepest=c.penetrationDepth();contact=p.immutable();contactState=state;mtv=c.mtv();}
                }
            }
        }
        AABB pad=new AABB(s.origin.getX(),s.level.getMinBuildHeight(),s.origin.getZ(),s.origin.getX()+PAD,s.level.getMaxBuildHeight(),s.origin.getZ()+PAD);
        if(hull==null||!HullTerrainGeometry.withinHorizontalPad(hull,pad)||hull.minY<s.level.getMinBuildHeight()||hull.maxY>=s.level.getMaxBuildHeight())complete=false;
        boolean below=HullTerrainGeometry.fullyBelowSupportSlab(hull,Y-LAYERS);
        return new Observation(s.level.getServer().getTickCount(),pose.position().get(new Vector3d()),new Quaterniond(pose.orientation()),
                handle.getLinearVelocity(new Vector3d()),handle.getAngularVelocity(new Vector3d()),hull,deepest,contact,contactState,mtv,complete,unchanged,below);
    }
    private static boolean fullCubeShape(ServerLevel level,BlockPos p,BlockState state,List<HullPhysicalSnapshot.Box> expected){
        var now=state.getCollisionShape(level,p).toAabbs();
        return now.size()==1&&expected.size()==1&&now.getFirst().minX==expected.getFirst().minX()&&now.getFirst().minY==expected.getFirst().minY()
                &&now.getFirst().minZ==expected.getFirst().minZ()&&now.getFirst().maxX==expected.getFirst().maxX()
                &&now.getFirst().maxY==expected.getFirst().maxY()&&now.getFirst().maxZ==expected.getFirst().maxZ();
    }
    private static boolean finite(Vector3d v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static boolean finite(Quaterniond q){return q!=null&&Double.isFinite(q.x)&&Double.isFinite(q.y)&&Double.isFinite(q.z)&&Double.isFinite(q.w);}
    private static boolean finite(AABB b){return b!=null&&Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ);}
    private static GameTestAssertException fail(String message){return new GameTestAssertException("stock hull impact: "+message);}
    private static void requireTwoSubsteps(ServerLevel level){
        int actual=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level).getConfig().substepsPerTick;
        if(actual!=2)throw fail("setup requires stock default two substeps; actual="+actual);
    }

    private static final class Scene{
        final ServerLevel level; final GameTestHelper helper; final BlockPos origin; final int speed; final Set<Long> addedForced=new HashSet<>(),ownedChunks=new HashSet<>();
        ServerSubLevel owner; BlockPos storage; List<Part> parts=List.of(); HullImpactTraceState trace; int waitTicks;
        PropertyCapture expectedProperties;
        Scene(ServerLevel l,GameTestHelper h,BlockPos o,int speed){level=l;helper=h;origin=o;this.speed=speed;}
        void begin(Runnable ready){
            helper.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo i){}
                public void testPassed(GameTestInfo i,GameTestRunner r){cleanup();}
                public void testFailed(GameTestInfo i,GameTestRunner r){cleanup();}
                public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){cleanup();}
            });
            try{
                for(int x=origin.getX()>>4;x<=(origin.getX()+PAD-1)>>4;x++)for(int z=origin.getZ()>>4;z<=(origin.getZ()+PAD-1)>>4;z++)force(new ChunkPos(x,z));
                awaitWorld(ready);
            }catch(RuntimeException|Error e){cleanup();throw e;}
        }
        void force(ChunkPos p){
            int minX=origin.getX()>>4,maxX=(origin.getX()+PAD-1)>>4,minZ=origin.getZ()>>4,maxZ=(origin.getZ()+PAD-1)>>4;
            if(p.x<minX||p.x>maxX||p.z<minZ||p.z>maxZ)throw fail("refusing physical chunk force outside pad: "+p);
            ownedChunks.add(p.toLong());if(!level.getForcedChunks().contains(p.toLong())){level.setChunkForced(p.x,p.z,true);addedForced.add(p.toLong());}
        }
        boolean inStorage(BlockPos p){return storage!=null&&p.getX()>=storage.getX()&&p.getX()<storage.getX()+W&&p.getY()>=storage.getY()&&p.getY()<storage.getY()+H&&p.getZ()>=storage.getZ()&&p.getZ()<storage.getZ()+D;}
        boolean physicalReady(BlockPos p){
            boolean inWorld=p.getX()>=origin.getX()&&p.getX()<origin.getX()+PAD&&p.getZ()>=origin.getZ()&&p.getZ()<origin.getZ()+PAD;
            if(!inWorld)return false;ChunkPos c=new ChunkPos(p);
            return ownedChunks.contains(c.toLong())&&level.isPositionEntityTicking(p)
                    &&level.getChunkSource().getChunk(c.x,c.z,ChunkStatus.FULL,false) instanceof LevelChunk;
        }
        boolean storageReady(BlockPos p){
            return inStorage(p)&&storageChunkReady(new ChunkPos(p));
        }
        boolean storageChunkReady(ChunkPos global){
            if(owner==null)return false;
            var container=SubLevelContainer.getContainer(level);
            if(container==null||container.getSubLevel(owner.getUniqueId())!=owner)return false;
            var plot=container.getPlot(global);
            return plot==owner.getPlot()&&plot.contains(global)&&plot.getChunk(plot.toLocal(global))!=null;
        }
        void awaitWorld(Runnable ready){if(worldReady()){requireEmptyOwnedWorld();ready.run();return;}if(++waitTicks>=60)throw fail("world pad chunks failed ENTITY-TICKING/FULL readiness");helper.runAfterDelay(1,()->awaitWorld(ready));}
        void requireEmptyOwnedWorld(){for(int x=0;x<PAD;x++)for(int z=0;z<PAD;z++)for(int y=Y-LAYERS;y<Y+32;y++){
            BlockPos p=new BlockPos(origin.getX()+x,y,origin.getZ()+z);
            if(!level.getBlockState(p).isAir()||!level.getFluidState(p).isEmpty()||level.getBlockEntity(p)!=null)throw fail("fresh bounded fixture region is not empty at "+p);
        }}
        boolean worldReady(){for(int x=0;x<PAD;x++)for(int z=0;z<PAD;z++){BlockPos p=origin.offset(x,0,z);if(!physicalReady(p))return false;}return true;}
        void awaitStorage(Runnable action){if(storageFootprintReady()){action.run();return;}if(++waitTicks>=120)throw fail("Sable virtual storage footprint failed exact-owner plot residency");helper.runAfterDelay(1,()->awaitStorage(action));}
        boolean storageFootprintReady(){
            var chunks=new LinkedHashSet<ChunkPos>();for(int x=0;x<W;x++)for(int z=0;z<D;z++)chunks.add(new ChunkPos(storage.offset(x,0,z)));
            for(ChunkPos chunk:chunks)if(!storageChunkReady(chunk))return false;
            return true;
        }
        void finishAssembly(BlockPos source,HullPhysicalSnapshot before,PropertyCapture beforeProperties,int speed){
            HullPhysicalSnapshot after=snapshot(level,storage);String mismatch=before.mismatch(after);if(mismatch!=null)throw fail("stock-transfer physical mismatch: "+mismatch);
            PropertyCapture afterProperties=captureProperties(level,storage);
            if(!beforeProperties.equals(afterProperties))throw fail("stock-resolved block-property mismatch before="+beforeProperties+" after="+afterProperties);
            expectedProperties=beforeProperties;
            logProperties("pre-assembly",beforeProperties);logProperties("post-assembly",afterProperties);
            parts=StockHullImpactGameTests.parts(level,storage); trace=new HullImpactTraceState();
            double actualMass=owner.getMassTracker().getMass();requireTrackerMass(actualMass);
            LOG.info("[stock-hull-residency] physicalPadChunks={} physicalAddedForcedChunks={} actualAllocatedStorageFootprintChunks={} physicalChunksOnly=true",
                    ownedChunks.size(),addedForced.size(),storageChunkCount());
            for(long packed:addedForced){ChunkPos chunk=new ChunkPos(packed);if(chunk.x<(origin.getX()>>4)||chunk.x>((origin.getX()+PAD-1)>>4)||chunk.z<(origin.getZ()>>4)||chunk.z>((origin.getZ()+PAD-1)>>4))throw fail("nonphysical chunk entered forced set: "+chunk);}
            LOG.info("[stock-hull-assembly] cells={} parts={} sha256={} resolvedMassSum={} actualMass={} com={} pivot={} pose={} frame={} NOTE=vanilla fingerprint differs from R16",
                    after.cells().size(),parts.size(),after.sha256Fingerprint(),expectedProperties.totalMass(),actualMass,owner.getMassTracker().getCenterOfMass(),owner.logicalPose().rotationPoint(),owner.logicalPose(),owner.getUniqueId());
            validateComposition(); awaitBody(0,0);
        }
        int storageChunkCount(){
            var chunks=new LinkedHashSet<ChunkPos>();for(int x=0;x<W;x++)for(int z=0;z<D;z++)chunks.add(new ChunkPos(storage.offset(x,0,z)));
            var container=SubLevelContainer.getContainer(level);int allocated=0;
            for(ChunkPos global:chunks){var plot=container==null?null:container.getPlot(global);
                if(plot==owner.getPlot()&&plot.contains(global)&&plot.getChunk(plot.toLocal(global))!=null)allocated++;}
            if(allocated!=chunks.size())throw fail("allocated Sable storage chunk count mismatch expected="+chunks.size()+" actual="+allocated);
            return allocated;
        }
        void validateComposition(){
            requireTwoSubsteps(level);
            double mass=owner.getMassTracker().getMass();var com=owner.getMassTracker().getCenterOfMass();
            if(!Double.isFinite(mass)||mass<=0||com==null||!Double.isFinite(com.x())||!Double.isFinite(com.y())||!Double.isFinite(com.z()))throw fail("invalid Sable mass/COM");
            requireTrackerMass(mass);
        }
        void requireTrackerMass(double actual){if(expectedProperties==null||!Double.isFinite(actual)||Math.abs(actual-expectedProperties.totalMass())>1e-6)
            throw fail("Sable mass tracker differs from independently resolved stock per-cell MASS sum expected="+(expectedProperties==null?null:expectedProperties.totalMass())+" actual="+actual);}
        void logProperties(String phase,PropertyCapture capture){for(PropertyEntry property:capture.byState().values())
            LOG.info("[stock-hull-properties] phase={} block={} stateCount={} resolvedMassEach={} friction={} restitution={} volume={} inertia={} totalMass={}",phase,property.blockId(),property.count(),property.mass(),property.friction(),property.restitution(),property.volume(),property.inertia(),capture.totalMass());}
        void authorPad(){for(int x=0;x<PAD;x++)for(int z=0;z<PAD;z++)for(int layer=1;layer<=LAYERS;layer++)level.setBlock(origin.offset(x,-layer,z),Blocks.STONE.defaultBlockState(),3);}
        void awaitBody(int tick,int settled){
            var handle=RigidBodyHandle.of(owner);if(!handle.isValid()){if(tick>=80)throw fail("native body invalid through bounded wait");helper.runAfterDelay(1,()->awaitBody(tick+1,settled));return;}
            Observation row=observe(this,false);if(row==null)throw fail("invalid native body during setup");
            if(!row.sceneComplete()||!row.hullUnchanged()||!finite(row.position())||!finite(row.orientation())||!finite(row.linearVelocity())||!finite(row.angularVelocity())||!finite(row.hullBounds()))throw fail("setup observation invalid: "+row);
            boolean near=HullTerrainGeometry.nearSupportSurface(row.hullBounds(),Y,.10,.02);
            boolean still=near&&row.linearVelocity().lengthSquared()<.0025&&row.angularVelocity().lengthSquared()<.0001;
            int now=still?settled+1:0;
            if(now>=10){
                if(!HullTerrainGeometry.nearIdentityOrientation(row.orientation()))throw fail("grounded admission orientation outside identity tolerance: "+row.orientation());
                validateComposition();
                double mass=owner.getMassTracker().getMass();var com=owner.getMassTracker().getCenterOfMass();var pose=owner.logicalPose();
                AABB normalized=row.hullBounds().move(-origin.getX(),-Y,-origin.getZ());
                LOG.info("[stock-hull-admission] phase=SETUP speed={} frame={} substeps=2 parts={} mass={} com={} pivot={} pose={} orientation={} normalizedBounds={} nativeLinear={} nativeAngular={} restSamples={}",
                        speed,owner.getUniqueId(),parts.size(),mass,com,pose.rotationPoint(),pose,row.orientation(),normalized,row.linearVelocity(),row.angularVelocity(),now);
                requireTrackerMass(mass);
                handle.addLinearAndAngularVelocity(new Vector3d(0,speed,0),new Vector3d());Vector3d admitted=handle.getLinearVelocity(new Vector3d());
                if(!finite(admitted)||admitted.y<speed*.9)throw fail("upward velocity not admitted; requested="+speed+" actual="+admitted);
                int substeps=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level).getConfig().substepsPerTick;
                LOG.info("[stock-hull-launch] speed={} admitted={} substeps={} frame={}",speed,admitted,substeps,owner.getUniqueId());
                flight(0,admitted,false,false,false,0);return;
            }
            if(tick>=300)throw fail("natural grounded admission did not settle");helper.runAfterDelay(1,()->awaitBody(tick+1,now));
        }
        void flight(int tick,Vector3d admitted,boolean asc,boolean desc,boolean contacted,int settled){
            boolean nativeOnly=trace.requiresNativeOnlySample(); Observation row=observe(this,nativeOnly);
            if(row==null)throw fail("TRAJECTORY invalid native body at tick="+tick);
            trace.observe(tick,row);
            var failure=trace.failure();
            if(failure!=null&&trace.postFailureSamples()==0){
                LOG.error("[stock-hull-failure] speed={} frame={} reason={} witness={} confirmed={} admitted={} maxDepth={}",speed,owner.getUniqueId(),failure.reason(),failure.firstTick(),failure.confirmedTick(),admitted,trace.maximumPenetration());
                trace.recentHistory().forEach(sample->logRow("stock-hull-history",sample.tick(),sample.observation()));
            }else if(failure!=null)logRow("stock-hull-postfailure",tick,row);
            if(!finite(row.position())||!finite(row.orientation())||!finite(row.linearVelocity())||!finite(row.angularVelocity())||!finite(row.hullBounds()))throw fail("nonfinite/invalid trajectory sample tick="+tick);
            boolean near=HullTerrainGeometry.nearSupportSurface(row.hullBounds(),Y,.10,.02);
            boolean still=near&&row.sceneComplete()&&row.hullUnchanged()&&row.linearVelocity().lengthSquared()<.0025&&row.angularVelocity().lengthSquared()<.0001;
            boolean nextAsc=asc||row.position().y()>Y+5;boolean nextDesc=desc||(nextAsc&&row.linearVelocity().y()<-.5);boolean nextContact=contacted||near;
            int nextSettled=still?settled+1:0;
            if(tick%20==0||row.maxTerrainPenetration()>.02)logRow("stock-hull-trace",tick,row);
            if(trace.failure()!=null&&trace.finishFailureDiagnostics(nextSettled,tick)){
                LOG.error("[stock-hull-result] outcome=FAIL speed={} reason={} witness={} confirmed={} tick={} postSamples={} maximum={} slabEscape={}",
                        speed,trace.failure().reason(),trace.failure().firstTick(),trace.failure().confirmedTick(),tick,
                        trace.postFailureSamples(),trace.maximumPenetration(),trace.supportSlabEscapeObserved());
                throw fail("latched trajectory failure reason="+trace.failure().reason()+" witness="+trace.failure().firstTick()+" confirmed="+trace.failure().confirmedTick()+" tick="+tick+" postSamples="+trace.postFailureSamples()+" maximum="+trace.maximumPenetration()+" slabEscape="+trace.supportSlabEscapeObserved());
            }
            if(trace.failure()==null&&nextSettled>=10){
                if(!nextAsc||!nextDesc||!nextContact)throw fail("no proved ascent/descent/contact");
                LOG.info("[stock-hull-result] outcome=PASS speed={} maxPenetration={} settled={} ascended={} descending={} contacted={}",speed,trace.maximumPenetration(),nextSettled,nextAsc,nextDesc,nextContact);
                finalizeBeforePass(); helper.succeed();return;
            }
            if(tick>=900)throw fail("trajectory timeout maximumPenetration="+trace.maximumPenetration());
            helper.runAfterDelay(1,()->flight(tick+1,admitted,nextAsc,nextDesc,nextContact,nextSettled));
        }
        void logRow(String event,int tick,Observation r){LOG.info("[{}] speed={} tick={} pose={} orientation={} linear={} angular={} bounds={} depth={} cell={} mtv={} coverage={} unchanged={} belowSlab={}",event,speed,tick,r.position(),r.orientation(),r.linearVelocity(),r.angularVelocity(),r.hullBounds(),r.maxTerrainPenetration(),r.contactPos(),r.contactMtv(),r.sceneComplete(),r.hullUnchanged(),r.fullyBelowSupportSlab());}
        boolean finalized;
        void finalizeBeforePass(){
            String errors=finalizeOnce();
            if(errors!=null)throw fail("fixture cleanup failed before PASS: "+errors);
        }
        void cleanup(){
            String errors=finalizeOnce();
            if(errors!=null)LOG.error("[stock-hull-cleanup] outcome=FAIL owner={} errors={}",owner==null?null:owner.getUniqueId(),errors);
        }
        private String finalizeOnce(){
            if(finalized)return null;
            finalized=true; var errors=new ArrayList<String>();
            try{
                if(owner!=null){
                    var container=SubLevelContainer.getContainer(level);
                    if(container.getSubLevel(owner.getUniqueId())==owner)container.removeSubLevel(owner,SubLevelRemovalReason.REMOVED);
                    if(container.getSubLevel(owner.getUniqueId())==owner)errors.add("returned owner remains registered uuid="+owner.getUniqueId());
                }
            }catch(RuntimeException|Error e){errors.add("owner removal: "+e);LOG.error("exact-owner fixture cleanup failed",e);}
            finally{
                for(long packed:new ArrayList<>(addedForced))try{
                    ChunkPos p=new ChunkPos(packed);level.setChunkForced(p.x,p.z,false);
                    if(level.getForcedChunks().contains(packed))errors.add("forced chunk remains set: "+p);
                    else addedForced.remove(packed);
                }catch(RuntimeException|Error e){errors.add("forced chunk release "+new ChunkPos(packed)+": "+e);LOG.error("fixture forced-chunk cleanup failed for {}",new ChunkPos(packed),e);}
            }
            return errors.isEmpty()?null:String.join("; ",errors);
        }
    }
}
