package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import java.util.List;
import com.juicyslew.moonstation14.ms14.construction.client.ConstructionKeyGesture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposalConstructionClientPolicyTest {
    @Test void tickContextBindingPreservesGestureUntilRealContextTransition() {
        var binding = new DisposalConstructionClient.ContextBinding();
        var gesture = new ConstructionKeyGesture();
        Object connection = new Object(), level = new Object();
        UUID body = UUID.randomUUID();
        int[] resets = {0};
        Runnable reset = () -> { resets[0]++; gesture.reset(); };
        assertTrue(DisposalConstructionClient.bindTickContext(binding, connection, level, body, 1, reset));

        // A short press opens the browser on release even with no active construction plan/family.
        gesture.update(true, false);
        assertFalse(DisposalConstructionClient.bindTickContext(binding, connection, level, body, 1, reset));
        assertTrue(gesture.update(false, false).openBrowser());
        assertEquals(1, resets[0]);

        // Held G with no family opens the browser on release; a family hold opens only its palette.
        gesture.update(true, false);
        for (int i = 1; i < ConstructionKeyGesture.HOLD_TICKS; i++) gesture.update(true, false);
        assertTrue(gesture.update(false, false).openBrowser());
        gesture.update(true, true);
        ConstructionKeyGesture.Result held = null;
        for (int i = 0; i < ConstructionKeyGesture.HOLD_TICKS; i++) held = gesture.update(true, true);
        assertTrue(held.showPalette());
        assertFalse(gesture.update(false, true).openBrowser());

        // Epoch/body, level, and connection changes each reset exactly once; a subsequent press works.
        assertTrue(DisposalConstructionClient.bindTickContext(binding, connection, level, body, 2, reset));
        assertFalse(DisposalConstructionClient.bindTickContext(binding, connection, level, body, 2, reset));
        Object nextLevel = new Object();
        assertTrue(DisposalConstructionClient.bindTickContext(binding, connection, nextLevel, body, 2, reset));
        Object nextConnection = new Object();
        assertTrue(DisposalConstructionClient.bindTickContext(binding, nextConnection, nextLevel, body, 2, reset));
        UUID nextBody = UUID.randomUUID();
        assertTrue(DisposalConstructionClient.bindTickContext(binding, nextConnection, nextLevel, nextBody, 2, reset));
        assertEquals(5, resets[0]);
        gesture.update(true, false);
        assertTrue(gesture.update(false, false).openBrowser());
    }

    @Test void paletteIsHeldStateAndOnlyInitialPressIsRising() {
        var first = DisposalConstructionClient.keyStep(false, true);
        assertTrue(first.rising());
        assertTrue(DisposalConstructionClient.paletteVisible(true, false, false, true));
        assertFalse(DisposalConstructionClient.keyStep(first.wasDown(), true).rising());
        assertTrue(DisposalConstructionClient.paletteVisible(true, false, true, false));
        assertFalse(DisposalConstructionClient.paletteVisible(false, false, true, false));
        assertFalse(DisposalConstructionClient.paletteVisible(true, true, true, false));
        var released = DisposalConstructionClient.keyStep(true, false);
        assertFalse(released.rising());
        assertTrue(DisposalConstructionClient.keyStep(released.wasDown(), true).rising());
    }

    @Test void constructionKeyAdmissionAllowsPointerHostButRejectsModalOrFocusedSearch() {
        assertTrue(DisposalConstructionClient.constructionKeysAdmitted(true,true,false));
        assertFalse(DisposalConstructionClient.constructionKeysAdmitted(true,false,false));
        assertFalse(DisposalConstructionClient.constructionKeysAdmitted(true,true,true));
        assertFalse(DisposalConstructionClient.constructionKeysAdmitted(false,true,false));
    }

    @Test void paletteRenderExcludesNormalPlacementHud() {
        var palette = DisposalConstructionClient.renderPolicy(true);
        assertTrue(palette.paletteOnly());
        assertFalse(palette.showPlacementStatus());
        var normal = DisposalConstructionClient.renderPolicy(false);
        assertFalse(normal.paletteOnly());
        assertTrue(normal.showPlacementStatus());
    }

    @Test void readinessUsesResolvedRequiredCountAndMaterial() {
        int recipeCount = 5;
        assertEquals(DisposalConstructionClient.Readiness.WRONG_MATERIAL,
                DisposalConstructionClient.readiness("moonstation14:steel", recipeCount, ItemStack.EMPTY).state());
        assertEquals(DisposalConstructionClient.Readiness.WRONG_MATERIAL,
                DisposalConstructionClient.readiness("moonstation14:steel", recipeCount, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 64)).state());
        assertEquals(DisposalConstructionClient.Readiness.INSUFFICIENT,
                DisposalConstructionClient.readiness("moonstation14:steel", recipeCount, new ItemStack(ModItems.STEEL.get(), 4)).state());
        var ready = DisposalConstructionClient.readiness("moonstation14:steel", recipeCount, new ItemStack(ModItems.STEEL.get(), 5));
        assertEquals(DisposalConstructionClient.Readiness.READY, ready.state());
        assertEquals(5, ready.required());
    }

    @Test void missingConfirmedObservationIsUpdatingNotAnEmptyHandGuess() {
        assertEquals(DisposalConstructionClient.Readiness.UPDATING,
                DisposalConstructionClient.readiness(false, "moonstation14:steel", 5, ItemStack.EMPTY).state());
        assertEquals(DisposalConstructionClient.Readiness.WRONG_MATERIAL,
                DisposalConstructionClient.readiness(true, "moonstation14:steel", 5, ItemStack.EMPTY).state());
        assertEquals(DisposalConstructionClient.Readiness.UPDATING,
                DisposalConstructionClient.readiness(false, "moonstation14:steel", 5,
                        new ItemStack(ModItems.STEEL.get(), 5)).state());
    }

    @Test void paletteWrapsAcrossSupportedShapes() {
        int size = DisposalConstructionClient.supportedShapes().size();
        assertEquals(size - 1, DisposalConstructionClient.wrapShapeIndex(-1));
        assertEquals(0, DisposalConstructionClient.wrapShapeIndex(size));
        assertTrue(DisposalConstructionClient.supportedShapes().contains("disposal_pipe"));
    }

    @Test void rapidFamilyWheelIntentAdvancesAndWrapsEntireFamily() {
        int index=0;
        for(int i=0;i<3;i++) index=DisposalConstructionClient.selectionIndex(index,1,9);
        assertEquals(3,index);
        assertEquals(0,DisposalConstructionClient.selectionIndex(8,1,9));
        assertEquals(8,DisposalConstructionClient.selectionIndex(0,-1,9));
    }

    @Test void rapidVariantRequestsIgnoreOldAcksAndLatestDenialOrLaterSharedAckSettlesIntent() {
        var owner=new DisposalConstructionClient.PaletteIntentOwner();
        var confirmed=net.minecraft.resources.ResourceLocation.parse("moonstation14:wall");
        var first=net.minecraft.resources.ResourceLocation.parse("moonstation14:one");
        var second=net.minecraft.resources.ResourceLocation.parse("moonstation14:two");
        var third=net.minecraft.resources.ResourceLocation.parse("moonstation14:three");
        owner.request(first,10); owner.request(second,11); owner.request(third,12);
        assertEquals(third,owner.display(confirmed));
        assertFalse(owner.acknowledge(10));
        assertFalse(owner.acknowledge(11));
        assertEquals(third,owner.display(confirmed));
        // A denied latest request keeps the prior server recipe; cursor/display reconcile to it.
        assertTrue(owner.acknowledge(12));
        assertEquals(confirmed,owner.display(confirmed));
        var options=List.of(first,confirmed,second,third);
        assertEquals(1,DisposalConstructionClient.confirmedCursor(confirmed,options,3));
        // A successful latest reply with a newer shared sequence settles to the accepted recipe.
        owner.request(first,12);
        assertTrue(owner.acknowledge(13));
        assertEquals(confirmed,owner.display(confirmed));
        owner.request(first,14); owner.reset();
        assertFalse(owner.pending());
        assertEquals(confirmed,owner.display(confirmed));
    }

    @Test void pendingRecipeSelectsItsOwnStableFamilyOptionsUntilAuthoritativeRejection() {
        var ids=new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        var families=new java.util.HashMap<net.minecraft.resources.ResourceLocation,String>();
        for(int i=0;i<9;i++){var id=net.minecraft.resources.ResourceLocation.parse("moonstation14:disposal_"+i);ids.add(id);families.put(id,"disposals");}
        var girder=net.minecraft.resources.ResourceLocation.parse("moonstation14:girder");
        var wall=net.minecraft.resources.ResourceLocation.parse("moonstation14:wall"); ids.add(girder);ids.add(wall);families.put(girder,"walls");families.put(wall,"walls");
        var owner=new DisposalConstructionClient.PaletteIntentOwner();
        var prior=ids.get(0); var pending=ids.get(8);
        owner.request(pending,20);
        var options=DisposalConstructionClient.familyOptions(owner.display(null),ids,families::get,"disposals");
        assertEquals(9,options.size()); assertEquals(pending,options.get(8));
        assertFalse(owner.acknowledge(19)); // independent legacy-domain sequence cannot settle shared intent
        owner.request(girder,21);
        assertEquals(List.of(girder,wall),DisposalConstructionClient.familyOptions(owner.display(prior),ids,families::get,"walls"));
        assertTrue(owner.acknowledge(21)); // denial returns to the previous disposal family
        assertEquals(9,DisposalConstructionClient.familyOptions(owner.display(prior),ids,families::get,"disposals").size());
    }

    @Test void paletteWheelAndRenderIndexFollowRecipeIdentityAcrossLegacyCursorChangesAndAcks() {
        var ids=new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        var families=new java.util.HashMap<net.minecraft.resources.ResourceLocation,String>();
        for(int i=0;i<9;i++){var id=net.minecraft.resources.ResourceLocation.parse("moonstation14:disposal_"+i);ids.add(id);families.put(id,"disposals");}
        var options=DisposalConstructionClient.familyOptions(ids.get(0),ids,families::get,"disposals");
        var owner=new DisposalConstructionClient.PaletteIntentOwner();
        var authoritative=ids.get(0);
        owner.request(authoritative,1);
        for(int i=1;i<9;i++){var next=owner.nextRecipe(authoritative,options,1);assertEquals(ids.get(i),next);owner.request(next,i+1);}
        assertEquals(ids.get(8),owner.display(authoritative));
        assertEquals(8,owner.displayIndex(authoritative,options));
        assertEquals(ids.get(0),owner.nextRecipe(authoritative,options,1));
        owner.request(ids.get(4),20);
        assertFalse(owner.acknowledge(19));
        assertEquals(ids.get(5),owner.nextRecipe(authoritative,options,1));
        assertTrue(owner.acknowledge(20)); // denial settles on authoritative selection
        assertEquals(ids.get(1),owner.nextRecipe(authoritative,options,1));
        // A successful authoritative update also governs subsequent navigation.
        authoritative=ids.get(6);
        assertEquals(ids.get(7),owner.nextRecipe(authoritative,options,1));
    }

    @Test void stateCorrelationRejectsWrongBodyEpochAndOlderPackets() {
        UUID body = UUID.randomUUID();
        assertTrue(DisposalConstructionClient.acceptsSnapshot(body, 4, 9, 3, body, 4, 9, 8, 2));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(body, 3, 9, 3, body, 4, 9, 8, 2));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(UUID.randomUUID(), 4, 9, 3, body, 4, 9, 8, 2));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(body, 4, 8, 3, body, 4, 9, 8, 2));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(body, 4, 9, 1, body, 4, 9, 8, 2));
    }

    @Test void sharedAndDisposalSnapshotsUseIndependentSequenceTracks() {
        UUID body=UUID.randomUUID();
        // Disposal bridge replies can advance to 2, then 4; they must not raise the shared-plan floor.
        assertTrue(DisposalConstructionClient.acceptsSnapshot(body,4,1,1,body,4,1,0,-1));
        assertTrue(DisposalConstructionClient.acceptsSnapshot(body,4,2,2,body,4,2,1,1));
        assertTrue(DisposalConstructionClient.acceptsSnapshot(body,4,3,3,body,4,3,2,2));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(body,4,2,2,body,4,3,3,2));
        assertTrue(DisposalConstructionClient.acceptsSnapshot(body,4,4,4,body,4,4,2,3));
        assertFalse(DisposalConstructionClient.acceptsSnapshot(body,4,3,3,body,4,4,4,3));
    }
}
