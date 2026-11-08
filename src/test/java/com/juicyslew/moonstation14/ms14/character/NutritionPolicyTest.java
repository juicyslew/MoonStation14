package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.ThirstPrototypeComponent;
import com.juicyslew.moonstation14.ms14.hunger.HungerReducer;
import com.juicyslew.moonstation14.ms14.thirst.ThirstReducer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NutritionPolicyTest {
    private static final HungerPrototypeComponent MOUSE_HUNGER = new HungerPrototypeComponent(35,25,15,10,0,.5f);
    private static final ThirstPrototypeComponent MOUSE_THIRST = new ThirstPrototypeComponent(35,25,15,10,0,.04f,25);

    @Test void mousePoliciesAndHumanDefaults() {
        assertEquals(25, HungerReducer.initialValue(25, MOUSE_HUNGER));
        assertEquals(HungerReducer.Level.OKAY, HungerReducer.classify(24, MOUSE_HUNGER));
        assertEquals(HungerReducer.Level.PECKISH, HungerReducer.classify(15, MOUSE_HUNGER));
        assertEquals(HungerReducer.Level.STARVING, HungerReducer.classify(9, MOUSE_HUNGER));
        assertEquals(24.5f, HungerReducer.decayOneSecond(25, MOUSE_HUNGER).after());
        assertEquals(ThirstReducer.Level.OKAY, ThirstReducer.classify(25, MOUSE_THIRST));
        assertEquals(24.96f, ThirstReducer.decayOneSecond(25, MOUSE_THIRST).after(), .0001f);
        assertEquals(150, HungerPrototypeComponent.DEFAULT.okay());
        assertEquals(100, HungerPrototypeComponent.DEFAULT.peckish());
        assertEquals(450, ThirstPrototypeComponent.DEFAULT.okay());
    }

    @Test void auditRejectsMalformedPoliciesAndAcceptsTypedFields() {
        assertDoesNotThrow(() -> CharacterSchemaAudit.auditComponent(JsonParser.parseString(
                "{type:'Hunger',overfed:35,okay:25,peckish:15,starving:10,dead:0,decay:0.5}".replace('\'', '"')).getAsJsonObject(), "mouse"));
        assertThrows(IllegalArgumentException.class, () -> CharacterSchemaAudit.auditComponent(JsonParser.parseString(
                "{\"type\":\"Hunger\",\"okay\":-1}" ).getAsJsonObject(), "bad"));
    }

    @Test void mouseThirstCodecResolvesExplicitStartWithoutHumanRandomRange() {
        var mouse = ThirstPrototypeComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"Thirst\",\"overHydrated\":35,\"okay\":25,\"thirsty\":15,\"parched\":10,\"dead\":0,\"decay\":0.04,\"startingThirst\":25}")).getOrThrow();
        assertEquals(25f, mouse.startingThirst());
        assertEquals(35f, mouse.overHydrated());
        assertEquals(ThirstReducer.Level.OKAY, ThirstReducer.classify(mouse.startingThirst(), mouse));
        assertDoesNotThrow(() -> CharacterSchemaAudit.auditComponent(JsonParser.parseString(
                "{\"type\":\"Thirst\",\"overHydrated\":35,\"okay\":25,\"thirsty\":15,\"parched\":10,\"dead\":0,\"decay\":0.04,\"startingThirst\":25}").getAsJsonObject(), "mouse"));
    }
}
