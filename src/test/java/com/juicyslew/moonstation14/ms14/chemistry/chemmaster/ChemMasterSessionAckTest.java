package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterWorkspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChemMasterSessionAckTest {
    @Test void snapshotsUseMonotonicAckPerSession(){
        // Open/new session, then an unsolicited snapshot after request 7 was terminal.
        assertEquals(-1, ChemMasterSessionService.acknowledgedRequest(-1, -1));
        assertEquals(7, ChemMasterSessionService.acknowledgedRequest(-1, 7));
        // A different viewer's session watermark is independent of the initiator's request.
        assertEquals(3, ChemMasterSessionService.acknowledgedRequest(-1, 3));
        assertEquals(8, ChemMasterSessionService.acknowledgedRequest(8, 7));
        assertEquals(7, ChemMasterSessionService.acknowledgedRequest(2, 7));
    }

    @Test void productionAckPolicyKeepsWorkspacePendingCorrectAcrossSnapshots(){
        var workspace=new ChemMasterWorkspace();var sent=new java.util.ArrayList<ChemMasterControlRequest>();workspace.sender(sent::add);
        UUID session=UUID.randomUUID(),machine=UUID.randomUUID();workspace.accept(state(session,machine,1,-1),640,360);
        var window=workspace.get(machine);workspace.send(window,ChemMasterControlRequest.Operation.EJECT,null,0);
        long first=sent.get(0).requestId();org.junit.jupiter.api.Assertions.assertTrue(window.pending());
        workspace.accept(state(session,machine,2,first),640,360);org.junit.jupiter.api.Assertions.assertFalse(window.pending());
        long unsolicited=ChemMasterSessionService.acknowledgedRequest(-1,first);
        workspace.accept(state(session,machine,3,unsolicited),640,360);org.junit.jupiter.api.Assertions.assertFalse(window.pending());
        workspace.accept(state(session,machine,2,-1),640,360);org.junit.jupiter.api.Assertions.assertFalse(window.pending());
        workspace.send(window,ChemMasterControlRequest.Operation.EJECT,null,0);long second=sent.get(1).requestId();
        org.junit.jupiter.api.Assertions.assertEquals(first+1,second);org.junit.jupiter.api.Assertions.assertTrue(window.pending());
        workspace.accept(state(session,machine,4,unsolicited),640,360);org.junit.jupiter.api.Assertions.assertTrue(window.pending());
        workspace.accept(state(session,machine,5,ChemMasterSessionService.acknowledgedRequest(second,first)),640,360);
        org.junit.jupiter.api.Assertions.assertFalse(window.pending());workspace.clear();
    }

    private static ChemMasterWindowState state(UUID session,UUID machine,long frame,long ack){
        return new ChemMasterWindowState(frame,frame,0,ack,Optional.empty(),session,machine,
                UUID.nameUUIDFromBytes("chem-master-test-body".getBytes(java.nio.charset.StandardCharsets.UTF_8)),1,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"),0,64,0,true,Optional.empty(),
                Optional.empty(),0,0,List.of(),List.of(),ChemMasterControlRequest.Mode.TRANSFER,
                ChemMasterControlRequest.Sort.OLDEST_FIRST);
    }
}
