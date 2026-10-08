package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.PullerComponent;
import com.juicyslew.moonstation14.ms14.character.components.TetherSettings;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PullerTetherPrototypeTest {
    @Test
    void tetherSchemaAllowsTypedPartialSettingsButRejectsUnknownAndOutOfRangeFields() {
        CharacterSchemaAudit.auditComponentFragment(JsonParser.parseString(
                "{\"type\":\"Puller\",\"tether\":{\"stiffness\":20}}").getAsJsonObject(), "$.components[0]");
        for (String tether : java.util.List.of(
                "{\"surprise\":1}", "{\"stiffness\":201}", "{\"damping\":-1}",
                "{\"maximum_acceleration\":0}", "{\"slackAllowance\":-0.1}", "null")) {
            var fragment = JsonParser.parseString("{\"type\":\"Puller\",\"tether\":" + tether + "}").getAsJsonObject();
            assertThrows(IllegalArgumentException.class,
                    () -> CharacterSchemaAudit.auditComponentFragment(fragment, "$.components[0]"), tether);
        }
    }

    @Test
    void omittedTetherAndPartialNestedOverrideUseTypedDefaults() {
        CharacterData omitted = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"components\":[{\"type\":\"Puller\"}]}")).getOrThrow();
        assertEquals(TetherSettings.DEFAULT, omitted.component(PullerComponent.class).orElseThrow().tether());

        CharacterData partial = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"components":[{"type":"Puller","tether":{"stiffness":31,"slackAllowance":2.25,
                         "maximum_acceleration":64}}]}
                        """)).getOrThrow();
        assertEquals(new TetherSettings(31, TetherSettings.DEFAULT.progressiveStiffness(),
                TetherSettings.DEFAULT.damping(), 64, 2.25),
                partial.component(PullerComponent.class).orElseThrow().tether());
    }

    @Test
    void parentPullerIsInheritedAndChildNestedTetherFieldsPatchIt() {
        var parentId = ResourceLocation.parse("moonstation14:puller_parent");
        var childId = ResourceLocation.parse("moonstation14:puller_child");
        JsonObject parent = JsonParser.parseString("""
                {"abstract":true,"components":[{"type":"Puller","walk_modifier":0.8,
                 "tether":{"stiffness":24,"progressive_stiffness":15,"damping":9,
                  "maximum_acceleration":55,"slackAllowance":2.75}}]}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"parent":"puller_parent","components":[{"type":"Puller",
                 "tether":{"stiffness":36}}]}
                """).getAsJsonObject();
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(parentId, parent, childId, child));

        PullerComponent resolved = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(childId)
                .component(PullerComponent.class).orElseThrow();
        assertEquals(0.8, resolved.walkModifier());
        assertEquals(new TetherSettings(36, 15, 9, 55, 2.75), resolved.tether());
    }

    @Test
    void humanCatalogResolvesPullerTuningForTheHostPolicy() throws Exception {
        JsonObject human;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/human.json")) {
            if (stream == null) throw new java.io.IOException("Missing human character prototype");
            human = JsonParser.parseReader(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, human));
        CharacterData source = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ModCharacters.HUMAN_ID);
        PullerComponent component = source.component(PullerComponent.class).orElseThrow();
        assertEquals(TetherSettings.DEFAULT, component.tether());
        JsonObject published = manager.encodePublishedCatalogs()
                .get(ModCharacters.CHARACTER_TYPE.typeId()).get(ModCharacters.HUMAN_ID);
        CharacterData roundTripped = CharacterData.CODEC.parse(JsonOps.INSTANCE, published).getOrThrow();
        assertEquals(component.tether(), roundTripped.component(PullerComponent.class).orElseThrow().tether());
    }
}
