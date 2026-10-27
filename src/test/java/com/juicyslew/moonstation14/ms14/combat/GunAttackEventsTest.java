package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GunAttackEventsTest {
    private GunAttackEvents.OwnedGunBinding binding() {
        var selection = new GunWeaponResolver.Selection(UUID.randomUUID(), 3, "left", "token", null);
        return new GunAttackEvents.OwnedGunBinding(new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD, "left", 3, "token"), selection);
    }
    @Test void attemptedEventsCarryActualOwnerBindingAndIndependentScopeCancellation() {
        var owned = binding();
        LivingEntity user = mock(LivingEntity.class);
        var gun = new GunAttackEvents.ShotAttemptedEvent(user, owned, GunAttackEvents.Scope.GUN);
        var owner = new GunAttackEvents.ShotAttemptedEvent(user, owned, GunAttackEvents.Scope.USER);
        assertSame(user, gun.user()); assertSame(owned, gun.gun()); assertEquals(GunAttackEvents.Scope.GUN, gun.scope());
        assertEquals(GunAttackEvents.Scope.USER, owner.scope());
        gun.setCanceled(true);
        assertTrue(gun.isCanceled()); assertFalse(owner.isCanceled());
        var attempt = new GunAttackEvents.AttemptShootEvent(user, owned, GunAttackEvents.Scope.GUN, 42);
        attempt.setCanceled(true);
        assertEquals(42, attempt.nextFireTick()); assertTrue(attempt.isCanceled());
        assertEquals("token", owned.binding().token()); assertEquals(3, owned.binding().revision());
    }
    @Test void productionDispatchShortCircuitsCanceledGunAndPostAttemptIsGunOnly() {
        var owned = binding();
        LivingEntity user = mock(LivingEntity.class);
        var preScopes = new ArrayList<GunAttackEvents.Scope>();
        assertFalse(GunAttackEvents.dispatchShotAttempted(user, owned, event -> {
            preScopes.add(event.scope());
            event.setCanceled(true);
        }));
        assertEquals(java.util.List.of(GunAttackEvents.Scope.GUN), preScopes);
        var postScopes = new ArrayList<GunAttackEvents.Scope>();
        assertFalse(GunAttackEvents.dispatchAttemptShoot(user, owned, 27, event -> {
            postScopes.add(event.scope());
            assertEquals(27, event.nextFireTick());
            event.setCanceled(true);
        }));
        assertEquals(java.util.List.of(GunAttackEvents.Scope.GUN), postScopes);
        preScopes.clear();
        assertTrue(GunAttackEvents.dispatchShotAttempted(user, owned, event -> preScopes.add(event.scope())));
        assertEquals(java.util.List.of(GunAttackEvents.Scope.GUN, GunAttackEvents.Scope.USER), preScopes);
    }
    @Test void profileUsesSourceBurstAndEmptySoundDefaults() {
        var gun = new GunProfile.GunData(2, 40, true, "SEMI_AUTO", java.util.Optional.empty(), java.util.Optional.empty());
        assertEquals(.25f, gun.burstCooldownSeconds());
        assertEquals(GunProfile.GunData.SOURCE_EMPTY_SOUND, gun.emptySound().orElseThrow());
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/guns/empty"), gun.emptySound().orElseThrow().event());
    }
}
