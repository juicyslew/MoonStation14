package com.juicyslew.moonstation14.ms14.status_effect;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.status_effect.prototype.StatusEffectReferenceValidator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusEffectReferenceValidatorTest {
    @Test
    void failuresContainSourceReagentAndPreciseEffectPath() {
        ResourceLocation source = id("sample");
        JsonObject reagent = new JsonObject();
        JsonObject stage = new JsonObject();
        var effects = new com.google.gson.JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "ModifyStatusEffect");
        effect.addProperty("effectproto", "Jitter");
        effects.add(effect);
        stage.add("effects", effects);
        JsonObject metabolisms = new JsonObject();
        metabolisms.add("digestion", stage);
        reagent.add("metabolisms", metabolisms);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> StatusEffectReferenceValidator.validate(candidate(source, reagent)));

        assertTrue(failure.getMessage().contains("moonstation14:sample"));
        assertTrue(failure.getMessage().contains("metabolisms.digestion.effects[0].effectproto"));
    }

    @Test
    void unqualifiedCanonicalReferencesResolveToMoonstation14() {
        JsonObject reagent = new JsonObject();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "ModifyStatusEffect");
        effect.addProperty("effectproto", "jitter");
        var effects = new com.google.gson.JsonArray();
        effects.add(effect);
        JsonObject stage = new JsonObject();
        stage.add("effects", effects);
        JsonObject metabolisms = new JsonObject();
        metabolisms.add("digestion", stage);
        reagent.add("metabolisms", metabolisms);

        StatusEffectReferenceValidator.validate(candidate(id("sample"), reagent));
    }

    @Test
    void movementReferencesRequireMovementBehaviorAndResolveTheOmittedDefault() {
        JsonObject reagent = new JsonObject();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "MovementSpeedModifier");
        var effects = new com.google.gson.JsonArray();
        effects.add(effect);
        JsonObject stage = new JsonObject();
        stage.add("effects", effects);
        JsonObject metabolisms = new JsonObject();
        metabolisms.add("digestion", stage);
        reagent.add("metabolisms", metabolisms);

        Map<ResourceLocation, JsonObject> statuses = new LinkedHashMap<>();
        statuses.put(id("jitter"), statusJson());
        statuses.put(id("reagentspeedstatuseffect"), movementStatusJson());
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> catalogs = Map.of(
                ModReagents.REAGENT_TYPE.typeId(), Map.of(id("sample"), reagent),
                ModStatusEffects.STATUS_EFFECT_TYPE.typeId(), statuses);
        StatusEffectReferenceValidator.validate(catalogs);

        effect.addProperty("effectproto", "jitter");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> StatusEffectReferenceValidator.validate(catalogs));
        assertTrue(failure.getMessage().contains("moonstation14:sample"));
        assertTrue(failure.getMessage().contains("metabolisms.digestion.effects[0].effectproto"));
        assertTrue(failure.getMessage().contains("movement_speed"));

        effect.addProperty("effectproto", "custommovement");
        statuses.put(id("custommovement"), movementStatusJson());
        StatusEffectReferenceValidator.validate(catalogs);
    }

    @Test
    void knockdownEffectsResolveTheirImplicitDedicatedStatusReference() {
        JsonObject reagent = new JsonObject();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "ModifyKnockdown");
        var effects = new com.google.gson.JsonArray();
        effects.add(effect);
        JsonObject stage = new JsonObject();
        stage.add("effects", effects);
        JsonObject metabolisms = new JsonObject();
        metabolisms.add("digestion", stage);
        reagent.add("metabolisms", metabolisms);

        Map<ResourceLocation, JsonObject> statuses = new LinkedHashMap<>();
        statuses.put(id("knockdown"), statusJson());
        StatusEffectReferenceValidator.validate(Map.of(
                ModReagents.REAGENT_TYPE.typeId(), Map.of(id("sample"), reagent),
                ModStatusEffects.STATUS_EFFECT_TYPE.typeId(), statuses));

        statuses.remove(id("knockdown"));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> StatusEffectReferenceValidator.validate(Map.of(
                        ModReagents.REAGENT_TYPE.typeId(), Map.of(id("sample"), reagent),
                        ModStatusEffects.STATUS_EFFECT_TYPE.typeId(), statuses)));
        assertTrue(failure.getMessage().contains("status_effect"));
    }

    @Test
    void electrocuteImplicitStunnedReferenceFailsTransactionalPublicationWhenMissing() {
        JsonObject stableReagent = new JsonObject();
        stableReagent.addProperty("id", "stable");
        JsonObject stunned = statusJson();
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);
        manager.register(ModStatusEffects.STATUS_EFFECT_TYPE);
        manager.reload(Map.of(ModReagents.REAGENT_TYPE, Map.of(id("stable"), stableReagent),
                ModStatusEffects.STATUS_EFFECT_TYPE, Map.of(id("statuseffectstunned"), stunned)));

        JsonObject replacement = new JsonObject();
        replacement.addProperty("id", "replacement");
        JsonObject electrocute = new JsonObject();
        electrocute.addProperty("type", "Electrocute");
        var effects = new com.google.gson.JsonArray();
        effects.add(electrocute);
        JsonObject stage = new JsonObject();
        stage.add("effects", effects);
        JsonObject metabolisms = new JsonObject();
        metabolisms.add("digestion", stage);
        replacement.add("metabolisms", metabolisms);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> manager.stage(
                Map.of(ModReagents.REAGENT_TYPE, Map.of(id("replacement"), replacement),
                        ModStatusEffects.STATUS_EFFECT_TYPE, Map.of()),
                StatusEffectReferenceValidator::validate));
        assertTrue(failure.getMessage().contains("status_effect"));
        assertTrue(manager.snapshot(ModReagents.REAGENT_TYPE).contains(id("stable")));
        assertTrue(!manager.snapshot(ModReagents.REAGENT_TYPE).contains(id("replacement")));
    }

    @Test
    void malformedReferencesAndCandidateFailuresAreTransactional() {
        JsonObject stableReagent = new JsonObject();
        stableReagent.addProperty("id", "stable");
        JsonObject status = statusJson();
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);
        manager.register(ModStatusEffects.STATUS_EFFECT_TYPE);
        manager.reload(Map.of(ModReagents.REAGENT_TYPE, Map.of(id("stable"), stableReagent),
                ModStatusEffects.STATUS_EFFECT_TYPE, Map.of(id("jitter"), status)));

        JsonObject malformedReagent = new JsonObject();
        malformedReagent.addProperty("id", "replacement");
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "ModifyStatusEffect");
        effect.addProperty("effectproto", "moonstation14:");
        var effects = new com.google.gson.JsonArray();
        effects.add(effect);
        JsonObject stage = new JsonObject();
        stage.add("effects", effects);
        JsonObject metabolisms = new JsonObject();
        metabolisms.add("digestion", stage);
        malformedReagent.add("metabolisms", metabolisms);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> manager.stage(
                Map.of(ModReagents.REAGENT_TYPE, Map.of(id("replacement"), malformedReagent),
                        ModStatusEffects.STATUS_EFFECT_TYPE, Map.of(id("jitter"), status)),
                StatusEffectReferenceValidator::validate));
        assertTrue(failure.getMessage().contains("moonstation14:replacement"));
        assertTrue(failure.getMessage().contains("metabolisms.digestion.effects[0].effectproto"));
        assertTrue(manager.snapshot(ModReagents.REAGENT_TYPE).contains(id("stable")));
        assertTrue(manager.snapshot(ModStatusEffects.STATUS_EFFECT_TYPE).contains(id("jitter")));
        assertTrue(!manager.snapshot(ModReagents.REAGENT_TYPE).contains(id("replacement")));
    }

    private static Map<ResourceLocation, Map<ResourceLocation, JsonObject>> candidate(
            ResourceLocation source, JsonObject reagent) {
        Map<ResourceLocation, JsonObject> reagents = new LinkedHashMap<>();
        reagents.put(source, reagent);
        Map<ResourceLocation, JsonObject> statuses = new LinkedHashMap<>();
        statuses.put(id("jitter"), new JsonObject());
        return Map.of(ModReagents.REAGENT_TYPE.typeId(), reagents,
                ModStatusEffects.STATUS_EFFECT_TYPE.typeId(), statuses);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    private static JsonObject statusJson() {
        return com.google.gson.JsonParser.parseString(
                "{\"translation_key\":\"status.jitter\",\"color\":\"0x123456\","
                        + "\"behaviors\":[\"client_jitter\"],\"eligibility\":[\"living_entity\"]}")
                .getAsJsonObject();
    }

    private static JsonObject movementStatusJson() {
        return com.google.gson.JsonParser.parseString(
                "{\"translation_key\":\"status.movement\",\"color\":\"0x123456\","
                        + "\"behaviors\":[\"movement_speed\"],\"eligibility\":[\"living_entity\"],"
                        + "\"movement_speed_multiplier\":0.5}").getAsJsonObject();
    }
}
