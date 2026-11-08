package com.juicyslew.moonstation14.ms14.combat.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class MeleeStrikePresentationProtocolTest {
    @Test void cameraMirrorReadiness() {
        assertTrue(MeleeStrikePresentationClientDiagnostic.cameraAimReady(-1, true));
        assertFalse(MeleeStrikePresentationClientDiagnostic.cameraAimReady(1, true));
        assertTrue(MeleeStrikePresentationClientDiagnostic.cameraAimReady(1, false));
        assertFalse(MeleeStrikePresentationClientDiagnostic.cameraAimReady(-1, false));
        assertFalse(MeleeStrikePresentationClientDiagnostic.cameraAimReady(Double.NaN, false));
    }

    @Test void combatAdmissionReasonReportsActualReadinessWithoutAssumingRequest() {
        var body=java.util.UUID.randomUUID();
        assertEquals("policy-context-mismatch", MeleeStrikePresentationClientDiagnostic.combatAdmissionReason(true,true,true,true,body,4,null,0,false));
        assertEquals("request-pending", MeleeStrikePresentationClientDiagnostic.combatAdmissionReason(true,true,true,true,body,4,body,4,true));
        assertEquals("request-toggle-eligible", MeleeStrikePresentationClientDiagnostic.combatAdmissionReason(true,true,true,true,body,4,body,4,false));
        assertEquals("not-spectator-carrier", MeleeStrikePresentationClientDiagnostic.combatAdmissionReason(true,true,true,false,body,4,body,4,false));
    }

    @Test void sceneProtocol() {
        assertTrue(MeleeStrikePresentationClientDiagnostic.thirdPersonScene("motion_fist_third"));
        assertTrue(MeleeStrikePresentationClientDiagnostic.thirdPersonScene("motion_knife_wide"));
        assertTrue(MeleeStrikePresentationClientDiagnostic.thirdPersonScene("motion_crowbar_light"));
        assertFalse(MeleeStrikePresentationClientDiagnostic.thirdPersonScene("motion_observer_wide"));
        assertFalse(MeleeStrikePresentationClientDiagnostic.thirdPersonScene("motion_knife_wide_vertical_up"));
        assertFalse(MeleeStrikePresentationClientDiagnostic.thirdPersonScene("motion_crowbar_wall_wide"));
        assertFalse(MeleeStrikePresentationClientDiagnostic.thirdPersonScene("motion_fist_wide"));
        assertFalse(MeleeStrikePresentationClientDiagnostic.thirdPersonScene("motion_crowbar_wide"));
        assertFalse(MeleeStrikePresentationClientDiagnostic.thirdPersonScene("motion_observer_third"));
        assertEquals(com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE, MeleeStrikePresentationClientDiagnostic.sceneMode("motion_observer_wide"));
        assertEquals(com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.DISARM, MeleeStrikePresentationClientDiagnostic.sceneMode("motion_disarm_flight"));
        assertEquals(com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, MeleeStrikePresentationClientDiagnostic.sceneMode("motion_fist_third"));
        assertTrue(MeleeStrikePresentationClientDiagnostic.windowStart("motion_observer_wide", 1) <= .2);
        assertTrue(MeleeStrikePresentationClientDiagnostic.windowEnd("motion_observer_wide", 1) >= .2);
    }

    @Test void warmupPolicyAndCooldownBoundary() {
        assertTrue(MeleeStrikePresentationClientDiagnostic.safeSceneWarmup("motion_observer_wide"));
        assertTrue(MeleeStrikePresentationClientDiagnostic.safeSceneWarmup("motion_knife_first"));
        assertFalse(MeleeStrikePresentationClientDiagnostic.safeSceneWarmup("motion_disarm_flight"));
        assertFalse(MeleeStrikePresentationClientDiagnostic.safeSceneWarmup("motion_observer_disarm_flight"));
        assertFalse(MeleeStrikePresentationClientDiagnostic.safeSceneWarmup("motion_jug_hit"));
        assertTrue(MeleeStrikePresentationClientDiagnostic.peerCooldownReady(12, 12));
        assertFalse(MeleeStrikePresentationClientDiagnostic.peerCooldownReady(11, 12));
        assertTrue(MeleeStrikePresentationClientDiagnostic.missedWindow(.31, "motion_fist_first", 0));
        assertFalse(MeleeStrikePresentationClientDiagnostic.missedWindow(.30, "motion_fist_first", 0));
    }

    @Test void missingFlightCueOnlyAllowedAfterSampleTwoWithValidVelocity() {
        JsonObject origin = vector(0, 0, 0), velocity = vector(1, 0, 0);
        assertFalse(MeleeStrikePresentationClientDiagnostic.flightCueMatches(null, origin, velocity, 1));
        assertTrue(MeleeStrikePresentationClientDiagnostic.flightCueMatches(null, origin, velocity, 2));
        assertFalse(MeleeStrikePresentationClientDiagnostic.flightCueMatches(null, origin, vector(0, 0, 0), 2));
        assertFalse(MeleeStrikePresentationClientDiagnostic.flightCueMatches(null, origin, vector(Double.NaN, 0, 1), 2));
    }

    @Test void observerReplyMustMatchFrozenSetupExpectation() {
        JsonObject setup=new JsonObject();setup.addProperty("peerBodyUUID","body");setup.addProperty("peerEpoch",4);setup.addProperty("peerExpectedSwingSequence",12);
        JsonObject reply=new JsonObject();reply.addProperty("phase","motion_observer_wide_swing");reply.addProperty("peerBodyUUID","body");reply.addProperty("peerEpoch",4);reply.addProperty("peerSwingEpoch",4);reply.addProperty("scene","motion_observer_wide");reply.addProperty("replySequence",12);
        assertTrue(MeleeStrikePresentationClientDiagnostic.observerReplyMatches(setup,reply,"motion_observer_wide"));
        reply.addProperty("replySequence",11);assertFalse(MeleeStrikePresentationClientDiagnostic.observerReplyMatches(setup,reply,"motion_observer_wide"));
        reply.addProperty("replySequence",12);reply.addProperty("scene","other");assertFalse(MeleeStrikePresentationClientDiagnostic.observerReplyMatches(setup,reply,"motion_observer_wide"));
        reply.addProperty("scene","motion_observer_wide");reply.addProperty("peerSwingEpoch",3);assertFalse(MeleeStrikePresentationClientDiagnostic.observerReplyMatches(setup,reply,"motion_observer_wide"));
    }

    @Test void expiredFlightEvidenceRecordDoesNotRequireRetainedCue() {
        var record=MeleeStrikePresentationClientDiagnostic.flightEvidenceRecord(null,"receiver","item",vector(0,0,0),vector(1,0,0),99);
        assertFalse(record.get("active").getAsBoolean());assertEquals(-1,record.get("ageNanos").getAsLong());
    }

    private static JsonObject vector(double x, double y, double z) {
        JsonObject value = new JsonObject(); value.addProperty("x", x); value.addProperty("y", y); value.addProperty("z", z); return value;
    }
}
