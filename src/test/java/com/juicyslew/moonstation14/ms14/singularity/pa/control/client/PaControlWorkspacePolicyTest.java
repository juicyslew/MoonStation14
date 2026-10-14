package com.juicyslew.moonstation14.ms14.singularity.pa.control.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("PointerOverlayRegistry")
class PaControlWorkspacePolicyTest {
    @AfterEach void cleanupPointerOverlayWithoutNetwork() {
        PaControlWorkspace.INSTANCE.closeLocal(false);
    }
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation PROFILE = ResourceLocation.withDefaultNamespace("pa_test");
    private static com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlSnapshot snapshot(
            UUID session, long request, UUID machine, UUID body, long epoch, int x, long revision, boolean accepted) {
        return snapshot(session,request,machine,body,epoch,x,revision,accepted,0);
    }
    private static com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlSnapshot snapshot(
            UUID session, long request, UUID machine, UUID body, long epoch, int x, long revision, boolean accepted, double received) {
        return new com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlSnapshot(session, request, machine, body,
                epoch, DIM, x, 4, 7, PROFILE, revision, 3, 0, false, false, true, 10, received,
                List.of("control"), new boolean[]{true}, accepted, false);
    }

    @Test void openCorrelationIsSeparateFromBodyAuthorityAndActionRequestOrder() {
        UUID session=UUID.randomUUID(), machine=UUID.randomUUID(), body=UUID.randomUUID();
        var initial=snapshot(session,77,machine,body,9,12,1,true);
        assertTrue(PaControlWorkspace.matchesOpen(77,body,9,DIM,new BlockPos(12,4,7),machine,PROFILE,initial));
        assertFalse(PaControlWorkspace.matchesOpen(77,body,9,DIM,new BlockPos(12,4,7),UUID.randomUUID(),PROFILE,initial));
        assertFalse(PaControlWorkspace.matchesOpen(78,body,9,DIM,new BlockPos(12,4,7),machine,PROFILE,initial));
        assertFalse(PaControlWorkspace.matchesOpen(77,body,9,DIM,new BlockPos(13,4,7),machine,PROFILE,initial));
        assertFalse(PaControlWorkspace.matchesOpen(77,body,8,DIM,new BlockPos(12,4,7),machine,PROFILE,initial));
        assertFalse(PaControlWorkspace.matchesOpen(77,body,9,DIM,new BlockPos(12,4,7),UUID.randomUUID(),PROFILE,initial));
    }

    @Test void acceptsOpenSnapshotCorrelatedToCurrentServerMachineAndClientSequence() {
        UUID session=UUID.randomUUID(), machine=UUID.randomUUID(), body=UUID.randomUUID();
        var initial=snapshot(session,77,machine,body,9,12,1,true);
        assertTrue(PaControlWorkspace.matchesOpen(77,body,9,DIM,new BlockPos(12,4,7),machine,PROFILE,initial));
    }

    @Test void oldCloseRequestIdIsNotAnOpenCorrelationAndReplacementHasFreshSessionIdentity() {
        UUID oldSession=UUID.randomUUID(), newSession=UUID.randomUUID();
        var oldClosed=new com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlSnapshot(oldSession,41,UUID.randomUUID(),UUID.randomUUID(),1,DIM,0,0,0,PROFILE,-1,0,0,false,false,false,0,0,List.of(),new boolean[0],false,true);
        assertTrue(oldClosed.closed());
        var opened=snapshot(newSession,41,UUID.randomUUID(),UUID.randomUUID(),1,0,1,true);
        assertNotEquals(oldClosed.sessionId(),opened.sessionId());
        assertTrue(PaControlWorkspace.matchesOpen(41,opened.bodyId(),1,DIM,new BlockPos(0,4,7),opened.machineId(),PROFILE,opened));
    }

    @Test void actualClosedSessionTransitionPreservesSameNumberedPendingOpen() {
        var workspace=new PaControlWorkspace();
        UUID body=UUID.randomUUID(), machine=UUID.randomUUID();
        workspace.expectOpen(41,body,1,DIM,new BlockPos(0,4,7),machine,PROFILE,10);
        var closed=new com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlSnapshot(UUID.randomUUID(),41,UUID.randomUUID(),body,1,DIM,0,0,0,PROFILE,-1,0,0,false,false,false,0,0,List.of(),new boolean[0],false,true);
        workspace.receive(closed);
        assertEquals(41L,workspace.pendingCorrelation());
    }

