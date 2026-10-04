package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisposalTubeConfigurePolicyTest {
    @Test void validatorsRejectMalformedInputs() {
        assertFalse(DisposalTubeRouting.validTaggerInput("fragile,!"));
        assertFalse(DisposalTubeRouting.validTagExpression("parcel|admin"));
        assertTrue(DisposalTubeRouting.validTaggerInput("fragile 2"));
        assertTrue(DisposalTubeRouting.validTagExpression("parcel, fragile"));
    }

    @Test void configurationRequiresExactCurrentRoutingTube() {
        UUID id=UUID.randomUUID();
        var tube=new DisposalTubeRecord(id,new BlockPos(1,2,3),"moonstation14:disposal_router",
                Direction.NORTH,true,"","",7);
        assertTrue(DisposalBuildMenuService.mayConfigure(tube,id,7));
        assertFalse(DisposalBuildMenuService.mayConfigure(tube,id,6));
        assertFalse(DisposalBuildMenuService.mayConfigure(tube,UUID.randomUUID(),7));
        assertFalse(DisposalBuildMenuService.mayConfigure(new DisposalTubeRecord(id,tube.pos(),
                "moonstation14:disposal_pipe",Direction.NORTH,true,"","",7),id,7));
    }
}
