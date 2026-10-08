package com.juicyslew.moonstation14.ms14.construction.world;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.construction.ConstructionGraphTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionWorldRoutingTest {
    private static ConstructionWorldService.InitialMaterialRoute route(String recipe, ItemStack stack) {
        var stage = catalog().initialStage(ModConstruction.id(recipe)).orElseThrow();
        return ConstructionWorldService.routeInitialMaterial(stage, stack);
    }

    private static com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionCatalog catalog() {
        try { return ConstructionGraphTest.resolvedCatalogForGuidance(); }
        catch (Exception e) { throw new AssertionError("could not resolve construction fixture prototypes",e); }
    }

    @Test
    void resolvedInitialMaterialClaimsPlacementButOtherUseRetainsHostProgression() {
        assertEquals(ConstructionWorldService.InitialMaterialRoute.PLACEMENT, route("steel_wall", new ItemStack(ModItems.STEEL.get(), 2)));
        assertEquals(ConstructionWorldService.InitialMaterialRoute.PLACEMENT, route("disposal_straight", new ItemStack(ModItems.STEEL.get(), 2)));
        assertEquals(ConstructionWorldService.InitialMaterialRoute.HOST_INTERACTION, route("steel_wall", ItemStack.EMPTY));
        assertEquals(ConstructionWorldService.InitialMaterialRoute.HOST_INTERACTION, route("steel_wall", new ItemStack(ModItems.PLASTIC.get(), 2)));
        assertEquals(ConstructionWorldService.InitialMaterialRoute.HOST_INTERACTION, route("steel_wall", new ItemStack(ModItems.WRENCH.get())));
        // Intent is selected by material identity, not available quantity: beginFoundation rejects an underfunded reservation.
        assertEquals(ConstructionWorldService.InitialMaterialRoute.PLACEMENT, route("steel_wall", new ItemStack(ModItems.STEEL.get())));
    }
}
