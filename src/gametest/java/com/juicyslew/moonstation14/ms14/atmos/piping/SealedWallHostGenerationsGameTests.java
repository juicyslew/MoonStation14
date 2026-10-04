package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.sealedwall.SealedWallHostGenerations;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestTags({"ventilation", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class SealedWallHostGenerationsGameTests {
    private SealedWallHostGenerationsGameTests() { }

@GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void chunkWritesInvalidateOnlyRegisteredHostGenerations(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos unrelated = pos.east();
        var chunk = level.getChunkAt(pos);
        var attachment = new SealedWallHostGenerations();
        UUID first = UUID.randomUUID();
        attachment.register(pos.asLong(), first);
        chunk.setData(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get(), attachment);
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        attachment.register(pos.asLong(), first);

        require(level.getChunkSource().hasChunk(chunk.getPos().x, chunk.getPos().z), "target chunk loaded");
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        require(attachment.active(pos.asLong()).isEmpty(), "same-state Level.setBlock invalidated generation");

        UUID second = UUID.randomUUID();
        attachment.register(pos.asLong(), second);
        chunk.setBlockState(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), false);
        require(attachment.active(pos.asLong()).isEmpty(), "direct chunk write invalidated generation");
        attachment.register(pos.asLong(), second);
        chunk.setUnsaved(false);
        chunk.setBlockState(unrelated, Blocks.STONE.defaultBlockState(), false);
        require(attachment.active(pos.asLong()).orElseThrow().equals(second), "unrelated position did not invalidate host");
        require(!attachment.invalidateExisting(unrelated.asLong()), "unrelated position has no existing generation to invalidate");
        require(attachment.hasRecord(pos.asLong()) && !attachment.hasRecord(unrelated.asLong()), "unrelated edit created no record");
        require(chunk.isUnsaved(), "vanilla unrelated block edit may dirty chunk independently of host metadata");

        attachment.invalidate(pos.asLong());
        UUID freshEpoch = UUID.randomUUID();
        attachment.register(pos.asLong(), freshEpoch);
        require(!freshEpoch.equals(second) && attachment.active(pos.asLong()).orElseThrow().equals(freshEpoch), "replacement generation is distinct");
        require(level.getChunkSource().hasChunk(chunk.getPos().x, chunk.getPos().z), "chunk remains loaded without lookup loads");
        require(level.getBlockState(helper.absolutePos(new BlockPos(5, 1, 5))).isAir(), "host metadata does not change air passability");
        require(level.getBlockState(pos).is(ModBlocks.STEEL_WALL_BLOCK.get()), "host state remains placed");
        require(PipeGasInventorySavedData.existing(level).map(data -> data.snapshot()
                .get(new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY)).isEmpty()).orElse(true),
                "host registration creates no gas owner entry");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void sealedSegmentAdmissionIsClickedOnlyAndQuarantinesReplacement(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        var chunk = level.getChunkAt(pos);
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        UUID id = UUID.randomUUID();
        var admitted = LoadedSealedWallSegment.prepareOne(level, pos, net.minecraft.core.Direction.EAST,
                PipeTopology.Layer.PRIMARY, id).orElseThrow();
        require(admitted.id().equals(id), "fresh segment identity persisted");
        require(admitted.ports() == PipeChunkData.Record.portMask(java.util.List.of(net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST)), "only clicked-face opposite ports");
        require(LoadedSealedWallSegment.matchingLive(level, pos, PipeTopology.Layer.PRIMARY).isPresent(), "admission live");
        require(chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get()).size() == 1, "one clicked record only");
        require(PipeGasInventorySavedData.existing(level).map(data -> data.snapshot().get(new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY)).isEmpty()).orElse(true), "no gas state created");
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        require(LoadedSealedWallSegment.matchingLive(level, pos, PipeTopology.Layer.PRIMARY).isEmpty(), "same-type replacement invalidates generation");
        require(chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get()).size() == 1, "stale record retained for quarantine");
        require(LoadedSealedWallSegment.prepareOne(level, pos, net.minecraft.core.Direction.EAST,
                PipeTopology.Layer.PRIMARY, UUID.randomUUID()).isEmpty(), "stale host cannot be readmitted");
        require(chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get()).size() == 1, "stale readmission adds no record");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void failedAdmissionVerificationRollsBackWithoutGas(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        var chunk = level.getChunkAt(pos);
        level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        LoadedSealedWallSegment.setVerificationHookForTests(() -> { throw new IllegalStateException("injected verification failure"); });
        try {
            require(LoadedSealedWallSegment.prepareOne(level, pos, net.minecraft.core.Direction.EAST,
                    PipeTopology.Layer.PRIMARY, UUID.randomUUID()).isEmpty(), "verification failure denies admission");
        } finally {
            LoadedSealedWallSegment.setVerificationHookForTests(null);
        }
        var records = chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get());
        require(records == null || records.size() == 0, "failed admission leaves no record");
        var generations = chunk.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        require(generations == null || !generations.hasRecord(pos.asLong()), "failed admission leaves no generation");
        require(PipeGasInventorySavedData.existing(level).map(data -> data.snapshot()
                .get(new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY)).isEmpty()).orElse(true), "failed admission creates no gas state");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
