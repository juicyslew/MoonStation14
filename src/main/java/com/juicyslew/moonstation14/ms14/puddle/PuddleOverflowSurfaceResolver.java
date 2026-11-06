package com.juicyslew.moonstation14.ms14.puddle;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/** Resolves a puddle falling over a frame edge onto the first loaded world/frame floor below it. */
public final class PuddleOverflowSurfaceResolver {
    private static final int MAX_CHUNKS = 512;
    private static final int MAX_DROP = 30;

    private PuddleOverflowSurfaceResolver() { }

    public record Surface(BlockPos puddlePos, java.util.UUID frameId, Vec3 worldPoint) {
        public Surface { puddlePos = puddlePos.immutable(); }
    }

    /** null means no source frame; unknown query coverage is distinct and must retain the source contents. */
    public record Source(boolean frameOwned, boolean unknown, Vec3 worldEdge, java.util.UUID frameId) { }

    public static Source sourceEdge(ServerLevel level, BlockPos source, Direction edge) {
        var container = SubLevelContainer.getContainer(level);
        var plot = container.getPlot(new net.minecraft.world.level.ChunkPos(source));
        if (plot == null || !plot.contains(new net.minecraft.world.level.ChunkPos(source)))
            return new Source(false, false, null, null);
        SubLevel frame = container.getSubLevel(plot.getSubLevel().getUniqueId());
        if (!(frame instanceof ServerSubLevel server) || server.isRemoved() || server.getLevel() != level
                || server.getPlot() != plot || container.getSubLevel(server.getUniqueId()) != server)
            return new Source(true, true, null, null);
        var pose = level instanceof LevelPoseProviderExtension provider
                ? provider.sable$getPose(server) : server.logicalPose();
        Vec3 localEdge = new Vec3(source.getX() + .5 + edge.getStepX() * .51,
                source.getY() + .02, source.getZ() + .5 + edge.getStepZ() * .51);
        Vec3 worldEdge = pose.transformPosition(localEdge);
        if (!Double.isFinite(worldEdge.x) || !Double.isFinite(worldEdge.y) || !Double.isFinite(worldEdge.z))
            return new Source(true, true, null, server.getUniqueId());
        return new Source(true, false, worldEdge, server.getUniqueId());
    }

    public static Surface resolve(ServerLevel level, Source source) {
        if (source == null || !source.frameOwned() || source.unknown() || source.worldEdge() == null) return null;
        Vec3 from = source.worldEdge();
        Vec3 to = from.add(0, -MAX_DROP, 0);
        LoadedFrameRayQuery.Result result = LoadedFrameRayQuery.clip(level, from, to, MAX_CHUNKS, 1, 0,
                (getter, start, end) -> new ClipContext(start, end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
        if (result.state() != LoadedFrameRayQuery.State.HIT) return null;
        if (result.frameHit() != null) {
            var hit = result.frameHit();
            if (hit.localFace() != Direction.UP || hit.frameId().equals(source.frameId())
                    || !LoadedFrameRayQuery.revalidate(level, hit)) return null;
            return new Surface(hit.storagePos().above(), hit.frameId(), hit.worldPoint());
        }
        var hit = result.worldHit();
        if (hit == null || hit.getDirection() != Direction.UP || !level.hasChunkAt(hit.getBlockPos())) return null;
        return new Surface(hit.getBlockPos().above(), null, hit.getLocation());
    }
}
