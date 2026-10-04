package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;

/** Bounded, loaded-only body-eye puddle nomination shared by logical client and server. */
public final class PuddleTargetProbe {
    public static final double MAX_REACH = 5.0;
    public static final double TARGET_MARGIN = BodyItemTargetProbe.TARGET_MARGIN;
    private static final int MAX_CELLS = 256, MAX_CANDIDATES = 32;
    private PuddleTargetProbe() { }

    @Nullable public static BlockPos find(Level level, LivingEntity body) {
        if (level == null || body == null || body.level() != level || body.isRemoved() || !body.isAlive()) return null;
        Vec3 from = body.getEyePosition(), look = body.getLookAngle();
        if (!finite(from) || !finite(look) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01) return null;
        Vec3 to = from.add(look.scale(MAX_REACH));
        AABB bounds = new AABB(from, to).inflate(TARGET_MARGIN);
        int minX = (int)Math.floor(bounds.minX), maxX = (int)Math.floor(bounds.maxX);
        int minY = (int)Math.floor(bounds.minY), maxY = (int)Math.floor(bounds.maxY);
        int minZ = (int)Math.floor(bounds.minZ), maxZ = (int)Math.floor(bounds.maxZ);
        long cells = (long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);
        if (cells <= 0 || cells > MAX_CELLS) return null;
        ArrayList<Candidate> candidates = new ArrayList<>();
        for (int x=minX;x<=maxX;x++) for(int y=minY;y<=maxY;y++) for(int z=minZ;z<=maxZ;z++) {
            BlockPos pos = new BlockPos(x,y,z);
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (!state.is(ModBlocks.PUDDLE.get())) continue;
            AABB box = state.getShape(level,pos,CollisionContext.of(body)).bounds().move(pos).inflate(TARGET_MARGIN);
            var hit = box.contains(from) ? java.util.Optional.of(from) : box.clip(from,to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) <= MAX_REACH*MAX_REACH)
                candidates.add(new Candidate(pos.immutable(), from.distanceToSqr(hit.get())));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparing(Candidate::pos, BlockPos::compareTo));
        if (candidates.size() > MAX_CANDIDATES) candidates.subList(MAX_CANDIDATES,candidates.size()).clear();
        for (Candidate candidate : candidates) if (visible(level,body,candidate.pos(),from)) return candidate.pos();
        return null;
    }

    public static boolean selected(Level level, LivingEntity body, BlockPos nominated) {
        return nominated != null && nominated.equals(find(level,body));
    }

    private static boolean visible(Level level, LivingEntity body, BlockPos pos, Vec3 from) {
        var shape = level.getBlockState(pos).getShape(level,pos,CollisionContext.of(body));
        if (shape.isEmpty()) return false;
        AABB b=shape.bounds(); Vec3 center=new Vec3(pos.getX()+(b.minX+b.maxX)/2,pos.getY()+(b.minY+b.maxY)/2,pos.getZ()+(b.minZ+b.maxZ)/2);
        if (from.distanceToSqr(center)>MAX_REACH*MAX_REACH) return false;
        AABB rayBounds = new AABB(from, center);
        int minChunkX = Math.floorDiv((int)Math.floor(rayBounds.minX), 16);
        int maxChunkX = Math.floorDiv((int)Math.floor(rayBounds.maxX == rayBounds.minX ? rayBounds.maxX : Math.nextDown(rayBounds.maxX)), 16);
        int minChunkZ = Math.floorDiv((int)Math.floor(rayBounds.minZ), 16);
        int maxChunkZ = Math.floorDiv((int)Math.floor(rayBounds.maxZ == rayBounds.minZ ? rayBounds.maxZ : Math.nextDown(rayBounds.maxZ)), 16);
        for (int x=minChunkX; x<=maxChunkX; x++) for (int z=minChunkZ; z<=maxChunkZ; z++)
            if (!level.hasChunkAt(new BlockPos(x * 16, 0, z * 16))) return false;
        var hit=level.clip(new ClipContext(from,center,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,body));
        return hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }
    private record Candidate(BlockPos pos, double distance) { }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }
}
