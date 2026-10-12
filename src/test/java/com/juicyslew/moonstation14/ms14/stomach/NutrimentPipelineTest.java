package com.juicyslew.moonstation14.ms14.stomach;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent;
import com.juicyslew.moonstation14.ms14.hunger.HungerReducer;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismReport;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismSystem;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolizerProfile;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.stomach.StomachDigestionTransfer;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end deterministic nutrition check using bundled cheese and nutriment prototypes. */
class NutrimentPipelineTest {
    private static final ResourceLocation NUTRIMENT_ID = id("nutriment");
    private static final ResourceKey<ReagentData> NUTRIMENT = ResourceKey.create(
            ModReagents.REAGENT_REGISTRY_KEY, id("nutriment"));

    @Test
    void fiveResolvedCheesesFlowThroughStomachMetabolismAndMouseHunger() {
        var profiles = new PrototypeManager();
        profiles.register(ModIngestionProfiles.TYPE);
        Map<ResourceLocation, JsonObject> rawProfiles = new LinkedHashMap<>();
        for (String name : new String[]{"edible_defaults", "food_base", "food_baking_base", "cheese_slice"})
            rawProfiles.put(id(name), json("/data/moonstation14/moonstation14/ingestion_profile/" + name + ".json"));
        profiles.reload(ModIngestionProfiles.TYPE, rawProfiles);
        IngestionProfile cheese = profiles.snapshot(ModIngestionProfiles.TYPE).get(id("cheese_slice"));
        assertEquals(IngestionProfile.Kind.FOOD, cheese.edible().kind());
        assertEquals(375L, cheese.edible().sourceSolution().contentsCents().get(id("nutriment")));

        ReagentData nutriment = ReagentData.CODEC.parse(JsonOps.INSTANCE,
                json("/data/moonstation14/moonstation14/reagent/nutriment.json")).getOrThrow();
        var metabolite = nutriment.metabolisms().get(MetabolismStage.METABOLITES);
        assertNotNull(metabolite);
        assertFalse(nutriment.metabolisms().containsKey(MetabolismStage.DIGESTION));
        assertEquals(.5f, metabolite.rate());
        var satiate = assertInstanceOf(EffectData.SatiateHunger.class, metabolite.effects().getFirst());
        assertEquals(1.5f, satiate.factor());
        Map<ResourceLocation, ReagentData> catalog = Map.of(NUTRIMENT_ID, nutriment);

        HungerPrototypeComponent mouseHunger = mouseHungerPolicy();
        AtomicReference<Float> hunger = new AtomicReference<>(25f);
        AtomicReference<Float> peakHunger = new AtomicReference<>(25f);
        ReagentAttachment stomach = new ReagentAttachment();
        ReagentAttachment body = new ReagentAttachment();
        long expectedSourceCents = cheese.edible().sourceSolution().contentsCents().get(id("nutriment")) * 5;
        assertEquals(expectedSourceCents, stomach.admitUnits(NUTRIMENT, expectedSourceCents, 5_000));
        AtomicLong metabolizedCents = new AtomicLong();
        double grossHunger = 0;

        // One due metabolism/transfer/hunger-second pass per loop; TickHooks processes body before stomach.
        for (int second = 0; second < 75; second++) {
            MetabolismReport report = metabolizeBody(body, catalog, hunger, mouseHunger, metabolizedCents, peakHunger);
            grossHunger += hungerGain(report);
            StomachDigestionTransfer.transfer(stomach, body, catalog, 1_000f);
            var decay = HungerReducer.decayOneSecond(hunger.get(), mouseHunger);
            hunger.set(decay.after());
        }
        MetabolismReport finalDrain = metabolizeBody(body, catalog, hunger, mouseHunger, metabolizedCents, peakHunger);
        grossHunger += hungerGain(finalDrain);

        assertEquals(900L, metabolizedCents.get(), "75 transfers of 12 cents each are metabolized");
        assertTrue(stomach.isEmpty());
        assertTrue(body.isEmpty());
        assertEquals(27d, grossHunger, 2e-6, "actual nutrient effect and metabolism scale produce gross hunger");
        assertEquals(expectedMouseHunger(mouseHunger), hunger.get(), 1e-5,
                "Mouse's resolved 0.5 hunger decay is applied for all 75 elapsed seconds, bounded by zero");
    }

