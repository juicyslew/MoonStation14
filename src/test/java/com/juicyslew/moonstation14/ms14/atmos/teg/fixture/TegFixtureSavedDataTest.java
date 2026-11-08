package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class TegFixtureSavedDataTest {
    private static TegFixtureSavedData.Entry entry(BlockPos pos,TegFixtureSavedData.Mode mode) {
        return new TegFixtureSavedData.Entry(pos,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),false,mode);
    }
    @Test void initializationRefusalDistinguishesModeFromConsumedFiniteToken() {
        var finite=entry(new BlockPos(1,2,3),TegFixtureSavedData.Mode.FINITE_CHARGE);
        assertNull(TegFixtureService.initializationRefusal(finite));
        var initializedFinite=new TegFixtureSavedData.Entry(finite.origin(),finite.core(),finite.north(),finite.south(),finite.token(),true,finite.mode());
        assertTrue(TegFixtureService.initializationRefusal(initializedFinite).contains("already initialized"));
        var fueled=entry(new BlockPos(4,5,6),TegFixtureSavedData.Mode.FUELED);
        assertTrue(TegFixtureService.initializationRefusal(fueled).contains("fueled fixture"));
        var initializedFueled=new TegFixtureSavedData.Entry(fueled.origin(),fueled.core(),fueled.north(),fueled.south(),fueled.token(),true,fueled.mode());
        assertTrue(TegFixtureService.initializationRefusal(initializedFueled).contains("fueled fixture"));
        assertFalse(TegFixtureService.finiteChargeInitializationAllowed(initializedFinite));
        assertFalse(TegFixtureService.finiteChargeInitializationAllowed(initializedFueled));
    }
    @Test void legacyDefaultsToFiniteAndFueledModeRoundTripsWithoutOverwrite() {
        var data=new TegFixtureSavedData();var legacy=entry(new BlockPos(1,2,3),TegFixtureSavedData.Mode.FINITE_CHARGE);
        assertTrue(data.recordBuilt(legacy));
        var encoded=data.save(new CompoundTag(),null);var list=(net.minecraft.nbt.ListTag)encoded.get("fixtures");
        ((CompoundTag)list.get(0)).remove("mode");
        assertEquals(TegFixtureSavedData.Mode.FINITE_CHARGE,TegFixtureSavedData.load(encoded,null).entry(legacy.origin()).orElseThrow().mode());
        var key=new com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey(new BlockPos(8,9,10),com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY,"outlet");
        var gas=Map.of(key,UUID.randomUUID());var machines=Map.of(new BlockPos(7,8,9),UUID.randomUUID());
        var base=entry(new BlockPos(4,5,6),TegFixtureSavedData.Mode.FUELED);var fueled=new TegFixtureSavedData.Entry(base.origin(),base.core(),base.north(),base.south(),base.token(),false,base.mode(),gas,machines);assertTrue(data.recordBuilt(fueled));
        var before=data.entry(fueled.origin()).orElseThrow();assertFalse(data.recordBuilt(entry(fueled.origin(),TegFixtureSavedData.Mode.FINITE_CHARGE)));
        assertEquals(before,data.entry(fueled.origin()).orElseThrow());
        assertTrue(before.matchesGasOwners(gas));assertFalse(before.matchesGasOwners(Map.of(key,UUID.randomUUID())));
        assertEquals(before,TegFixtureSavedData.load(data.save(new CompoundTag(),null),null).entry(fueled.origin()).orElseThrow());
        assertFalse(TegFixtureService.finiteChargeInitializationAllowed(before));
        assertTrue(TegFixtureService.finiteChargeInitializationAllowed(legacy));
    }
    @Test void fueledInitializationRetainsFingerprintAndRequiresExactCurrentEntry() {
        var key=new com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey(new BlockPos(8,9,10),com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY,"outlet");
        var gas=new HashMap<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey,UUID>();UUID gasId=UUID.randomUUID();gas.put(key,gasId);
        var machinePos=new BlockPos.MutableBlockPos(7,8,9);UUID machineId=UUID.randomUUID();var machines=new HashMap<BlockPos,UUID>();machines.put(machinePos,machineId);
        var base=entry(new BlockPos(4,5,6),TegFixtureSavedData.Mode.FUELED);var candidate=new TegFixtureSavedData.Entry(base.origin(),base.core(),base.north(),base.south(),base.token(),false,base.mode(),gas,machines);
        gas.clear();machines.clear();machinePos.set(100,101,102);
        assertEquals(Map.of(key,gasId),candidate.ownedGas());assertEquals(Map.of(new BlockPos(7,8,9),machineId),candidate.ownedMachines());
        assertThrows(UnsupportedOperationException.class,()->candidate.ownedGas().clear());assertThrows(UnsupportedOperationException.class,()->candidate.ownedMachines().clear());
        var data=new TegFixtureSavedData();assertTrue(data.recordBuilt(candidate));
        var staleToken=new TegFixtureSavedData.Entry(candidate.origin(),candidate.core(),candidate.north(),candidate.south(),UUID.randomUUID(),false,candidate.mode(),candidate.ownedGas(),candidate.ownedMachines());
        var staleMap=new TegFixtureSavedData.Entry(candidate.origin(),candidate.core(),candidate.north(),candidate.south(),candidate.token(),false,candidate.mode(),Map.of(key,UUID.randomUUID()),candidate.ownedMachines());
        var unchanged=data.entry(candidate.origin()).orElseThrow();assertFalse(data.markInitialized(staleToken));assertFalse(data.markInitialized(staleMap));assertEquals(unchanged,data.entry(candidate.origin()).orElseThrow());
        assertTrue(data.markInitialized(candidate));var initialized=data.entry(candidate.origin()).orElseThrow();
        assertTrue(initialized.initialized());assertEquals(candidate.core(),initialized.core());assertEquals(candidate.north(),initialized.north());assertEquals(candidate.south(),initialized.south());assertEquals(candidate.token(),initialized.token());assertEquals(candidate.mode(),initialized.mode());assertEquals(candidate.ownedGas(),initialized.ownedGas());assertEquals(candidate.ownedMachines(),initialized.ownedMachines());
        assertFalse(data.markInitialized(candidate));assertFalse(data.markInitialized(initialized));assertEquals(initialized,data.entry(candidate.origin()).orElseThrow());
    }
    @Test void continuousModeAcceptsSamePersistedGasAndMachineOwnershipContract() {
        var key=new com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey(new BlockPos(18,20,22),com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY,"outlet");
        var base=entry(new BlockPos(4,5,6),TegFixtureSavedData.Mode.CONTINUOUS);
         BlockPos auxiliaryApc=new BlockPos(9,8,7);UUID auxiliaryIdentity=UUID.randomUUID();
         var continuous=new TegFixtureSavedData.Entry(base.origin(),base.core(),base.north(),base.south(),base.token(),false,base.mode(),Map.of(key,UUID.randomUUID()),Map.of(auxiliaryApc,auxiliaryIdentity));
        var data=new TegFixtureSavedData();assertTrue(data.recordBuilt(continuous));
        var loaded=TegFixtureSavedData.load(data.save(new CompoundTag(),null),null).entry(continuous.origin()).orElseThrow();
         assertEquals(TegFixtureSavedData.Mode.CONTINUOUS,loaded.mode());assertEquals(continuous.ownedGas(),loaded.ownedGas());assertEquals(continuous.ownedMachines(),loaded.ownedMachines());assertEquals(auxiliaryIdentity,loaded.ownedMachines().get(auxiliaryApc),"dedicated APC identity is part of the same saved fixture ownership token");
        assertTrue(data.markInitialized(continuous));assertTrue(data.entry(continuous.origin()).orElseThrow().initialized());
    }
    @Test void continuousOperatorPhaseAndFreshPowerFloorPersistAndRejectStaleTransitions() {
        var built=entry(new BlockPos(14,15,16),TegFixtureSavedData.Mode.CONTINUOUS);
        var data=new TegFixtureSavedData();assertTrue(data.recordBuilt(built));
        assertEquals(TegFixtureSavedData.ContinuousPhase.BUILT,built.phase());
        assertTrue(data.markInitialized(built));var startup=data.entry(built.origin()).orElseThrow();
        assertEquals(TegFixtureSavedData.ContinuousPhase.STARTUP,startup.phase());
        assertTrue(data.transition(startup,TegFixtureSavedData.ContinuousPhase.STARTUP,TegFixtureSavedData.ContinuousPhase.IGNITION,100,0.25));
        var ignition=data.entry(built.origin()).orElseThrow();
        assertTrue(data.transition(ignition,TegFixtureSavedData.ContinuousPhase.IGNITION,TegFixtureSavedData.ContinuousPhase.RUNNING,100,0.3,77));
        var running=data.entry(built.origin()).orElseThrow();
        assertEquals(100,running.phaseStartedTick());assertEquals(.3,running.phaseCo2Baseline());assertEquals(77,running.phasePowerSampleFloor());
        assertFalse(data.transition(ignition,TegFixtureSavedData.ContinuousPhase.IGNITION,TegFixtureSavedData.ContinuousPhase.RUNNING,100,.3,77));
        assertEquals(running,TegFixtureSavedData.load(data.save(new CompoundTag(),null),null).entry(built.origin()).orElseThrow());
    }
    @Test void continuousAdmissionTickIsPersistedAndCannotBeReseeded() {
        var built=entry(new BlockPos(21,22,23),TegFixtureSavedData.Mode.CONTINUOUS);
        var data=new TegFixtureSavedData();assertTrue(data.recordBuilt(built));
        assertTrue(data.markInitialized(built,2400));
        var admitted=data.entry(built.origin()).orElseThrow();
        assertTrue(admitted.initialized());assertEquals(2400,admitted.phaseStartedTick());
        assertEquals(admitted,TegFixtureSavedData.load(data.save(new CompoundTag(),null),null).entry(built.origin()).orElseThrow());
        assertFalse(data.markInitialized(built,2401));
        assertEquals(2400,data.entry(built.origin()).orElseThrow().phaseStartedTick());
    }
    @Test void automaticStartupIsContinuousOnlyOneShotAndRoundTripsWithoutCreatingFixtures() {
        var data=new TegFixtureSavedData();var continuous=entry(new BlockPos(20,21,22),TegFixtureSavedData.Mode.CONTINUOUS);
        assertTrue(data.armAutomaticStartup(continuous,UUID.randomUUID(),15).isPresent());
        assertTrue(data.recordBuilt(continuous));UUID initiator=UUID.randomUUID();
        assertTrue(data.armAutomaticStartup(continuous,initiator,15).isEmpty());
        assertTrue(data.armAutomaticStartup(continuous,UUID.randomUUID(),16).isPresent());
        assertEquals(1,data.count());assertEquals(initiator,data.automaticStartup(continuous.origin()).orElseThrow().initiator());
        assertTrue(data.updateAutomaticStartup(continuous.origin(),TegFixtureSavedData.AutomaticStartupStatus.WAITING_START,TegFixtureSavedData.AutomaticStartupStatus.ACTIVE,""));
        assertTrue(data.updateAutomaticStartup(continuous.origin(),TegFixtureSavedData.AutomaticStartupStatus.ACTIVE,TegFixtureSavedData.AutomaticStartupStatus.FAILED,"START deadline expired"));
        assertFalse(data.updateAutomaticStartup(continuous.origin(),TegFixtureSavedData.AutomaticStartupStatus.FAILED,TegFixtureSavedData.AutomaticStartupStatus.ACTIVE,""));
        var loaded=TegFixtureSavedData.load(data.save(new CompoundTag(),null),null);
        assertEquals(data.automaticStartup(continuous.origin()),loaded.automaticStartup(continuous.origin()));assertEquals(1,loaded.count());
        var finite=entry(new BlockPos(23,24,25),TegFixtureSavedData.Mode.FINITE_CHARGE);assertTrue(data.recordBuilt(finite));
        assertTrue(data.armAutomaticStartup(finite,UUID.randomUUID(),17).isPresent());assertEquals(2,data.count());
    }
    @Test void automaticStartupPolicyUsesExclusiveDeadlinesAndOriginalPhaseAnchor() {
        var S=TegFixtureSavedData.AutomaticStartupStatus.ACTIVE;
        assertEquals(TegAutomaticStartupPolicy.Attempt.START,TegAutomaticStartupPolicy.decide(TegFixtureSavedData.AutomaticStartupStatus.WAITING_START,TegFixtureSavedData.ContinuousPhase.BUILT,false,100,100,-1).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.IGNITE,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.STARTUP,true,2499,100,-1).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.STARTUP,true,2500,100,-1).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.IGNITION,true,2499,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.RUN,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.IGNITION,true,699,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.IGNITION,true,700,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.RUN,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.IGNITION,true,2700,100,2600).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.IGNITION,true,900,100,-1).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.HEATER_OFF,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.RUNNING,true,699,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.RUNNING,true,700,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.RUNNING,true,99,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.BUILT,false,2500,100,-1).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.COMPLETE,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.HEATER_OFF,true,900,100,700).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(S,TegFixtureSavedData.ContinuousPhase.HEATER_OFF,true,901,100,700).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.WAIT,TegAutomaticStartupPolicy.decide(TegFixtureSavedData.AutomaticStartupStatus.COMPLETE,TegFixtureSavedData.ContinuousPhase.HEATER_OFF,true,900,100,700).attempt());
    }
    @Test void coldPreparationUsesAdmissionClockAndOriginalArmBudget() {
        var C=TegFixtureSavedData.AutomaticStartupStatus.PREPARING_COLD;
        var S=TegFixtureSavedData.ContinuousPhase.STARTUP;
        assertEquals(TegAutomaticStartupPolicy.Attempt.WAIT,TegAutomaticStartupPolicy.decide(C,S,true,299,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.START_HEATING,TegAutomaticStartupPolicy.decide(C,S,true,300,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.WAIT,TegAutomaticStartupPolicy.decide(C,S,true,2399,100,2300).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(C,S,true,2500,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(C,S,true,101,100,102).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(C,S,false,200,100,100).attempt());
        assertEquals(TegAutomaticStartupPolicy.Attempt.FAIL,TegAutomaticStartupPolicy.decide(C,TegFixtureSavedData.ContinuousPhase.BUILT,true,200,100,100).attempt());
    }
}
