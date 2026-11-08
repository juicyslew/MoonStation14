package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BotanyCatalogPromotionParityTest {
    @Test void positiveCompositionPassesAndBehaviorFieldMismatchFails() {
        var candidate = JsonParser.parseString("""
                {"id":"moonstation14:sample","seed":"moonstation14:wheat","amount":2,
                 "extractable":{"solution":"moonstation14:grain"}}
                """).getAsJsonObject();
        var same = candidate.deepCopy();
        same.addProperty("id", "moonstation14:renamed-decorative-id");
        BotanyCatalogCandidateTest.assertPromotedCompositionMatches("produce_profile", "sample", candidate, same);

        var changed = candidate.deepCopy();
        changed.addProperty("seed", "moonstation14:potato");
        assertThrows(AssertionError.class, () -> BotanyCatalogCandidateTest.assertPromotedCompositionMatches(
                "produce_profile", "sample", candidate, changed));
    }

    @Test void onlyBotanyWearableModelWiringIsAnAllowedCompositionDeparture() {
        var candidate = JsonParser.parseString("""
                {"parent":"moonstation14:clothing_head_base","armor":{"coefficients":{"blunt":0.95}}}
                """).getAsJsonObject();
        var promoted = candidate.deepCopy();
        promoted.add("clothing", JsonParser.parseString("""
                {"worn_model":{"mesh":"moonstation14:wearables/meshes/hats/hat_watermelon.json","body_shape":"WIDE"}}
                """));
        BotanyCatalogCandidateTest.assertPromotedCompositionMatches("equipment_item", "clothing_head_hat_watermelon", candidate, promoted);
        assertThrows(AssertionError.class, () -> BotanyCatalogCandidateTest.assertPromotedCompositionMatches(
                "equipment_item", "unrelated_hat", candidate, promoted));
    }

    @Test void onlyKnownMeatwheatSoundAliasMapsToCanonicalEvent() {
        var candidate = JsonParser.parseString("""
                {"spawn_items_on_use":{"sound":"moonstation14:slime_squish","outputs":[]}}
                """).getAsJsonObject();
        var canonical = JsonParser.parseString("""
                {"spawn_items_on_use":{"sound":"moonstation14:botany/produce/slime_squish","outputs":[]}}
                """).getAsJsonObject();
        BotanyCatalogCandidateTest.assertPromotedCompositionMatches(
                "item_processing_profile", "meatwheat_bushel", candidate, canonical);

        var unrelatedSound = canonical.deepCopy();
        unrelatedSound.getAsJsonObject("spawn_items_on_use").addProperty("sound", "moonstation14:other_sound");
        assertThrows(AssertionError.class, () -> BotanyCatalogCandidateTest.assertPromotedCompositionMatches(
                "item_processing_profile", "meatwheat_bushel", candidate, unrelatedSound));
    }
}
