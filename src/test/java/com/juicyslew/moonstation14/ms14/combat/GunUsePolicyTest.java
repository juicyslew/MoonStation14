package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunUsePolicyTest {
    @Test void postCommitOutputGuardRequiresCommittedHandsRevisionAndExactStack() {
        var oldHands = org.mockito.Mockito.mock(com.juicyslew.moonstation14.ms14.hands.live.LiveHands.class);
        var committedHands = org.mockito.Mockito.mock(com.juicyslew.moonstation14.ms14.hands.live.LiveHands.class);
        var expected = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var wrong = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        wrong.setCount(2);
        assertFalse(GunUseSystem.postCommitMatches(oldHands, committedHands, 2, 2, expected, expected));
        assertTrue(GunUseSystem.postCommitMatches(committedHands, committedHands, 2, 2, expected, expected));
        assertFalse(GunUseSystem.postCommitMatches(committedHands, committedHands, 1, 2, expected, expected));
        assertFalse(GunUseSystem.postCommitMatches(committedHands, committedHands, 2, 2, expected, wrong));
    }

    @Test void operationAdmissionRecognizesCycleableBallisticAndMultimodeBatteryProfiles() {
        var gun = new GunProfile.GunData(1, 1, true, "SEMI_AUTO", java.util.Optional.empty(), java.util.Optional.empty());
        var ballistic = new GunProfile(gun, java.util.Optional.empty(), false, false, java.util.Optional.empty(),
                java.util.Optional.of(new GunProfile.BallisticProviderData(5,
                net.minecraft.resources.ResourceLocation.parse("test:round_tag"), net.minecraft.resources.ResourceLocation.parse("test:round"), true)),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), false, 1f,
                java.util.Optional.empty(), java.util.Optional.empty());
        assertEquals(GunUsePolicy.Operation.BALLISTIC, GunUsePolicy.operation(ballistic));
    }
    @Test void sourceUseDelayRequiresComponentAndHonorsDeadline() {
        long deadline = GunUsePolicy.deadline(10, .66f);
        assertEquals(24, deadline);
        assertFalse(GunUsePolicy.canCycle(true, 23, deadline));
        assertTrue(GunUsePolicy.canCycle(true, 24, deadline));
        assertFalse(GunUsePolicy.canCycle(false, Long.MAX_VALUE, 0));
    }
    @Test void fireAndCycleMutationsPreserveIndependentDeadlines() {
        GunRuntimeState initial = new GunRuntimeState(40, 35, 2f, 17);
        assertEquals(17, initial.withFire(50, 50, 3f).nextUseTick());
        GunRuntimeState fired = initial.withFire(50, 50, 3f);
        assertEquals(50, fired.withUseDeadline(31).nextFireTick());
        assertEquals(50, fired.withUseDeadline(31).lastFireTick());
        assertEquals(3f, fired.withUseDeadline(31).currentSpreadDegrees());
        GunProfile profile = new GunProfile(new GunProfile.GunData(1, 1, true, "SEMI_AUTO", java.util.Optional.empty(), java.util.Optional.of(.66f)),
                java.util.Optional.of(new GunProfile.RevolverAmmoProviderData(3, net.minecraft.resources.ResourceLocation.parse("test:round"), java.util.Set.of(), java.util.Set.of())));
        RevolverState rounds = RevolverProviderKernel.initialize(profile);
        assertEquals(1, RevolverProviderKernel.cycle(rounds).index());
        assertEquals(rounds.chambers(), RevolverProviderKernel.cycle(rounds).chambers());
    }

    @Test void fireModeControlCyclesCanonicallyAndPreservesCadenceAndBatteryState() {
        var gun = new GunProfile.GunData(1, 1, true, "SEMI_AUTO", java.util.Optional.empty(),
                java.util.Optional.empty(), GunProfile.SpreadData.SOURCE_DEFAULTS, .25f,
                java.util.Optional.empty(), 1f, java.util.Set.of("SEMI_AUTO", "FULL_AUTO"));
        var profile = new GunProfile(gun, java.util.Optional.empty());
        var state = new GunRuntimeState(77, 70, 2f, 33, null, 4);
        var cycled = GunModePolicy.cycleFireMode(state, profile);
        assertEquals("FULL_AUTO", cycled.selectedFireMode());
        assertEquals(77, cycled.nextFireTick());
        assertEquals(70, cycled.lastFireTick());
        assertEquals(2f, cycled.currentSpreadDegrees());
        assertEquals(33, cycled.nextUseTick());
        assertEquals(4, cycled.batteryModeIndex());
        assertEquals("SEMI_AUTO", GunModePolicy.cycleFireMode(cycled, profile).selectedFireMode());
    }
}
