package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;

/** Read-only runtime view of the hand capability declared by a body's bound character prototype. */
public final class HandCapability {
    private HandCapability() { }

    /**
     * Resolves hand IDs from the body's own bound character identity. This never creates identity or hand state.
     * Empty, unbound, dangling, client-side, and non-server bodies have no runtime hand capability.
     */
    public static Optional<List<String>> resolve(Entity body) {
        Objects.requireNonNull(body, "body");
        return resolveCharacter(body).flatMap(policy -> policy.component(HandsPrototypeComponent.class))
                .map(HandsPrototypeComponent::hands).filter(ids -> !ids.isEmpty());
    }

    /** Read-only server actor prototype; never treats a carrier or a bare HUMAN key as a body. */
    public static Optional<CharacterData> resolveCharacter(Entity body) {
        return body instanceof LivingEntity living ? CharacterIdentitySystem.resolveForActor(living) : Optional.empty();
    }

    /** Resolve the body's equipment layout from its immutable character/template prototype bindings. */
    public static Optional<List<String>> resolveEquipmentSlots(Entity body) {
        return resolveCharacter(body).flatMap(character -> character.component(EquipmentSlotsPrototypeComponent.class)).flatMap(component -> {
            if (component.template().isEmpty()) return Optional.of(component.slots());
            var catalog = body.level().isClientSide() ? PrototypeRuntime.clientInventoryTemplates()
                    : PrototypeRuntime.serverInventoryTemplates();
            InventoryTemplateData template = catalog.get(component.template().orElseThrow());
            if (template == null) return Optional.empty();
            return Optional.of(template.slots().stream().map(InventoryTemplateData.Slot::id).toList());
        });
    }

    /**
     * Returns whether persisted hand IDs exactly match the prototype's IDs and order. A mismatch is rejected
     * rather than rewriting slots and potentially discarding held tokens; this method never mutates or saves state.
     */
    public static boolean isCompatible(HandComponent existing, List<String> prototypeHandIds) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(prototypeHandIds, "prototypeHandIds");
        return existing.hands().stream().map(HandComponent.Slot::id).toList().equals(prototypeHandIds);
    }
}
