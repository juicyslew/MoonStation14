package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("PointerOverlayRegistry")
class ChemMasterOutputRefreshTest {
    private static final UUID SESSION=UUID.randomUUID(),MACHINE=UUID.randomUUID(),BODY=UUID.randomUUID();
    @AfterEach void cleanup(){PointerOverlayRegistry.resetOwnedContext();}

    @Test void workspaceFramesRefreshOnlyChangedNumericInputsAndRespectFocusedEdit(){
        var workspace=new ChemMasterWorkspace(s->s.length(),()->true);workspace.lastWindowClosedHook(()->{});
        var first=state(1,ChemMasterControlRequest.DrawSource.BUFFER,0,2000,ChemMasterWindowState.OutputKind.PILL_CANISTER,8,0,false);
        workspace.accept(first,640,360);
        var window=workspace.get(MACHINE);
        var overlay=(ChemMasterOverlay)PointerOverlayRegistry.paintOrder().stream().filter(ChemMasterOverlay.class::isInstance).findFirst().orElseThrow();
        overlay.refreshOutputForm(window.state(),null,null);
        assertEquals("10",window.outputForm.pillDose());assertEquals("2",window.outputForm.pillCount());

        window.outputForm.editPillCount("2");window.outputForm.editPillDose("5",Integer.MAX_VALUE);
        window.outputForm.editBottleDose("7",Integer.MAX_VALUE);window.outputForm.editLabel("manual");
        var typeAck=state(2,ChemMasterControlRequest.DrawSource.BUFFER,12,2000,ChemMasterWindowState.OutputKind.PILL_CANISTER,8,0,false);
        workspace.accept(typeAck,640,360);
        overlay.refreshOutputForm(window.state(),ChemMasterOutputEditors.Field.LABEL,"manual");
        assertEquals("2",window.outputForm.pillCount());assertEquals("5",window.outputForm.pillDose());
        assertEquals("7",window.outputForm.bottleDose());assertEquals("manual",window.outputForm.label());

        var labelAck=state(3,ChemMasterControlRequest.DrawSource.BUFFER,12,2000,ChemMasterWindowState.OutputKind.PILL_CANISTER,8,0,true);
        workspace.accept(labelAck,640,360);overlay.refreshOutputForm(window.state(),null,null);
        assertEquals("2",window.outputForm.pillCount());assertEquals("5",window.outputForm.pillDose());
        assertNotEquals("manual",window.outputForm.label());assertFalse(window.outputForm.updateLabel());

        var changedSource=state(4,ChemMasterControlRequest.DrawSource.BUFFER,12,3000,ChemMasterWindowState.OutputKind.PILL_CANISTER,8,0,false);
        window.outputForm.editPillDose("999",Integer.MAX_VALUE);
        workspace.accept(changedSource,640,360);
        overlay.refreshOutputForm(window.state(),ChemMasterOutputEditors.Field.PILL_DOSE,"999");
        assertEquals("0",window.outputForm.pillCount(),"an unavailable explicit dose disables the derived count");
        assertEquals("999",window.outputForm.pillDose(),"focused draft survives changed bounds");
        assertFalse(window.outputForm.state(30,8,10,0).validPillDose(),"an invalid retained draft remains ineligible");

        var changedCapacity=state(5,ChemMasterControlRequest.DrawSource.BUFFER,12,3000,ChemMasterWindowState.OutputKind.BOTTLE,0,5,false);
        workspace.accept(changedCapacity,640,360);overlay.refreshOutputForm(window.state(),null,null);
        assertEquals("0",window.outputForm.pillCount());assertEquals("5",window.outputForm.bottleDose());

        window.outputForm.editPillCount("8");
        var changedSourceSelection=state(6,ChemMasterControlRequest.DrawSource.INPUT,12,3000,ChemMasterWindowState.OutputKind.BOTTLE,0,5,false);
        workspace.accept(changedSourceSelection,640,360);overlay.refreshOutputForm(window.state(),null,null);
        assertEquals("0",window.outputForm.pillCount(),"source selection is a numeric-input change even at equal volume");

        window.outputForm.editPillCount("8");
        workspace.accept(changedCapacity,640,360); // stale frame is rejected by the workspace
        overlay.refreshOutputForm(window.state(),null,null);
        assertEquals("8",window.outputForm.pillCount(),"rejected stale snapshots cannot reset local drafts");
    }

