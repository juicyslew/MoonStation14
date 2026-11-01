package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native host fixtures for the standard two-cell firelock; intentionally not run in the author JVM lane. */
@GameTestTags({"firelock"})
@PrefixGameTestTemplate(false)
public final class FirelockHostGameTests {
    private FirelockHostGameTests() { }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="firelock_host",timeoutTicks=80)
    @GameTestTags({"firelock","physics"})
    public static void authoritativeProjectionQueriesMatchControllerState(GameTestHelper helper) {
        var level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));var lower=ModBlocks.FIRELOCK.get().defaultBlockState();
        level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(FirelockBlock.HALF,DoubleBlockHalf.UPPER),3);
        if(!(level.getBlockEntity(root) instanceof FirelockBlockEntity door)){helper.fail("registered lower firelock BE missing");return;}
        door.initializeFromResolvedPrototype();door.refreshProjection();
        if(!door.projectionPassable()||door.projectionCollidable()){helper.fail("open controller projection disagrees");return;}
        door.emergencyPressureStop();
        if(door.projectionPassable()||!door.projectionCollidable()){helper.fail("closing controller projection disagrees");return;}
        helper.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="firelock_host",timeoutTicks=80)
    @GameTestTags({"firelock","physics"})
    public static void presentationSnapshotDrivesBothCellCollisionWithoutControllerState(GameTestHelper helper) {
        var level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));var lower=ModBlocks.FIRELOCK.get().defaultBlockState();
        level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(FirelockBlock.HALF,DoubleBlockHalf.UPPER),3);
        if(!(level.getBlockEntity(root) instanceof FirelockBlockEntity door)){helper.fail("registered lower firelock BE missing");return;}
        for(var phase:new AirlockState.DoorPhase[]{AirlockState.DoorPhase.OPEN,AirlockState.DoorPhase.CLOSED}) {
            var state=new FirelockState(phase,false,false,false,false,0,0,0);
            var view=new FirelockController.Presentation(state,phase==AirlockState.DoorPhase.CLOSED,0,0,0,0,net.minecraft.core.Direction.NORTH);
            var tag=new net.minecraft.nbt.CompoundTag();FirelockPresentationNbt.write(tag,"FirelockView",view);
            door.handleUpdateTag(tag,null);
            boolean passable=phase==AirlockState.DoorPhase.OPEN;
            for(BlockPos cell:new BlockPos[]{root,root.above()}) {
                boolean empty=level.getBlockState(cell).getCollisionShape(level,cell,net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty();
                if(empty!=passable){helper.fail("snapshot phase "+phase+" produced wrong collision at "+cell);return;}
            }
            if(door.projectionPassable()!=passable||door.projectionCollidable()==passable){helper.fail("snapshot phase disagrees with live projection query");return;}
        }
        door.handleUpdateTag(new net.minecraft.nbt.CompoundTag(),null);
        if(door.projectionPassable()||!door.projectionCollidable())helper.fail("unknown client snapshot must remain fail-closed");else helper.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="firelock_host",timeoutTicks=80)
    @GameTestTags({"firelock","atmosphere"})
    public static void emergencyClosureProjectsImmediateSealToBothRegisteredCells(GameTestHelper helper){
        var level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));
        var lower=ModBlocks.FIRELOCK.get().defaultBlockState();level.setBlock(root,lower,3);
        level.setBlock(root.above(),lower.setValue(FirelockBlock.HALF,DoubleBlockHalf.UPPER),3);
        if(!(level.getBlockEntity(root) instanceof FirelockBlockEntity door)){helper.fail("registered lower firelock BE missing");return;}
        door.initializeFromResolvedPrototype();door.refreshProjection();
        if(!door.emergencyPressureStop()){helper.fail("initially-open standard firelock did not admit emergency stop");return;}
        if(level.getBlockState(root).getValue(FirelockBlock.OPEN)||level.getBlockState(root.above()).getValue(FirelockBlock.OPEN)){
            helper.fail("emergency stop did not project closed state to both cells");return;
        }
        if(AtmosphereTopology.isPassable(level,root)||AtmosphereTopology.isPassable(level,root.above())){
            helper.fail("both collidable firelock cells must seal atmosphere");return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="firelock_host",timeoutTicks=80)
    @GameTestTags({"firelock","physics"})
    public static void emergencyCloseAdmissionRejectsARealDoorwayOccupant(GameTestHelper helper){
        var level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));var lower=ModBlocks.FIRELOCK.get().defaultBlockState();
        level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(FirelockBlock.HALF,DoubleBlockHalf.UPPER),3);
        if(!(level.getBlockEntity(root) instanceof FirelockBlockEntity door)){helper.fail("registered lower firelock BE missing");return;}
        door.initializeFromResolvedPrototype();door.refreshProjection();var actor=net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if(actor==null){helper.fail("villager fixture actor unavailable");return;}actor.setNoAi(true);actor.setPos(root.getX()+.5,root.getY(),root.getZ()+.5);level.addFreshEntity(actor);
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(actor,level);
        if(door.emergencyPressureStop()){helper.fail("emergency close admitted an obstructed doorway");return;}
        if(door.toggle(false)){helper.fail("ordinary close admitted an obstructed doorway");return;}
        if(door.state().phase()!=AirlockState.DoorPhase.OPEN){helper.fail("obstruction changed the firelock phase");return;}
        actor.discard();helper.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="firelock_host",timeoutTicks=80)
    @GameTestTags({"firelock","physics"})
    public static void occupantEnteringDuringOrdinaryPhaseOneAbortsAndLeavesBothCellsPassable(GameTestHelper helper){
        var level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));var lower=ModBlocks.FIRELOCK.get().defaultBlockState();
        level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(FirelockBlock.HALF,DoubleBlockHalf.UPPER),3);
        if(!(level.getBlockEntity(root) instanceof FirelockBlockEntity door)){helper.fail("registered lower firelock BE missing");return;}
        door.initializeFromResolvedPrototype();door.refreshProjection();if(!door.toggle(false)){helper.fail("clear ordinary close was not admitted");return;}
        helper.runAfterDelay(1,()->{
            var actor=net.minecraft.world.entity.EntityType.VILLAGER.create(level);if(actor==null){helper.fail("villager fixture actor unavailable");return;}
            actor.setNoAi(true);actor.setPos(root.getX()+.5,root.getY(),root.getZ()+.5);level.addFreshEntity(actor);
            com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(actor,level);
            helper.runAfterDelay(2,()->{
                if(door.state().phase()!=AirlockState.DoorPhase.OPEN){helper.fail("partial-close admission did not abort back to Open");return;}
                if(!level.getBlockState(root).getValue(FirelockBlock.OPEN)||!level.getBlockState(root.above()).getValue(FirelockBlock.OPEN)
                        ||!AtmosphereTopology.isPassable(level,root)||!AtmosphereTopology.isPassable(level,root.above())){helper.fail("aborted close failed to preserve passable projection on both cells");return;}
                actor.discard();helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="firelock_host",timeoutTicks=80)
    @GameTestTags({"firelock","atmosphere"})
    public static void clearOrdinaryCloseAdvancesToClosedSeal(GameTestHelper helper){
        var level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));var lower=ModBlocks.FIRELOCK.get().defaultBlockState();
        level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(FirelockBlock.HALF,DoubleBlockHalf.UPPER),3);
        if(!(level.getBlockEntity(root) instanceof FirelockBlockEntity door)){helper.fail("registered lower firelock BE missing");return;}
        door.initializeFromResolvedPrototype();door.refreshProjection();if(!door.toggle(false)){helper.fail("clear ordinary close was not admitted");return;}
        helper.runAfterDelay(4,()->{
            if(door.state().phase()!=AirlockState.DoorPhase.CLOSING_PASSABLE||level.getBlockState(root).getValue(FirelockBlock.OPEN)
                    ||level.getBlockState(root.above()).getValue(FirelockBlock.OPEN)){helper.fail("clear admitted close did not project both cells as collidable");return;}
            helper.runAfterDelay(12,()->{if(door.state().phase()!=AirlockState.DoorPhase.CLOSED){helper.fail("clear close did not reach Closed");return;}helper.succeed();});
        });
    }
}
