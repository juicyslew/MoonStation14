package com.juicyslew.moonstation14.ms14.disposal.world;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal"})
@PrefixGameTestTemplate(false)
public final class DisposalWorldGameTests {
    private DisposalWorldGameTests() { }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void installingRecordReplacesAirWithTubeHost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var data = DisposalTubeWorldData.forMutation(level);
        var record = record(pos, false, 0);
        require(data.installExposed(level, record), "resolved tube installed into loaded air cell");
        require(level.getBlockState(pos).is(ModBlocks.DISPOSAL_TUBE.get()), "installation creates real tube owner block");
        require(data.lookup(level, pos).kind() == DisposalTubeWorldData.LookupKind.PRESENT, "tube record resolves at host");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void sealedInstallLeavesWallAndHostReplacementInvalidates(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos pos=helper.absolutePos(new BlockPos(8,2,8));
        level.setBlock(pos,ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
        var wall=level.getBlockState(pos); var data=DisposalTubeWorldData.forMutation(level);
        require(data.installSealed(level,pos,com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation.identity(),"moonstation14:disposal_pipe"),"straight tube enrolls in eligible steel wall");
        var record=data.lookup(level,pos).record();
        require(record.hostGeneration()!=null && record.hostKind()==DisposalTubeRecord.HostKind.SEALED_WALL,"common host incarnation retained");
        require(data.lookup(level,pos).kind()==DisposalTubeWorldData.LookupKind.PRESENT,"sealed record passes live host identity validation");
        require(level.getBlockState(pos).equals(wall),"concealed installation does not change wall state");
        level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        require(data.lookup(level,pos).kind()==DisposalTubeWorldData.LookupKind.ABSENT,"wall replacement invalidates concealed tube record");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void sameStateHostWriteInvalidatesIncarnation(GameTestHelper helper) {
        ServerLevel level=helper.getLevel(); BlockPos pos=helper.absolutePos(new BlockPos(8,2,8));
        level.setBlock(pos,ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
        var data=DisposalTubeWorldData.forMutation(level);
        require(data.installSealed(level,pos,com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation.identity(),"disposal_pipe"),"sealed segment installs with unqualified local ID");
        var host=level.getBlockState(pos);
        level.setBlock(pos,host,3);
        require(data.lookup(level,pos).kind()==DisposalTubeWorldData.LookupKind.ABSENT,"same-state host write retires host incarnation");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void independentFloorDoesNotCoverTube(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var data = DisposalTubeWorldData.forMutation(level);
        var record = record(pos, true, 0);
        require(data.installExposed(level, record), "seed tube record");
        BlockPos floor = pos.below();
        level.setBlock(floor, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        require(data.lookup(level, pos).kind() == DisposalTubeWorldData.LookupKind.PRESENT, "independent floor leaves tube record available");
        require(data.rotate(level, record, Direction.EAST, p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get())), "tube remains editable over separate floor");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void replacingTubeInvalidatesRecordWithoutChangingNeighbors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        BlockPos neighbor = pos.east();
        level.setBlock(neighbor, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        var data = DisposalTubeWorldData.forMutation(level);
        var record = record(pos, true, 0);
        AtomicInteger disconnects = new AtomicInteger();
        data.addDisconnectListener(ignored -> disconnects.incrementAndGet());
        require(data.installExposed(level, record), "seed anchored tube");
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        require(data.lookup(level, pos).kind() == DisposalTubeWorldData.LookupKind.ABSENT, "replaced tube removes record");
        require(disconnects.get() == 1, "tube replacement disconnects exactly once");
        require(level.getBlockState(neighbor).is(ModBlocks.STATION_FLOOR.get()), "adjacent floor remains unchanged");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void staleRecordAndMissingSourceCannotMutate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var data = DisposalTubeWorldData.forMutation(level);
        var initial = record(pos, false, 0);
        require(data.installExposed(level, initial), "seed record");
        java.util.function.Predicate<BlockPos> editable = p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get());
        require(data.configure(level, initial, "source", "filter", editable), "advance exact record revision");
        var current = data.lookup(level, pos).record();
        require(!data.anchor(level, initial, true, editable), "stale revision rejected");
        require(!data.remove(level, record(pos.relative(Direction.NORTH), false, 0), editable), "wrong source position rejected");
        require(data.lookup(level, pos).record().equals(current), "rejected mutations preserve current record");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void unanchorAndRemovalDisconnectExactlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var data = DisposalTubeWorldData.forMutation(level);
        var record = record(pos, true, 0);
        AtomicInteger disconnects = new AtomicInteger();
        data.addDisconnectListener(ignored -> disconnects.incrementAndGet());
        require(data.installExposed(level, record), "seed anchored record");
        java.util.function.Predicate<BlockPos> editable = p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get());
        require(data.anchor(level, record, false, editable), "unanchor exact record");
        require(disconnects.get() == 1, "unanchor disconnects once");
        var unanchored = data.lookup(level, pos).record();
        require(data.remove(level, unanchored, editable), "remove exact unanchored record");
        require(disconnects.get() == 2, "removal disconnects once in addition to unanchor");
        require(!data.invalidate(level, pos) && disconnects.get() == 2, "subsequent invalidation has no duplicate disconnect");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void lookupOfUnloadedNeighborIsUnknownWithoutLoading(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(1_000_000, 2, 1_000_000);
        require(!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4), "remote neighbor begins unloaded");
        var data = DisposalTubeWorldData.forMutation(level);
        require(data.lookup(level, pos).kind() == DisposalTubeWorldData.LookupKind.UNKNOWN, "unloaded cell lookup is unknown");
        require(!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4), "lookup does not load remote chunk");
        helper.succeed();
    }

    private static DisposalTubeRecord record(BlockPos pos, boolean anchored, long revision) {
        return new DisposalTubeRecord(UUID.randomUUID(), pos, "moonstation14:disposal_pipe", Direction.NORTH, anchored, "", "", revision);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
