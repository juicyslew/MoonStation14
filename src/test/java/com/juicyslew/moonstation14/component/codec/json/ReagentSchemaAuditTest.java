package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReagentSchemaAuditTest {
    private static final ResourceLocation REAGENT_ID =
            ResourceLocation.fromNamespaceAndPath("moonstation14", "reactive-method-test");

    @Test
    void acceptsEveryCanonicalReactionMethodAndFutureReactiveGroups() {
        JsonObject reagent = reagent("""
                {"acidic":{"methods":["touch"],"effects":[]},
                 "future_species_group":{"methods":["injection","ingestion"],"effects":[]}}
                """);

        assertDoesNotThrow(() -> ReagentSchemaAudit.audit(REAGENT_ID, reagent));
    }

    @Test
    void rejectsUnknownOrCaseDriftedMethodsWithReagentAndPath() {
        assertRejects("[\"Touch\"]", "reactiveeffects.acidic.methods[0]");
        assertRejects("[\"touchh\"]", "reactiveeffects.acidic.methods[0]");
    }

    @Test
    void rejectsEmptyAndDuplicateMethodLists() {
        assertRejects("[]", "reactiveeffects.acidic.methods");
        assertRejects("[\"touch\",\"touch\"]", "reactiveeffects.acidic.methods[1]");
    }

    private static void assertRejects(String methods, String path) {
        JsonObject reagent = reagent("{\"acidic\":{\"methods\":" + methods + ",\"effects\":[]}}");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ReagentSchemaAudit.audit(REAGENT_ID, reagent));
        assertTrue(exception.getMessage().contains(REAGENT_ID.toString()), exception.getMessage());
        assertTrue(exception.getMessage().contains(path), exception.getMessage());
    }

    private static JsonObject reagent(String reactiveEffects) {
        return JsonParser.parseString("{\"id\":\"reactive-method-test\",\"reactiveeffects\":"
                + reactiveEffects + "}").getAsJsonObject();
    }
}
