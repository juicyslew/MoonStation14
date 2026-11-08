package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.hands.client.ClientOwnedItemProjection;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import java.util.UUID;

/** Client presentation projection for the confirmed active held item only. */
public final class ClientGunWeaponResolver {
    private ClientGunWeaponResolver() { }

    public static Optional<GunWeaponResolver.Selection> resolve() {
        LivingEntity body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
        return resolve(body, epoch, BodyHandClient.confirmedOwnedHandSnapshot(), PrototypeRuntime.clientGunProfiles());
    }

    static Optional<GunWeaponResolver.Selection> resolve(LivingEntity body, long epoch,
            BodyHandStateSnapshot snapshot, PrototypeCatalog<GunProfile> catalog) {
        if (body == null || !body.level().isClientSide() || snapshot == null || catalog == null
                || snapshot.reason() != BodyHandStateSnapshot.Reason.OK || snapshot.epoch() != epoch
                || !snapshot.bodyId().equals(body.getUUID())) return Optional.empty();
        return project(body.getUUID(), epoch, snapshot, catalog);
    }

    static Optional<GunWeaponResolver.Selection> project(UUID body, long epoch,
            BodyHandStateSnapshot snapshot, PrototypeCatalog<GunProfile> catalog) {
        if (body == null || snapshot == null || catalog == null
                || snapshot.reason() != BodyHandStateSnapshot.Reason.OK || snapshot.epoch() != epoch
                || !body.equals(snapshot.bodyId())) return Optional.empty();
        var hand = snapshot.hands().stream().filter(h -> h.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (hand == null || hand.token() == null || hand.itemId() == null || hand.presentation() == null
                || !hand.itemId().equals(BuiltInRegistries.ITEM.getKey(hand.presentation().stack().getItem()).toString()))
            return Optional.empty();
        ResourceLocation key = ResourceLocation.tryParse(hand.itemId());
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return Optional.empty();
        ItemStack actual = hand.presentation().stack();
        if (!BuiltInRegistries.ITEM.get(key).equals(actual.getItem())) return Optional.empty();
        GunProfile profile = catalog.get(key);
        return profile == null ? Optional.empty() : Optional.of(new GunWeaponResolver.Selection(body,
                snapshot.revision(), snapshot.activeHand(), hand.token(), profile));
    }

    public static Optional<ItemStack> presentationStack(GunWeaponResolver.Selection selection) {
        if (selection == null) return Optional.empty();
        var snapshot = BodyHandClient.confirmedOwnedHandSnapshot();
        return ClientOwnedItemProjection.stack(snapshot, selection.body(), snapshot == null ? 0 : snapshot.epoch(),
                selection.handRevision(), selection.activeHand(), ClientOwnedItemProjection.Location.HAND,
                selection.activeHand(), selection.itemToken());
    }
}