    @Test void replacementCloseThenOpenSelectsFreshSessionAndRejectsWrongAuthority() {
        var workspace=new PaControlWorkspace(); UUID body=UUID.randomUUID(),machine=UUID.randomUUID();
        workspace.expectOpen(41,body,2,DIM,new BlockPos(0,4,7),machine,PROFILE,10);
        var closed=new com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlSnapshot(UUID.randomUUID(),41,UUID.randomUUID(),body,2,DIM,0,0,0,PROFILE,-1,0,0,false,false,false,0,0,List.of(),new boolean[0],false,true);
        workspace.receive(closed);
        var fresh=snapshot(UUID.randomUUID(),41,machine,body,2,0,1,true);
        var wrongBody=snapshot(UUID.randomUUID(),41,machine,UUID.randomUUID(),2,0,1,true);
        var wrongEpoch=snapshot(UUID.randomUUID(),41,machine,body,3,0,1,true);
        var wrongMachine=snapshot(UUID.randomUUID(),41,UUID.randomUUID(),body,2,0,1,true);
        assertFalse(workspace.activateOpen(wrongBody)); assertFalse(workspace.activateOpen(wrongEpoch)); assertFalse(workspace.activateOpen(wrongMachine));
        assertTrue(workspace.activateOpen(fresh));
        assertEquals(fresh.sessionId(),workspace.session().sessionId()); assertNull(workspace.pendingCorrelation());
    }

    @Test void paGeometryPreservesTopLeftAcrossViewportAndSnapshotRefresh() {
        var workspace=new PaControlWorkspace();
        var overlay=workspace.overlay();
        try {
            overlay.ensureGeometry(400,240);
            var geometry=overlay.geometry(); assertEquals(137,geometry.x()); assertEquals(74,geometry.y());
            assertTrue(geometry.beginPointer(geometry.x()+4,geometry.y()+4,0,12,0));
            assertTrue(geometry.dragPointer(-500,-500)); assertEquals(0,geometry.x());assertEquals(0,geometry.y());
            geometry.releasePointer(0);
            overlay.ensureGeometry(500,300); // viewport resize, as render does
            assertEquals(0,geometry.x());assertEquals(0,geometry.y());
            overlay.ensureGeometry(500,300); // refreshed session/snapshot does not initialize or recenter placement
            assertEquals(0,geometry.x());assertEquals(0,geometry.y());
        } finally {
            // This policy-only test has no connected client: retire local overlay state without sending CLOSE.
            workspace.closeLocal(false);
        }
    }

    @Test void periodicPowerRefreshAtSameRevisionIsAcceptedButOldRevisionAndRetiredAreNot() {
        UUID session=UUID.randomUUID(),machine=UUID.randomUUID(),body=UUID.randomUUID();
        var current=snapshot(session,5,machine,body,1,0,8,true);
        assertTrue(PaControlWorkspace.acceptSnapshot(current,snapshot(session,5,machine,body,1,0,8,true),false,false,0));
        assertTrue(PaControlWorkspace.acceptSnapshot(current,snapshot(session,-1,machine,body,1,0,8,true,91),false,false,0));
        assertFalse(PaControlWorkspace.acceptSnapshot(current,snapshot(session,-1,machine,body,1,0,7,true),false,false,0));
        assertFalse(PaControlWorkspace.acceptSnapshot(current,snapshot(session,6,machine,body,1,0,8,true),true,false,0));
        assertFalse(PaControlWorkspace.acceptSnapshot(current,snapshot(session,4,machine,body,1,0,8,true),false,true,5));
    }

    @Test void strengthUsesProtocolSemanticLevelsAndResolvedMaximum() {
        assertEquals(0,com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.STANDBY.level());
        assertEquals(1,com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_0.level());
        assertEquals(2,com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_1.level());
        assertEquals(3,com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_2.level());
        assertEquals(1,PaControlWorkspace.nextStrengthLevel(0,3));
        assertEquals(2,PaControlWorkspace.nextStrengthLevel(1,3));
        assertEquals(3,PaControlWorkspace.nextStrengthLevel(2,3));
        assertEquals(0,PaControlWorkspace.nextStrengthLevel(3,3));
        assertEquals(0,PaControlWorkspace.nextStrengthLevel(2,2));
    }
}
