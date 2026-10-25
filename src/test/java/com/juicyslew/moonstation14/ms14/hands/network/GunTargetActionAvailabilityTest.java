package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GunTargetActionAvailabilityTest {
    private static GunProfile profile() {
        return new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(6, ResourceLocation.parse("moonstation14:cartridge_magnum"), Set.of(), Set.of())));
    }
    @Test void liveQueryAvailabilityUsesPreviewStateAndOccupiedCountWithoutMutatingSourceState() {
        var gun = profile();
        var absent = BodyTargetActionService.gunAvailability(gun, null);
        assertEquals(List.of(BodyTargetActionSnapshot.ActionId.EMPTY_REVOLVER, BodyTargetActionSnapshot.ActionId.SPIN_REVOLVER), absent.stream().map(BodyTargetActionSnapshot.ActionEntry::id).toList());
        assertTrue(absent.get(0).enabled()); assertTrue(absent.get(1).enabled());
        var empty = BodyTargetActionService.gunAvailability(gun, RevolverState.empty(6));
        assertFalse(empty.get(0).enabled()); assertTrue(empty.get(1).enabled());
        var occupied = new RevolverState(0, List.of(RevolverState.Chamber.spentDeferred(ResourceLocation.parse("moonstation14:cartridge_magnum")),
                RevolverState.Chamber.empty(), RevolverState.Chamber.empty(), RevolverState.Chamber.empty(), RevolverState.Chamber.empty(), RevolverState.Chamber.empty()));
        var prior = occupied;
        var entries = BodyTargetActionService.gunAvailability(gun, occupied);
        assertTrue(entries.get(0).enabled()); assertTrue(entries.get(1).enabled()); assertSame(prior, occupied);
    }
}
