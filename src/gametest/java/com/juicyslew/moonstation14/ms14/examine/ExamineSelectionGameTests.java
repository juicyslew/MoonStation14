package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.doors.AirlockGeometry;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the production world-query implementation against loaded server blocks. */
@PrefixGameTestTemplate(false)
@GameTestTags("examine_selection")
public final class ExamineSelectionGameTests {
    private ExamineSelectionGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=20)
    public static void airlockHalvesAndFacingsCanonicalizeToLowerRoot(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos root=helper.absolutePos(new BlockPos(2,1,4));
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            var lower=ModBlocks.AIRLOCK.get().defaultBlockState().setValue(AirlockBlock.FACING,facing)
                    .setValue(AirlockBlock.HALF,DoubleBlockHalf.LOWER);
            level.setBlock(root,lower,3);
            level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
            for(int half=0;half<2;half++) {
                final int halfIndex=half;
                BlockPos cell=half==0?root:root.above();
                Vec3 origin=cell.getCenter().subtract(Vec3.atLowerCornerOf(facing.getNormal()).scale(2));
                var result=ExamineWorldSelector.select(level,origin,Vec3.atLowerCornerOf(facing.getNormal()),8,java.util.Set.of());
                var found=result.stream().filter(c->c.target().kind()==ExamineTarget.Kind.BLOCK
                        && c.target().blockPos().equals(root)).findFirst().orElseThrow(
                        ()->new GameTestAssertException("airlock " + facing + " half " + halfIndex + " selects canonical root"));
                require(found.distance()<8,"airlock hit lies in bounded ray");
            }
            level.setBlock(root,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(root.above(),Blocks.AIR.defaultBlockState(),3);
        }
        helper.succeed();
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", timeoutTicks=400)
    public static void closedLeavesAndOpenApertureUseRenderedGeometry(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos root=helper.absolutePos(new BlockPos(3,1,3));
        helper.runAtTickTime(0,()->testAirlockFacing(helper,root,0));
    }

    private static void testAirlockFacing(GameTestHelper helper,BlockPos root,int index) {
        Direction[] facings={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        if(index>=facings.length) { helper.succeed(); return; }
        Direction facing=facings[index];
        ServerLevel level=helper.getLevel();
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState().setValue(AirlockBlock.FACING,facing)
                .setValue(AirlockBlock.HALF,DoubleBlockHalf.LOWER);
        level.setBlock(root,lower,3); level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        Vec3 direction=Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 origin=Vec3.atCenterOf(root).subtract(direction.scale(2));
        var closed=ExamineWorldSelector.select(level,origin,direction,6,java.util.Set.of());
        var hit=closed.stream().filter(c->c.target().kind()==ExamineTarget.Kind.BLOCK&&c.target().blockPos().equals(root))
                .findFirst().orElseThrow(()->new GameTestAssertException("closed rendered leaf/frame selectable: "+facing));
        require(hit.box().inflate(.02).contains(hit.hitPoint()),"highlight extent encloses selected rendered surface");
        var door=level.getBlockEntity(root) instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity be?be:null;
        require(door!=null&&door.pry(),"fixture opens through real airlock controller transition");
        BlockPos wall=root.relative(facing,2); level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        helper.runAtTickTime(20,()->awaitOpenThenProbe(helper,door,root,wall,origin,direction,facing,index,0));
    }

    private static void awaitOpenThenProbe(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity door,BlockPos root,BlockPos wall,
            Vec3 origin,Vec3 direction,Direction facing,int index,int waited) {
        var view=door.clientView();
        if(view.phase()!=com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN) {
            if(waited>=80) throw new GameTestAssertException("controller and renderer reach OPEN phase before aperture ray: facing="
                    +facing+", phase="+view.phase()+", gameTime="+helper.getLevel().getGameTime()
                    +", elapsedTestTicks="+waited);
            // runAfterDelay is relative to this fixture's current test tick.
            // runAtTickTime takes a test-relative tick, not the world's absolute time.
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->awaitOpenThenProbe(helper,door,root,wall,origin,direction,facing,index,waited+1));
            return;
        }
        ServerLevel level=helper.getLevel();
        var through=ExamineWorldSelector.select(level,origin,direction,6,java.util.Set.of());
        require(through.stream().noneMatch(c->c.target().kind()==ExamineTarget.Kind.BLOCK&&c.target().blockPos().equals(root)),
                "open aperture has no invisible full-plane airlock candidate: "+facing);
        require(through.stream().anyMatch(c->c.target().kind()==ExamineTarget.Kind.BLOCK&&c.target().blockPos().equals(wall)),
                "ray through open aperture selects wall behind: "+facing);
        level.setBlock(root,Blocks.AIR.defaultBlockState(),3); level.setBlock(root.above(),Blocks.AIR.defaultBlockState(),3);
        level.setBlock(wall,Blocks.AIR.defaultBlockState(),3);
        testAirlockFacing(helper,root,index+1);
    }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=20)
    public static void worldQueryReturnsGlassAndOpaqueWallCandidates(GameTestHelper helper) {
        ServerLevel level=helper.getLevel(); BlockPos pane=helper.absolutePos(new BlockPos(2,2,3));
        BlockPos wall=pane.south();
        level.setBlock(pane,Blocks.GLASS.defaultBlockState(),3);
        level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        Vec3 origin=Vec3.atCenterOf(pane).add(0,0,-3);
        var result=ExamineWorldSelector.select(level,origin,new Vec3(0,0,1),8,java.util.Set.of());
        boolean glass=result.stream().anyMatch(c->c.target().kind()==ExamineTarget.Kind.BLOCK&&c.target().blockPos().equals(pane));
        boolean stone=result.stream().anyMatch(c->c.target().kind()==ExamineTarget.Kind.BLOCK&&c.target().blockPos().equals(wall));
        require(glass&&stone,"transparent glass and visible opaque wall are both selectable through shared query");
        helper.succeed();
    }
    private static void require(boolean value,String message){if(!value)throw new GameTestAssertException(message);}
}
