package com.juicyslew.moonstation14.ms14.effect;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.ConditionData;
import com.juicyslew.moonstation14.ms14.character.components.MetabolizerPrototypeComponent;
import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConditionSystemTest {
    @Test
    void oxygenMetabolizerConditionUsesOnlyConfiguredCharacterTypes() throws Exception {
        var oxygenGate = new ConditionData.MetabolizerTypeCondition(List.of(
                MetabolizerTypeEnum.HUMAN, MetabolizerTypeEnum.ANIMAL,
                MetabolizerTypeEnum.RAT, MetabolizerTypeEnum.PLANT), false);
        for (String character : List.of("human", "pig")) {
            String path = "data/moonstation14/moonstation14/character/" + character + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream);
                var raw = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                CharacterData configured = CharacterData.CODEC.parse(JsonOps.INSTANCE, raw).getOrThrow();
                assertTrue(ConditionSystem.evaluate(oxygenGate, ConditionContext.builder()
                        .metabolizerTypes(configured.component(MetabolizerPrototypeComponent.class).orElseThrow().types()).build()));
                var components = raw.getAsJsonArray("components");
                for (int index = 0; index < components.size(); index++) {
                    if ("Metabolizer".equals(components.get(index).getAsJsonObject().get("type").getAsString())) {
                        components.remove(index);
                        break;
                    }
                }
                assertEquals(Optional.empty(), CharacterData.CODEC.parse(JsonOps.INSTANCE, raw)
                        .getOrThrow().component(MetabolizerPrototypeComponent.class));
                assertFalse(ConditionSystem.evaluate(oxygenGate, ConditionContext.unavailable()));
            }
        }
        assertFalse(ConditionSystem.evaluate(oxygenGate,
                ConditionContext.builder().metabolizerTypes(Set.of(MetabolizerTypeEnum.VOX)).build()));
        assertFalse(ConditionSystem.evaluate(oxygenGate,
                ConditionContext.builder().metabolizerTypes(Set.of()).build()));
    }
}