    @Test void explicitDoseSurvivesSuccessfulAuthoritativeResultAndNextPackageUsesRecountedValue(){
        var requests=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest>();
        var workspace=new ChemMasterWorkspace(s->s.length(),()->true);workspace.sender(requests::add);workspace.lastWindowClosedHook(()->{});
        var initial=state(20,ChemMasterControlRequest.DrawSource.BUFFER,0,12000,ChemMasterWindowState.OutputKind.PILL_CANISTER,10,0,false,20);
        workspace.accept(initial,640,360);var window=workspace.get(MACHINE);
        var overlay=(ChemMasterOverlay)PointerOverlayRegistry.paintOrder().stream().filter(ChemMasterOverlay.class::isInstance).findFirst().orElseThrow();
        overlay.refreshOutputForm(window.state(),null,null);
        assertEquals("20",window.outputForm.pillDose());assertEquals("6",window.outputForm.pillCount());
        window.outputForm.editPillDose("5",20);window.outputForm.editPillCount("6");window.outputForm.editLabel("manual batch");

        var completed=state(21,ChemMasterControlRequest.DrawSource.BUFFER,0,9000,ChemMasterWindowState.OutputKind.PILL_CANISTER,4,0,false,20);
        workspace.accept(completed,640,360);overlay.refreshOutputForm(window.state(),null,null);
        assertEquals("5",window.outputForm.pillDose());assertEquals("4",window.outputForm.pillCount());
        assertEquals("manual batch",window.outputForm.label());
        assertTrue(window.outputForm.state(90,4,20,0).canCreatePills());
        assertTrue(workspace.createPills(window,4,5,window.outputForm.label()));
        assertEquals(1,requests.size());
        var request=requests.getFirst();assertEquals(ChemMasterControlRequest.Operation.CREATE_PILLS,request.operation());
        assertEquals(new ChemMasterControlRequest.OutputRequest(4,5,"manual batch"),request.outputRequest().orElseThrow());
    }

    private static ChemMasterWindowState state(long frame,ChemMasterControlRequest.DrawSource source,int pillType,long sourceCents,
                                               ChemMasterWindowState.OutputKind kind,int freeCells,int freeBottle,boolean updateLabel){
        return state(frame,source,pillType,sourceCents,kind,freeCells,freeBottle,updateLabel,10);
    }
    private static ChemMasterWindowState state(long frame,ChemMasterControlRequest.DrawSource source,int pillType,long sourceCents,
                                               ChemMasterWindowState.OutputKind kind,int freeCells,int freeBottle,boolean updateLabel,int doseLimit){
        var water=ModReagents.createKey("water");
        var sourceRows=sourceCents==0?List.<ChemMasterWindowState.ReagentRow>of():List.of(new ChemMasterWindowState.ReagentRow(water,sourceCents));
        var inputRows=source==ChemMasterControlRequest.DrawSource.INPUT?sourceRows:List.<ChemMasterWindowState.ReagentRow>of();
        var bufferRows=source==ChemMasterControlRequest.DrawSource.BUFFER?sourceRows:List.<ChemMasterWindowState.ReagentRow>of();
        var output=switch(kind){
            case EMPTY->new ChemMasterWindowState.OutputSlot(frame,kind,Optional.empty(),0,0,List.of(),0,0,0,Optional.empty(),List.of());
            case PILL_CANISTER->new ChemMasterWindowState.OutputSlot(frame,kind,Optional.of(Component.literal("Canister")),0,0,List.of(),5,2,freeCells,Optional.empty(),List.of());
            case BOTTLE->new ChemMasterWindowState.OutputSlot(frame,kind,Optional.of(Component.literal("Bottle")),freeBottle*100L,0,List.of(),0,0,0,Optional.empty(),List.of());
        };
        return new ChemMasterWindowState(frame,frame,frame,frame,-1,Optional.empty(),SESSION,MACHINE,BODY,1,
                ResourceLocation.withDefaultNamespace("overworld"),0,64,0,true,Optional.empty(),Optional.empty(),sourceCents,source==ChemMasterControlRequest.DrawSource.INPUT?sourceCents:0,
                inputRows,bufferRows,ChemMasterControlRequest.Mode.TRANSFER,ChemMasterControlRequest.Sort.OLDEST_FIRST,output,source,pillType,doseLimit,updateLabel);
    }
}
