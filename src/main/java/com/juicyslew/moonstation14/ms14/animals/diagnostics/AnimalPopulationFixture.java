package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import com.juicyslew.moonstation14.entities.AnimalMobRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Owns only blocks and registered animals created for one bounded population fixture. */
public final class AnimalPopulationFixture {
    public record BlockChange(BlockPos pos, BlockState originalState, BlockState placedState) { }
    public record CleanupResult(int removedEntities, int restoredBlocks, List<UUID> unresolvedEntities) { }
    public record PopulationStatus(int loadedAlive, int confirmedRemoved, int unresolved) { }
    public record Creation(Handle handle, String failure) { public boolean succeeded() { return handle != null; } }
    public static final class Handle {
        private final ServerLevel level; private final BlockPos origin; private final AnimalPopulationSpec spec;
        private final List<BlockChange> changes=new ArrayList<>(); private final Map<UUID,Entity> animals=new LinkedHashMap<>();
        private Handle(ServerLevel level, BlockPos origin, AnimalPopulationSpec spec) { this.level=level; this.origin=origin.immutable(); this.spec=spec; }
        public ServerLevel level(){return level;} public BlockPos origin(){return origin;} public AnimalPopulationSpec spec(){return spec;}
        public List<BlockChange> changes(){return List.copyOf(changes);} public List<UUID> animalIds(){return List.copyOf(animals.keySet());}
        public int spawnedCount(){return animals.size();}
        public PopulationStatus populationStatus(){return status(this);}
        public CleanupResult cleanup(){return cleanupOwned(this);}
    }
    private AnimalPopulationFixture() { }

    public static Creation prepare(ServerLevel level, BlockPos origin, AnimalPopulationSpec spec) {
        Handle h=new Handle(level,origin,spec);
        String failure=preflight(level,origin,spec);
        if(failure!=null)return new Creation(null,failure);
        try {
            for(int x=0;x<spec.width();x++) for(int z=0;z<spec.width();z++) {
                BlockPos floor=origin.offset(x,0,z); boolean boundary=x==0||z==0||x==spec.width()-1||z==spec.width()-1;
                if(level.getBlockState(floor).isAir()) {
                    boolean marker=x==spec.width()/2&&z==spec.width()/2;
                    place(h,floor,marker?Blocks.GLOWSTONE.defaultBlockState():Blocks.STONE.defaultBlockState());
                }
                if(boundary) for(int y=1;y<=3;y++) place(h,floor.above(y),Blocks.GLASS.defaultBlockState());
                if(boundary&&(x==0||x==spec.width()-1)) place(h,floor.above(4),Blocks.GLASS.defaultBlockState());
            }
            return new Creation(h,null);
        } catch(RuntimeException ex) {
            cleanupOwned(h);
            return new Creation(null,"fixture creation failed at "+origin+": "+ex.getMessage());
        }
    }
    public static Creation create(ServerLevel level,BlockPos origin,AnimalPopulationSpec spec) {
        Creation prepared=prepare(level,origin,spec);
        if(!prepared.succeeded())return prepared;
        String failure=spawn(prepared.handle());
        return failure==null?prepared:new Creation(null,failure);
    }
    public static String spawn(Handle h) {
        try {
            for(int i=0;i<h.spec.count();i++) {
                BlockPos cell=FixtureSettling.spawnCell(i,h.spec.width(),h.spec.cellSpacing());
                String species=h.spec.speciesAt(i);
                Entity e=AnimalPopulationSpec.showcaseType(species).create(h.level);
                if(e==null)throw new IllegalStateException("registered factory returned null: "+species);
                e.moveTo(h.origin.getX()+cell.getX()+.5,h.origin.getY()+1,h.origin.getZ()+cell.getZ()+.5,0,0);
                if(!h.level.addFreshEntity(e))throw new IllegalStateException("entity admission rejected: "+species);
                h.animals.put(e.getUUID(),e);
            }
            return null;
        } catch(RuntimeException ex) { cleanupOwned(h);return "fixture spawn failed at "+h.origin+": "+ex.getMessage(); }
    }
    public static boolean chunksLoaded(ServerLevel level,BlockPos origin,int width) {
        return level.hasChunkAt(origin)&&level.hasChunkAt(origin.offset(width-1,4,width-1))
                &&level.hasChunkAt(origin.offset(width-1,0,0))&&level.hasChunkAt(origin.offset(0,4,width-1));
    }
    static String preflight(ServerLevel level,BlockPos o,AnimalPopulationSpec s) {
        int min=level.getMinBuildHeight(),max=level.getMaxBuildHeight()-1;
        for(int x=0;x<s.width();x++)for(int z=0;z<s.width();z++) {
            BlockPos floor=o.offset(x,0,z);
            for(int y=0;y<=4;y++) {
                BlockPos p=floor.above(y);
                if(p.getY()<min||p.getY()>max)return "outside build height at "+p;
                if(!level.hasChunkAt(p))return "chunk not loaded at "+p;
                if(!level.getWorldBorder().isWithinBounds(p))return "outside world border at "+p;
            }
            BlockState state=level.getBlockState(floor);
            if(!state.isAir()&&(!state.isFaceSturdy(level,floor, Direction.UP)||!level.getFluidState(floor).isEmpty()))return "unsafe wet/unsupported floor at "+floor;
            for(int y=1;y<=4;y++)if(!level.getBlockState(floor.above(y)).isAir())return "headroom obstructed at "+floor.above(y);
        }
        var box=new net.minecraft.world.phys.AABB(o.getX(),o.getY(),o.getZ(),o.getX()+s.width(),o.getY()+5,o.getZ()+s.width());
        if(!level.getEntities((Entity)null,box,e->true).isEmpty())return "non-fixture entity occupies fixture volume at "+o;
        return null;
    }
    private static void place(Handle h,BlockPos p,BlockState state) {
        BlockState original=h.level.getBlockState(p);
        h.level.setBlock(p,state,3);
        h.changes.add(new BlockChange(p.immutable(),original,state));
    }
    private static CleanupResult cleanupOwned(Handle h) {
        int removed=0,restored=0; List<UUID> unresolved=new ArrayList<>();
        for(var owned:h.animals.entrySet()) {
            Entity e=h.level.getEntity(owned.getKey());
            if(e!=null){owned.setValue(e);e.discard();removed++;continue;}
            Entity known=owned.getValue();
            if(known.isRemoved()&&known.getRemovalReason()!=null&&known.getRemovalReason().shouldDestroy()) continue;
            unresolved.add(owned.getKey());
        }
        for(int i=h.changes.size()-1;i>=0;i--){BlockChange c=h.changes.get(i);if(h.level.getBlockState(c.pos()).equals(c.placedState())){h.level.setBlock(c.pos(),c.originalState(),3);restored++;}}
        return new CleanupResult(removed,restored,List.copyOf(unresolved));
    }
    private static PopulationStatus status(Handle h) {
        int alive=0,gone=0,unresolved=0;
        for(var owned:h.animals.entrySet()) {
            Entity current=h.level.getEntity(owned.getKey());
            if(current!=null) { if(current.isAlive()&&!current.isRemoved())alive++; else gone++; continue; }
            Entity known=owned.getValue();
            if(known.isRemoved()&&known.getRemovalReason()!=null&&known.getRemovalReason().shouldDestroy())gone++;
            else unresolved++;
        }
        return new PopulationStatus(alive,gone,unresolved);
    }
}
