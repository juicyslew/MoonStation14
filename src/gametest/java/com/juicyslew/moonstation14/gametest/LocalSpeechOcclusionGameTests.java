package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.chat.server.LocalSpeechOcclusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LocalSpeechOcclusionGameTests {
    private LocalSpeechOcclusionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void openHallwayPasses(GameTestHelper helper) {
        require(audible(helper, eye(helper, 2, 1.6, 2), eye(helper, 6, 1.6, 2)),
                "open horizontal eye-to-eye path must pass");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void clearEyeLineAcrossLoadedChunkBoundaryPasses(GameTestHelper helper) {
        int originX = helper.absolutePos(BlockPos.ZERO).getX();
        int boundary = 16 - Math.floorMod(originX, 16);
        Vec3 source = eye(helper, boundary - 1, 1.6, 2);
        Vec3 listener = eye(helper, boundary, 1.6, 2);
        require((int) Math.floor(source.x) >> 4 != (int) Math.floor(listener.x) >> 4,
                "test eyes must straddle a chunk boundary");
        require(audible(helper, source, listener), "loaded clear line across chunk boundary must pass");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nearOneStoneWallPasses(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        require(audible(helper, eye(helper, 2, 1.6, 2), eye(helper, 4, 1.6, 2)),
                "one side wall between nearby eyes must pass");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void twoStoneWallsPassNearFacesButThreeDeny(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.STONE);
        require(audible(helper, eye(helper, 2, 1.6, 2).add(0.4, 0, 0),
                eye(helper, 5, 1.6, 2).add(-0.4, 0, 0)),
                "two full blocks pass with both eyes close to the wall faces");
        require(!audible(helper, eye(helper, 2, 1.6, 2), eye(helper, 5, 1.6, 2)),
                "two full blocks fail at three metres");
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.AIR);
        require(audible(helper, eye(helper, 2, 1.6, 2), eye(helper, 8, 1.6, 2)),
                "one full block passes at six metres");
        require(!audible(helper, eye(helper, 2, 1.6, 2), eye(helper, 9, 1.6, 2)),
                "one full block fails beyond 6.5 metres");
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.STONE);
        require(!audible(helper, eye(helper, 2, 1.6, 2).add(0.49, 0, 0),
                eye(helper, 6, 1.6, 2).add(-0.49, 0, 0)),
                "three blocks fail even with eyes nearly touching their faces");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void diagonalOneSheetPassesButTwoSheetsDenyAtDistance(GameTestHelper helper) {
        for (int z = 0; z <= 6; z++) helper.setBlock(new BlockPos(3, 1, z), Blocks.STONE);
        require(audible(helper, eye(helper, 2, 1.6, 2), eye(helper, 4, 1.6, 4)),
                "oblique ray intersects multiple cells of one continuous sheet");
        for (int z = 0; z <= 6; z++) helper.setBlock(new BlockPos(4, 1, z), Blocks.STONE);
        require(!audible(helper, eye(helper, 2, 1.6, 2), eye(helper, 5, 1.6, 4)),
                "two continuous sheets deny this oblique distance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void clearEyeLineUsesFifteenBlockEyeToEyeRange(GameTestHelper helper) {
        Vec3 source = eye(helper, 2, 1.6, 2);
        require(audible(helper, source, eye(helper, 17, 1.6, 2)),
                "clear eye-to-eye ray at exactly 15 blocks must pass");
        require(!audible(helper, source, eye(helper, 17, 1.6, 2).add(0.001, 0, 0)),
                "clear eye-to-eye ray beyond 15 blocks must fail");
        require(!audible(helper, source, eye(helper, 17, 3.4, 2)),
                "15 horizontal blocks plus eye-height difference exceed the range");
        require(audible(helper, source, eye(helper, 16, 3.4, 2)),
                "mixed-level body positions within 15 eye-to-eye blocks still pass");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stoneAndSlabFloorsDenyNearVertical(GameTestHelper helper) {
        Vec3 below = eye(helper, 2, 1.6, 2);
        Vec3 above = eye(helper, 2, 3.0, 2);
        require(audible(helper, below, above), "open short vertical shaft must pass");
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        require(!audible(helper, below, above), "full stone floor must deny near vertical speech");
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE_SLAB);
        require(!audible(helper, below, above), "bottom slab floor must deny near vertical speech");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void diagonalFloorTopExitDeniesButOpenShaftPasses(GameTestHelper helper) {
        Vec3 below = eye(helper, 2, 1.6, 2);
        Vec3 above = eye(helper, 2, 3.1, 2).add(0.8, 0, 0);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        require(!audible(helper, below, above),
                "diagonal ray entering the side and exiting the top of stone must deny");
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE_SLAB);
        require(!audible(helper, below, eye(helper, 3, 2.7, 2)),
                "diagonal side-to-top traversal of a real bottom slab must deny");
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
        require(audible(helper, below, above), "the same open diagonal shaft must pass");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void raisedRoomAcrossFullHeightStoneWallPassesBothDirections(GameTestHelper helper) {
        for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(3, 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(3, 2, z), Blocks.STONE);
            helper.setBlock(new BlockPos(4, 1, z), Blocks.STONE); // raised room floor
        }
        Vec3 lower = eye(helper, 2, 1.6, 2);
        Vec3 raised = eye(helper, 4, 2.6, 2);
        require(audible(helper, lower, raised), "full-height wall must pass rising diagonal through internal seam");
        require(audible(helper, raised, lower), "full-height wall must pass falling diagonal through internal seam");
        for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(3, 2, z), Blocks.AIR);
        require(!audible(helper, lower, raised), "one-height wall has an exposed roof, not a seam");
        require(!audible(helper, raised, lower), "exposed roof must deny in both directions");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void twoFloorsDenyAndOpenShaftPasses(GameTestHelper helper) {
        Vec3 below = eye(helper, 2, 1.6, 2);
        Vec3 above = eye(helper, 2, 5.6, 2);
        require(audible(helper, below, above), "open shaft across two floors must pass");
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
        require(!audible(helper, below, above), "two complete floors must deny vertical speech");
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(2, 4, 2), Blocks.AIR);
        require(audible(helper, below, above), "reopened shaft must pass immediately");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stackedStoneFloorsDenyAcrossInternalSeamBothDirections(GameTestHelper helper) {
        Vec3 below = eye(helper, 2, 1.6, 2);
        Vec3 above = eye(helper, 2, 4.6, 2);
        require(audible(helper, below, above), "open upward shaft must pass");
        require(audible(helper, above, below), "open downward shaft must pass");
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        require(!audible(helper, below, above),
                "internal stone seam cannot bypass the exposed bottom face upward");
        require(!audible(helper, above, below),
                "internal stone seam cannot bypass the exposed top face downward");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void closedDoorPassesLocallyButDeniesFarther(GameTestHelper helper) {
        Vec3 source = eye(helper, 2, 1.6, 2);
        setDoor(helper, false);
        require(audible(helper, source, eye(helper, 8, 1.6, 2)),
                "closed door panel is thin enough for a six metre ray");
        Vec3 listener = eye(helper, 14, 1.6, 2);
        require(!audible(helper, source, listener), "closed door panel attenuates a twelve metre ray");
        setDoor(helper, true);
        require(audible(helper, source, listener), "open door panel must leave the center ray clear");
        helper.succeed();
    }

    private static void setDoor(GameTestHelper helper, boolean open) {
        var door = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.OPEN, open);
        helper.setBlock(new BlockPos(3, 1, 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(3, 2, 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static Vec3 eye(GameTestHelper helper, int x, double y, int z) {
        BlockPos base = helper.absolutePos(new BlockPos(x, 0, z));
        return new Vec3(base.getX() + 0.5, base.getY() + y, base.getZ() + 0.5);
    }

    private static boolean audible(GameTestHelper helper, Vec3 source, Vec3 listener) {
        // Never force-load chunks for a speech check; the sampler fails closed.
        return LocalSpeechOcclusion.audible(helper.getLevel(), source, listener);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
