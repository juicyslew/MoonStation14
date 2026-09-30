package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import com.juicyslew.moonstation14.ms14.character.components.ComplexInteractionComponent;
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
    void humanPrototypeIsTypedAndRoundTripsThroughThePrototypeCatalog() throws IOException {
        JsonObject raw;
        try (var stream = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Missing resource " + RESOURCE);
            raw = JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        CharacterData data = CharacterData.CODEC.parse(JsonOps.INSTANCE, raw).getOrThrow();
        var slip = data.slipData();
        assertTrue(slip.canReceiveStun());
        assertFalse(slip.noSlip());
        assertTrue(slip.standingEligible());
        assertTrue(slip.proneEligible());
        assertEquals(List.of(CharacterData.ReactiveGroup.FLAMMABLE, CharacterData.ReactiveGroup.EXTINGUISH,
                CharacterData.ReactiveGroup.ACIDIC), slip.reactiveGroups());
        assertEquals(List.of(CharacterData.ReactiveMethod.TOUCH), slip.reactiveMethods());
        assertEquals(2.5, data.movement().orElseThrow().walkSpeed());
        assertEquals(List.of(ResourceLocation.parse("minecraft:player"), ResourceLocation.parse("minecraft:villager"),
                        ResourceLocation.parse("moonstation14:player_character_harness")),
                data.hostEntityTypes());
        assertEquals(List.of("left", "right"), data.hands());
        assertTrue(data.component(ComplexInteractionComponent.class).isPresent());
        assertEquals(2, data.components().size());
        assertEquals(Optional.of(java.util.Set.of(MetabolizerTypeEnum.HUMAN)), data.metabolizerTypes());
        var thermal = data.thermal().orElseThrow();
        assertEquals(Math.PI * 0.35 * 0.35 * 185, thermal.massKg(), 1e-12);
        assertEquals(42.0, thermal.specificHeatJoulesPerKgKelvin());
        assertEquals(310.15, thermal.currentKelvin());
        assertEquals(Math.PI * 0.35 * 0.35 * 185 * 42, thermal.toProfile().bodyHeatCapacityJoulesPerKelvin(), 1e-9);
        assertEquals(310.15, thermal.normalBodyTemperatureKelvin());
        assertEquals(800, thermal.metabolismHeatJoulesPerSecond());
        assertEquals(100, thermal.radiatedHeatJoulesPerSecond());
        assertEquals(500, thermal.implicitHeatRegulationJoulesPerSecond());
        assertEquals(2000, thermal.sweatHeatRegulationJoulesPerSecond());
        assertEquals(2000, thermal.shiveringHeatRegulationJoulesPerSecond());
        assertEquals(7000, thermal.spaceHeatCapacityJoulesPerKelvin());
        assertEquals(8, thermal.spaceHeatScale());
        var blood = data.blood().orElseThrow();
        var lungs = data.lungs().orElseThrow();
        assertEquals(1.0, blood.bleedPuddleThreshold());
         assertEquals(5.0, lungs.maxSaturation());
         assertEquals(5.0, lungs.initialSaturation());
         assertEquals(-2.0, lungs.minSaturation());
         assertEquals(0.5, lungs.breathVolumeLiters());
         assertEquals(1144.0, lungs.breathMolesToSaturationMultiplier());
        assertEquals(Map.of("poison", 1.0), lungs.toxicGasDamagePerMole().get("plasma"));
        assertEquals(Map.of("radiation", 1.0), lungs.toxicGasDamagePerMole().get("tritium"));
        assertFalse(lungs.toxicGasDamagePerMole().containsKey("nitrogen"));
        assertEquals(Map.of("blunt", 0.5, "heat", 0.1), data.component(BarotraumaComponent.class).orElseThrow().damage().types());
        assertEquals(200, data.barotrauma().orElseThrow().maxDamage());
        assertFalse(raw.getAsJsonObject("blood").has("initial_volume"));
        assertFalse(raw.getAsJsonObject("blood").has("max_volume"));
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
        JsonObject legacy = JsonParser.parseString("""
                {"slip_data":{"can_receive_stun":true,"no_slip":false,"standing_eligible":true,
                 "prone_eligible":true,"reactive_groups":[],"reactive_methods":[]}}
                """).getAsJsonObject();
        CharacterData oldData = CharacterData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(Optional.empty(), oldData.movement());
        assertEquals(Optional.empty(), oldData.thermal());
        assertEquals(Optional.empty(), oldData.barotrauma());
        assertEquals(Optional.empty(), oldData.metabolizerTypes());
        assertTrue(oldData.hostEntityTypes().isEmpty());
        assertTrue(oldData.hands().isEmpty());
        assertTrue(oldData.components().isEmpty());
        assertEquals(oldData, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, oldData).getOrThrow()).getOrThrow());
        assertEquals(oldData, new CharacterData(oldData.slipData()));

        JsonObject pigJson = readResource("data/moonstation14/moonstation14/character/pig.json");
        CharacterData pig = CharacterData.CODEC.parse(JsonOps.INSTANCE, pigJson).getOrThrow();
        assertEquals(4.0, pig.movement().orElseThrow().walkSpeed());
        assertNotEquals(2.5, pig.movement().orElseThrow().walkSpeed());
        assertEquals(List.of(ResourceLocation.parse("minecraft:pig")), pig.hostEntityTypes());
        assertTrue(pig.hands().isEmpty(), "pig prototypes do not acquire implicit hands");
        assertTrue(pig.component(ComplexInteractionComponent.class).isEmpty(), "movement does not grant complex interaction");
        assertEquals(Optional.of(java.util.Set.of(MetabolizerTypeEnum.ANIMAL)), pig.metabolizerTypes());
        var pigThermal = pig.thermal().orElseThrow();
        assertEquals(Math.PI * 0.35 * 0.35 * 250, pigThermal.massKg(), 1e-12);
        assertEquals(1.0, pigThermal.coldDamagePerSecond());
        assertEquals(250, pigThermal.implicitHeatRegulationJoulesPerSecond());
        assertEquals(500, pigThermal.shiveringHeatRegulationJoulesPerSecond());
         assertEquals(Map.of("moonstation14:blood", 150.0), pig.blood().orElseThrow().referenceSolution());
         assertEquals(List.of(ResourceLocation.parse("moonstation14:blood")),
                pig.blood().orElseThrow().metabolismExclusions());
        assertEquals(2.0, pig.blood().orElseThrow().maxVolumeModifier());
         assertEquals(1.0, pig.blood().orElseThrow().bleedPuddleThreshold());
         assertEquals(dataFromResource(RESOURCE).blood().orElseThrow().damageBleedMultipliers(),
                 pig.blood().orElseThrow().damageBleedMultipliers());
         assertEquals(3.0, pig.blood().orElseThrow().updateIntervalSeconds());
         assertEquals(10.0, pig.blood().orElseThrow().maxBleedRate());
         assertEquals(0.33, pig.blood().orElseThrow().bleedDecayPerUpdate());
         assertEquals(1.0, pig.blood().orElseThrow().bloodRefreshPerUpdate());
         assertEquals(0.9, pig.blood().orElseThrow().bloodlossThresholdFraction());
         assertEquals(Map.of("bloodloss", 0.5), pig.blood().orElseThrow().bloodlossDamagePerUpdate());
         assertEquals(Map.of("bloodloss", 1.0), pig.blood().orElseThrow().bloodlossHealPerUpdate());
         assertEquals(2.0, pig.lungs().orElseThrow().breathIntervalSeconds());
         assertEquals(5.0, pig.lungs().orElseThrow().initialSaturation());
         assertEquals(0.5, pig.lungs().orElseThrow().breathVolumeLiters());
         assertEquals(2.0, pig.lungs().orElseThrow().suffocationDamagePerUpdate());
        assertEquals(Map.of("caustic", 0.3), pig.lungs().orElseThrow().toxicGasDamagePerMole().get("ammonia"));
        assertNotEquals(dataFromResource(RESOURCE).barotrauma(), pig.barotrauma());
        assertNotEquals(dataFromResource(RESOURCE).lungs(), pig.lungs());
         assertEquals(300.0, referenceTotal(pig.blood().orElseThrow().referenceSolution())
                * pig.blood().orElseThrow().maxVolumeModifier());
        assertFalse(pigJson.getAsJsonObject("blood").has("initial_volume"));
        assertFalse(pigJson.getAsJsonObject("blood").has("max_volume"));
         assertEquals(dataFromResource(RESOURCE).blood().orElseThrow().referenceSolution().keySet(),
                 pig.blood().orElseThrow().referenceSolution().keySet());
        assertFalse(pig.slipData().canReceiveStun());
        assertTrue(pig.slipData().noSlip());
        assertFalse(pig.slipData().standingEligible());
        assertFalse(pig.slipData().proneEligible());
        assertTrue(pig.slipData().reactiveGroups().isEmpty());
        assertTrue(pig.slipData().reactiveMethods().isEmpty());
        assertEquals(pig, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, pig).getOrThrow()).getOrThrow());
    }

    @Test
    void metabolizerTypesRequireExplicitCanonicalDistinctPolicy() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject absent = valid.deepCopy();
        absent.remove("metabolizer_types");
        assertEquals(Optional.empty(), CharacterData.CODEC.parse(JsonOps.INSTANCE, absent).getOrThrow().metabolizerTypes());
        for (String invalidValue : List.of("null", "1", "\"human\"", "[null]", "[1]",
                "[\"Human\"]", "[\"unknown\"]", "[\"human\",\"human\"]")) {
            JsonObject invalid = valid.deepCopy();
            invalid.add("metabolizer_types", JsonParser.parseString(invalidValue));
            var failure = CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().orElseThrow();
            assertTrue(failure.message().contains("$.metabolizer_types"), failure.message());
        }
        JsonObject empty = valid.deepCopy();
        empty.add("metabolizer_types", JsonParser.parseString("[]"));
        assertEquals(Optional.of(java.util.Set.of()), CharacterData.CODEC.parse(JsonOps.INSTANCE, empty)
                .getOrThrow().metabolizerTypes());
    }

    @Test
    void thermalPolicyIsStrictAndOptional() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject thermal = valid.getAsJsonObject("thermal");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withoutThermal(valid)).getOrThrow().thermal().isEmpty());

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
        assertTrue(mapped.thermal().isPresent());
        assertEquals(List.of(ResourceLocation.parse("test:thermal_living_host")), mapped.hostEntityTypes());
        assertTrue(mapped.movement().isEmpty());
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
                ResourceLocation.parse("minecraft:player")).orElseThrow()).blood().orElseThrow().referenceSolution()));
         assertEquals(150.0, referenceTotal(published.get(ModCharacters.characterForHost(published,
                ResourceLocation.parse("minecraft:pig")).orElseThrow()).blood().orElseThrow().referenceSolution()));
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
        assertTrue(data.blood().isPresent(), "blood remains available through the explicit host mapping");
        assertTrue(data.movement().isEmpty(), "blood enrollment must not synthesize movement authority");
        assertEquals(List.of(ResourceLocation.parse("test:configured_living_host")), data.hostEntityTypes());
    }

    @Test
    void bloodPolicyIsOptionalButStrictAndRequiresEveryTypedField() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject withoutBlood = valid.deepCopy();
        withoutBlood.remove("blood");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withoutBlood).getOrThrow().blood().isEmpty());
        var blood = CharacterData.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow().blood().orElseThrow();
        assertEquals(Map.of("moonstation14:blood", 300.0), blood.referenceSolution());
        assertEquals(List.of(ResourceLocation.parse("moonstation14:blood")), blood.metabolismExclusions());
        assertEquals(Map.of("bloodloss", 0.5), blood.bloodlossDamagePerUpdate());
        assertFalse(blood.bloodlossIgnoreResistances());
        CharacterData bypass = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                bloodField(valid, "bloodloss_ignore_resistances", "true")).getOrThrow();
        assertTrue(bypass.blood().orElseThrow().bloodlossIgnoreResistances());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, bypass).getOrThrow()).getOrThrow()
                .blood().orElseThrow().bloodlossIgnoreResistances());
        for (JsonObject invalid : List.of(withoutBloodField(valid, "bloodloss_ignore_resistances"),
                bloodField(valid, "bloodloss_ignore_resistances", "1"),
                bloodField(valid, "bloodloss_ignore_resistances", "\"false\""),
                bloodField(valid, "bloodloss_ignore_resistances", "null"))) {
            var error = CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().orElseThrow();
            assertTrue(error.message().contains("$.blood.bloodloss_ignore_resistances"), error.message());
            assertFalse(error.message().contains("NullPointerException"), error.message());
        }
        JsonObject nullFlag = bloodField(valid, "bloodloss_ignore_resistances", "null");
        var auditError = assertThrows(IllegalArgumentException.class, () -> CharacterSchemaAudit.audit(nullFlag));
        assertTrue(auditError.getMessage().contains("$.blood.bloodloss_ignore_resistances"), auditError.getMessage());

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
            assertTrue(failure.getMessage().contains("$.blood.bloodloss_ignore_resistances"), failure.getMessage());
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
            assertTrue(error.message().contains("$.blood." + legacyField), error.message());
            assertTrue(error.message().contains("unknown field"), error.message());
        }
        assertEquals("blood", readResource("data/moonstation14/moonstation14/reagent/blood.json").get("id").getAsString());
        assertEquals("sulfurblood", readResource("data/moonstation14/moonstation14/reagent/sulfurblood.json").get("id").getAsString());
    }

    @Test
    void lungPolicyIsOptionalIndependentAndStrict() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject standalone = valid.deepCopy();
        standalone.remove("blood");
        standalone.remove("thermal");
        standalone.remove("movement");
        CharacterData standaloneData = CharacterData.CODEC.parse(JsonOps.INSTANCE, standalone).getOrThrow();
        assertTrue(standaloneData.lungs().isPresent());
        assertTrue(standaloneData.blood().isEmpty());
        assertTrue(standaloneData.thermal().isEmpty());
        assertTrue(standaloneData.movement().isEmpty());
        assertTrue(standaloneData.barotrauma().isPresent());
        assertEquals(standaloneData, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, standaloneData).getOrThrow()).getOrThrow());

        JsonObject emptyToxins = lungField(valid, "toxic_gas_damage_per_mole", "{}");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, emptyToxins).getOrThrow()
                .lungs().orElseThrow().toxicGasDamagePerMole().isEmpty());

        JsonObject noLungs = valid.deepCopy();
        noLungs.remove("lungs");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, noLungs).getOrThrow().lungs().isEmpty());
        for (JsonObject invalid : List.of(
                withoutLungField(valid, "breath_interval_seconds"),
                lungField(valid, "unexpected", "1"),
                lungField(valid, "initial_saturation", "null"),
                lungField(valid, "suffocation_ignore_resistances", "1"),
                lungField(valid, "breath_interval_seconds", "0"),
                lungField(valid, "breath_interval_seconds", "0.001"),
                 lungField(valid, "breath_volume_liters", "-1"),
                lungField(valid, "max_lung_moles", "NaN"),
                lungField(valid, "initial_saturation", "101"),
                lungField(valid, "suffocation_threshold", "101"),
                 lungField(valid, "breath_moles_to_saturation_multiplier", "1000001"),
                withoutLungField(valid, "toxic_gas_damage_per_mole"),
                withoutLungField(valid, "toxic_gas_damage_cap_per_inhale"),
                lungField(valid, "toxic_gas_damage_cap_per_inhale", "-1"),
                lungField(valid, "toxic_gas_damage_cap_per_inhale", "NaN"),
                lungField(valid, "toxic_gas_damage_per_mole", "null"),
                lungField(valid, "toxic_gas_damage_per_mole", "[]"),
                lungField(valid, "toxic_gas_damage_per_mole", "{\"PLASMA\":{\"poison\":1}}"),
                lungField(valid, "toxic_gas_damage_per_mole", "{\"unknown\":{\"poison\":1}}"),
                lungField(valid, "toxic_gas_damage_per_mole", "{\"plasma\":null}"),
                lungField(valid, "toxic_gas_damage_per_mole", "{\"plasma\":{\"Poison\":1}}"),
                lungField(valid, "toxic_gas_damage_per_mole", "{\"plasma\":{\"poison\":-1}}"),
                lungField(valid, "toxic_gas_damage_per_mole", "{\"plasma\":{\"poison\":NaN}}"),
                lungField(valid, "toxic_gas_damage_per_mole", "{\"plasma\":{\"poison\":\"1\"}}"),
                lungsBlock(valid, "null"))) {
            var error = CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().orElseThrow();
            assertFalse(error.message().contains("NullPointerException"), error.message());
        }
        JsonObject pig = readResource("data/moonstation14/moonstation14/character/pig.json");
        assertNotEquals(CharacterData.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow().lungs(),
                CharacterData.CODEC.parse(JsonOps.INSTANCE, pig).getOrThrow().lungs());
        for (String fragment : List.of("$.lungs.toxic_gas_damage_per_mole.unknown",
                "$.lungs.toxic_gas_damage_per_mole.plasma.unknown")) {
            JsonObject bad = fragment.endsWith(".unknown") && fragment.contains("plasma.unknown")
                    ? lungField(valid, "toxic_gas_damage_per_mole", "{\"plasma\":{\"unknown\":1}}")
                    : lungField(valid, "toxic_gas_damage_per_mole", "{\"unknown\":{}}");
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, bad).error().orElseThrow().message().contains(fragment));
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
        only.remove("lungs"); only.remove("blood"); only.remove("thermal");
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
        bloodless.remove("blood");
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, bloodless));
        var publishedCharacters = manager.snapshot(ModCharacters.CHARACTER_TYPE);

        var missingBloodFailure = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                ModCharacters.CHARACTER_TYPE, Map.of(pigId, pig)),
                ModCharacters::validateReagentReferences));
        assertTrue(missingBloodFailure.getMessage().contains("moonstation14:pig"), missingBloodFailure.getMessage());
         assertTrue(missingBloodFailure.getMessage().contains("$.blood.reference_solution.moonstation14:blood"),
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
        assertTrue(missingExclusionFailure.getMessage().contains("$.blood.metabolism_exclusions[1]"),
                missingExclusionFailure.getMessage());
        assertEquals(publishedCharacters, manager.snapshot(ModCharacters.CHARACTER_TYPE));
    }

    private static CharacterData dataFromResource(String path) throws IOException {
        return CharacterData.CODEC.parse(JsonOps.INSTANCE, readResource(path)).getOrThrow();
    }

    private static double referenceTotal(Map<String, Double> referenceSolution) {
        return referenceSolution.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static JsonObject bloodField(JsonObject source, String field, String json) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("blood").add(field, JsonParser.parseString(json));
        return result;
    }

    private static JsonObject withoutBloodField(JsonObject source, String field) {
        JsonObject result = source.deepCopy(); result.getAsJsonObject("blood").remove(field); return result;
    }

    private static JsonObject lungField(JsonObject source, String field, String json) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("lungs").add(field, JsonParser.parseString(json));
        return result;
    }

    private static JsonObject lungsBlock(JsonObject source, String json) {
        JsonObject result = source.deepCopy();
        result.add("lungs", JsonParser.parseString(json));
        return result;
    }

    private static JsonObject withoutLungField(JsonObject source, String field) {
        JsonObject result = source.deepCopy(); result.getAsJsonObject("lungs").remove(field); return result;
    }

    private static JsonObject readResource(String path) throws IOException {
        try (var stream = CharacterPrototypeTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource " + path);
            return JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject withoutMovement(JsonObject source) {
        JsonObject result = source.deepCopy(); result.remove("movement"); return result;
    }

    private static JsonObject hosts(JsonObject source, String json) {
        JsonObject result = source.deepCopy(); result.add("host_entity_types", JsonParser.parseString(json)); return result;
    }

    private static JsonObject withMovement(JsonObject source, String field, String value) {
        JsonObject result = source.deepCopy(); result.getAsJsonObject("movement").add(field, JsonParser.parseString(value)); return result;
    }

    private static JsonObject replaceMovement(JsonObject source, String field, String value) {
        JsonObject result = source.deepCopy(); result.getAsJsonObject("movement").add(field, JsonParser.parseString(value)); return result;
    }

    private static JsonObject nonFiniteMovement(JsonObject source) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("movement").addProperty("walk_speed", Double.NaN);
        return result;
    }

    @Test
    void codecRejectsMissingUnknownNoncanonicalAndSemanticallyInconsistentPolicy() {
        JsonObject valid = JsonParser.parseString("""
                {"slip_data":{"can_receive_stun":true,"no_slip":false,
                 "standing_eligible":true,"prone_eligible":true,
                 "reactive_groups":["flammable","extinguish","acidic"],"reactive_methods":["touch"]}}
                """).getAsJsonObject();
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, valid).result().isPresent());

        for (JsonObject invalid : List.of(
                new JsonObject(),
                withSlip(valid, "extra", true),
                replace(valid, "reactive_methods", "[\"Touch\"]"),
                replace(valid, "reactive_groups", "[\"flammable\",\"flammable\"]"),
                replace(valid, "reactive_groups", "[]"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
        }

        JsonObject missingRequired = valid.deepCopy();
        missingRequired.getAsJsonObject("slip_data").remove("can_receive_stun");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, missingRequired).error().isPresent());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("null")).error().isPresent());
    }

    @Test
    void handsAreOptionalOrderedCapabilityAndMalformedDeclarationsRejectCodecAndReload() throws IOException {
        JsonObject human = readResource(RESOURCE);
        assertEquals(List.of("left", "right"), CharacterData.CODEC.parse(JsonOps.INSTANCE, human)
                .getOrThrow().hands());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withHands(human, "[]"))
                .getOrThrow().hands().isEmpty());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withoutHands(human))
                .getOrThrow().hands().isEmpty());

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
        JsonObject unknownField = human.deepCopy();
        unknownField.addProperty("hand", "left");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, unknownField).error().isPresent());

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, human));
        for (JsonObject invalid : List.of(withHands(human, "[\"left\",\"left\"]"),
                withHands(human, "[\"" + tooLong + "\"]"))) {
            assertThrows(RuntimeException.class,
                    () -> manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, invalid)));
        }
    }

    private static JsonObject withHands(JsonObject source, String json) {
        JsonObject result = source.deepCopy(); result.add("hands", JsonParser.parseString(json)); return result;
    }

    @Test
    void componentsAreStrictOptionalAndDoNotInferFromHandsOrMovement() throws IOException {
        JsonObject human = readResource(RESOURCE);
        JsonObject absent = human.deepCopy();
        absent.remove("components");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, absent).getOrThrow().components().isEmpty());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withoutHands(absent)).getOrThrow()
                .component(ComplexInteractionComponent.class).isEmpty());
        for (String invalid : List.of("{}", "[1]", "[\"ComplexInteraction\"]",
                "[\"unknown\"]", "[\"complex_interaction\"]", "[{\"type\":\"Unknown\"}]",
                "[{\"type\":\"ComplexInteraction\",\"enabled\":true}]",
                "[{\"type\":\"ComplexInteraction\"},{\"type\":\"ComplexInteraction\"}]")) {
            JsonObject input = human.deepCopy();
            input.add("components", JsonParser.parseString(invalid));
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, input).error().isPresent(), invalid);
            PrototypeManager manager = new PrototypeManager();
            manager.register(ModCharacters.CHARACTER_TYPE);
            assertThrows(RuntimeException.class,
                    () -> manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, input)));
        }
    }

    private static JsonObject withoutHands(JsonObject source) {
        JsonObject result = source.deepCopy(); result.remove("hands"); return result;
    }

    private static JsonObject withSlip(JsonObject source, String field, boolean value) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("slip_data").addProperty(field, value);
        return result;
    }

    private static JsonObject withoutThermal(JsonObject source) {
        JsonObject result = source.deepCopy(); result.remove("thermal"); return result;
    }

    private static JsonObject withThermalField(JsonObject source, String field, String value) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("thermal").add(field, JsonParser.parseString(value));
        return result;
    }

    private static JsonObject nonFiniteThermal(JsonObject source) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("thermal").addProperty("mass_kg", Double.NaN);
        return result;
    }

    private static JsonObject withoutThermalField(JsonObject source, String field) {
        JsonObject result = source.deepCopy(); result.getAsJsonObject("thermal").remove(field); return result;
    }

    private static JsonObject replace(JsonObject source, String field, String json) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("slip_data").add(field, JsonParser.parseString(json));
        return result;
    }
}