    @Test
    void threeResolvedPumpkinPieSlicesRestoreStarvingMouseThroughRealReagentPipeline() {
        var profiles = new PrototypeManager(); profiles.register(ModIngestionProfiles.TYPE);
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (String name : new String[]{"edible_defaults", "food_base", "pumpkin_pie_slice"})
            raw.put(id(name), json("/data/moonstation14/moonstation14/ingestion_profile/" + name + ".json"));
        profiles.reload(ModIngestionProfiles.TYPE, raw);
        var pie = profiles.snapshot(ModIngestionProfiles.TYPE).get(id("pumpkin_pie_slice"));
        assertEquals(1225, pie.edible().sourceSolution().capacityCents());
        assertEquals(Map.of(id("nutriment"), 750L, id("vitamin"), 350L, id("sugar"), 125L),
                pie.edible().sourceSolution().contentsCents());

        Map<ResourceLocation, ReagentData> catalog = new LinkedHashMap<>();
        for (String name : new String[]{"nutriment", "vitamin", "sugar"}) {
            var reagent = ReagentData.CODEC.parse(JsonOps.INSTANCE,
                    json("/data/moonstation14/moonstation14/reagent/" + name + ".json")).getOrThrow();
            catalog.put(id(name), reagent);
        }
        var stomach = new ReagentAttachment(); var body = new ReagentAttachment();
        long expected = 0;
        for (int i = 0; i < 3; i++) for (var e : pie.edible().sourceSolution().contentsCents().entrySet()) {
            var key = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, e.getKey());
            assertEquals(e.getValue(), stomach.admitUnits(key, e.getValue(), 5_000));
            expected += e.getValue();
        }
        HungerPrototypeComponent policy = mouseHungerPolicy();
        AtomicReference<Float> hunger = new AtomicReference<>(5f);
        AtomicReference<Float> peakHunger = new AtomicReference<>(5f);
        AtomicLong metabolized = new AtomicLong();
        double gross = 0;
        long stomachRemoved = 0;
        long bodyAdded = 0;
        long digestionLoss = 0;
        for (int second = 0; second < 90; second++) {
            var report = metabolizeBody(body, catalog, hunger, policy, metabolized, peakHunger);
            gross += hungerGain(report);
            long stomachBefore = stomach.totalUnits();
            long bodyBefore = body.totalUnits();
            StomachDigestionTransfer.transfer(stomach, body, catalog, 1_000f);
            long removedThisSecond = stomachBefore - stomach.totalUnits();
            long addedThisSecond = body.totalUnits() - bodyBefore;
            stomachRemoved += removedThisSecond;
            bodyAdded += addedThisSecond;
            digestionLoss += removedThisSecond - addedThisSecond;
            hunger.set(HungerReducer.decayOneSecond(hunger.get(), policy).after());
        }
        assertTrue(peakHunger.get() > 15f, "unconditional Nutriment and Vitamin effects take Mouse beyond Peckish; peak=" + peakHunger.get());
        assertTrue(hunger.get() <= policy.overfed());
        assertTrue(gross > 0, "unconditional Nutriment and Vitamin hunger effects actually ran: " + gross);
        assertTrue(metabolized.get() > 0 && metabolized.get() <= expected);
        assertEquals(expected, stomach.totalUnits() + stomachRemoved, "source meal is remaining or digested");
        assertEquals(stomachRemoved, bodyAdded + digestionLoss, "transfer loss is explicit cent-quantized efficacy");
        assertEquals(bodyAdded, metabolized.get() + body.totalUnits(), "body contents are remaining or metabolized");
        assertEquals(1_911L, digestionLoss, "25-cent source quanta at 50% efficacy floor to 12 body cents");
    }

    private static MetabolismReport metabolizeBody(ReagentAttachment body, Map<ResourceLocation, ReagentData> catalog,
                                                     AtomicReference<Float> hunger,
                                                     HungerPrototypeComponent policy, AtomicLong metabolized,
                                                     AtomicReference<Float> peakHunger) {
        return MetabolismSystem.process(body, catalog, MetabolizerProfile.SHARED_BODY, 1_000f,
                RandomSource.create(123L), invocation -> {
                    if (invocation.stage() != MetabolismStage.METABOLITES) return;
                    metabolized.addAndGet(com.juicyslew.moonstation14.ms14.reagent.ReagentUnits
                            .fromFloat(invocation.result().actualRemoved()));
                    for (var effect : invocation.metabolism().effects()) {
                        if (effect instanceof EffectData.SatiateHunger satiate) {
                            // Sugar's hunger effect is conditional; omit it for a conservative lower bound.
                            if (invocation.reagent().location().equals(id("sugar"))) continue;
                            hunger.set(HungerReducer.satiate(hunger.get(), satiate.factor(),
                                    invocation.result().scale(), policy).after());
                            peakHunger.set(Math.max(peakHunger.get(), hunger.get()));
                        }
                    }
                });
    }

    private static double hungerGain(MetabolismReport report) {
        return report.attempts().stream().filter(attempt -> attempt.stage() == MetabolismStage.METABOLITES)
                .mapToDouble(attempt -> attempt.metabolism().effects().stream()
                        .filter(EffectData.SatiateHunger.class::isInstance)
                        .map(EffectData.SatiateHunger.class::cast)
                        .mapToDouble(effect -> effect.factor() * attempt.result().scale()).sum()).sum();
    }

    private static HungerPrototypeComponent mouseHungerPolicy() {
        JsonObject mouse = json("/data/moonstation14/moonstation14/character/mouse.json");
        for (var component : mouse.getAsJsonArray("components")) {
            if ("Hunger".equals(component.getAsJsonObject().get("type").getAsString()))
                return HungerPrototypeComponent.CODEC.parse(JsonOps.INSTANCE, component).getOrThrow();
        }
        throw new AssertionError("Mouse prototype has no Hunger component");
    }

    private static float expectedMouseHunger(HungerPrototypeComponent policy) {
        float value = 25f;
        // The live loop above performs one metabolism pass, then the one-second Hunger activity.
        // Independently run the reducer schedule for the expected bounded result.
        for (int second = 0; second < 75; second++) {
            float metabolizedThisSecond = second == 0 ? 0f : 0.12f;
            if (metabolizedThisSecond > 0f)
                value = HungerReducer.satiate(value, 1.5f, metabolizedThisSecond / .5f, policy).after();
            value = HungerReducer.decayOneSecond(value, policy).after();
        }
        value = HungerReducer.satiate(value, 1.5f, .12f / .5f, policy).after();
        return value;
    }

    private static JsonObject json(String path) {
        try (var stream = NutrimentPipelineTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }
}
