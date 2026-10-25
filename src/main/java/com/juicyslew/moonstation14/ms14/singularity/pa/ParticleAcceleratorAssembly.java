package com.juicyslew.moonstation14.ms14.singularity.pa;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

/** Resolves a PA footprint from prototype-defined offsets, never from a second runtime layout. */
public final class ParticleAcceleratorAssembly {
    private ParticleAcceleratorAssembly() {}

    public record Part(ParticleAcceleratorPartProfile profile, BlockPos position, Rotation rotation) {}
    public record Resolved(BlockPos control, List<Part> parts) {}

    /** SS14 x/y projects to Minecraft X/-Z; this preserves Robust Angle's south-zero convention. */
    public static Optional<Resolved> resolve(BlockPos control, Rotation rotation,
                                               ParticleAcceleratorControlProfile profile,
                                               java.util.function.Function<BlockPos, ParticleAcceleratorPartIdentity> snapshot) {
        var result = new java.util.ArrayList<Part>();
        var worldPositions = new java.util.HashSet<BlockPos>();
        for (var entry : profile.parts().entrySet()) {
            var part = entry.getValue();
            BlockPos target = control.offset(toWorldVec(part.offsetX(), part.offsetY(), rotation));
            if (!worldPositions.add(target) || !part.role().equals(entry.getKey())) return Optional.empty();
            Rotation partRotation = rotation.getRotated(part.relativeRotation());
            ParticleAcceleratorPartIdentity identity = snapshot.apply(target);
            ResourceLocation expected;
            try { expected = ResourceLocation.parse(part.completedPrototype()); }
            catch (RuntimeException e) { return Optional.empty(); }
            if (identity == null || !identity.completed() || !identity.anchored()
                    || !part.role().equals(identity.role()) || !expected.equals(identity.prototypeId())
                    || identity.orientation() != partRotation) return Optional.empty();
            result.add(new Part(part, target, partRotation));
        }
        return Optional.of(new Resolved(control, List.copyOf(result)));
    }

    /** SS14 local PA x/y maps to Minecraft X/-Z. NONE points south; source -90/CW90 points west. */
    public static BlockPos toWorldVec(int localX, int localY, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new BlockPos(localX, 0, -localY);
            case CLOCKWISE_90 -> new BlockPos(localY, 0, localX);
            case CLOCKWISE_180 -> new BlockPos(-localX, 0, localY);
            case COUNTERCLOCKWISE_90 -> new BlockPos(-localY, 0, -localX);
        };
    }

    /** Loaded-only PA lookup shared by assembly snapshots and invalid-assembly cleanup. */
    public static BlockEntity loadedPartEntity(Level level, BlockPos pos) {
        if (level == null || pos == null || !(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false) instanceof LevelChunk chunk)) return null;
        return chunk.getBlockEntity(pos);
    }

    /** Physical UI-presence projection: matching role/prototype only; assembly readiness is resolved separately. */
    public static List<Boolean> presence(Level level, BlockPos control, Rotation rotation,
                                         ParticleAcceleratorControlProfile profile) {
        var result = new java.util.ArrayList<Boolean>();
        for (var part : profile.parts().values()) {
            BlockPos pos = control.offset(toWorldVec(part.offsetX(), part.offsetY(), rotation));
            var found = loadedPartEntity(level, pos);
            result.add(found instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity pa
                    && pa.role().equals(part.role()) && pa.prototypeId().toString().equals(part.completedPrototype()));
        }
        return List.copyOf(result);
    }
}

