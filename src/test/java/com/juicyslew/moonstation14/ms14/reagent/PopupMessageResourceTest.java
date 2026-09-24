package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.ReagentSchemaAudit;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PopupMessageResourceTest {
    private static final Map<String, String> EXPECTED_MESSAGES = Map.ofEntries(
            Map.entry("absinthe-effect-hear-voice", "You hear a tiny voice. \"Tee hee hee!\""),
            Map.entry("absinthe-effect-feel-tulips", "You feel tulips brush up against your legs."),
            Map.entry("ammonia-smell", "Something smells pungent!"),
            Map.entry("barozine-effect-skin-burning", "You feel like your skin is burning off!"),
            Map.entry("barozine-effect-muscle-contract", "You can feel your muscles contracting."),
            Map.entry("generic-reagent-effect-burning-insides", "You feel your insides burning up!"),
            Map.entry("buzzochloricbees-effect-oh-god-bees", "You are swarmed by many, many bees."),
            Map.entry("buzzochloricbees-effect-its-the-bees", "It's the bees, oh god the bees."),
            Map.entry("buzzochloricbees-effect-why-am-i-covered-in-bees", "You are covered in angry bees."),
            Map.entry("buzzochloricbees-effect-one-with-the-bees", "You are one with the bees."),
            Map.entry("buzzochloricbees-effect-squeaky-clean", "You feel squeaky clean as the bees try and get rid of you."),
            Map.entry("buzzochloricbees-effect-histamine-bee-allergy", "You are highly allergic to bees, apparently."),
            Map.entry("buzzochloricbees-effect-histamine-swells", "You swell like a balloon in the presence of the bees."),
            Map.entry("buzzochloricbees-effect-histamine-numb-to-the-bees", "You are numb to the bees."),
            Map.entry("buzzochloricbees-effect-histamine-cannot-be-one-with-the-bees", "You are not one with the bees."),
            Map.entry("buzzochloricbees-effect-licoxide-electrifying", "The bees are electrifying."),
            Map.entry("buzzochloricbees-effect-licoxide-shocked-by-bee-facts", "You are shocked by these five bee facts."),
            Map.entry("buzzochloricbees-effect-licoxide-buzzed", "You feel buzzed."),
            Map.entry("buzzochloricbees-effect-licoxide-buzzes", "You buzz with the bees."),
            Map.entry("buzzochloricbees-effect-fiber-hairy", "You feel fuzzy, like a bee."),
            Map.entry("buzzochloricbees-effect-fiber-soft", "You feel some exceptionally soft bees."),
            Map.entry("capsaicin-effect-light-burn", "You feel a slight tingle in your throat..."),
            Map.entry("carpetium-effect-blood-fibrous", "Your blood feels oddly fibrous today."),
            Map.entry("carpetium-effect-jumpsuit-insides", "You feel like there's a jumpsuit inside you, for some reason."),
            Map.entry("clf3-it-burns", "It burns like hell!!"),
            Map.entry("clf3-get-away", "You need to get away now!"),
            Map.entry("ethyloxyephedrine-effect-feeling-awake", "You feel more awake."),
            Map.entry("ethyloxyephedrine-effect-clear-mind", "The fog of sleep before you clears away."),
            Map.entry("ephedrine-effect-tight-pain", "You feel a tight pain in your chest."),
            Map.entry("ephedrine-effect-heart-pounds", "Your heart pounds!"),
            Map.entry("fresium-effect-freeze-insides", "You feel your insides freezing up!"),
            Map.entry("fresium-effect-frozen", "Your legs have completely frozen up!"),
            Map.entry("fresium-effect-slow", "Your legs buckle and struggle to move!"),
            Map.entry("frezon-lungs-cold", "Your lungs feel colder.."),
            Map.entry("frezon-euphoric", "You feel chilly, but euphoric.."),
            Map.entry("psicodine-effect-fearless", "You feel totally fearless!"),
            Map.entry("psicodine-effect-anxieties-wash-away", "All of your anxieties wash away!"),
            Map.entry("psicodine-effect-at-peace", "You feel completely at peace."),
            Map.entry("frost-oil-effect-light-cold", "You feel a slight cold tingle in your throat..."),
            Map.entry("histamine-effect-light-itchiness", "You feel a little itchy..."),
            Map.entry("histamine-effect-heavy-itchiness", "You feel REALLY itchy!"),
            Map.entry("generic-reagent-effect-thirsty", "You feel thirsty."),
            Map.entry("generic-reagent-effect-parched", "You feel parched."),
            Map.entry("generic-reagent-effect-nauseous", "You feel nauseous."),
            Map.entry("laughter-effect-control-laughter", "You can't contain your laughter!"),
            Map.entry("leporazine-effect-temperature-adjusting", "You feel your body's temperature adjust rapidly."),
            Map.entry("mannitol-effect-enlightened", "You feel ENLIGHTENED!"),
            Map.entry("effect-sleepy", "You feel a bit sleepy."),
            Map.entry("generic-reagent-effect-burning-eyes", "Your eyes begin to slightly burn."),
            Map.entry("generic-reagent-effect-burning-eyes-a-bit", "Your eyes burn a bit."),
            Map.entry("generic-reagent-effect-tearing-up", "Your eyes start to tear up."),
            Map.entry("norepinephricacid-effect-eyelids", "Your eyelids are rapidly twitching."),
            Map.entry("norepinephricacid-effect-eyes-itch", "Your eyes feel itchy."),
            Map.entry("norepinephricacid-effect-vision-fade", "You feel your vision fading."),
            Map.entry("norepinephricacid-effect-vision-fail", "You can feel your vision failing you."),
            Map.entry("norepinephricacid-effect-eye-pain", "You feel a deep pain in your eyes!"),
            Map.entry("norepinephricacid-effect-blindness", "Your eyes cease function!"),
            Map.entry("norepinephricacid-effect-darkness", "You are plunged into a world of darkness!"),
            Map.entry("norepinephricacid-effect-eye-disconnect", "Your eyes feel like they're disconnecting!"),
            Map.entry("generic-reagent-effect-slicing-insides", "You feel an incredibly sharp pain in your gut!"),
            Map.entry("generic-reagent-effect-sick", "You feel sick after consuming that...")
    );

    @Test
    void productionUsesExactlyFortySevenPopupEffectsAndSixtyOneKeys() throws Exception {
        Set<String> keys = new HashSet<>();
        int[] occurrences = {0};
        for (JsonObject reagent : ReagentResourceSmokeTest.loadResources().values()) {
            collectPopupMessages(reagent, keys, occurrences);
        }
        assertEquals(47, occurrences[0]);
        assertEquals(61, keys.size());
        Set<String> missing = new HashSet<>(EXPECTED_MESSAGES.keySet());
        missing.removeAll(keys);
        Set<String> extra = new HashSet<>(keys);
        extra.removeAll(EXPECTED_MESSAGES.keySet());
        assertEquals(EXPECTED_MESSAGES.keySet(), keys, "missing=" + missing + ", extra=" + extra);
    }

    @Test
    void localeContainsPinnedEnglishForEveryProductionPopupKey() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "assets/moonstation14/lang/en_us.json")) {
            JsonObject locale = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (Map.Entry<String, String> expected : EXPECTED_MESSAGES.entrySet()) {
                assertEquals(expected.getValue(), locale.get(expected.getKey()).getAsString(), expected.getKey());
            }
        }
    }

    @Test
    void canonicalAuditRejectsBlankPopupMessageKeys() throws Exception {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("moonstation14", "absinthe");
        JsonObject malformed = ReagentResourceSmokeTest.loadResources().get(id).deepCopy();
        JsonArray effects = malformed.getAsJsonObject("metabolisms").getAsJsonObject("digestion")
                .getAsJsonArray("effects");
        JsonObject popup = effects.get(1).getAsJsonObject();
        popup.add("messages", JsonParser.parseString("[\" \"]"));
        assertThrows(IllegalArgumentException.class, () -> ReagentSchemaAudit.audit(id, malformed));
    }

    private static void collectPopupMessages(JsonElement element, Set<String> keys, int[] occurrences) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectPopupMessages(child, keys, occurrences);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if ("PopupMessage".equals(object.has("type") ? object.get("type").getAsString() : null)) {
                occurrences[0]++;
                for (JsonElement message : object.getAsJsonArray("messages")) keys.add(message.getAsString());
            }
            for (Map.Entry<String, JsonElement> child : object.entrySet()) {
                collectPopupMessages(child.getValue(), keys, occurrences);
            }
        }
    }
}
