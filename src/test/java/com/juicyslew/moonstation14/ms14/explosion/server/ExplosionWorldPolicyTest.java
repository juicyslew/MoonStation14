package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExplosionWorldPolicyTest {
    @Test
    void fixtureFloorAndWallShareNominalTolerance() {
        assertEquals(2.0, ServerExplosionService.classify(ModBlocks.STATION_FLOOR.get().defaultBlockState()).tolerance());
        assertEquals(2.0, ServerExplosionService.classify(ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState()).tolerance());
        assertEquals(2.0, ServerExplosionService.classify(ModBlocks.STEEL_WALL_GIRDER_BLOCK.get().defaultBlockState()).tolerance());
    }

    @Test
    void ordinaryVanillaBlocksAreDestructibleButProtectedBlocksAreImmune() {
        assertEquals(2.0, ServerExplosionService.classify(Blocks.STONE.defaultBlockState()).tolerance());
        assertEquals(2.0, ServerExplosionService.classify(Blocks.DIRT.defaultBlockState()).tolerance());
        assertEquals(Double.POSITIVE_INFINITY, ServerExplosionService.classify(Blocks.BEDROCK.defaultBlockState()).tolerance());
        assertEquals(Double.POSITIVE_INFINITY, ServerExplosionService.classify(Blocks.BARRIER.defaultBlockState()).tolerance());
        assertEquals(Double.POSITIVE_INFINITY, ServerExplosionService.classify(Blocks.END_PORTAL.defaultBlockState()).tolerance());
        assertEquals(Double.POSITIVE_INFINITY, ServerExplosionService.classify(Blocks.STRUCTURE_VOID.defaultBlockState()).tolerance());
    }

    @Test
    void unknownChunkSamplerDoesNotFetchAChunk() {
        // The server sampler returns UNKNOWN when getChunkNow is null; UNKNOWN is impassable to the planner.
        assertEquals(BlastPlanner.Cell.UNKNOWN, BlastPlanner.Sample.unknown().cell());
        assertEquals(BlastPlanner.Sample.open(), ServerExplosionService.classify(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void allOrdinaryNonAirBlocksUseTheSamePolicy() {
        assertEquals(2.0, ServerExplosionService.classify(Blocks.OAK_FENCE.defaultBlockState()).tolerance());
        assertEquals(2.0, ServerExplosionService.classify(Blocks.TORCH.defaultBlockState()).tolerance());
        assertEquals(2.0, ServerExplosionService.classify(Blocks.POPPY.defaultBlockState()).tolerance());
        assertEquals(2.0, ServerExplosionService.classify(Blocks.RAIL.defaultBlockState()).tolerance());
        assertEquals(2.0, ServerExplosionService.classify(Blocks.CHEST.defaultBlockState()).tolerance());
        assertEquals(2.0, ServerExplosionService.classify(Blocks.FURNACE.defaultBlockState()).tolerance());
    }
}
