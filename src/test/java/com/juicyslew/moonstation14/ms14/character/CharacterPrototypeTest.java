package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import com.juicyslew.moonstation14.ms14.character.components.StandingStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.NoSlipComponent;
import com.juicyslew.moonstation14.ms14.character.components.StunnableComponent;
import com.juicyslew.moonstation14.ms14.character.components.ReactiveComponent;
import com.juicyslew.moonstation14.ms14.character.components.MetabolizerPrototypeComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterPrototypeTest {
    private static final String RESOURCE = "data/moonstation14/moonstation14/character/human.json";

    @Test
    void slipMarkersInheritAndStandingAndReactiveFieldsPatchWithoutImplicitCapabilities() {
        ResourceLocation parentId = ResourceLocation.parse("moonstation14:slip_parent");
        ResourceLocation childId = ResourceLocation.parse("moonstation14:slip_child");
        JsonObject parent = JsonParser.parseString("""
                {"abstract":true,"components":[{"type":"StandingState","standing_eligible":true,
                 "prone_eligible":false},{"type":"NoSlip"},{"type":"Stunnable"},
                 {"type":"Reactive","reactive_groups":["acidic"],"reactive_methods":["touch"]}]}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"parent":"slip_parent","components":[{"type":"StandingState","prone_eligible":true},
                 {"type":"Reactive","reactive_groups":["flammable"]}]}
                """).getAsJsonObject();
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(parentId, parent, childId, child));
        CharacterData resolved = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(childId);
        assertTrue(resolved.component(NoSlipComponent.class).isPresent());
        assertTrue(resolved.component(StunnableComponent.class).isPresent());
        assertTrue(resolved.component(StandingStateComponent.class).orElseThrow().standingEligible());
        assertTrue(resolved.component(StandingStateComponent.class).orElseThrow().proneEligible());
        assertEquals(List.of(ReactiveComponent.ReactiveGroup.FLAMMABLE),
                resolved.component(ReactiveComponent.class).orElseThrow().reactiveGroups());
        assertEquals(List.of(ReactiveComponent.ReactiveMethod.TOUCH),
                resolved.component(ReactiveComponent.class).orElseThrow().reactiveMethods());
        assertEquals(resolved, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                manager.encodePublishedCatalogs().get(ModCharacters.CHARACTER_TYPE.typeId()).get(childId)).getOrThrow());
        JsonObject invalidParent = parent.deepCopy();
        invalidParent.add("components", JsonParser.parseString("[{\"type\":\"NoSlip\",\"remove\":true}]"));
        var failure = assertThrows(RuntimeException.class, () -> manager.reload(ModCharacters.CHARACTER_TYPE,
                Map.of(parentId, invalidParent, childId, child)));
        assertTrue(failure.getMessage().contains("remove"), failure.getMessage());
        assertEquals(resolved, manager.snapshot(ModCharacters.CHARACTER_TYPE).get(childId));
        for (String legacy : List.of("null", "{}")) {
            JsonObject rawParent = parent.deepCopy();
            rawParent.add("slip_data", JsonParser.parseString(legacy));
            failure = assertThrows(RuntimeException.class, () -> manager.reload(ModCharacters.CHARACTER_TYPE,
                    Map.of(parentId, rawParent, childId, child)));
            assertTrue(failure.getMessage().contains("legacy top-level slip_data"), failure.getMessage());
        }
    }

    @Test
    void inheritedComponentsReachOnlyResolvedCandidateAndFailedStagePreservesPublishedCatalog() throws IOException {
        JsonObject human = readResource(RESOURCE);
        JsonObject parent = new JsonObject();
        parent.addProperty("abstract", true);
        parent.add("components", human.getAsJsonArray("components").deepCopy());
        JsonObject child = human.deepCopy();
        child.remove("components");
        child.addProperty("parent", "component_parent");
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        ResourceLocation parentId = ResourceLocation.parse("moonstation14:component_parent");
        Map<ResourceLocation, JsonObject> raw = Map.of(parentId, parent, ModCharacters.HUMAN_ID, child);
        var token = manager.stage(Map.of(ModCharacters.CHARACTER_TYPE, raw), encoded -> {
            JsonObject candidate = encoded.get(ModCharacters.CHARACTER_TYPE.typeId()).get(ModCharacters.HUMAN_ID);
            assertEquals(200, candidate.getAsJsonArray("components").get(0).getAsJsonObject()
                    .get("maxDamage").getAsInt());
            assertFalse(encoded.get(ModCharacters.CHARACTER_TYPE.typeId()).containsKey(parentId));
        });
        assertTrue(manager.snapshot(ModCharacters.CHARACTER_TYPE).keys().isEmpty());
        assertTrue(manager.commit(token));
        var published = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertEquals(200, published.get(ModCharacters.HUMAN_ID).barotrauma().orElseThrow().maxDamage());
        assertFalse(child.has("components"));
        assertFalse(parent.has("slip_data"));

        JsonObject patched = child.deepCopy();
        patched.add("components", JsonParser.parseString("[{\"type\":\"Barotrauma\",\"maxDamage\":270}]"));
        var staged = manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                Map.of(parentId, parent, ModCharacters.HUMAN_ID, patched)));
        assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));
        JsonObject malformed = child.deepCopy();
        malformed.add("components", JsonParser.parseString("[{\"type\":\"Barotrauma\",\"remove\":false}]"));
        var failure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                Map.of(parentId, parent, ModCharacters.HUMAN_ID, malformed))));
        assertTrue(failure.getMessage().contains("moonstation14:human"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Barotrauma"), failure.getMessage());
        assertTrue(failure.getMessage().contains("$.components[0].remove"), failure.getMessage());
        assertTrue(failure.getMessage().contains("unknown field"), failure.getMessage());
        assertFalse(manager.commit(staged));
        assertFalse(manager.commitStagedReload());
        assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));
        ResourceLocation componentFreeParentId = ResourceLocation.parse("moonstation14:component_free_parent");
        JsonObject componentFreeParent = new JsonObject();
        componentFreeParent.addProperty("abstract", true);
        JsonObject componentFreeChild = child.deepCopy();
        componentFreeChild.addProperty("parent", "component_free_parent");
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(componentFreeParentId, componentFreeParent,
                ModCharacters.HUMAN_ID, componentFreeChild));
        assertTrue(manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ModCharacters.HUMAN_ID).barotrauma().isEmpty());
        assertTrue(published.get(ModCharacters.HUMAN_ID).barotrauma().isPresent());
        assertFalse(manager.encodePublishedCatalogs().get(ModCharacters.CHARACTER_TYPE.typeId())
                .get(ModCharacters.HUMAN_ID).has("components"));
    }

    @Test
    void fragmentValidationRejectsHiddenParentAndChildErrorsWithoutPublishing() throws IOException {
        JsonObject human = readResource(RESOURCE);
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, human));
        var published = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        ResourceLocation parentId = ResourceLocation.parse("moonstation14:partial_parent");
        JsonObject child = human.deepCopy();
        child.addProperty("parent", "partial_parent");
        child.add("components", JsonParser.parseString("[{\"type\":\"Barotrauma\",\"maxDamage\":250}]"));
        for (String invalid : List.of("\"unexpected\":1", "\"maxDamage\":null",
                "\"damage\":null", "\"damage\":{\"types\":{\"Blunt\":1}}")) {
            JsonObject parent = new JsonObject();
            parent.addProperty("abstract", true);
            parent.add("components", JsonParser.parseString("[{\"type\":\"Barotrauma\"," + invalid + "}]"));
            var failure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                    ModCharacters.CHARACTER_TYPE, Map.of(parentId, parent, ModCharacters.HUMAN_ID, child))));
            assertTrue(failure.getMessage().contains("moonstation14:partial_parent"), failure.getMessage());
            assertTrue(failure.getMessage().contains("Barotrauma"), failure.getMessage());
            assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));
        }
        JsonObject parent = new JsonObject();
        parent.addProperty("abstract", true);
        parent.add("components", JsonParser.parseString("[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"blunt\":0.5}}}]"));
        JsonObject invalidChild = child.deepCopy();
        invalidChild.add("components", JsonParser.parseString("[{\"type\":\"Barotrauma\",\"damage\":null}]"));
        ResourceLocation descendantId = ResourceLocation.parse("moonstation14:descendant");
        JsonObject descendant = human.deepCopy();
        descendant.addProperty("parent", "human");
        descendant.add("components", human.getAsJsonArray("components").deepCopy());
        var failure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                ModCharacters.CHARACTER_TYPE, Map.of(parentId, parent, ModCharacters.HUMAN_ID, invalidChild,
                        descendantId, descendant))));
        assertTrue(failure.getMessage().contains("moonstation14:human"), failure.getMessage());
        assertTrue(failure.getMessage().contains("$.components[0].damage"), failure.getMessage());
        assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));

        JsonObject incomplete = human.deepCopy();
        incomplete.add("components", JsonParser.parseString("[{\"type\":\"Barotrauma\",\"maxDamage\":200}]"));
        failure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, incomplete))));
        assertTrue(failure.getMessage().contains("$.components[0].damage"), failure.getMessage());
        assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));
    }

    @Test
    void humanPrototypeIsTypedAndRoundTripsThroughThePrototypeCatalog() throws IOException {
        JsonObject raw;
        try (var stream = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Missing resource " + RESOURCE);
            raw = JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        CharacterData data = CharacterData.CODEC.parse(JsonOps.INSTANCE, raw).getOrThrow();
        assertTrue(data.component(StunnableComponent.class).isPresent());
        assertTrue(data.component(NoSlipComponent.class).isEmpty());
        assertTrue(data.component(StandingStateComponent.class).orElseThrow().standingEligible());
        assertTrue(data.component(StandingStateComponent.class).orElseThrow().proneEligible());
        var reactive = data.component(ReactiveComponent.class).orElseThrow();
        assertEquals(List.of(ReactiveComponent.ReactiveGroup.FLAMMABLE, ReactiveComponent.ReactiveGroup.EXTINGUISH,
                ReactiveComponent.ReactiveGroup.ACIDIC), reactive.reactiveGroups());
        assertEquals(List.of(ReactiveComponent.ReactiveMethod.TOUCH), reactive.reactiveMethods());
        assertEquals(2.5, data.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).orElseThrow().walkSpeed());
        assertEquals(List.of(ResourceLocation.parse("minecraft:player"), ResourceLocation.parse("minecraft:villager")),
                data.hostEntityTypes());
        assertEquals(List.of("left", "right"), data.component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).orElseThrow().hands());
        assertEquals(java.util.Set.of(MetabolizerTypeEnum.HUMAN), data.component(MetabolizerPrototypeComponent.class).orElseThrow().types());
        var thermal = data.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureComponent.class).orElseThrow();
        assertEquals(Math.PI * 0.35 * 0.35 * 185, thermal.massKg(), 1e-12);
        assertEquals(42.0, thermal.specificHeatJoulesPerKgKelvin());
        assertEquals(310.15, thermal.currentKelvin());
        assertEquals(Math.PI * 0.35 * 0.35 * 185 * 42, thermal.toProfile().bodyHeatCapacityJoulesPerKelvin(), 1e-9);
        assertEquals(310.15, data.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().normalBodyTemperatureKelvin());
        assertEquals(800, data.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().metabolismHeatJoulesPerSecond());
        assertEquals(100, data.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().radiatedHeatJoulesPerSecond());
        assertEquals(500, data.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().implicitHeatRegulationJoulesPerSecond());
        assertEquals(2000, data.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().sweatHeatRegulationJoulesPerSecond());
        assertEquals(2000, data.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().shiveringHeatRegulationJoulesPerSecond());
        assertEquals(7000, thermal.spaceHeatCapacityJoulesPerKelvin());
        assertEquals(8, thermal.spaceHeatScale());
        var humanDamage = data.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent.class).orElseThrow();
        assertEquals(325, humanDamage.heatDamageThresholdKelvin());
        assertEquals(260, humanDamage.coldDamageThresholdKelvin());
        assertEquals(1.5, humanDamage.heatDamagePerSecond());
        assertEquals(0.1, humanDamage.coldDamagePerSecond());
        assertEquals(8, humanDamage.damageCap());
        assertEquals(0.1, thermal.atmosphereTransferEfficiency());
        assertEquals(2.7, thermal.spaceTemperatureKelvin());
        var blood = data.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow();
        var lungs = data.component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class).orElseThrow().policy();
        assertEquals(1.0, blood.bleedPuddleThreshold());
         assertEquals(5.0, lungs.maxSaturation());
         assertEquals(5.0, lungs.initialSaturation());
         assertEquals(-2.0, lungs.minSaturation());
         assertEquals(0.5, lungs.breathVolumeLiters());
         assertEquals(1144.0, organFromResource("human").breathMolesToSaturationMultiplier());
         assertEquals(Map.of("poison", 1.0), organFromResource("human").toxicGasDamagePerMole().get("plasma"));
         assertEquals(Map.of("radiation", 1.0), organFromResource("human").toxicGasDamagePerMole().get("tritium"));
         assertFalse(organFromResource("human").toxicGasDamagePerMole().containsKey("nitrogen"));
        assertEquals(Map.of("blunt", 0.5, "heat", 0.1), data.component(BarotraumaComponent.class).orElseThrow().damage().types());
        assertEquals(200, data.barotrauma().orElseThrow().maxDamage());
        assertFalse(raw.getAsJsonArray("components").get(1).getAsJsonObject().has("initial_volume"));
        assertFalse(raw.getAsJsonArray("components").get(1).getAsJsonObject().has("max_volume"));
        assertEquals(Map.of("moonstation14:blood", 300.0), blood.referenceSolution());
        assertEquals(List.of(ResourceLocation.parse("moonstation14:blood")), blood.metabolismExclusions());
        assertEquals(2.0, blood.maxVolumeModifier());
        assertEquals(600.0, referenceTotal(blood.referenceSolution()) * blood.maxVolumeModifier());
        assertEquals(Map.of("blunt", 0.08, "piercing", 0.2, "slash", 0.25, "heat", -0.5), blood.damageBleedMultipliers());
        assertFalse(blood.bloodlossIgnoreResistances());
        assertEquals(data, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow());

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, raw));
        assertEquals(data, manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ModCharacters.HUMAN_ID));
        JsonObject encoded = manager.encodePublishedCatalogs()
                .get(ModCharacters.CHARACTER_TYPE.typeId()).get(ModCharacters.HUMAN_ID);
        assertEquals(data, CharacterData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void legacySlipOnlyAndPigPrototypeRemainTypedAndRoundTrip() throws IOException {
        JsonObject empty = new JsonObject();
        CharacterData emptyData = CharacterData.CODEC.parse(JsonOps.INSTANCE, empty).getOrThrow();
        assertTrue(emptyData.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).isEmpty());
        assertTrue(emptyData.component(StunnableComponent.class).isEmpty());
        assertTrue(emptyData.component(StandingStateComponent.class).isEmpty());
        assertTrue(emptyData.component(ReactiveComponent.class).isEmpty());
        assertEquals(emptyData, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, emptyData).getOrThrow()).getOrThrow());
        assertEquals(emptyData, new CharacterData());

        JsonObject pigJson = readResource("data/moonstation14/moonstation14/character/pig.json");
        CharacterData pig = CharacterData.CODEC.parse(JsonOps.INSTANCE, pigJson).getOrThrow();
        assertEquals(4.0, pig.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).orElseThrow().walkSpeed());
        assertEquals(4.0, com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy
                .fromCharacterData(pig).sprintSpeedPerSecond());
        assertNotEquals(2.5, pig.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).orElseThrow().walkSpeed());
        assertEquals(List.of(ResourceLocation.parse("minecraft:pig")), pig.hostEntityTypes());
        assertTrue(pig.component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).isEmpty(), "pig prototypes do not acquire implicit hands");
        assertEquals(java.util.Set.of(MetabolizerTypeEnum.ANIMAL), pig.component(MetabolizerPrototypeComponent.class).orElseThrow().types());
        var pigThermal = pig.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureComponent.class).orElseThrow();
        assertEquals(Math.PI * 0.35 * 0.35 * 250, pigThermal.massKg(), 1e-12);
        assertEquals(1.0, pig.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent.class).orElseThrow().coldDamagePerSecond());
        assertEquals(250, pig.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().implicitHeatRegulationJoulesPerSecond());
        assertEquals(500, pig.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().shiveringHeatRegulationJoulesPerSecond());
        assertEquals(500, pig.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().sweatHeatRegulationJoulesPerSecond());
        assertEquals(800, pig.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().metabolismHeatJoulesPerSecond());
        assertEquals(100, pig.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().radiatedHeatJoulesPerSecond());
        assertEquals(310.15, pig.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().normalBodyTemperatureKelvin());
        assertEquals(2, pig.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow().thermalRegulationThresholdKelvin());
         assertEquals(Map.of("moonstation14:blood", 150.0), pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().referenceSolution());
         assertEquals(List.of(ResourceLocation.parse("moonstation14:blood")),
                pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().metabolismExclusions());
        assertEquals(2.0, pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().maxVolumeModifier());
         assertEquals(1.0, pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().bleedPuddleThreshold());
         assertEquals(dataFromResource(RESOURCE).component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().damageBleedMultipliers(),
                 pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().damageBleedMultipliers());
         assertEquals(3.0, pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().updateIntervalSeconds());
         assertEquals(10.0, pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().maxBleedRate());
         assertEquals(0.33, pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().bleedDecayPerUpdate());
         assertEquals(1.0, pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().bloodRefreshPerUpdate());
         assertEquals(0.9, pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().bloodlossThresholdFraction());
         assertEquals(Map.of("bloodloss", 0.5), pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().bloodlossDamagePerUpdate());
         assertEquals(Map.of("bloodloss", 1.0), pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().bloodlossHealPerUpdate());
          var pigRespirator = pig.component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class).orElseThrow().policy();
          assertEquals(2.0, pigRespirator.breathIntervalSeconds());
          assertEquals(5.0, pigRespirator.initialSaturation());
          assertEquals(0.5, pigRespirator.breathVolumeLiters());
          assertEquals(2.0, pigRespirator.suffocationDamagePerUpdate());
         assertEquals(Map.of("caustic", 0.3), organFromResource("pig").toxicGasDamagePerMole().get("ammonia"));
        assertNotEquals(dataFromResource(RESOURCE).barotrauma(), pig.barotrauma());
         assertNotEquals(dataFromResource(RESOURCE).component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class), pig.component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class));
         assertEquals(300.0, referenceTotal(pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().referenceSolution())
                * pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().maxVolumeModifier());
        assertFalse(pigJson.getAsJsonArray("components").get(1).getAsJsonObject().has("initial_volume"));
        assertFalse(pigJson.getAsJsonArray("components").get(1).getAsJsonObject().has("max_volume"));
         assertEquals(dataFromResource(RESOURCE).component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().referenceSolution().keySet(),
                 pig.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().referenceSolution().keySet());
        assertTrue(pig.component(StunnableComponent.class).isEmpty());
        assertTrue(pig.component(NoSlipComponent.class).isPresent());
        assertFalse(pig.component(StandingStateComponent.class).orElseThrow().standingEligible());
        assertFalse(pig.component(StandingStateComponent.class).orElseThrow().proneEligible());
        assertTrue(pig.component(ReactiveComponent.class).isEmpty());
        assertEquals(pig, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, pig).getOrThrow()).getOrThrow());
    }

    @Test
    void metabolizerTypesRequireExplicitCanonicalDistinctPolicy() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject absent = valid.deepCopy();
        absent.getAsJsonArray("components").remove(absent.getAsJsonArray("components").size() - 1);
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, absent).getOrThrow()
                .component(MetabolizerPrototypeComponent.class).isEmpty());
        for (String invalidValue : List.of("null", "1", "\"human\"", "[null]", "[1]",
                "[\"Human\"]", "[\"unknown\"]", "[\"human\",\"human\"]")) {
            JsonObject invalid = valid.deepCopy();
            invalid.getAsJsonArray("components").get(invalid.getAsJsonArray("components").size() - 1)
                    .getAsJsonObject().add("types", JsonParser.parseString(invalidValue));
            var failure = CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().orElseThrow();
            assertTrue(failure.message().contains(".types"), failure.message());
        }
        JsonObject empty = valid.deepCopy();
        empty.getAsJsonArray("components").get(empty.getAsJsonArray("components").size() - 1)
                .getAsJsonObject().add("types", JsonParser.parseString("[]"));
        assertEquals(java.util.Set.of(), CharacterData.CODEC.parse(JsonOps.INSTANCE, empty)
                .getOrThrow().component(MetabolizerPrototypeComponent.class).orElseThrow().types());
        for (String old : List.of("null", "[]", "[\"human\"]")) {
            JsonObject legacy = valid.deepCopy();
            legacy.add("metabolizer_types", JsonParser.parseString(old));
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, legacy).error().orElseThrow()
                    .message().contains("legacy top-level metabolizer_types"));
            JsonObject parent = new JsonObject();
            parent.addProperty("abstract", true);
            parent.add("metabolizer_types", JsonParser.parseString(old));
            JsonObject child = absent.deepCopy();
            child.addProperty("parent", "metabolizer_parent");
            PrototypeManager manager = new PrototypeManager();
            manager.register(ModCharacters.CHARACTER_TYPE);
            var failure = assertThrows(RuntimeException.class, () -> manager.reload(ModCharacters.CHARACTER_TYPE,
                    Map.of(ResourceLocation.parse("moonstation14:metabolizer_parent"), parent, ModCharacters.HUMAN_ID, child)));
            assertTrue(failure.getMessage().contains("legacy top-level metabolizer_types"), failure.getMessage());
        }
        JsonObject missing = valid.deepCopy();
        missing.getAsJsonArray("components").get(missing.getAsJsonArray("components").size() - 1)
                .getAsJsonObject().remove("types");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, missing).error().orElseThrow().message().contains(".types"));
        JsonObject unknown = valid.deepCopy();
        unknown.getAsJsonArray("components").get(unknown.getAsJsonArray("components").size() - 1)
                .getAsJsonObject().addProperty("remove", true);
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, unknown).error().orElseThrow().message().contains(".remove"));
    }

    @Test
    void inheritedMetabolizerTypesReplaceAtomicallyAndFragmentsAreAudited() {
        ResourceLocation parentId = ResourceLocation.parse("moonstation14:metabolizer_parent");
        ResourceLocation childId = ResourceLocation.parse("moonstation14:metabolizer_child");
        JsonObject parent = JsonParser.parseString("""
                {"abstract":true,"components":[{"type":"Metabolizer","types":["human","animal"]}]}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"parent":"metabolizer_parent","components":[{"type":"Metabolizer","types":["rat"]}]}
                """).getAsJsonObject();
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(parentId, parent, childId, child));
        CharacterData resolved = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(childId);
        assertEquals(java.util.Set.of(MetabolizerTypeEnum.RAT), resolved.component(MetabolizerPrototypeComponent.class).orElseThrow().types());
        assertEquals(resolved, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                manager.encodePublishedCatalogs().get(ModCharacters.CHARACTER_TYPE.typeId()).get(childId)).getOrThrow());
        for (String fragment : List.of("\"types\":null", "\"types\":[\"Human\"]", "\"types\":[\"human\",\"human\"]", "\"remove\":true")) {
            JsonObject invalid = JsonParser.parseString("{\"abstract\":true,\"components\":[{\"type\":\"Metabolizer\"," + fragment + "}]}").getAsJsonObject();
            var failure = assertThrows(RuntimeException.class, () -> manager.reload(ModCharacters.CHARACTER_TYPE,
                    Map.of(parentId, invalid, childId, child)));
            assertTrue(failure.getMessage().contains("Metabolizer"), failure.getMessage());
            assertEquals(resolved, manager.snapshot(ModCharacters.CHARACTER_TYPE).get(childId));
        }
    }

    @Test
    void thermalPolicyIsStrictAndOptional() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject thermal = thermalComponent(valid, "heat_damage_threshold_kelvin");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withoutThermal(valid)).getOrThrow()
                .component(com.juicyslew.moonstation14.ms14.character.components.TemperatureComponent.class).isEmpty());

        for (JsonObject invalid : List.of(
                nonFiniteThermal(valid),
                withThermalField(valid, "mass_kg", "-1"),
                withThermalField(valid, "unknown", "1"),
                withoutThermalField(valid, "damage_cap"),
                withoutThermalField(valid, "normal_body_temperature_kelvin"),
                 withoutThermalField(valid, "radiated_heat_joules_per_second"),
                 withoutThermalField(valid, "space_heat_scale"),
                 withThermalField(valid, "space_heat_capacity_joules_per_kelvin", "0"),
                 withThermalField(valid, "space_heat_scale", "NaN"),
                 withThermalField(valid, "space_temperature_kelvin", "null"),
                withThermalField(valid, "sweat_heat_regulation_joules_per_second", "-1"),
                withThermalField(valid, "metabolism_heat_joules_per_second", "1000001"),
                withThermalField(valid, "thermal_regulation_threshold_kelvin", "NaN"),
                withThermalField(valid, "normal_body_temperature_kelvin", "260"),
                withThermalField(valid, "radiated_heat_joules_per_second", "null"),
                withThermalField(valid, "implicit_heat_regulation_joules_per_second", "\"1\""),
                withThermalField(valid, "current_kelvin", "325"),
                withThermalField(valid, "heat_damage_threshold_kelvin", "250"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent(), invalid.toString());
        }
        assertTrue(thermal.get("heat_damage_threshold_kelvin").getAsDouble()
                > thermal.get("cold_damage_threshold_kelvin").getAsDouble());
        JsonObject customHost = hosts(withoutMovement(valid), "[\"test:thermal_living_host\"]");
        CharacterData mapped = CharacterData.CODEC.parse(JsonOps.INSTANCE, customHost).getOrThrow();
        assertTrue(mapped.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureComponent.class).isPresent());
        assertEquals(List.of(ResourceLocation.parse("test:thermal_living_host")), mapped.hostEntityTypes());
        assertTrue(mapped.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).isEmpty());
    }

    @Test
    void thermalMembershipInheritanceAndFailedCandidateDoNotPublish() throws IOException {
        JsonObject human = readResource(RESOURCE);
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, human));
        JsonObject temperatureOnly = human.deepCopy();
        var parts = temperatureOnly.getAsJsonArray("components");
        for (int i = parts.size() - 1; i >= 0; i--) {
            String type = parts.get(i).getAsJsonObject().get("type").getAsString();
            if (type.equals("TemperatureDamage") || type.equals("ThermalRegulator")) parts.remove(i);
        }
        var parsed = CharacterData.CODEC.parse(JsonOps.INSTANCE, temperatureOnly).getOrThrow();
        assertTrue(parsed.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureComponent.class).isPresent());
        assertTrue(parsed.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent.class).isEmpty());
        assertTrue(parsed.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).isEmpty());
        JsonObject orphan = withoutThermal(human);
        orphan.getAsJsonArray("components").add(thermalComponent(human, "damage_cap").deepCopy());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, orphan).error().orElseThrow().message().contains("requires Temperature"));
        assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                Map.of(ModCharacters.HUMAN_ID, orphan))));
        assertEquals(CharacterData.CODEC.parse(JsonOps.INSTANCE, human).getOrThrow(),
                manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ModCharacters.HUMAN_ID));
        JsonObject parent = new JsonObject(); parent.addProperty("abstract", true);
        parent.add("components", JsonParser.parseString("[{\"type\":\"Temperature\",\"current_kelvin\":310.15}]"));
        JsonObject child = human.deepCopy(); child.addProperty("parent", "base");
        thermalComponent(child, "current_kelvin").remove("current_kelvin");
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ResourceLocation.parse("moonstation14:base"), parent,
                ModCharacters.HUMAN_ID, child));
        assertEquals(310.15, manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ModCharacters.HUMAN_ID)
                .component(com.juicyslew.moonstation14.ms14.character.components.TemperatureComponent.class)
                .orElseThrow().currentKelvin());
        for (String raw : List.of("null", "{}")) {
            JsonObject legacy = parent.deepCopy(); legacy.add("thermal", JsonParser.parseString(raw));
            assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                    Map.of(ResourceLocation.parse("moonstation14:base"), legacy, ModCharacters.HUMAN_ID, child))));
        }
    }

    @Test
    void orphanThermalComponentsRejectCandidatesWithoutReplacingPublishedSnapshot() throws IOException {
        JsonObject human = readResource(RESOURCE);
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, human));
        var published = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        for (String retained : List.of("TemperatureDamage", "ThermalRegulator")) {
            JsonObject orphan = withoutThermal(human);
            for (var component : human.getAsJsonArray("components")) {
                if (retained.equals(component.getAsJsonObject().get("type").getAsString()))
                    orphan.getAsJsonArray("components").add(component.deepCopy());
            }
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, orphan).error().orElseThrow()
                    .message().contains("requires Temperature"), retained);
            var failure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                    ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, orphan))));
            assertTrue(failure.getMessage().contains("requires Temperature"), failure.getMessage());
            assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));
        }
    }

    @Test
    void initialTemperatureAndRegulatorSetpointMustStayStrictlyInsideDamageThresholds() throws IOException {
        JsonObject human = readResource(RESOURCE);
        for (String field : List.of("current_kelvin", "normal_body_temperature_kelvin")) {
            for (String boundary : List.of("260", "325")) {
                JsonObject invalid = withThermalField(human, field, boundary);
                var error = CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().orElseThrow();
                assertTrue(error.message().contains("between damage thresholds"), error.message());
            }
            for (String inside : List.of("260.01", "324.99")) {
                assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                        withThermalField(human, field, inside)).result().isPresent(), field + "=" + inside);
            }
        }
    }

    @Test
    void rejectsInvalidMovementAndHostMappings() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        for (JsonObject invalid : List.of(
                replaceMovement(valid, "mode", "\"airborne\""),
                replaceMovement(valid, "acceleration", "-1"),
                nonFiniteMovement(valid),
                withMovement(valid, "unexpected", "1"),
                hosts(valid, "[\"not a resource id\"]"),
                hosts(valid, "[\"minecraft:pig\",\"minecraft:pig\"]"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent(), invalid.toString());
        }
    }

    @Test
    void hostLookupIsIndexedPerPublishedCatalogAndDuplicateClaimsRejectReload() throws IOException {
        JsonObject human = readResource(RESOURCE);
        JsonObject pig = readResource("data/moonstation14/moonstation14/character/pig.json");
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(
                ModCharacters.HUMAN_ID, human,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "pig"), pig));

        var published = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertEquals(Optional.of(ModCharacters.HUMAN_ID),
                ModCharacters.characterForHost(published, ResourceLocation.parse("minecraft:player")));
        assertEquals(Optional.of(ModCharacters.HUMAN_ID),
                ModCharacters.characterForHost(published, ResourceLocation.parse("minecraft:villager")));
        assertEquals(Optional.of(ResourceLocation.fromNamespaceAndPath("moonstation14", "pig")),
                ModCharacters.characterForHost(published, ResourceLocation.parse("minecraft:pig")));
        assertEquals(300.0, referenceTotal(published.get(ModCharacters.characterForHost(published,
                ResourceLocation.parse("minecraft:player")).orElseThrow()).component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().referenceSolution()));
         assertEquals(150.0, referenceTotal(published.get(ModCharacters.characterForHost(published,
                ResourceLocation.parse("minecraft:pig")).orElseThrow()).component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().referenceSolution()));
        assertEquals(Optional.empty(),
                ModCharacters.characterForHost(published, ResourceLocation.parse("minecraft:zombie")));

        JsonObject conflictingPig = hosts(pig, "[\"minecraft:villager\"]");
        var failure = assertThrows(RuntimeException.class, () -> manager.reload(ModCharacters.CHARACTER_TYPE,
                Map.of(ModCharacters.HUMAN_ID, human,
                        ResourceLocation.fromNamespaceAndPath("moonstation14", "pig"), conflictingPig)));
        assertTrue(failure.getMessage().contains("minecraft:villager"));
        assertTrue(failure.getMessage().contains("moonstation14:human"));
        assertTrue(failure.getMessage().contains("moonstation14:pig"));
        assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE),
                "a rejected candidate must leave the previously published catalog intact");
    }

    @Test
    void bloodMappedHostDoesNotRequireOrImplyMovementPolicy() throws IOException {
        JsonObject valid = hosts(withoutMovement(readResource(RESOURCE)), "[\"test:configured_living_host\"]");
        CharacterData data = CharacterData.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow();
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).isPresent(), "blood remains available through the explicit host mapping");
        assertTrue(data.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).isEmpty(), "blood enrollment must not synthesize movement authority");
        assertEquals(List.of(ResourceLocation.parse("test:configured_living_host")), data.hostEntityTypes());
    }

    @Test
    void bloodPolicyIsOptionalButStrictAndRequiresEveryTypedField() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject withoutBlood = valid.deepCopy();
        withoutBlood.remove("components");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withoutBlood).getOrThrow().component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).isEmpty());
        JsonObject legacy = valid.deepCopy();
        legacy.add("blood", legacy.getAsJsonArray("components").get(1).getAsJsonObject().deepCopy());
        for (JsonObject rejected : List.of(legacy, bloodField(valid, "unknown", "1"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, rejected).error().isPresent());
        }
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, legacy).error().orElseThrow().message()
                .contains("manually move"));
        PrototypeManager legacyManager = new PrototypeManager();
        legacyManager.register(ModCharacters.CHARACTER_TYPE);
        JsonObject abstractLegacy = legacy.deepCopy();
        abstractLegacy.addProperty("abstract", true);
        assertTrue(assertThrows(RuntimeException.class, () -> legacyManager.stage(Map.of(
                ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, abstractLegacy)))).getMessage()
                .contains("$.blood"));
        JsonObject duplicateBloodstream = valid.deepCopy();
        duplicateBloodstream.getAsJsonArray("components").add(valid.getAsJsonArray("components").get(1).deepCopy());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, duplicateBloodstream).error().orElseThrow().message()
                  .contains("$.components[" + (valid.getAsJsonArray("components").size()) + "].type"));
        var blood = CharacterData.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow().component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow();
        assertEquals(Map.of("moonstation14:blood", 300.0), blood.referenceSolution());
        assertEquals(List.of(ResourceLocation.parse("moonstation14:blood")), blood.metabolismExclusions());
        assertEquals(Map.of("bloodloss", 0.5), blood.bloodlossDamagePerUpdate());
        assertFalse(blood.bloodlossIgnoreResistances());
        CharacterData bypass = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                bloodField(valid, "bloodloss_ignore_resistances", "true")).getOrThrow();
        assertTrue(bypass.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().bloodlossIgnoreResistances());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, bypass).getOrThrow()).getOrThrow()
                .component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).orElseThrow().bloodlossIgnoreResistances());
        for (JsonObject invalid : List.of(withoutBloodField(valid, "bloodloss_ignore_resistances"),
                bloodField(valid, "bloodloss_ignore_resistances", "1"),
                bloodField(valid, "bloodloss_ignore_resistances", "\"false\""),
                bloodField(valid, "bloodloss_ignore_resistances", "null"))) {
            var error = CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().orElseThrow();
            assertTrue(error.message().contains("$.components[1].bloodloss_ignore_resistances"), error.message());
            assertFalse(error.message().contains("NullPointerException"), error.message());
        }
        JsonObject nullFlag = bloodField(valid, "bloodloss_ignore_resistances", "null");
        var auditError = assertThrows(IllegalArgumentException.class, () -> CharacterSchemaAudit.audit(nullFlag));
        assertTrue(auditError.getMessage().contains("$.components[1].bloodloss_ignore_resistances"), auditError.getMessage());

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, valid));
        var published = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        for (JsonObject invalid : List.of(nullFlag,
                withoutBloodField(valid, "bloodloss_ignore_resistances"),
                bloodField(valid, "bloodloss_ignore_resistances", "1"))) {
            var failure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                    ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, invalid))));
            assertTrue(failure.getMessage().contains("moonstation14:human"), failure.getMessage());
            assertTrue(failure.getMessage().contains("$.components[1].bloodloss_ignore_resistances"), failure.getMessage());
            assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));
        }

        for (JsonObject invalid : List.of(
                bloodField(valid, "unknown", "1"),
                 bloodField(valid, "bloodloss_damage_cap_per_update", "0.5"),
                bloodField(valid, "update_interval_seconds", "0.001"),
                bloodField(valid, "bloodloss_threshold_fraction", "1.1"),
                bloodField(valid, "bleed_decay_per_update", "-1"),
                bloodField(valid, "damage_bleed_multipliers", "[]"),
                bloodField(valid, "bloodloss_damage_per_update", "{\"slash\":-1}"),
                bloodField(valid, "damage_bleed_multipliers", "{\"not_damage\":1}"),
                bloodField(valid, "bloodloss_heal_per_update", "{\"blunt\":\"1\"}"),
                withoutBloodField(valid, "reference_solution"),
                bloodField(valid, "reference_solution", "null"),
                bloodField(valid, "reference_solution", "[]"),
                bloodField(valid, "reference_solution", "{}"),
                bloodField(valid, "reference_solution", "{\"moonstation14:blood\":0}"),
                bloodField(valid, "reference_solution", "{\"moonstation14:blood\":0.001}"),
                bloodField(valid, "reference_solution", "{\"blood\":300}"),
                bloodField(valid, "reference_solution", "{\"moonstation14:bad id\":300}"),
                bloodField(valid, "reference_solution", "{\"moonstation14:blood\":\"300\"}"),
                bloodField(valid, "reference_solution", "{\"moonstation14:blood\":NaN}"),
                bloodField(valid, "reference_solution", "{\"moonstation14:blood\":1000001}"),
                withoutBloodField(valid, "metabolism_exclusions"),
                bloodField(valid, "metabolism_exclusions", "[]"),
                bloodField(valid, "metabolism_exclusions", "[\"moonstation14:other\"]"),
                bloodField(valid, "metabolism_exclusions", "[\"moonstation14:blood\",\"moonstation14:blood\"]"),
                bloodField(valid, "metabolism_exclusions", "[\"blood\"]"),
                bloodField(valid, "metabolism_exclusions", "[1]"),
                bloodField(valid, "max_volume_modifier", "0.99"),
                 bloodField(valid, "max_volume_modifier", "NaN"),
                 withoutBloodField(valid, "max_volume_modifier"),
                 withoutBloodField(valid, "bleed_puddle_threshold"),
                  bloodField(valid, "bleed_puddle_threshold", "0"),
                  bloodField(valid, "bleed_puddle_threshold", "0.001"),
                  bloodField(valid, "bleed_puddle_threshold", "null"),
                  bloodField(valid, "bleed_puddle_threshold", "NaN"),
                  bloodField(valid, "bleed_puddle_threshold", "1000001"),
                  bloodField(valid, "pending_leak_capacity", "10"),
                 bloodField(valid, "unknown_spill_option", "1"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent(), invalid.toString());
        }
        for (String legacyField : List.of("initial_volume", "max_volume")) {
            var error = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    bloodField(valid, legacyField, "1")).error().orElseThrow();
            assertTrue(error.message().contains("$.components[1]." + legacyField), error.message());
            assertTrue(error.message().contains("unknown field"), error.message());
        }
        assertEquals("blood", readResource("data/moonstation14/moonstation14/reagent/blood.json").get("id").getAsString());
        assertEquals("sulfurblood", readResource("data/moonstation14/moonstation14/reagent/sulfurblood.json").get("id").getAsString());
    }

    @Test
    void lungPolicyIsSplitAndStrict() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject standalone = valid.deepCopy();
        standalone.getAsJsonArray("components").remove(1);
        removeComponent(standalone, "Stomach");
        standalone = withoutThermal(standalone);
        standalone = withoutMovement(standalone);
        CharacterData standaloneData = CharacterData.CODEC.parse(JsonOps.INSTANCE, standalone).getOrThrow();
        assertTrue(standaloneData.component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class).isPresent());
        assertTrue(standaloneData.component(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent.class).map(com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent::policy).isEmpty());
        assertTrue(standaloneData.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureComponent.class).isEmpty());
        assertTrue(standaloneData.component(com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent.class).isEmpty());
        assertTrue(standaloneData.barotrauma().isPresent());
        assertEquals(standaloneData, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, standaloneData).getOrThrow()).getOrThrow());

        JsonObject noLungs = valid.deepCopy();
        noLungs.getAsJsonArray("components").remove(4);
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, noLungs).getOrThrow()
                .component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class).isEmpty());
        for (JsonObject invalid : List.of(
                 withoutLungField(valid, "breath_interval_seconds"),
                 lungField(valid, "unexpected", "1"),
                 lungField(valid, "initial_saturation", "null"),
                 lungField(valid, "suffocation_ignore_resistances", "1"),
                 lungField(valid, "breath_interval_seconds", "0"),
                 lungField(valid, "breath_interval_seconds", "0.001"),
                  lungField(valid, "breath_volume_liters", "-1"),
                 lungField(valid, "initial_saturation", "101"),
                 lungField(valid, "suffocation_threshold", "101"),
                  lungField(valid, "max_lung_moles", "6"),
                  lungsBlock(valid, "null"))) {
            var error = CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().orElseThrow();
            assertFalse(error.message().contains("NullPointerException"), error.message());
        }
        JsonObject pig = readResource("data/moonstation14/moonstation14/character/pig.json");
        assertNotEquals(CharacterData.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow().component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class),
                CharacterData.CODEC.parse(JsonOps.INSTANCE, pig).getOrThrow().component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class));
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        for (JsonObject source : List.of(valid, pig)) {
            JsonObject legacy = source.deepCopy(); legacy.add("lungs", new JsonObject());
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, legacy).error().orElseThrow().message().contains("move mob fields"));
            legacy.addProperty("abstract", true);
            assertTrue(assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                    ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, legacy)))).getMessage().contains("$.lungs"));
        }
        JsonObject parent = new JsonObject(); parent.addProperty("abstract", true);
        parent.add("components", JsonParser.parseString("[{\"type\":\"Respirator\",\"max_saturation\":5}]"));
        JsonObject child = valid.deepCopy(); child.addProperty("parent", "parent");
        child.getAsJsonArray("components").get(4).getAsJsonObject().remove("max_saturation");
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ResourceLocation.parse("moonstation14:parent"), parent,
                ModCharacters.HUMAN_ID, child));
        assertEquals(5, manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ModCharacters.HUMAN_ID)
                .component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class)
                .orElseThrow().policy().maxSaturation());
        JsonObject badParent = parent.deepCopy();
        badParent.getAsJsonArray("components").get(0).getAsJsonObject().add("max_saturation", JsonParser.parseString("null"));
        assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                Map.of(ResourceLocation.parse("moonstation14:parent"), badParent, ModCharacters.HUMAN_ID, child))));
        JsonObject organ = readResource("data/moonstation14/moonstation14/organ/organ_lungs_human.json");
        for (String field : List.of("max_lung_moles", "breath_moles_to_saturation_multiplier", "toxic_gas_damage_per_mole", "toxic_gas_damage_cap_per_inhale")) {
            JsonObject bad = organ.deepCopy(); bad.getAsJsonObject("Lung").remove(field);
            assertTrue(com.juicyslew.moonstation14.ms14.organ.OrganData.CODEC.parse(JsonOps.INSTANCE, bad).error().isPresent(), field);
        }
    }

    @Test
    void directBarotraumaCodecAuditsDecodeAndEncode() {
        JsonObject valid = JsonParser.parseString("""
                {"type":"Barotrauma","damage":{"types":{"blunt":0.5}},"maxDamage":200}
                """).getAsJsonObject();
        BarotraumaComponent parsed = BarotraumaComponent.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow();
        assertEquals(Map.of("blunt", 0.5), parsed.damage().types());
        assertEquals(parsed, BarotraumaComponent.CODEC.parse(JsonOps.INSTANCE,
                BarotraumaComponent.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow()).getOrThrow());

        for (String[] invalid : List.of(
                new String[]{"{\"extra\":1}", "$.components[0].extra"},
                new String[]{"{\"damage\":{\"types\":{\"Blunt\":1}}}", "$.components[0].damage.types.Blunt"},
                new String[]{"{\"damage\":{\"extra\":1}}", "$.components[0].damage.extra"},
                new String[]{"{\"damage\":null}", "$.components[0].damage"},
                new String[]{"{\"damage\":{\"types\":null}}", "$.components[0].damage.types"},
                new String[]{"{\"damage\":{\"types\":{\"blunt\":-1}}}", "$.components[0].damage.types.blunt"},
                new String[]{"{\"damage\":{\"types\":{\"blunt\":\"1\"}}}", "$.components[0].damage.types.blunt"},
                new String[]{"{\"maxDamage\":-1}", "$.components[0].maxDamage"},
                new String[]{"{\"maxDamage\":null}", "$.components[0].maxDamage"},
                new String[]{"{\"maxDamage\":\"200\"}", "$.components[0].maxDamage"},
                new String[]{"{\"type\":\"Other\"}", "$.components[0].type"})) {
            JsonObject bad = valid.deepCopy();
            invalidJsonInto(bad, invalid[0]);
            var failure = BarotraumaComponent.CODEC.parse(JsonOps.INSTANCE, bad).error().orElseThrow();
            assertTrue(failure.message().contains(invalid[1]), failure.message());
        }
        for (BarotraumaComponent bad : List.of(
                new BarotraumaComponent(new BarotraumaComponent.Damage(Map.of("Blunt", 1.0)), 200),
                new BarotraumaComponent(new BarotraumaComponent.Damage(Map.of("blunt", -1.0)), 200),
                new BarotraumaComponent(new BarotraumaComponent.Damage(Map.of("blunt", 1_000_001.0)), 200))) {
            assertTrue(BarotraumaComponent.CODEC.encodeStart(JsonOps.INSTANCE, bad).error().isPresent());
        }
    }

    private static void invalidJsonInto(JsonObject target, String fields) {
        JsonParser.parseString(fields).getAsJsonObject().entrySet().forEach(entry -> target.add(entry.getKey(), entry.getValue()));
    }

    @Test
    void barotraumaComponentIsIndependentOptionalAndStrict() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject without = valid.deepCopy();
        without.remove("components");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, without).getOrThrow().barotrauma().isEmpty());
        JsonObject only = valid.deepCopy();
        only.getAsJsonArray("components").remove(1); removeComponent(only, "Stomach"); only.remove("thermal");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, only).getOrThrow().barotrauma().isPresent());
        for (String malformed : List.of("null", "{}", "[null]", "[1]", "[{}]",
                "[{\"type\":\"Unknown\"}]", "[{\"type\":null}]", "[{\"type\":1}]",
                "[{\"type\":\"Barotrauma\",\"damage\":null,\"maxDamage\":200}]",
                "[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"invalid\":1}},\"maxDamage\":200}]",
                "[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"blunt\":-1}},\"maxDamage\":200}]",
                "[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"blunt\":0.5}},\"maxDamage\":null}]")) {
            JsonObject bad = valid.deepCopy(); bad.add("components", JsonParser.parseString(malformed));
            var error = CharacterData.CODEC.parse(JsonOps.INSTANCE, bad).error().orElseThrow();
            assertTrue(error.message().contains("$.components"), error.message());
        }
        JsonObject duplicate = valid.deepCopy();
        duplicate.add("components", JsonParser.parseString("[" + valid.getAsJsonArray("components").get(0) + "," + valid.getAsJsonArray("components").get(0) + "]"));
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, duplicate).error().orElseThrow().message().contains("$.components[1].type"));
        JsonObject oldPressure = valid.deepCopy(); oldPressure.add("pressure", new JsonObject());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, oldPressure).error().orElseThrow().message().contains("$.pressure"));
        JsonObject oldOnly = without.deepCopy(); oldOnly.add("pressure", new JsonObject());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, oldOnly).error().isPresent());
        JsonObject noDamage = valid.deepCopy();
        noDamage.getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonObject("damage").remove("types");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, noDamage).error().orElseThrow().message().contains("$.components[0].damage.types"));
        assertThrows(UnsupportedOperationException.class, () -> CharacterData.CODEC.parse(JsonOps.INSTANCE, valid)
                .getOrThrow().components().clear());
    }

    @Test
    void bloodReagentReferencesValidateAgainstTheAtomicCandidateAndKeepPublishedCatalogOnFailure()
            throws IOException {
        JsonObject human = readResource(RESOURCE);
        JsonObject pig = readResource("data/moonstation14/moonstation14/character/pig.json");
        ResourceLocation pigId = ResourceLocation.fromNamespaceAndPath("moonstation14", "pig");
        ResourceLocation bloodId = ResourceLocation.fromNamespaceAndPath("moonstation14", "blood");
        ResourceLocation sulfurId = ResourceLocation.fromNamespaceAndPath("moonstation14", "sulfurblood");

        ModCharacters.validateReagentReferences(Map.of(
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_TYPE.typeId(),
                Map.of(bloodId, new JsonObject(), sulfurId, new JsonObject()),
                ModCharacters.CHARACTER_TYPE.typeId(), Map.of(ModCharacters.HUMAN_ID, human, pigId, pig)));

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        JsonObject bloodless = human.deepCopy();
        bloodless.getAsJsonArray("components").remove(1);
        removeComponent(bloodless, "Stomach");
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, bloodless));
        var publishedCharacters = manager.snapshot(ModCharacters.CHARACTER_TYPE);

        var missingBloodFailure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                ModCharacters.CHARACTER_TYPE, Map.of(pigId, pig)),
                ModCharacters::validateReagentReferences));
        assertTrue(missingBloodFailure.getMessage().contains("moonstation14:pig"), missingBloodFailure.getMessage());
         assertTrue(missingBloodFailure.getMessage().contains("$.components[1].reference_solution.moonstation14:blood"),
                missingBloodFailure.getMessage());
        assertTrue(missingBloodFailure.getMessage().contains("data/moonstation14/moonstation14/character/pig.json"),
                missingBloodFailure.getMessage());
        assertEquals(publishedCharacters, manager.snapshot(ModCharacters.CHARACTER_TYPE));

         JsonObject extraExclusionPig = bloodField(pig, "metabolism_exclusions",
                 "[\"moonstation14:blood\",\"moonstation14:missing\"]");
        var missingExclusionFailure = assertThrows(RuntimeException.class, () -> ModCharacters.validateReagentReferences(
                Map.of(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_TYPE.typeId(),
                                Map.of(bloodId, new JsonObject(), sulfurId, new JsonObject()),
                        ModCharacters.CHARACTER_TYPE.typeId(), Map.of(pigId, extraExclusionPig))));
        assertTrue(missingExclusionFailure.getMessage().contains("$.components[1].metabolism_exclusions[1]"),
                missingExclusionFailure.getMessage());
        assertEquals(publishedCharacters, manager.snapshot(ModCharacters.CHARACTER_TYPE));
    }

    private static void removeComponent(JsonObject character, String type) {
        var components = character.getAsJsonArray("components");
        for (int i = components.size() - 1; i >= 0; i--) {
            if (type.equals(components.get(i).getAsJsonObject().get("type").getAsString())) components.remove(i);
        }
    }

    private static CharacterData dataFromResource(String path) throws IOException {
        return CharacterData.CODEC.parse(JsonOps.INSTANCE, readResource(path)).getOrThrow();
    }

    private static com.juicyslew.moonstation14.ms14.organ.OrganData.Lung organFromResource(String species) throws IOException {
        return com.juicyslew.moonstation14.ms14.organ.OrganData.CODEC.parse(JsonOps.INSTANCE,
                readResource("data/moonstation14/moonstation14/organ/organ_lungs_" + species + ".json"))
                .getOrThrow().lung().orElseThrow();
    }

    private static double referenceTotal(Map<String, Double> referenceSolution) {
        return referenceSolution.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static JsonObject bloodField(JsonObject source, String field, String json) {
        JsonObject result = source.deepCopy();
        result.getAsJsonArray("components").get(1).getAsJsonObject().add(field, JsonParser.parseString(json));
        return result;
    }

    private static JsonObject withoutBloodField(JsonObject source, String field) {
        JsonObject result = source.deepCopy(); result.getAsJsonArray("components").get(1).getAsJsonObject().remove(field); return result;
    }

    private static JsonObject lungField(JsonObject source, String field, String json) {
        JsonObject result = source.deepCopy();
        result.getAsJsonArray("components").get(4).getAsJsonObject().add(field, JsonParser.parseString(json));
        return result;
    }

    private static JsonObject lungsBlock(JsonObject source, String json) {
        JsonObject result = source.deepCopy();
        result.add("lungs", JsonParser.parseString(json));
        return result;
    }

    private static JsonObject withoutLungField(JsonObject source, String field) {
        JsonObject result = source.deepCopy(); result.getAsJsonArray("components").get(4).getAsJsonObject().remove(field); return result;
    }

    private static JsonObject readResource(String path) throws IOException {
        try (var stream = CharacterPrototypeTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource " + path);
            return JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject withoutMovement(JsonObject source) {
        JsonObject result = source.deepCopy();
        var components = result.getAsJsonArray("components");
        for (int i = components.size() - 1; i >= 0; i--)
            if ("MovementSpeedModifier".equals(components.get(i).getAsJsonObject().get("type").getAsString())) components.remove(i);
        return result;
    }

    private static JsonObject hosts(JsonObject source, String json) {
        JsonObject result = source.deepCopy(); result.add("host_entity_types", JsonParser.parseString(json)); return result;
    }

    private static JsonObject withMovement(JsonObject source, String field, String value) {
        JsonObject result = source.deepCopy(); movementComponent(result).add(field, JsonParser.parseString(value)); return result;
    }

    private static JsonObject replaceMovement(JsonObject source, String field, String value) {
        JsonObject result = source.deepCopy(); movementComponent(result).add(field, JsonParser.parseString(value)); return result;
    }

    private static JsonObject nonFiniteMovement(JsonObject source) {
        JsonObject result = source.deepCopy();
        movementComponent(result).addProperty("walk_speed", Double.NaN);
        return result;
    }

    @Test
    void movementComponentRejectsLegacyRootAndIncompleteFragments() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        var human = CharacterData.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow();
        assertEquals(4.5, com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy
                .fromCharacterData(human).sprintSpeedPerSecond());
        for (String value : List.of("null", "{}", "{\"mode\":\"grounded\"}")) {
            JsonObject legacy = valid.deepCopy();
            legacy.add("movement", JsonParser.parseString(value));
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, legacy).error().orElseThrow()
                    .message().contains("legacy top-level movement"));
            assertThrows(IllegalArgumentException.class,
                    () -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.rejectLegacyMovement(legacy));
        }
        JsonObject fragment = JsonParser.parseString("{\"type\":\"MovementSpeedModifier\",\"walk_speed\":4.0}").getAsJsonObject();
        com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponentFragment(fragment, "$.components[0]");
        assertThrows(IllegalArgumentException.class, () ->
                com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(fragment, "$.components[0]"));
        fragment.add("walk_speed", JsonParser.parseString("null"));
        assertThrows(IllegalArgumentException.class, () ->
                com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponentFragment(fragment, "$.components[0]"));
        assertThrows(IllegalArgumentException.class, () ->
                com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.fromCharacterData(new CharacterData()));
    }

    private static JsonObject movementComponent(JsonObject source) {
        for (var component : source.getAsJsonArray("components")) {
            JsonObject object = component.getAsJsonObject();
            if ("MovementSpeedModifier".equals(object.get("type").getAsString())) return object;
        }
        throw new IllegalArgumentException("missing movement test component");
    }

    @Test
    void slipComponentsRejectMalformedCompleteAndFragmentDeclarations() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, valid).result().isPresent());
        for (String legacy : List.of("null", "{}", "[]")) {
            JsonObject raw = valid.deepCopy();
            raw.add("slip_data", JsonParser.parseString(legacy));
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, raw).error().orElseThrow()
                    .message().contains("move standing_eligible"));
        }
        for (String invalid : List.of(
                "[{\"type\":\"StandingState\",\"standing_eligible\":null,\"prone_eligible\":true}]",
                "[{\"type\":\"StandingState\",\"standing_eligible\":true}]",
                "[{\"type\":\"Stunnable\",\"enabled\":false}]",
                "[{\"type\":\"NoSlip\",\"remove\":true}]",
                "[{\"type\":\"Reactive\",\"reactive_groups\":[\"flammable\",\"flammable\"],\"reactive_methods\":[\"touch\"]}]",
                "[{\"type\":\"Reactive\",\"reactive_groups\":[\"Flammable\"],\"reactive_methods\":[\"touch\"]}]",
                "[{\"type\":\"Reactive\",\"reactive_groups\":[],\"reactive_methods\":[\"touch\"]}]")) {
            JsonObject raw = new JsonObject();
            raw.add("components", JsonParser.parseString(invalid));
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, raw).error().isPresent(), invalid);
        }
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("null")).error().isPresent());
    }

    @Test
    void handsAreOptionalOrderedCapabilityAndMalformedDeclarationsRejectCodecAndReload() throws IOException {
        JsonObject human = readResource(RESOURCE);
        assertEquals(List.of("left", "right"), CharacterData.CODEC.parse(JsonOps.INSTANCE, human)
                .getOrThrow().component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).orElseThrow().hands());
        var handsComponent = CharacterData.CODEC.parse(JsonOps.INSTANCE, human).getOrThrow()
                .component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).orElseThrow();
        assertEquals(handsComponent, com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry.CODEC
                .parse(JsonOps.INSTANCE, com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry.CODEC
                        .encodeStart(JsonOps.INSTANCE, handsComponent).getOrThrow()).getOrThrow());
        assertThrows(UnsupportedOperationException.class, () -> handsComponent.hands().clear());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withHands(human, "[]"))
                .getOrThrow().component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).orElseThrow().hands().isEmpty());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withoutHands(human))
                .getOrThrow().component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).isEmpty());

        String tooLong = "a".repeat(com.juicyslew.moonstation14.ms14.hands.HandState.MAX_ID_LENGTH + 1);
        for (JsonObject invalid : List.of(
                withHands(human, "{}"),
                withHands(human, "[1]"),
                withHands(human, "[\"left\",\"left\"]"),
                withHands(human, "[\" \"]"),
                withHands(human, "[\"" + tooLong + "\"]"),
                withHands(human, "[" + String.join(",", java.util.Collections.nCopies(
                        com.juicyslew.moonstation14.ms14.hands.HandState.MAX_HANDS + 1, "\"h\"")) + "]"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent(), invalid.toString());
        }
        JsonObject unknownHandsField = human.deepCopy();
        handsComponentObject(unknownHandsField).addProperty("extra", true);
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, unknownHandsField).error().isPresent());
        JsonObject missingHandsField = human.deepCopy();
        handsComponentObject(missingHandsField).remove("hands");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, missingHandsField).error().isPresent());
        JsonObject legacy = human.deepCopy();
        legacy.add("hands", JsonParser.parseString("[]"));
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, legacy).error().orElseThrow().message().contains("$.hands"));
        JsonObject unknownField = human.deepCopy();
        unknownField.addProperty("hand", "left");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, unknownField).error().isPresent());

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, human));
        var published = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        ResourceLocation abstractId = ResourceLocation.parse("moonstation14:abstract_hands");
        for (String legacyValue : List.of("[\"left\"]", "null")) {
            JsonObject abstractLegacy = new JsonObject();
            abstractLegacy.addProperty("abstract", true);
            abstractLegacy.add("hands", JsonParser.parseString(legacyValue));
            var failure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                    ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, human,
                            abstractId, abstractLegacy))));
            assertTrue(failure.getMessage().contains("moonstation14:abstract_hands"), failure.getMessage());
            assertTrue(failure.getMessage().contains("$.hands"), failure.getMessage());
            assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE));
        }
        for (JsonObject invalid : List.of(withHands(human, "[\"left\",\"left\"]"),
                withHands(human, "[\"" + tooLong + "\"]"))) {
            assertThrows(RuntimeException.class,
                    () -> manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, invalid)));
        }
    }

    private static JsonObject withHands(JsonObject source, String json) {
        JsonObject result = source.deepCopy();
        handsComponentObject(result).add("hands", JsonParser.parseString(json));
        return result;
    }

    private static JsonObject handsComponentObject(JsonObject source) {
        for (var element : source.getAsJsonArray("components")) {
            JsonObject component = element.getAsJsonObject();
            if ("Hands".equals(component.get("type").getAsString())) return component;
        }
        throw new IllegalArgumentException("missing Hands component");
    }

    private static JsonObject withoutHands(JsonObject source) {
        JsonObject result = source.deepCopy();
        var components = result.getAsJsonArray("components");
        for (int i = components.size() - 1; i >= 0; i--) {
            if ("Hands".equals(components.get(i).getAsJsonObject().get("type").getAsString())) components.remove(i);
        }
        return result;
    }

    private static JsonObject thermalComponent(JsonObject source, String field) {
        for (var element : source.getAsJsonArray("components")) {
            JsonObject component = element.getAsJsonObject();
            if (component.has(field)) return component;
        }
        throw new IllegalArgumentException("missing thermal field " + field);
    }

    private static JsonObject withoutThermal(JsonObject source) {
        JsonObject result = source.deepCopy();
        var components = result.getAsJsonArray("components");
        for (int i = components.size() - 1; i >= 0; i--)
            if (java.util.Set.of("Temperature", "TemperatureDamage", "ThermalRegulator")
                    .contains(components.get(i).getAsJsonObject().get("type").getAsString())) components.remove(i);
        return result;
    }

    private static JsonObject withThermalField(JsonObject source, String field, String value) {
        JsonObject result = source.deepCopy();
        JsonObject target = field.equals("unknown") ? thermalComponent(result, "mass_kg") : thermalComponent(result, field);
        target.add(field, JsonParser.parseString(value));
        return result;
    }

    private static JsonObject nonFiniteThermal(JsonObject source) {
        JsonObject result = source.deepCopy();
        thermalComponent(result, "mass_kg").addProperty("mass_kg", Double.NaN);
        return result;
    }

    private static JsonObject withoutThermalField(JsonObject source, String field) {
        JsonObject result = source.deepCopy(); thermalComponent(result, field).remove(field); return result;
    }

}
