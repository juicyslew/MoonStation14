package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterOutputRequestTest {
    private static ChemMasterWindowState state(long frame,long ack,UUID session,UUID machine,UUID body){
        return new ChemMasterWindowState(frame,5,6,13,ack,Optional.empty(),session,machine,body,1,
                ResourceLocation.withDefaultNamespace("overworld"),1,64,2,true,Optional.empty(),Optional.empty(),0,0,List.of(),List.of(),
                ChemMasterControlRequest.Mode.TRANSFER,ChemMasterControlRequest.Sort.OLDEST_FIRST,
                new ChemMasterWindowState.OutputSlot(11,ChemMasterWindowState.OutputKind.BOTTLE,Optional.of(Component.literal("Bottle")),3000,0,List.of(),0,0,0,Optional.empty(),List.of()),
                ChemMasterControlRequest.DrawSource.BUFFER,4,20,false);
    }

    @Test void outputOperationsUseCanonicalFieldsAndLatestProofRevisions(){
        var workspace=new ChemMasterWorkspace(s->s.length(),()->true);var sent=new ArrayList<ChemMasterControlRequest>();workspace.sender(sent::add);
        var session=UUID.randomUUID();var machine=UUID.randomUUID();var body=UUID.randomUUID();
        var s=state(7,-1,session,machine,body);workspace.accept(s,640,360);var w=workspace.get(s.machineId());
        assertTrue(workspace.drawSource(w,ChemMasterControlRequest.DrawSource.INPUT));
        assertTrue(workspace.pillType(w,9));
        assertTrue(workspace.createPills(w,3,8,"Label"));
        assertTrue(workspace.fillBottle(w,7,"Bottle"));
        assertTrue(workspace.ejectOutput(w));
        assertEquals(5,sent.size());
        var source=sent.get(0);assertEquals(ChemMasterControlRequest.Operation.SET_DRAW_SOURCE,source.operation());
        assertEquals(Optional.of(ChemMasterControlRequest.DrawSource.INPUT),source.drawSource());
        assertEquals(0,source.expectedBufferRevision());assertEquals(0,source.expectedOutputSlotRevision());assertEquals(-1,source.pillType());assertTrue(source.outputRequest().isEmpty());
        var type=sent.get(1);assertEquals(ChemMasterControlRequest.Operation.SET_PILL_TYPE,type.operation());assertEquals(9,type.pillType());
        assertTrue(type.drawSource().isEmpty());assertTrue(type.outputRequest().isEmpty());assertEquals(0,type.expectedBufferRevision());
        var pills=sent.get(2);assertEquals(13,pills.expectedBufferRevision());assertEquals(11,pills.expectedOutputSlotRevision());
        assertEquals(new ChemMasterControlRequest.OutputRequest(3,8,"Label"),pills.outputRequest().orElseThrow());
        var bottle=sent.get(3);assertEquals(13,bottle.expectedBufferRevision());assertEquals(11,bottle.expectedOutputSlotRevision());
        assertEquals(new ChemMasterControlRequest.OutputRequest(0,7,"Bottle"),bottle.outputRequest().orElseThrow());
        var eject=sent.get(4);assertEquals(11,eject.expectedOutputSlotRevision());assertEquals(0,eject.expectedBufferRevision());assertTrue(eject.outputRequest().isEmpty());
        workspace.accept(state(8,5,session,machine,body),640,360);assertFalse(w.pending());
        workspace.accept(state(7,-1,session,machine,body),640,360);assertEquals(8,w.state().frame(),"stale snapshot must not replace current proof state");
        workspace.clear();
    }
}
