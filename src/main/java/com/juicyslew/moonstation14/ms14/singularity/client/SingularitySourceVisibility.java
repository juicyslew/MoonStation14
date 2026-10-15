package com.juicyslew.moonstation14.ms14.singularity.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Opaque-world-only source visibility using Minecraft's native segment traversal and VISUAL shapes. */
final class SingularitySourceVisibility {
    private SingularitySourceVisibility() { }

    static float fraction(BlockGetter world, Predicate<BlockPos> loaded, Vec3 camera, Vec3 center,
                          float halfExtent, SingularityBillboardBasis basis) {
        int visible=0;
        for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
             Vec3 point=center.add(basis.right().scale(x*halfExtent)).add(basis.up().scale(y*halfExtent));
            if(clear(world,loaded,camera,point)) visible++;
        }
        return visible/9f;
    }

    private static boolean clear(BlockGetter world, Predicate<BlockPos> loaded, Vec3 from, Vec3 to) {
        Boolean blocked=BlockGetter.traverseBlocks(from,to,null,(ignored,mutable)-> {
            BlockPos pos=mutable.immutable();
            if(!loaded.test(pos)) return null;
            BlockState state=world.getBlockState(pos);
            if(!state.canOcclude()) return null;
            var hit=state.getVisualShape(world,pos,CollisionContext.empty()).clip(from,to,pos);
            return hit!=null ? true : null;
        },ignored->false);
        return !Boolean.TRUE.equals(blocked);
    }

    static final class Cache {
        private final Map<UUID, Entry> entries=new HashMap<>();
        float update(UUID id,Object world,float target,long now) {
            Entry e=entries.get(id);
            if(e==null||e.world!=world) { e=new Entry(world,clamp(target),now); entries.put(id,e); }
            float dt=(float)Math.min(.25,Math.max(0,(now-e.time)*1e-9)); e.time=now;
            e.value+=(clamp(target)-e.value)*Math.min(1,dt/.12f);
            if(e.value<.002f)e.value=0; if(e.value>.998f)e.value=1;
            return e.value;
        }
        void prune(java.util.Set<UUID> active) { entries.keySet().removeIf(id->!active.contains(id)); }
        void clear() { entries.clear(); }
        int size() { return entries.size(); }
        private static float clamp(float f) { return Float.isFinite(f)?Math.max(0,Math.min(1,f)):0; }
        private static final class Entry { final Object world; float value; long time; Entry(Object w,float v,long t){world=w;value=v;time=t;} }
    }
}
