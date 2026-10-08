package com.juicyslew.moonstation14.ms14.construction.client;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionStructurePreviewPolicyTest {
    @Test void materialAndSpaceAreIndependent() {
        var steel=new ItemStack(com.juicyslew.moonstation14.item.ModItems.STEEL.get(),1);
        assertEquals(ConstructionStructurePreview.Readiness.INSUFFICIENT, ConstructionStructurePreview.readiness("moonstation14:steel",2,steel));
        var blocked=ConstructionStructurePreview.status("moonstation14:steel",2,new ItemStack(com.juicyslew.moonstation14.item.ModItems.STEEL.get(),2),true,false);
        assertEquals(ConstructionStructurePreview.Readiness.READY,blocked.material());
        assertEquals(ConstructionStructurePreview.Target.BLOCKED,blocked.target());
    }
    @Test void foundationGhostMatchesServerAirOnlyRuleAndSharedBodyCollisionPreflight() {
        var stack=new ItemStack(com.juicyslew.moonstation14.item.ModItems.STEEL.get(),1);
        assertEquals(ConstructionStructurePreview.Target.UNAVAILABLE,ConstructionStructurePreview.status("moonstation14:steel",2,stack,true,true).target());
        assertEquals(ConstructionStructurePreview.Target.UNAVAILABLE,ConstructionStructurePreview.status("moonstation14:steel",2,stack,false,false).target());
        assertTrue(ConstructionStructurePreview.foundationEligible(true,true,true,false));
        assertFalse(ConstructionStructurePreview.foundationEligible(true,true,false,false)); // replaceable grass is not air
        assertFalse(ConstructionStructurePreview.foundationEligible(true,true,true,true));
        assertFalse(ConstructionStructurePreview.foundationEligible(false,true,true,false));
        assertFalse(ConstructionStructurePreview.foundationEligible(true,false,true,false));
    }
}
