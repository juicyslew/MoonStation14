package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.blood.BloodAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent;
import com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.ThirstPrototypeComponent;
import com.juicyslew.moonstation14.ms14.hunger.HungerSystem;
import com.juicyslew.moonstation14.ms14.thirst.ThirstSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.UUID;

/** Read-only, transition-deduplicated observation for ghost physiology reports. */
final class GhostPhysiologyDiagnostic {
    private GhostPhysiologyDiagnostic() { }

    static String snapshot(LivingEntity ghost, ServerPlayer carrier) {
        var actor = CharacterIdentitySystem.resolveForActor(ghost);
        ResourceLocation identity = ghost.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()) == null
                ? null : ghost.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()).characterId();
        boolean hunger = actor.flatMap(data -> data.component(HungerPrototypeComponent.class)).isPresent();
        boolean thirst = actor.flatMap(data -> data.component(ThirstPrototypeComponent.class)).isPresent();
        boolean blood = actor.flatMap(data -> data.component(BloodstreamComponent.class)).isPresent();
        BloodAttachment state = ghost.getExistingDataOrNull(ModDataAttachments.BLOOD.get());
        return "ghostType=" + ghost.getType() + " identity=" + identity + " actorResolved=" + actor.isPresent()
                + " policies[hunger=" + hunger + ",thirst=" + thirst + ",blood=" + blood + "]"
                + " bleedRate=" + (state == null ? "absent" : state.bleedRate())
                + " ghost[hungerEligible=" + HungerSystem.isEligible(ghost) + ",thirstEligible=" + ThirstSystem.isEligible(ghost)
                + ",speed=" + speed(ghost) + ",hungerModifier=" + HungerSystem.STARVING_MODIFIER_ID
                + ":" + hasModifier(ghost, HungerSystem.STARVING_MODIFIER_ID) + ",thirstModifier="
                + ThirstSystem.PARCHED_MODIFIER_ID + ":" + hasModifier(ghost, ThirstSystem.PARCHED_MODIFIER_ID) + "]"
                + " carrier[uuid=" + carrier.getUUID() + ",hungerEligible=" + HungerSystem.isEligible(carrier)
                + ",thirstEligible=" + ThirstSystem.isEligible(carrier) + ",speed=" + speed(carrier)
                + ",hungerModifier=" + hasModifier(carrier, HungerSystem.STARVING_MODIFIER_ID)
                + ",thirstModifier=" + hasModifier(carrier, ThirstSystem.PARCHED_MODIFIER_ID) + "]";
    }

    private static String speed(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed == null ? "absent" : Double.toString(speed.getBaseValue()) + "/" + speed.getValue();
    }

    private static boolean hasModifier(LivingEntity entity, ResourceLocation id) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(id) != null;
    }

    static void log(LivingEntity ghost, ServerPlayer carrier, UUID formerBodyId, long epoch, String snapshot) {
        MoonStation14.LOGGER.info("[controlled view] ghost physiology epoch={} ghostUuid={} ghostEntityId={} formerBodyUuid={} {}",
                epoch, ghost.getUUID(), ghost.getId(), formerBodyId, snapshot);
    }
}
