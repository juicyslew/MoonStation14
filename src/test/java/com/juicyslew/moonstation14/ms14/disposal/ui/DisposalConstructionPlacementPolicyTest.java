package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.item.Items;

class DisposalConstructionPlacementPolicyTest {
    @Test void onlyUnoccupiedEligibleStraightUsesSealedWallCell() {
        assertTrue(DisposalConstructionTargetPolicy.sealed(true,false,DisposalEntityData.Kind.STRAIGHT));
        assertFalse(DisposalConstructionTargetPolicy.sealed(true,false,DisposalEntityData.Kind.BEND));
        assertFalse(DisposalConstructionTargetPolicy.sealed(true,false,DisposalEntityData.Kind.TAGGER));
        assertFalse(DisposalConstructionTargetPolicy.sealed(true,true,DisposalEntityData.Kind.STRAIGHT));
        assertFalse(DisposalConstructionTargetPolicy.sealed(false,false,DisposalEntityData.Kind.STRAIGHT));
    }
    @Test void resolvedStepDrivesMaterialCountAndDuration(){
        var custom=new DisposalEntityData.ConstructionStep(4,2.25,"minecraft:gold_ingot");
        assertEquals(Items.GOLD_INGOT,DisposalConstructionPlacementPolicy.requiredMaterial(custom).orElseThrow());
        assertTrue(DisposalConstructionPlacementPolicy.enoughMaterial(custom,Items.GOLD_INGOT,4));
        assertFalse(DisposalConstructionPlacementPolicy.enoughMaterial(custom,Items.GOLD_INGOT,3));
        assertFalse(DisposalConstructionPlacementPolicy.enoughMaterial(custom,Items.IRON_INGOT,64));
        assertEquals(45,DisposalConstructionPlacementPolicy.requiredTicks(custom));
        assertFalse(DisposalBuildActionPolicy.mayComplete(44,45,true,true,true,true,true));
        assertTrue(DisposalBuildActionPolicy.mayComplete(45,45,true,true,true,true,true));
    }
    @Test void unknownConstructionMaterialCannotAdmitAnyHeldItem(){
        var invalid=new DisposalEntityData.ConstructionStep(1,1,"moonstation14:not_registered");
        assertTrue(DisposalConstructionPlacementPolicy.requiredMaterial(invalid).isEmpty());
        assertFalse(DisposalConstructionPlacementPolicy.enoughMaterial(invalid,Items.GOLD_INGOT,64));
    }
    @Test void zeroTimeRecipeIsValidAndCompletesWithoutTickDelay(){
        var instant=new DisposalEntityData.ConstructionStep(2,0,"moonstation14:steel");
        assertEquals(0,DisposalConstructionPlacementPolicy.requiredTicks(instant));
        assertTrue(DisposalBuildActionPolicy.mayComplete(0,0,true,true,true,true,true));
        assertEquals(20,DisposalConstructionPlacementPolicy.requiredTicks(
                new DisposalEntityData.ConstructionStep(2,1,"moonstation14:steel")));
    }
    @Test void placementRequiresTheCapturedModePlanRevision(){
        var plan=new DisposalConstructionMode.Plan(UUID.randomUUID(),3,"moonstation14:disposal_pipe",2,8);
        assertTrue(DisposalConstructionPlacementPolicy.samePlan(plan,"moonstation14:disposal_pipe",8));
        assertFalse(DisposalConstructionPlacementPolicy.samePlan(plan,"moonstation14:disposal_bend",8));
        assertFalse(DisposalConstructionPlacementPolicy.samePlan(plan,"moonstation14:disposal_pipe",9));
    }
}
