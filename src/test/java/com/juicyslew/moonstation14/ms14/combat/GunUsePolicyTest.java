package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunUsePolicyTest {
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
}
