package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TegFixtureCommandsTest {
    @Test void publicContinuousCommandSurfaceHasOnlyBuildStartStatusAndFiniteBranch() {
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        TegFixtureCommands.register(dispatcher);
        var continuous=dispatcher.getRoot().getChild("ms14").getChild("teg_fixture");
        assertEquals(java.util.Set.of("build","start","status","finite"),continuous.getChildren().stream().map(com.mojang.brigadier.tree.CommandNode::getName).collect(java.util.stream.Collectors.toSet()));
        var finite=continuous.getChild("finite");
        assertEquals(java.util.Set.of("build","status"),finite.getChildren().stream().map(com.mojang.brigadier.tree.CommandNode::getName).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void automaticPowerWaitClassifierAcceptsOnlyExplicitTransientAdmissionResults() {
        assertTrue(TegAutomaticStartupPolicy.retryablePowerWait("Operating startup waiting for power topology: controls configured"));
        assertTrue(TegAutomaticStartupPolicy.retryablePowerWait("Operating startup refused before controls: power topology is not ready."));
        assertFalse(TegAutomaticStartupPolicy.retryablePowerWait("Operating startup refused before controls: APC receiver topology is unknown or not on the operating APC at BlockPos{x=1,y=2,z=3} (receiver-topology-unknown)."));
        assertFalse(TegAutomaticStartupPolicy.retryablePowerWait("Operating startup refused before controls: APC receiver topology is unknown or not on the operating APC (provider-missing)."));
        assertFalse(TegAutomaticStartupPolicy.retryablePowerWait("Operating startup refused before controls: power topology is not ready; receiver missing."));
        assertFalse(TegAutomaticStartupPolicy.retryablePowerWait("Operating startup refused: hard failure mentions power topology"));
        assertFalse(TegAutomaticStartupPolicy.retryablePowerWait("Operating controls not confirmed; power topology failed"));
    }
    @Test void shutdownAcceptsOnlyLiveMinerWithSavedPlacementIdentity() {
        var saved=UUID.randomUUID();
        assertTrue(TegContinuousFixtureService.matchingPassiveMiner(saved,saved,true));
        assertFalse(TegContinuousFixtureService.matchingPassiveMiner(saved,UUID.randomUUID(),true));
        assertFalse(TegContinuousFixtureService.matchingPassiveMiner(saved,saved,false));
        assertFalse(TegContinuousFixtureService.matchingPassiveMiner(null,saved,true));
    }
    @Test void nearestSavedSelectionIsDimensionLocalInputAndIgnoresLegacyFixtureMode() {
        var nearbyLegacy = entry(new BlockPos(1, 64, 1), TegFixtureSavedData.Mode.FINITE_CHARGE);
        var farFueled = entry(new BlockPos(20, 64, 0), TegFixtureSavedData.Mode.FUELED);
        var nearestFueled = entry(new BlockPos(2, 64, 0), TegFixtureSavedData.Mode.FUELED);
        assertEquals(nearestFueled.origin(), TegFixtureCommands.nearestFueledOrigin(List.of(nearbyLegacy, farFueled, nearestFueled), BlockPos.ZERO));
        assertNull(TegFixtureCommands.nearestFueledOrigin(List.of(nearbyLegacy), BlockPos.ZERO));
    }

    @Test void continuousOperatorSelectionIgnoresNearbyFiniteDemoAndPhaseStartsBuilt() {
        var finite=entry(new BlockPos(1,64,1),TegFixtureSavedData.Mode.FUELED);
        var continuous=entry(new BlockPos(4,64,4),TegFixtureSavedData.Mode.CONTINUOUS);
        assertEquals(continuous.origin(),TegFixtureCommands.nearestContinuousOrigin(List.of(finite,continuous),BlockPos.ZERO));
        assertEquals(TegFixtureSavedData.ContinuousPhase.BUILT,continuous.phase());
    }

    private static TegFixtureSavedData.Entry entry(BlockPos origin, TegFixtureSavedData.Mode mode) {
        return new TegFixtureSavedData.Entry(origin, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, mode, Map.of(), Map.of());
    }
}
