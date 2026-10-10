package com.juicyslew.moonstation14.ms14.stripping;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.StrippablePrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.StrippingPrototypeComponent;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/** Fail-closed stripping capability view over the actor's resolved character and existing hand/template bindings. */
public final class StripCapability {
    private StripCapability() { }

    public record Resolved(boolean actorCanOpen, boolean targetCanBeStripped,
                           boolean targetInventoryCanBeStripped, boolean targetHandsCanBeStripped,
                           Optional<StrippablePrototypeComponent> targetPolicy,
                           Optional<HandsPrototypeComponent> targetHands,
                           Optional<EquipmentSlotsPrototypeComponent> targetEquipment) { }

    /** Resolve actor admission and target composition independently; target mob state is intentionally not admission. */
    public static Resolved resolve(Entity actor, Entity target) {
        Optional<CharacterData> actorData = HandCapability.resolveCharacter(actor);
        Optional<CharacterData> targetData = HandCapability.resolveCharacter(target);
        Optional<HandsPrototypeComponent> targetHands = targetData.flatMap(c -> c.component(HandsPrototypeComponent.class));
        Optional<StrippablePrototypeComponent> targetPolicy = targetData.flatMap(c -> c.component(StrippablePrototypeComponent.class));
        boolean hasResolvedSlots = HandCapability.resolveEquipmentSlots(target).isPresent();
        boolean actorAllowed = actorData.isPresent() && canActorOpen(actorData.orElseThrow());
        boolean inventoryAllowed = targetData.isPresent() && canStripInventory(targetData.orElseThrow(), hasResolvedSlots);
        boolean handsAllowed = targetData.isPresent() && canStripHands(targetData.orElseThrow());
        return new Resolved(actorAllowed, inventoryAllowed || handsAllowed, inventoryAllowed, handsAllowed,
                targetPolicy, targetHands,
                targetData.flatMap(c -> c.component(EquipmentSlotsPrototypeComponent.class)));
    }

    /** Pure resolved-composition consumer; the caller supplies proof that the target template resolved. */
    public static boolean canActorOpen(CharacterData resolvedActor) {
        return resolvedActor != null && resolvedActor.component(StrippingPrototypeComponent.class).isPresent();
    }

    public static boolean canTargetBeStripped(CharacterData resolvedTarget, boolean equipmentTemplateResolved) {
        return canStripInventory(resolvedTarget, equipmentTemplateResolved) || canStripHands(resolvedTarget);
    }

    /** Inventory policy is independent of Hands.CanBeStripped; that flag gates hand actions only. */
    public static boolean canStripInventory(CharacterData resolvedTarget, boolean equipmentTemplateResolved) {
        return resolvedTarget != null && equipmentTemplateResolved
                && resolvedTarget.component(StrippablePrototypeComponent.class).isPresent()
                && resolvedTarget.component(EquipmentSlotsPrototypeComponent.class).isPresent();
    }

    /** Hand policy requires a resolved, nonempty hand set and upstream CanBeStripped=true. */
    public static boolean canStripHands(CharacterData resolvedTarget) {
        return resolvedTarget != null && resolvedTarget.component(StrippablePrototypeComponent.class).isPresent()
                && resolvedTarget.component(HandsPrototypeComponent.class)
                .filter(HandsPrototypeComponent::canBeStripped).filter(hands -> !hands.hands().isEmpty()).isPresent();
    }
}
