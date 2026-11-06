package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Minimal authored plot proof: ordinary parent-level writes must initialize live MoonStation owners. */
@PrefixGameTestTemplate(false)
public final class SablePlotBlockEntityGameTests {
    private SablePlotBlockEntityGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "sable_plot", timeoutTicks = 100)
    @GameTestTags({"sable", "sable-precision"})
    public static void parentLevelStorageWritesCreateAndRetainRealAirlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos worldOrigin = helper.absolutePos(new BlockPos(2, 8, 2));
        var authored = SableFixturePlot.allocateAndAuthor(level,
                new Vector3d(worldOrigin.getX() + .5, worldOrigin.getY(), worldOrigin.getZ() + .5), plot -> {
            BlockPos origin = plot.storageOrigin();
            BlockPos foreignWorldBlock = worldOrigin.offset(2, 1, 2);
            int plotChunksBeforeForeignWrite = plot.subLevel().getPlot().getLoadedChunks().size();
            try {
                plot.setStorageBlock(foreignWorldBlock, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState());
                throw new GameTestAssertException("world-space coordinates must not be admitted as plot storage");
            } catch (IllegalArgumentException expected) {
                // Refusal must happen before Sable allocation or a parent-Level mutation.
            }
            require(level.getBlockState(foreignWorldBlock).isAir()
                            && plot.subLevel().getPlot().getLoadedChunks().size() == plotChunksBeforeForeignWrite,
                    "foreign/world coordinates are rejected without world mutation or plot chunk allocation");
            BlockPos floor = origin;
            BlockPos extraFloor = origin.east();
            BlockPos door = origin.above();
            BlockPos upper = door.above();
            require(plot.setStorageBlock(floor, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState()), "plot floor write accepted");
            require(plot.setStorageBlock(extraFloor, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState()), "connected neighboring floor write accepted");
            require(plot.setStorageBlock(door, ModBlocks.AIRLOCK.get().defaultBlockState()), "lower airlock write accepted");
            require(plot.setStorageBlock(upper, ModBlocks.AIRLOCK.get().defaultBlockState()
                    .setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER)), "upper airlock write accepted");

            var chunk = level.getChunkSource().getChunk(floor.getX() >> 4, floor.getZ() >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
            require(chunk != null, "authored plot chunk is loaded through parent ServerLevel");
            AirlockBlockEntity airlock = (AirlockBlockEntity) level.getBlockEntity(door);
            require(airlock != null && airlock.prototype() != null, "real door BE and resolved door composition initialize normally");
            var identity = airlock.identity();
            require(airlock.getBlockPos().equals(door), "BE is stored at the authored global plot-storage coordinate");
            // This is still synchronous construction: test the ordinary neighboring edit before
            // the factory seeds the one-time native placement snapshot.
            require(level.setBlock(extraFloor, Blocks.GLASS.defaultBlockState(), 3), "ordinary parent-level plot edit succeeds");
            return new PrimitiveAuthored(floor, extraFloor, door, airlock, identity);
        });
        SableFixturePlot plot = authored.plot();
        PrimitiveAuthored fixture = authored.result();
        try {
            BlockPos floor = fixture.floor();
            BlockPos extraFloor = fixture.extraFloor();
            BlockPos door = fixture.door();
            var projected = plot.worldOrigin();
            var requested = plot.requestedWorldOrigin();
            require(projected.distance(requested) < 1.0e-8,
                    "Sable's native Pose3d projection maps global plot origin to the requested world origin: " + projected);
            require(dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
                            .getSubLevel(plot.engineId()) == plot.subLevel() && !plot.subLevel().isRemoved(),
                    "allocated engine UUID resolves to this live sublevel");
            AirlockBlockEntity airlock = fixture.airlock();
            var identity = fixture.identity();
            require(airlock.getBlockPos().equals(door), "BE is stored at the authored global plot-storage coordinate");

            BoundedGameTestWait.safeDelay(helper, 8, () -> {
                try {
                    require(level.getBlockEntity(door) == airlock && airlock.identity().equals(identity),
                            "real plot BE instance and stable identity survive native ticks and neighboring edits");
                    requireWorldNear(plot.worldOrigin(), plot.requestedWorldOrigin(), 2.0,
                            "native ticks preserve the requested world-space plot origin");
                    require(level.getBlockState(extraFloor).is(Blocks.GLASS), "plot edit remains readable through parent ServerLevel");
                    helper.succeed();
                } finally {
                    plot.close();
                }
            });
        } catch (RuntimeException | Error failure) {
            plot.close();
            throw failure;
        }
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "sable_plot", timeoutTicks = 180)
    @GameTestTags({"sable", "sable-precision"})
    public static void nativeVelocityMovesPlotPoseWithoutMovingStorageBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos worldOrigin = helper.absolutePos(new BlockPos(2, 8, 2));
        var authored = SableFixturePlot.allocateAndAuthor(level,
                new Vector3d(worldOrigin.getX() + .5, worldOrigin.getY(), worldOrigin.getZ() + .5), plot -> {
                    BlockPos origin = plot.storageOrigin();
                    BlockPos door = origin.above();
                    plot.setStorageBlock(origin, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState());
                    plot.setStorageBlock(origin.east(), ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState());
                    plot.setStorageBlock(door, ModBlocks.AIRLOCK.get().defaultBlockState());
                    plot.setStorageBlock(door.above(), ModBlocks.AIRLOCK.get().defaultBlockState()
                            .setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER));
                    AirlockBlockEntity airlock = (AirlockBlockEntity) level.getBlockEntity(door);
                    require(airlock != null && airlock.prototype() != null,
                            "real airlock BE initializes before placement finalization");
                    return new PrimitiveAuthored(origin, origin.east(), door, airlock, airlock.identity());
                });
        SableFixturePlot plot = authored.plot();
        PrimitiveAuthored fixture = authored.result();
        try {
            BlockPos origin = fixture.floor();
            BlockPos door = fixture.door();
            AirlockBlockEntity airlock = fixture.airlock();
            var identity = fixture.identity();
            var engineId = plot.engineId();
            var body = plot.subLevel();
            var handle = RigidBodyHandle.of(body);
            BoundedGameTestWait.await(helper, 80,
                    () -> "native Sable rigid body did not register for " + engineId,
                    handle::isValid, () -> {
                        try {
                            var before = body.logicalPose().position().get(new Vector3d());
                            handle.addLinearAndAngularVelocity(new Vector3d(0, -2.0, 0), new Vector3d());
                            var admittedVelocity = handle.getLinearVelocity(new Vector3d());
                            require(admittedVelocity.y < -.1, "native velocity API admits requested downward motion: " + admittedVelocity);
                            BoundedGameTestWait.safeDelay(helper, 3, () -> {
                                try {
                                    var after = body.logicalPose().position();
                                    var velocity = handle.getLinearVelocity(new Vector3d());
                                    require(handle.isValid() && after.y < before.y - 1.0e-5,
                                            "native physics advances Sable pose after admitted motion; before="
                                                    + before + ", after=" + after + ", delta=" + new Vector3d(after).sub(before)
                                                    + ", velocity=" + velocity);
                                    var projectedOrigin = plot.worldOrigin();
                                    requireWorldNear(projectedOrigin, plot.requestedWorldOrigin(), 2.0,
                                            "native physics preserves the engine-projected world plot origin within short-flight envelope");
                                    require(Math.abs(projectedOrigin.x - worldOrigin.getX() - .5) < 2.0,
                                            "native downward nudge does not introduce an implausible horizontal frame jump: "
                                                    + projectedOrigin + " dx=" + (projectedOrigin.x - worldOrigin.getX() - .5));
                                    require(body.getUniqueId().equals(engineId)
                                                    && dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
                                                    .getSubLevel(engineId) == body,
                                            "engine craft UUID remains attached to the same live sublevel");
                                    require(level.getBlockState(origin).is(ModBlocks.STEEL_WALL_BLOCK.get())
                                                    && level.getBlockEntity(door) == airlock
                                                    && airlock.identity().equals(identity),
                                            "native pose impulse retains exact plot block and airlock BE coordinates/identity");
                                    helper.succeed();
                                } finally {
                                    plot.close();
                                }
                            });
                        } catch (RuntimeException | Error failure) {
                            plot.close();
                            throw failure;
                        }
                    });
        } catch (RuntimeException | Error failure) {
            plot.close();
            throw failure;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static void requireWorldNear(Vector3d actual, Vector3d expected, double maxDistance, String message) {
        var delta = new Vector3d(actual).sub(expected);
        double distance = actual.distance(expected);
        require(Double.isFinite(actual.x) && Double.isFinite(actual.y) && Double.isFinite(actual.z)
                        && distance <= maxDistance,
                message + ": projected=" + actual + ", expected=" + expected + ", delta=" + delta + ", distance=" + distance);
    }

    private record PrimitiveAuthored(BlockPos floor, BlockPos extraFloor, BlockPos door,
                                     AirlockBlockEntity airlock, java.util.UUID identity) { }
}
