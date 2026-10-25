package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClientGunWeaponResolverTest {
    @Test void projectsOnlyMatchingConfirmedHeldPresentationAndSuppliedCatalog() {
        UUID body = UUID.randomUUID();
        var gun = new GunProfile(new GunProfile.GunData(1, 10, true, "SEMI_AUTO", Optional.empty(), Optional.empty()), Optional.empty());
        var id = BuiltInRegistries.ITEM.getKey(Items.STICK);
        var catalog = new PrototypeCatalog<>(Map.of(id, gun));
        var presentation = ItemPresentation.fromStack(Items.STICK.getDefaultInstance());
        var hand = new BodyHandStateSnapshot.Hand("right", "token", id.toString(), 1, false, null, null, 0,
                null, presentation, null);
        var snapshot = new BodyHandStateSnapshot(2, 1, BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), body,
                2, 4, "right", List.of(hand));
        var result = ClientGunWeaponResolver.project(body, 2, snapshot, catalog).orElseThrow();
        assertEquals(gun, result.profile());
        assertEquals("token", result.itemToken());
        assertTrue(ClientGunWeaponResolver.project(UUID.randomUUID(), 2, snapshot, catalog).isEmpty());
        assertTrue(ClientGunWeaponResolver.project(body, 3, snapshot, catalog).isEmpty());
        assertTrue(ClientGunWeaponResolver.project(body, 2, snapshot, new PrototypeCatalog<>(Map.of())).isEmpty());
        var copy = com.juicyslew.moonstation14.ms14.hands.client.ClientOwnedItemProjection.stack(snapshot, body, 2, 4,
                "right", com.juicyslew.moonstation14.ms14.hands.client.ClientOwnedItemProjection.Location.HAND,
                "right", "token").orElseThrow();
        copy.shrink(1);
        assertEquals(1, presentation.stack().getCount());
    }
}
