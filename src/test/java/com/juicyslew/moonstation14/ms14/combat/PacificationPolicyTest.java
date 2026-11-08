package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.status_effect.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class PacificationPolicyTest {
    private static GunProfile gun(boolean allowed) {
        return new GunProfile(new GunProfile.GunData(2, 40, true, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.empty(), allowed);
    }

    @Test void liveAndClientSharedPolicyUsesResolvedGunMarkerOnly() {
        assertTrue(PacificationSystem.mayFire(false, gun(false)));
        assertFalse(PacificationSystem.mayFire(true, gun(false)));
        assertTrue(PacificationSystem.mayFire(true, gun(true))); // cap/fake including lethal AP fill
    }

    @Test void meleePolicyPreservesSourceExceptionsAndFiltersEveryLivingWideCandidate() {
        var mob = org.mockito.Mockito.mock(net.minecraft.world.entity.LivingEntity.class);
        var loose = org.mockito.Mockito.mock(net.minecraft.world.entity.Entity.class);
        assertTrue(PacificationSystem.mayMelee(true, true, mob, Map.of("blunt", 5f)));
        assertTrue(PacificationSystem.mayMelee(true, false, null, Map.of("blunt", 5f))); // wide pre-attempt
        assertTrue(PacificationSystem.mayMelee(true, false, mob, Map.of("blunt", 0f)));
        assertTrue(PacificationSystem.mayMelee(true, false, mob, Map.of()));
        assertFalse(PacificationSystem.mayMelee(true, false, mob, Map.of("blunt", 5f))); // also applies dead/critical mobs
        assertTrue(PacificationSystem.mayMelee(true, false, loose, Map.of("blunt", 5f)));
        assertTrue(PacificationSystem.mayMelee(false, false, mob, Map.of("blunt", 5f)));
    }

    @Test void pacifiedBehaviorIsReadFromResolvedDefinitionAndExpiresNormally() {
        var data = StatusEffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"translation_key":"status-effect-pacified","beneficial":true,"color":"0x00FF00",
                 "behaviors":["marker","pacified"],"eligibility":["living_entity","prototype_admitted"]}
                """)).getOrThrow();
        assertTrue(data.behaviors().contains(StatusEffectBehavior.PACIFIED));
        ResourceKey<StatusEffectData> key = ResourceKey.create(ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("status_effect")),
                ResourceLocation.fromNamespaceAndPath("moonstation14", "pacified"));
        var catalog = new PrototypeCatalog<>(Map.of(key.location(), data));
        var attachment = new StatusEffectAttachment();
        assertFalse(StatusEffectSystem.hasActiveBehavior(attachment, catalog, StatusEffectBehavior.PACIFIED));
        attachment.apply(key, StatusEffectOperation.UPDATE, OptionalInt.of(80), 0);
        assertTrue(StatusEffectSystem.hasActiveBehavior(attachment, catalog, StatusEffectBehavior.PACIFIED));
        for (int tick = 0; tick < 79; tick++) attachment.advanceOneTick();
        assertTrue(StatusEffectSystem.hasActiveBehavior(attachment, catalog, StatusEffectBehavior.PACIFIED));
        attachment.advanceOneTick();
        assertFalse(StatusEffectSystem.hasActiveBehavior(attachment, catalog, StatusEffectBehavior.PACIFIED));
    }

    @Test void admissionPredicateRequiresResolvedCharacterPermissionAndOtherStatusesStayUnchanged() {
        var pacified = StatusEffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"translation_key":"status-effect-pacified","beneficial":true,"color":"0x00FF00",
                 "behaviors":["marker","pacified"],"eligibility":["living_entity","prototype_admitted"]}
                """)).getOrThrow();
        var key = com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects.createKey("pacified");
        var allowed = new com.juicyslew.moonstation14.ms14.character.components.StatusEffectsPrototypeComponent(
                java.util.Set.of(com.juicyslew.moonstation14.ms14.character.components.StatusEffectsPrototypeComponent.PACIFIED));
        assertTrue(DefaultStatusEffectLifecycle.canApplyEligibility(pacified, key, allowed));
        assertFalse(DefaultStatusEffectLifecycle.canApplyEligibility(pacified, key,
                com.juicyslew.moonstation14.ms14.character.components.StatusEffectsPrototypeComponent.DEFAULT));
        var legacy = new StatusEffectData("status.legacy", false, 0, java.util.List.of(StatusEffectBehavior.MARKER),
                java.util.List.of(com.juicyslew.moonstation14.component.codec.json.StatusEffectEligibility.LIVING_ENTITY));
        assertTrue(DefaultStatusEffectLifecycle.canApplyEligibility(legacy, key, null));
    }
}
