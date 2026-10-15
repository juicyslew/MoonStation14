package com.juicyslew.moonstation14.ms14.mobs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.DamageableComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SuicideCommandTest {
    @Test
    void eligibilityUsesResolvedBundledPrototypeComponents() throws Exception {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(
                ModCharacters.HUMAN_ID, resource("human"),
                id("pig"), resource("pig"),
                id("mouse"), resource("mouse"),
                 id("supported_simple_mob"), resource("supported_simple_mob"),
                 id("supported_animal"), resource("supported_animal"),
                 id("supported_npc"), resource("supported_npc")));
        var catalog = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        for (var prototype : Set.of(ModCharacters.HUMAN_ID, id("pig"), id("mouse"))) {
            var character = catalog.get(prototype);
            var damageable = character.component(DamageableComponent.class).orElseThrow();
            var policy = MobStatePolicy.from(character).orElseThrow();
            assertTrue(SuicideCommand.medicalEligibility(damageable, policy), prototype.toString());
        }
        assertFalse(SuicideCommand.medicalEligibility(null,
                MobStatePolicy.from(catalog.get(ModCharacters.HUMAN_ID)).orElseThrow()));
        assertFalse(SuicideCommand.medicalEligibility(
                new DamageableComponent(Optional.of("Mechanical")),
                MobStatePolicy.from(catalog.get(ModCharacters.HUMAN_ID)).orElseThrow()));
        var humanDamageable = catalog.get(ModCharacters.HUMAN_ID).component(DamageableComponent.class).orElseThrow();
        var humanPolicy = MobStatePolicy.from(catalog.get(ModCharacters.HUMAN_ID)).orElseThrow();
        var noDeadPolicy = new MobStatePolicy(new MobStateComponent(Set.of(MobStateComponent.State.ALIVE)), humanPolicy.thresholds());
        assertFalse(SuicideCommand.medicalEligibility(humanDamageable, noDeadPolicy));
        assertFalse(SuicideCommand.medicalEligibility(humanDamageable, null));
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("moonstation14:" + path); }

    private JsonObject resource(String name) throws Exception {
        String path = "data/moonstation14/moonstation14/character/" + name + ".json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
