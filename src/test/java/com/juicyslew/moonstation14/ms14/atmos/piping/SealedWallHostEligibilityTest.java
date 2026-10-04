package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SealedWallHostEligibilityTest {
    private static final BlockPos POS = new BlockPos(2, 64, 3);

    @Test
    void explicitlyOptedSteelOnlyAndRejectsNonHosts() {
        var policy = SealedWallHostEligibility.defaults();
        assertTrue(policy.eligible(reader(ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), true, null), POS));
        assertFalse(policy.eligible(reader(Blocks.STONE.defaultBlockState(), true, null), POS));
        assertFalse(policy.eligible(reader(ModBlocks.STEEL_WALL_GIRDER_BLOCK.get().defaultBlockState(), true, null), POS));
        assertFalse(policy.eligible(reader(ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), true, null), POS));
    }

    @Test
    void rejectsOpenableStatesEvenIfTheyWouldOtherwisePassPolicy() {
        var policy = SealedWallHostEligibility.of(Map.of(Blocks.OAK_DOOR, state -> true,
                Blocks.OAK_TRAPDOOR, state -> true, Blocks.OAK_FENCE_GATE, state -> true));
        assertFalse(policy.eligible(reader(Blocks.OAK_DOOR.defaultBlockState(), true, null), POS));
        assertFalse(policy.eligible(reader(Blocks.OAK_TRAPDOOR.defaultBlockState(), true, null), POS));
        assertFalse(policy.eligible(reader(Blocks.OAK_FENCE_GATE.defaultBlockState(), true, null), POS));
    }

    @Test
    void unloadedAndOutOfHeightPositionsAreDeniedBeforeStateRead() {
        AtomicInteger reads = new AtomicInteger();
        assertFalse(SealedWallHostEligibility.defaults().eligible(reader(
                ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), false, reads), POS));
        assertEquals(0, reads.get());
        assertFalse(SealedWallHostEligibility.defaults().eligible(reader(
                ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), true, reads), new BlockPos(2, 320, 3)));
        assertEquals(0, reads.get());
    }

    @Test
    void registeredStatePredicateIsAppliedAndRegistryIsCopied() {
        BlockState allowed = Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState denied = allowed.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X);
        var source = new java.util.HashMap<Block, java.util.function.Predicate<BlockState>>();
        source.put(Blocks.OAK_LOG, state -> state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) == Direction.Axis.Y);
        var policy = SealedWallHostEligibility.of(source);
        source.clear();
        assertTrue(policy.eligible(reader(allowed, true, null), POS));
        assertFalse(policy.eligible(reader(denied, true, null), POS));
    }

    private static LevelReader reader(BlockState state, boolean loaded, AtomicInteger reads) {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMinBuildHeight" -> -64;
                    case "getMaxBuildHeight" -> 320;
                    case "hasChunkAt" -> loaded;
                    case "getBlockState" -> { if (reads != null) reads.incrementAndGet(); yield state; }
                    case "toString" -> "test-level-reader";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
