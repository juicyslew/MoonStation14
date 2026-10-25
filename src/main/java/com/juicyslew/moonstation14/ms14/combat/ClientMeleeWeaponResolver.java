package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.MeleeWeaponComponent;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import java.util.UUID;

/** Read-only client projection from confirmed body-owned hand/equipment authority. */
public final class ClientMeleeWeaponResolver {
    private ClientMeleeWeaponResolver() {}

    public static Optional<MeleeWeaponResolver.Selection> resolve() {
        LivingEntity body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
        return resolve(body, epoch, BodyHandClient.confirmedOwnedHandSnapshot(),
                PrototypeRuntime.clientMeleeWeaponProfiles());
    }

    static Optional<MeleeWeaponResolver.Selection> resolve(LivingEntity body, long epoch,
            BodyHandStateSnapshot snapshot, PrototypeCatalog<MeleeWeaponProfile> catalog) {
        if (body == null || !body.level().isClientSide() || snapshot == null || catalog == null
                || snapshot.reason() != BodyHandStateSnapshot.Reason.OK || snapshot.epoch() != epoch
                || !snapshot.bodyId().equals(body.getUUID())) return Optional.empty();
        CharacterData actor = CharacterIdentitySystem.projectForActor(body).orElse(null);
        if (actor == null) return Optional.empty();
        return project(body.getUUID(), epoch, snapshot, catalog,
                actor.component(MeleeWeaponComponent.class).map(MeleeWeaponComponent::weapon));
    }

    public static Optional<ItemStack> presentationStack(MeleeWeaponResolver.Selection selected) {
        if (selected == null) return Optional.empty();
        var snapshot = BodyHandClient.confirmedOwnedHandSnapshot();
        if (selected.kind() == MeleeWeaponResolver.Kind.HELD)
            return com.juicyslew.moonstation14.ms14.hands.client.ClientOwnedItemProjection.stack(snapshot,
                    selected.body(), snapshot == null ? 0 : snapshot.epoch(), selected.handRevision(), selected.activeHand(),
                    com.juicyslew.moonstation14.ms14.hands.client.ClientOwnedItemProjection.Location.HAND,
                    selected.activeHand(), selected.itemToken().orElse(null));
        if (selected.kind() == MeleeWeaponResolver.Kind.GLOVES)
            return com.juicyslew.moonstation14.ms14.hands.client.ClientOwnedItemProjection.stack(snapshot,
                    selected.body(), snapshot == null ? 0 : snapshot.epoch(), selected.handRevision(), selected.activeHand(),
                    com.juicyslew.moonstation14.ms14.hands.client.ClientOwnedItemProjection.Location.EQUIPMENT,
                    com.juicyslew.moonstation14.ms14.equipment.EquipmentSlots.GLOVES, selected.itemToken().orElse(null));
        return Optional.empty();
    }

    /** Pure DTO-to-selection projection shared by the live client boundary and focused tests. */
    static Optional<MeleeWeaponResolver.Selection> project(UUID body, long epoch,
            BodyHandStateSnapshot snapshot, PrototypeCatalog<MeleeWeaponProfile> catalog,
            Optional<MeleeWeaponData> unarmed) {
        if (body == null || snapshot == null || catalog == null || unarmed == null
                || snapshot.reason() != BodyHandStateSnapshot.Reason.OK || snapshot.epoch() != epoch
                || !snapshot.bodyId().equals(body)) return Optional.empty();
        var hand = snapshot.hands().stream().filter(h -> h.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (hand == null) return Optional.empty();
        Optional<String> heldToken = Optional.ofNullable(hand.token());
        boolean occupied = hand.itemId() != null;
        if (!MeleeWeaponResolver.coherentOwnedStack(occupied, heldToken)) return Optional.empty();
        Optional<MeleeWeaponProfile> held = occupied ? profile(hand.itemId(), catalog) : Optional.empty();
        var glovesSlot = snapshot.equipment().stream()
                .filter(slot -> slot.id().equals(com.juicyslew.moonstation14.ms14.equipment.EquipmentSlots.GLOVES)).findFirst().orElse(null);
        Optional<String> gloveToken = Optional.ofNullable(glovesSlot == null ? null : glovesSlot.token());
        boolean glovePresent = glovesSlot != null && glovesSlot.itemId() != null;
        if (!MeleeWeaponResolver.coherentOwnedStack(glovePresent, gloveToken)) return Optional.empty();
        Optional<MeleeWeaponProfile> gloves = glovePresent ? profile(glovesSlot.itemId(), catalog) : Optional.empty();
        return MeleeWeaponResolver.choose(body, snapshot.revision(), snapshot.activeHand(), occupied,
                heldToken, gloveToken, held, gloves, unarmed);
    }

    private static Optional<MeleeWeaponProfile> profile(String id, PrototypeCatalog<MeleeWeaponProfile> catalog) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return Optional.empty();
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(key));
        MeleeWeaponProfile profile = catalog.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return Optional.ofNullable(profile);
    }
}
