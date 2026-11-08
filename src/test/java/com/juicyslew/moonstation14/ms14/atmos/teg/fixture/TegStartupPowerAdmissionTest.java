package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TegStartupPowerAdmissionTest {
    private record Setup(ServerLevel level,TegFixtureSavedData data,TegFixtureSavedData.Entry entry,
                         TegContinuousFixtureService.ObservedHardware observed) { }

    private Setup setup() {
        var level=mock(ServerLevel.class);var data=mock(TegFixtureSavedData.class);var pos=BlockPos.ZERO;
        var entry=new TegFixtureSavedData.Entry(pos,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),false,TegFixtureSavedData.Mode.CONTINUOUS);
        when(data.entry(pos)).thenReturn(Optional.of(entry));
        return new Setup(level,data,entry,mock(TegContinuousFixtureService.ObservedHardware.class));
    }

    private String attempt(Setup s,TegContinuousFixtureService.PowerAdmission admission,boolean ready) {
        try(var saved=mockStatic(TegFixtureSavedData.class);var service=mockStatic(TegContinuousFixtureService.class, CALLS_REAL_METHODS)) {
            saved.when(()->TegFixtureSavedData.existing(s.level())).thenReturn(Optional.of(s.data()));
            when(s.data().entry(s.entry().origin())).thenReturn(Optional.of(s.entry()));
            service.when(()->TegContinuousFixtureService.observeOwnedHardware(s.level(),s.entry())).thenReturn(s.observed());
            service.when(()->TegContinuousFixtureService.startupPowerAdmission(s.level(),s.observed())).thenReturn(admission);
            if(ready) {
                service.when(()->TegContinuousFixtureService.bindOperatingSettings(s.level(),s.observed(),TegContinuousOperatingPlan.Stage.STARTUP))
                        .thenReturn(new TegContinuousFixtureService.OperatingSettings(java.util.List.of(),java.util.List.of()));
                service.when(()->TegContinuousFixtureService.initializeResources(s.level(),s.entry().origin()))
                        .thenReturn("Startup resources admitted once: test delegate");
            }
            service.clearInvocations();
            String result=TegContinuousFixtureService.initializeOperating(s.level(),s.entry().origin());
            if(ready) {
                service.verify(()->TegContinuousFixtureService.bindOperatingSettings(s.level(),s.observed(),TegContinuousOperatingPlan.Stage.STARTUP),times(1));
                service.verify(()->TegContinuousFixtureService.initializeResources(s.level(),s.entry().origin()),times(1));
            } else {
                service.verify(()->TegContinuousFixtureService.bindOperatingSettings(s.level(),s.observed(),TegContinuousOperatingPlan.Stage.STARTUP),never());
                service.verify(()->TegContinuousFixtureService.initializeResources(s.level(),s.entry().origin()),never());
            }
            return result;
        }
    }

    @Test void waitAndRefusalAtRealEntryDoNotBindOrConsumeToken() {
        for(var status:new TegContinuousFixtureService.PowerAdmissionStatus[]{TegContinuousFixtureService.PowerAdmissionStatus.WAIT,TegContinuousFixtureService.PowerAdmissionStatus.REFUSED}) {
            var s=setup();var result=attempt(s,new TegContinuousFixtureService.PowerAdmission(status,"receiver-topology-unknown"),false);
            if(status==TegContinuousFixtureService.PowerAdmissionStatus.WAIT) {
                assertTrue(result.startsWith(TegAutomaticStartupPolicy.POWER_TOPOLOGY_WAIT_PREFIX));
                assertTrue(TegAutomaticStartupPolicy.retryablePowerWait(result));
                assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(TegFixtureSavedData.AutomaticStartupStatus.ACTIVE,
                        TegFixtureSavedData.ContinuousPhase.BUILT,false,2400,0,-1).attempt());
            } else { assertTrue(result.contains("refused before controls"));assertFalse(TegAutomaticStartupPolicy.retryablePowerWait(result)); }
            assertFalse(s.entry().initialized());
            verify(s.data(),never()).markInitialized(any(),anyLong());
        }
    }

    @Test void readyAtRealEntryDelegatesResourceInitializationOnce() {
        var s=setup();var result=attempt(s,new TegContinuousFixtureService.PowerAdmission(TegContinuousFixtureService.PowerAdmissionStatus.READY,""),true);
        assertTrue(result.startsWith("Operating startup admitted once:"));
        verify(s.data(),never()).markInitialized(any(),anyLong());
    }

    @Test void repeatedWaitPreservesEntryAndOriginalArmDeadline() {
        var s=setup();var wait=new TegContinuousFixtureService.PowerAdmission(TegContinuousFixtureService.PowerAdmissionStatus.WAIT,"receiver-topology-unknown");
        assertTrue(TegAutomaticStartupPolicy.retryablePowerWait(attempt(s,wait,false)));
        assertTrue(TegAutomaticStartupPolicy.retryablePowerWait(attempt(s,wait,false)));
        assertEquals(TegAutomaticStartupPolicy.Attempt.START,TegAutomaticStartupPolicy.decide(TegFixtureSavedData.AutomaticStartupStatus.WAITING_START,TegFixtureSavedData.ContinuousPhase.BUILT,false,2399,0,-1).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(TegFixtureSavedData.AutomaticStartupStatus.WAITING_START,TegFixtureSavedData.ContinuousPhase.BUILT,false,2400,0,-1).attempt());
        assertFalse(s.entry().initialized());verify(s.data(),never()).markInitialized(any(),anyLong());verify(s.data(),never()).armAutomaticStartup(any(),any(),anyLong());
    }

    @Test void canonicalReceiverObservationsClassifyAtTypedBoundary() {
        var expected=new BlockPos(1,2,3);var receiver=new BlockPos(4,5,6);long generation=7;
        var ready=new com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ApcConnectionObservation(true,expected,generation,"");
        assertEquals(TegContinuousFixtureService.PowerAdmissionStatus.READY,TegContinuousFixtureService.classifyReceiverAdmission(ready,expected,generation,true,receiver).status());
        for(String reason:new String[]{"receiver-topology-unknown","topology-unready","topology-changed-or-unready"})
            assertEquals(TegContinuousFixtureService.PowerAdmissionStatus.WAIT,TegContinuousFixtureService.classifyReceiverAdmission(new com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ApcConnectionObservation(false,null,generation,reason),expected,generation,true,receiver).status());
        assertEquals(TegContinuousFixtureService.PowerAdmissionStatus.WAIT,TegContinuousFixtureService.classifyReceiverAdmission(ready,expected,generation+1,true,receiver).status());
        assertEquals(TegContinuousFixtureService.PowerAdmissionStatus.REFUSED,TegContinuousFixtureService.classifyReceiverAdmission(new com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ApcConnectionObservation(true,new BlockPos(9,9,9),generation,""),expected,generation,true,receiver).status());
        for(String reason:new String[]{"provider-missing","provider-ambiguous","breaker-open","unsupported-profile","invalid-port-count"})
            assertEquals(TegContinuousFixtureService.PowerAdmissionStatus.REFUSED,TegContinuousFixtureService.classifyReceiverAdmission(new com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ApcConnectionObservation(false,null,generation,reason),expected,generation,true,receiver).status());
    }

    @Test void admissionRequiresNonforcingFullChunkLookup() {
        var level=mock(ServerLevel.class);var cache=mock(ServerChunkCache.class);var pos=new BlockPos(32,64,48);
        when(level.getChunkSource()).thenReturn(cache);when(cache.getChunk(2,3,ChunkStatus.FULL,false)).thenReturn(null);
        assertFalse(TegContinuousFixtureService.loadedReceiver(level,pos));
        var full=mock(LevelChunk.class);when(cache.getChunk(2,3,ChunkStatus.FULL,false)).thenReturn(full);
        assertTrue(TegContinuousFixtureService.loadedReceiver(level,pos));
        verify(cache,times(2)).getChunk(2,3,ChunkStatus.FULL,false);verify(cache,never()).getChunkNow(anyInt(),anyInt());
        verify(cache,never()).getChunk(anyInt(),anyInt(),any(ChunkStatus.class),eq(true));
    }
}
