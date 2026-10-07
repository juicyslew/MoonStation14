package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisposalTubeRotationActionTest {
    @Test
    void rotationCommandsUseCardinalNinetyDegreeTurnsAndNorthReset() {
        assertEquals(Direction.EAST, DisposalBuildMenuService.rotationTarget(Direction.NORTH, "@rotate_cw").orElseThrow());
        assertEquals(Direction.WEST, DisposalBuildMenuService.rotationTarget(Direction.NORTH, "@rotate_ccw").orElseThrow());
        assertEquals(Direction.NORTH, DisposalBuildMenuService.rotationTarget(Direction.WEST, "@rotate_reset").orElseThrow());
        assertEquals(Direction.SOUTH, DisposalBuildMenuService.rotationTarget(Direction.EAST, "@rotate_cw").orElseThrow());
        assertTrue(DisposalBuildMenuService.rotationTarget(Direction.UP, "@rotate_cw").isEmpty());
        assertTrue(DisposalBuildMenuService.rotationTarget(Direction.NORTH, "unknown").isEmpty());
    }

    @Test
    void anchoredTubeMustBeUnanchoredBeforeRotation() {
        UUID id = UUID.randomUUID();
        var tube = new DisposalTubeRecord(id, new BlockPos(0, 64, 0), "disposal_pipe", Direction.NORTH,
                false, "", "", 1);
        assertTrue(DisposalBuildMenuService.mayRotate(tube));
        assertFalse(DisposalBuildMenuService.mayRotate(new DisposalTubeRecord(id, tube.pos(), tube.prototypeId(),
                tube.orientation(), true, "", "", 1)));
        assertFalse(DisposalBuildMenuService.mayRotate(null));
    }

    @Test void rotationRetainsFullFrameUpIncludingVerticalFrames() {
        var frame = new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(Direction.NORTH, Direction.EAST);
        var rotated = DisposalBuildMenuService.rotationFrameTarget(frame, "@rotate_cw").orElseThrow();
        assertEquals(frame.up(), rotated.up());
        assertNotEquals(frame.forward(), rotated.forward());
        assertEquals(Direction.NORTH, DisposalBuildMenuService.rotationFrameTarget(
                new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(Direction.NORTH, Direction.UP),
                "@rotate_reset").orElseThrow().forward());
    }

    @Test void confirmedCandidateCommandIsBoundedAndDoesNotCarryAnArbitraryFrame(){
        assertEquals(0,DisposalBuildMenuService.rotationCandidateIndex("@rotate_confirm:0").orElseThrow());
        assertEquals(23,DisposalBuildMenuService.rotationCandidateIndex("@rotate_confirm:23").orElseThrow());
        for(String invalid:new String[]{"@rotate_cw","@rotate_ccw","@rotate_reset","@rotate_confirm:-1","@rotate_confirm:24","@rotate_confirm:999","@rotate_confirm:1x"})
            assertTrue(DisposalBuildMenuService.rotationCandidateIndex(invalid).isEmpty(),invalid);
    }

    @Test void candidateIndexSelectsFullSemanticOrientationFrame(){
        var tube=new com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Tube(
                com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Kind.BEND,
                com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Topology.BEND,0,.1,0,"",java.util.Set.of());
        var preferred=new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(Direction.NORTH,Direction.UP);
        var candidates=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientationCandidates.candidates(tube,preferred,java.util.Set.of());
        assertTrue(candidates.size()<=24);
        assertEquals(DisposalBuildMenuService.rotationCandidateIndex("@rotate_confirm:0").orElseThrow(),0);
        var chosen=candidates.get(0);
        assertNotNull(chosen);
        assertNotEquals(chosen.forward().getAxis(),chosen.up().getAxis());
    }
}
