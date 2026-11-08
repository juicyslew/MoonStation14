package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.StatusEffectsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StatusEffectsPrototypeTest {
    @Test void requiredAllowedListAndKnockdownMapping() {
        var codec = StatusEffectsPrototypeComponent.CODEC;
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"StatusEffects\"}")).error().isPresent());
        var empty = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"StatusEffects\",\"allowed_effects\":[]}")).getOrThrow();
        assertTrue(empty.allowed().isEmpty());
        var knockdown = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"StatusEffects\",\"allowed_effects\":[\"knockdown\"]}")).getOrThrow();
        assertTrue(knockdown.allowed().contains(StatusEffectsPrototypeComponent.KNOCKDOWN));
        var pacified = codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"StatusEffects\",\"allowed_effects\":[\"pacified\"]}")).getOrThrow();
        assertTrue(pacified.allowed().contains(StatusEffectsPrototypeComponent.PACIFIED));
        assertEquals(pacified, codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, pacified).getOrThrow()).getOrThrow());
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"StatusEffects\",\"allowed_effects\":[\"stunned\"]}")).error().isPresent());
    }

    @Test void inheritanceAndChildSetReplacementResolveThroughCharacterRegistry() {
        var parent = JsonParser.parseString("""
                {"components":[{"type":"StatusEffects","allowed_effects":["knockdown"]}]}
                """).getAsJsonObject();
        var child = JsonParser.parseString("""
                {"parent":"status_parent","components":[{"type":"StatusEffects","allowed_effects":[]}]}
                """).getAsJsonObject();
        var manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(
                ResourceLocation.parse("moonstation14:status_parent"), parent,
                ResourceLocation.parse("moonstation14:status_child"), child));
        var inherited = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ResourceLocation.parse("moonstation14:status_parent"))
                .component(StatusEffectsPrototypeComponent.class).orElseThrow();
        assertTrue(inherited.allowed().contains(StatusEffectsPrototypeComponent.KNOCKDOWN));
        var replaced = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ResourceLocation.parse("moonstation14:status_child"))
                .component(StatusEffectsPrototypeComponent.class).orElseThrow();
        assertTrue(replaced.allowed().isEmpty());
    }
}
