package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeleePresentationServerDiagnosticTest {
    @Test
    void resolvesObserverScenePosesWithoutMutatingTheSource() {
        var down = MeleePresentationServerDiagnostic.resolveMotionObserverPose("motion_observer_down");
        var up = MeleePresentationServerDiagnostic.resolveMotionObserverPose("motion_observer_up");
        assertEquals(new MeleePresentationServerDiagnostic.MotionObserverPose(1, 0, 3, -90, 45), down);
        assertEquals(new MeleePresentationServerDiagnostic.MotionObserverPose(1, 0, 3, -90, -45), up);
        assertEquals(new MeleePresentationServerDiagnostic.MotionObserverPose(-.45, 0, .75, -60, 0),
                MeleePresentationServerDiagnostic.resolveMotionObserverPose("motion_observer_close"));
        var wide = MeleePresentationServerDiagnostic.resolveMotionObserverPose("motion_observer_wide");
        assertEquals(new MeleePresentationServerDiagnostic.MotionObserverPose(1, 0, 3, -90, 0), wide);
        for (int i = 0; i < 5; i++) {
            assertEquals(down, MeleePresentationServerDiagnostic.resolveMotionObserverPose("motion_observer_down"));
            assertEquals(up, MeleePresentationServerDiagnostic.resolveMotionObserverPose("motion_observer_up"));
        }
    }

    @Test
    void expectedSwingSequenceUsesIndependentCurrentAndSetupCounters() {
        assertEquals(8, MeleePresentationServerDiagnostic.nextExpectedSwingSequence(7, false));
        assertEquals(7, MeleePresentationServerDiagnostic.nextExpectedSwingSequence(7, true));
        assertEquals(1, MeleePresentationServerDiagnostic.nextExpectedSwingSequence(0, false));
    }

    @Test void serializedFlightIdentityRoundTripsBothActorRolesToClientValidator() {
        var primary=java.util.UUID.randomUUID(); var peer=java.util.UUID.randomUUID();
        for(boolean observer:new boolean[]{false,true}) {
            var scene=MeleePresentationServerDiagnostic.writeFlightSceneIdentity(new com.google.gson.JsonObject(),observer,11,primary.toString(),3,22,peer.toString(),4);
            var parsed=com.google.gson.JsonParser.parseString(scene.toString()).getAsJsonObject();
            var actor=observer?peer:primary; var victim=observer?primary:peer;
            assertEquals(victim,com.juicyslew.moonstation14.ms14.combat.client.MeleeStrikePresentationClientDiagnostic.validateFlightMetadata(parsed,null,observer?"motion_observer_disarm_flight":"motion_disarm_flight"));
            assertEquals(observer?11:22,parsed.get("targetId").getAsInt());
            assertEquals(22,parsed.get("peerBodyId").getAsInt());
            assertEquals(actor.toString(),parsed.get("expectedActorUUID").getAsString());
            assertEquals(victim.toString(),parsed.get("targetUUID").getAsString());
        }
    }

    @Test void flightIdentityRejectsMissingPeerFieldsAndWrongVictimRoleDescriptively() {
        var p=java.util.UUID.randomUUID(); var q=java.util.UUID.randomUUID();
        for(String key:new String[]{"peerBodyId","peerBodyUUID","peerEpoch"}) {
            var scene=MeleePresentationServerDiagnostic.writeFlightSceneIdentity(new com.google.gson.JsonObject(),false,1,p.toString(),2,3,q.toString(),4); scene.remove(key);
            var error=assertThrows(IllegalStateException.class,()->com.juicyslew.moonstation14.ms14.combat.client.MeleeStrikePresentationClientDiagnostic.validateFlightMetadata(scene,null,"motion_disarm_flight"));
            assertTrue(error.getMessage().contains(key));
        }
        var scene=MeleePresentationServerDiagnostic.writeFlightSceneIdentity(new com.google.gson.JsonObject(),true,1,p.toString(),2,3,q.toString(),4); scene.addProperty("targetUUID",q.toString());
        assertTrue(assertThrows(IllegalStateException.class,()->com.juicyslew.moonstation14.ms14.combat.client.MeleeStrikePresentationClientDiagnostic.validateFlightMetadata(scene,null,"motion_observer_disarm_flight")).getMessage().contains("wrong-target-role"));
    }

    @Test void observerFlightPreflightRejectsMissingPeerIdentityBeforeArmGateCanProceed() {
        var scene=MeleePresentationServerDiagnostic.writeFlightSceneIdentity(new com.google.gson.JsonObject(),true,1,java.util.UUID.randomUUID().toString(),2,3,java.util.UUID.randomUUID().toString(),4);
        scene.remove("peerBodyUUID");
        var error=assertThrows(IllegalStateException.class,()->com.juicyslew.moonstation14.ms14.combat.client.MeleeStrikePresentationClientDiagnostic.validateFlightMetadata(scene,null,"motion_observer_disarm_flight"));
        assertTrue(error.getMessage().contains("peerBodyUUID"));
    }
}
