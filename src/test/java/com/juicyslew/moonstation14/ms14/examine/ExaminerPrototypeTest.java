package com.juicyslew.moonstation14.ms14.examine;

import static org.junit.jupiter.api.Assertions.*;
import com.juicyslew.moonstation14.ms14.character.components.ExaminerComponent;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ExaminerPrototypeTest {
    @Test void codecSuppliesInheritedDefaultsAndObserverCanOverrideOneFlag() {
        var inherited = ExaminerComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"Examiner\"}")).getOrThrow();
        assertEquals(ExaminerComponent.DEFAULT, inherited);
        var override = ExaminerComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"Examiner\",\"skip_checks\":true}")).getOrThrow();
        assertEquals(new ExaminerComponent(true, true), override);
        assertTrue(CharacterComponentRegistry.registered("Examiner"));
    }
}
