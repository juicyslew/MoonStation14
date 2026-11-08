package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.character.components.ProtectedFromStepTriggersComponent;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/** Resolves direct character protection and protection projected by declared worn slots. */
public final class EquipmentStepTriggerProtectionPolicy {
    private EquipmentStepTriggerProtectionPolicy() { }

    public static boolean protects(Entity target) {
        var actor = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(target);
        Optional<ProtectedFromStepTriggersComponent> direct = actor.flatMap(data -> data.component(ProtectedFromStepTriggersComponent.class));
        return protects(direct, ResolvedWornEquipment.entries(target));
    }

    public static boolean protects(Optional<ProtectedFromStepTriggersComponent> direct,
                                   List<ResolvedWornEquipment.Entry> wornEntries) {
        if (direct.isPresent()) return true;
        for (var entry : wornEntries) {
            var protection = entry.profile().protectedFromStepTriggers();
            if (protection.isPresent() && protection.orElseThrow().intersects(entry.slotFlags())) return true;
        }
        return false;
    }
}
