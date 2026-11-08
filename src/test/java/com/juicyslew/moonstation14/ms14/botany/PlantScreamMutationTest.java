package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class PlantScreamMutationTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void screamingToggleUsesExistingStatPathAndCopiesResolvedAudioData() {
        var profile = new SeedProfile(id("packet"), id("produce"), "plant",
                new SeedProfile.Biology(30, 1, 1, 293, 10, 81, 121, 5, 10, 4),
                new SeedProfile.Lifecycle(5, 25, 10, 5, 1, 1, SeedProfile.Harvest.REPEAT), Map.of(), "test-plant-name");
        assertEquals(SeedProfile.Scream.DISABLED, profile.scream());
        var parent = new JsonObject(); parent.add("scream", com.google.gson.JsonParser.parseString("{\"sound\":\"moonstation14:emote/scream\",\"volume_db\":-10}"));
        var child = new JsonObject(); child.add("scream", com.google.gson.JsonParser.parseString("{\"enabled\":true}"));
        var merged = new SeedProfileMergeStrategy().merge("scream", id("child"), child, java.util.List.of(parent));
        assertEquals(true, merged.getAsJsonObject().get("enabled").getAsBoolean());
        assertEquals("moonstation14:emote/scream", merged.getAsJsonObject().get("sound").getAsString());
        var sound = new SeedProfile.Scream(false, SeedProfile.Scream.DEFAULT_SOUND, -10);
        var genetic = new GeneticValue(id("seed"), new SeedProfile(profile.packet(), profile.product(), profile.visualKey(),
                profile.biology(), profile.lifecycle(), profile.chemicals(), Map.of(), Map.of(), java.util.List.of(), sound, profile.sourceName()));
        var toggle = PlantStatMutation.toggle(PlantStatMutation.Target.CAN_SCREAM);
        assertEquals(new SeedProfile.Scream(true, sound.sound(), sound.volumeDb()),
                toggle.apply(genetic, noDrawRandom()).profile().scream());
        var enabled = new GeneticValue(genetic.species(), new SeedProfile(genetic.profile().packet(), genetic.profile().product(),
                genetic.profile().visualKey(), genetic.profile().biology(), genetic.profile().lifecycle(), genetic.profile().chemicals(),
                Map.of(), Map.of(), java.util.List.of(), sound.withEnabled(true), genetic.profile().sourceName()));
        assertEquals(sound, toggle.apply(enabled, noDrawRandom()).profile().scream());
        assertEquals(sound, genetic.adjustPotency(1).profile().scream());
        assertEquals(new SeedProfile.Scream(false, SeedProfile.Scream.DEFAULT_SOUND, -10f), genetic.transformSpecies(id("other"), profile).profile().scream());
        assertEquals(sound.withEnabled(true), SeedProfile.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"packet\":\"moonstation14:packet\",\"product\":\"moonstation14:produce\",\"visual_key\":\"plant\","
                        + "\"biology\":{\"endurance\":30,\"nutrient_consumption\":1,\"water_consumption\":1,\"ideal_heat\":293,\"heat_tolerance\":10,\"low_pressure\":81,\"high_pressure\":121,\"weed_tolerance\":5,\"weed_high_level\":10,\"toxin_tolerance\":4},"
                        + "\"lifecycle\":{\"growth_stages\":5,\"lifespan\":25,\"maturation\":10,\"production\":5,\"yield\":1,\"potency\":1,\"harvest\":\"REPEAT\"},\"chemicals\":{},\"source_name\":\"test-scream-name\","
                        + "\"scream\":{\"enabled\":true,\"sound\":\"moonstation14:emote/scream\",\"volume_db\":-10}}"))
                .result().orElseThrow().scream());
    }

    private static RandomGenerator noDrawRandom() {
        return new java.util.Random() {
            @Override public float nextFloat() { fail("scream toggle must not consume simulation RNG"); return 0; }
        };
    }
}
