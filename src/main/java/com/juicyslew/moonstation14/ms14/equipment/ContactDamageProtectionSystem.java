package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.character.components.DamageOnInteractProtectionComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageModifierData;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Selects one contact protection source; equipment takes precedence over actor fallback. */
public final class ContactDamageProtectionSystem {
    private ContactDamageProtectionSystem() { }
    public static Map<String, Float> apply(Entity target, Map<String, Float> raw, boolean ignore) {
        if (ignore) return raw;
        var actor = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(target);
        var direct = actor.flatMap(data -> data.component(DamageOnInteractProtectionComponent.class));
        return apply(raw, ResolvedWornEquipment.entries(target), direct);
    }
    public static Map<String, Float> apply(Map<String, Float> raw, List<ResolvedWornEquipment.Entry> worn,
                                            Optional<DamageOnInteractProtectionComponent> actor) {
        for (var entry : worn) {
            var profile = entry.profile().contactDamageProtection();
            if (profile.isPresent() && profile.orElseThrow().intersects(entry.slotFlags())) return DamageModifierData.apply(raw, profile.orElseThrow().protection());
        }
        return actor.map(value -> DamageModifierData.apply(raw, value.profile().protection())).orElse(raw);
    }
    public static Map<String, Float> apply(Map<String, Float> raw, List<ResolvedWornEquipment.Entry> worn,
                                            Optional<DamageOnInteractProtectionComponent> actor, boolean ignore) {
        return ignore ? raw : apply(raw, worn, actor);
    }
}
