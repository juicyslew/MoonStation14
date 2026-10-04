package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Explicit opt-in policy for blocks that may serve as sealed-wall hosts. Eligibility is only a
 * material/state check: it is not an incarnation marker or writable block capability.
 */
public final class SealedWallHostEligibility {
    private static final SealedWallHostEligibility DEFAULT = new SealedWallHostEligibility(Map.of(
            ModBlocks.STEEL_WALL_BLOCK.get(), state -> true));

    private final Map<Block, Predicate<BlockState>> providers;

    private SealedWallHostEligibility(Map<Block, Predicate<BlockState>> providers) {
        this.providers = Map.copyOf(providers);
    }

    /** Creates an immutable policy from explicit block-to-state-predicate registrations. */
    public static SealedWallHostEligibility of(Map<Block, Predicate<BlockState>> providers) {
        return new SealedWallHostEligibility(providers);
    }

    public static SealedWallHostEligibility defaults() {
        return DEFAULT;
    }

    /** Fails closed for unloaded positions and outside-build-height positions without reading state. */
    public boolean eligible(LevelReader level, BlockPos pos) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(pos);
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        Predicate<BlockState> provider = providers.get(state.getBlock());
        if (provider == null || state.isAir() || !provider.test(state)) return false;
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock) return false;
        return state.isCollisionShapeFullBlock(level, pos) && !AtmosphereTopology.isPassable(level, pos);
    }
}
