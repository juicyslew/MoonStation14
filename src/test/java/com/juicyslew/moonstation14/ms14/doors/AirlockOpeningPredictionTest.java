package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AirlockOpeningPredictionTest {
    private static AirlockData resolved() throws Exception {
        var raw=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
        for(String name:new String[]{"base_airlock","standard_airlock"})try(var in=AirlockOpeningPredictionTest.class.getResourceAsStream(
                "/data/moonstation14/moonstation14/airlock/"+name+".json")){
            raw.put(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14",name),
                    com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
        }
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks.TYPE);
        manager.reload(com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks.TYPE,raw);
        return manager.snapshot(com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks.TYPE)
                .get(com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks.STANDARD);
    }
    private static AirlockBlockEntity.ClientView view(UUID id,long rev,AirlockState.DoorPhase phase,
            boolean powered,boolean bolts,boolean passable) {
        return new AirlockBlockEntity.ClientView(id,rev,phase,0,0,0,passable,powered,bolts,true,false,false);
    }
    private static AirlockData data(boolean bump,double first,double second) {
        return new AirlockData(new AirlockData.Door(first,second,.4,.2,.45,15,2,bump),
                new AirlockData.Airlock(true,true,5,9,3,false),new AirlockData.DoorBolt(true),
                new AirlockData.ApcPowerReceiver(20),new AirlockData.ExtensionCableReceiver(3),
                new AirlockData.WiresPanel(com.juicyslew.moonstation14.ms14.tools.ToolProfile.Quality.SCREWING,1,
                        "moonstation14:airlock/panel_open","moonstation14:airlock/panel_close"),
                new AirlockData.WiresPanelSecurity(true),new AirlockData.Wires("standard_airlock",java.util.Set.of(),30,15,.01,.5,30,30),
                com.juicyslew.moonstation14.ms14.doors.prototype.ElectrifiedData.AIRLOCK_DEFAULTS);
    }
    @Test void predictionUsesResolvedDurationsAndReconcilesWithoutRetime() {
        UUID id=UUID.randomUUID();var p=new AirlockOpeningPrediction();var d=data(true,.4,.2);
        var auth=view(id,4,AirlockState.DoorPhase.CLOSED,true,false,false);p.accept(auth);
        assertTrue(p.start(100,d));assertFalse(p.effective().passable());
        for(long tick=101;tick<108;tick++){assertFalse(p.start(tick,d),"held contact cannot restart opening");p.tick(tick,d);}
        assertEquals(108,p.effective().transitionAt());assertFalse(p.effective().passable());
        p.accept(view(id,5,AirlockState.DoorPhase.OPENING,true,false,false));
        p.tick(108,d);assertTrue(p.effective().passable());
        assertEquals(108,p.effective().transitionStartedAt());
        assertEquals(.5,com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.openness(AirlockState.DoorPhase.OPENING,.2,.4,.2,.4,.2));
        assertEquals(1,com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.openness(p.effective().phase(),0,.4,.2,.4,.2));
        p.tick(111,d);assertTrue(p.effective().passable());
        p.tick(112,d);assertEquals(AirlockState.DoorPhase.OPEN,p.effective().phase());
        p.accept(view(id,6,AirlockState.DoorPhase.OPEN,true,false,true));
        assertFalse(p.active());assertTrue(p.effective().passable());
    }
    @Test void resolvedPrototypeDurationsDriveTheOverlay() throws Exception {
        var data=resolved();var p=new AirlockOpeningPrediction();var v=view(UUID.randomUUID(),0,AirlockState.DoorPhase.CLOSED,true,false,false);
        p.accept(v);assertTrue(p.start(200,data));p.tick(207,data);assertFalse(p.effective().passable());
        p.tick(208,data);assertTrue(p.effective().passable());assertEquals(208,p.effective().transitionStartedAt());
        p.tick(212,data);
        assertEquals(AirlockState.DoorPhase.OPEN,p.effective().phase());
    }
    @Test void gatesAndRollbackAndStaleSnapshots() {
        UUID id=UUID.randomUUID();var p=new AirlockOpeningPrediction();var d=data(true,.4,.2);
        assertFalse(p.start(0,d));
        var closed=view(id,10,AirlockState.DoorPhase.CLOSED,true,false,false);p.accept(closed);
        assertFalse(p.start(0,data(false,.4,.2)));
        p.accept(view(id,11,AirlockState.DoorPhase.CLOSED,false,false,false));assertFalse(p.start(0,d));
        p.accept(view(id,12,AirlockState.DoorPhase.CLOSED,true,true,false));assertFalse(p.start(0,d));
        p.accept(view(id,13,AirlockState.DoorPhase.CLOSED,true,false,false));assertTrue(p.start(0,d));
        p.accept(view(id,9,AirlockState.DoorPhase.CLOSED,false,true,false));assertTrue(p.active());
        p.accept(view(id,13,AirlockState.DoorPhase.DENYING,true,false,false));assertTrue(p.active());
        p.accept(view(id,14,AirlockState.DoorPhase.DENYING,true,false,false));assertFalse(p.active());
        p.accept(view(UUID.randomUUID(),0,AirlockState.DoorPhase.CLOSED,true,false,false));assertFalse(p.active());
    }
    @Test void boundedExpiryAndExplicitClear() {
        UUID id=UUID.randomUUID();var p=new AirlockOpeningPrediction();var v=view(id,0,AirlockState.DoorPhase.CLOSED,true,false,false);
        p.accept(v);assertTrue(p.start(20,data(true,.4,.2)));p.tick(52,data(true,.4,.2));assertFalse(p.active());
        assertTrue(p.start(60,data(true,.4,.2)));p.clear();assertFalse(p.active());assertEquals(v,p.authoritative());
        var fractional=new AirlockOpeningPrediction();fractional.accept(v);assertTrue(fractional.start(90,data(true,.06,.06)));
        fractional.tick(113,data(true,.06,.06));assertTrue(fractional.active());
        fractional.tick(114,data(true,.06,.06));assertFalse(fractional.active());
    }
    @Test void liveClientCollisionShapePolicyConsumesEffectiveViewForBothHalves() {
        UUID id=UUID.randomUUID();
        var lowerPos=new net.minecraft.core.BlockPos(4,10,2);var upperPos=lowerPos.above();
        for(boolean upper:new boolean[]{false,true}) {
            var queryPos=upper?upperPos:lowerPos;
            assertEquals(lowerPos,AirlockBlock.controllerPos(upper,queryPos));
            var effective=view(id,0,AirlockState.DoorPhase.OPENING,true,false,false);
            assertFalse(AirlockBlock.selectClientCollisionShape(false,effective).isEmpty(),"both halves use root opening phase");
            assertTrue(AirlockBlock.selectClientCollisionShape(false,effective,true).isEmpty(),"known crush exemption overrides closed collision");
            assertFalse(AirlockBlock.selectClientCollisionShape(false,effective,false).isEmpty(),"non-exempt body remains blocked");
            effective=view(id,1,AirlockState.DoorPhase.OPENING_PASSABLE,true,false,true);
            assertTrue(AirlockBlock.selectClientCollisionShape(false,effective).isEmpty(),"both halves use root passability");
        }
        assertTrue(AirlockBlock.selectClientCollisionShape(true,null).isEmpty(),"unknown controller retains replicated OPEN fallback");
        assertFalse(AirlockBlock.selectClientCollisionShape(false,null).isEmpty());
    }
    @Test void stalePacketCannotReplaceRevisionCoupledVictimSnapshot() {
        UUID id=UUID.randomUUID(),oldVictim=UUID.randomUUID(),currentVictim=UUID.randomUUID();
        var p=new AirlockOpeningPrediction();
        assertTrue(p.accept(view(id,20,AirlockState.DoorPhase.CLOSING_PASSABLE,true,false,true),java.util.Set.of(currentVictim)));
        assertFalse(p.accept(view(id,19,AirlockState.DoorPhase.CLOSING,true,false,false),java.util.Set.of(oldVictim)));
        assertFalse(p.accept(view(id,20,AirlockState.DoorPhase.CLOSING,true,false,false),java.util.Set.of(oldVictim)));
        assertEquals(java.util.Set.of(currentVictim),p.authoritativeCrushVictims());
        assertTrue(p.accept(view(id,21,AirlockState.DoorPhase.CLOSED,true,false,false),java.util.Set.of()));
        assertTrue(p.authoritativeCrushVictims().isEmpty());
        UUID replacement=UUID.randomUUID();
        assertTrue(p.accept(view(replacement,0,AirlockState.DoorPhase.CLOSED,true,false,false),java.util.Set.of(oldVictim)));
        assertEquals(java.util.Set.of(oldVictim),p.authoritativeCrushVictims());
    }
}
