package com.juicyslew.moonstation14.gametest.power.cable;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.power.cable.CableScanCandidates;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.infrastructure.scanner.ScanGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Set;
import java.util.stream.Collectors;

@GameTestTags({"power"})
@PrefixGameTestTemplate(false)
public final class CableScanCandidatesGameTests {
    private CableScanCandidatesGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void readsValidatedFacesAtTheirSurfacePositionsWithoutMutation(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(3, 1, 3));
        Vec3 body = new Vec3(host.getX() + .5, host.getY() + .5, host.getZ() + 1.5);
        int minChunkX = (int) Math.floor(body.x - 2) >> 4;
        int maxChunkX = (int) Math.floor(body.x + 2) >> 4;
        int minChunkZ = (int) Math.floor(body.z - 2) >> 4;
        int maxChunkZ = (int) Math.floor(body.z + 2) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, host, Direction.UP, CableTier.HV), "floor-facing cable");
        require(CableStorage.place(level, host, Direction.NORTH, CableTier.MV), "wall-facing cable on same host");
        BlockPos stale = host.above(2);
        level.setBlock(stale, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, stale, Direction.UP, CableTier.APC), "stale fixture cable");
        level.setBlock(stale, Blocks.DIRT.defaultBlockState(), 3);
        var chunk = level.getChunkAt(host);
        var data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        var before = data.snapshot();
        chunk.setUnsaved(false);
        var actual = CableScanCandidates.readLoaded(level, body, 2, ScanGeometry.MAX_CANDIDATES);
        Set<String> keys = actual.stream().map(c -> c.position().toShortString() + ":" + c.face().name() + ":" + c.tier())
                .collect(Collectors.toSet());
        Set<String> expected = Set.of(host.toShortString() + ":" + Direction.UP.name() + ":HV",
                host.toShortString() + ":" + Direction.NORTH.name() + ":MV");
        StringBuilder chunkStatus = new StringBuilder();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                boolean full = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) instanceof LevelChunk;
                if (chunkStatus.length() > 0) chunkStatus.append(", ");
                chunkStatus.append(new ChunkPos(chunkX, chunkZ)).append(" FULL=").append(full);
            }
        }
        require(keys.equals(expected), "returns distinct valid faces and excludes stale host; actual keys=" + keys
                + ", expected=" + expected + ", required chunks=" + chunkStatus + ", recorded data snapshot size=" + before.size());
        require(data.snapshot().equals(before), "query does not prune stale records");
        require(!chunk.isUnsaved(), "query does not dirty chunk");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
