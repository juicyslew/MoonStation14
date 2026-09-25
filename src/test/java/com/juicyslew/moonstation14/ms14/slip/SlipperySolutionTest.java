package com.juicyslew.moonstation14.ms14.slip;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.ReagentSchemaAudit;
import com.juicyslew.moonstation14.component.codec.json.SlipData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SlipperySolutionTest {
    private static final ResourceKey<ReagentData> LUBE = key("lube");
    private static final ResourceKey<ReagentData> ACID = key("acid");

    @Test
    void volumeCutoffIsInertAtAndBelowFifteenUnits() {
        PrototypeCatalog<ReagentData> catalog = catalog(
                reagent("lube", "\"slipData\":{\"requiredSlipSpeed\":1,\"superSlippery\":true}"));
        assertTrue(result(catalog, Map.of(LUBE, 1_499L)).inert());
        assertTrue(result(catalog, Map.of(LUBE, 1_500L)).inert());
        assertFalse(result(catalog, Map.of(LUBE, 1_501L)).inert());
    }

    @Test
    void successiveAcceptedTouchSplitsCanDropSpaceLubeBelowStrictSlipThreshold() {
        ResourceKey<ReagentData> spaceLube = key("spacelube");
        PrototypeCatalog<ReagentData> catalog = catalog(
                reagent("spacelube", "\"slipData\":{\"requiredSlipSpeed\":1}"));
        ReagentAttachment source = new ReagentAttachment();
        source.specificAdd(spaceLube, 20f, 50f);

        assertTrue(result(catalog, source.snapshotUnits()).slippery());
        long firstTouch = ReactiveTouchSystem.touchRequestCents(source.totalUnits());
        assertEquals(300L, firstTouch);
        assertEquals(300L, ReagentUnits.total(source.splitUnits(firstTouch).values()));
        assertEquals(1_700L, source.totalUnits());
        assertTrue(result(catalog, source.snapshotUnits()).slippery(),
                "the first accepted 15% Touch leaves 17u, still above the strict >15u slip threshold");

        long secondTouch = ReactiveTouchSystem.touchRequestCents(source.totalUnits());
        assertEquals(255L, secondTouch, "Touch is 15% of the then-current solution, not the original dose");
        assertEquals(255L, ReagentUnits.total(source.splitUnits(secondTouch).values()));
        assertEquals(1_445L, source.totalUnits());
        SlipperySolution.Outcome remainder = result(catalog, source.snapshotUnits());
        assertTrue(remainder.inert());
        assertFalse(remainder.slippery(), "14.45u is below the strict >15u slip threshold");
    }

    @Test
    void slipperyAndSuperThresholdsHaveDifferentStrictness() {
        PrototypeCatalog<ReagentData> catalog = catalog(
                reagent("lube", "\"slipData\":{\"requiredSlipSpeed\":1,\"superSlippery\":true}"),
                reagent("acid", ""));
        assertFalse(result(catalog, Map.of(LUBE, 1_499L, ACID, 1L)).slippery());
        assertFalse(result(catalog, Map.of(LUBE, 1_500L, ACID, 1L)).slippery());
        assertTrue(result(catalog, Map.of(LUBE, 1_500L, ACID, 1L)).superSlippery());
        assertTrue(result(catalog, Map.of(LUBE, 1_501L)).slippery());
        assertFalse(result(catalog, Map.of(LUBE, 1_499L, ACID, 2L)).superSlippery());
    }

    @Test
    void weightedFieldsUseTotalMixtureOrSlipperyPortionAsSpecified() {
        PrototypeCatalog<ReagentData> catalog = catalog(
                reagent("lube", "\"slipData\":{\"requiredSlipSpeed\":1,\"stunTime\":1,\"knockdownTime\":3,\"launchForwardsMultiplier\":2},\"friction\":0.1"),
                reagent("acid", "\"friction\":0.9"));
        SlipperySolution.Outcome outcome = result(catalog, Map.of(LUBE, 2_000L, ACID, 2_000L));
        assertEquals(3.25d, outcome.requiredSlipSpeedBlocksPerSecond(), 1e-9);
        assertEquals(0.5d, outcome.friction(), 1e-7);
        assertEquals(1d, outcome.stunSeconds(), 1e-9);
        assertEquals(3d, outcome.knockdownSeconds(), 1e-9);
        assertEquals(2d, outcome.launchVelocityMultiplier(), 1e-9);
    }

    @Test
    void absentReagentFrictionUsesUpstreamDefaultInWholeMixtureWeighting() {
        ResourceKey<ReagentData> spaceLube = key("spacelube");
        ResourceKey<ReagentData> polytrinicAcid = key("polytrinicacid");
        PrototypeCatalog<ReagentData> catalog = catalog(
                reagent("spacelube", "\"slipData\":{\"requiredSlipSpeed\":1},\"friction\":0.05"),
                reagent("polytrinicacid", ""));

        SlipperySolution.Outcome mixed = result(catalog, Map.of(spaceLube, 2_000L, polytrinicAcid, 2_000L));
        assertEquals(0.525d, mixed.friction(), 1e-7);
        assertEquals(1d, result(catalog, Map.of(polytrinicAcid, 2_000L)).friction(), 1e-9);
    }

    @Test
    void outputIsIndependentOfInputMapOrderingAndEmptySolutionIsSafe() {
        PrototypeCatalog<ReagentData> catalog = catalog(
                reagent("lube", "\"slipData\":{\"requiredSlipSpeed\":1}"), reagent("acid", ""));
        Map<ResourceKey<ReagentData>, Long> forward = new LinkedHashMap<>();
        forward.put(LUBE, 3_000L);
        forward.put(ACID, 2_000L);
        Map<ResourceKey<ReagentData>, Long> reverse = new LinkedHashMap<>();
        reverse.put(ACID, 2_000L);
        reverse.put(LUBE, 3_000L);
        assertEquals(result(catalog, forward), result(catalog, reverse));
        SlipperySolution.Outcome empty = result(catalog, Map.of());
        assertTrue(empty.inert());
        assertEquals(0L, empty.totalCents());
        assertEquals(SlipperySolution.DEFAULT_SLIP_SPEED, empty.requiredSlipSpeedBlocksPerSecond());
    }

    @Test
    void unknownKeysAndInvalidCentOrPrototypeValuesFailClosed() {
        PrototypeCatalog<ReagentData> catalog = catalog(reagent("lube", ""));
        assertThrows(IllegalArgumentException.class, () -> result(catalog, Map.of(ACID, 1L)));
        assertThrows(IllegalArgumentException.class, () -> result(catalog, Map.of(LUBE, -1L)));
        ReagentData invalid = decode("lube", "\"slipData\":{\"requiredSlipSpeed\":-1}");
        assertThrows(IllegalArgumentException.class,
                () -> result(new PrototypeCatalog<>(Map.of(LUBE.location(), invalid)), Map.of(LUBE, 2_000L)));
    }

    @Test
    void extendedSlipSourceFieldsRoundTripAndAuditTheirRanges() {
        ReagentData source = decode("lube", "\"slipData\":{\"requiredSlipSpeed\":1,\"stunTime\":0.25,"
                + "\"knockdownTime\":2,\"launchForwardsMultiplier\":1.25,\"autoStand\":false,\"slipFriction\":0.5}");
        var encoded = ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
        assertTrue(encoded.getAsJsonObject().has("slipData"));
        assertFalse(encoded.getAsJsonObject().has("slipdata"));
        assertTrue(encoded.getAsJsonObject().getAsJsonObject("slipData").has("launchForwardsMultiplier"));
        assertFalse(encoded.getAsJsonObject().getAsJsonObject("slipData").has("launchvelocitymultiplier"));
        ReagentData decoded = ReagentData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(source.slipData(), decoded.slipData());
        assertEquals(0.25f, decoded.slipData().orElseThrow().stunTime().orElseThrow());

        assertDoesNotThrow(() -> ReagentSchemaAudit.audit(id("lube"),
                JsonParser.parseString("{\"id\":\"lube\",\"slipData\":{\"requiredSlipSpeed\":1,"
                        + "\"stunTime\":0,\"knockdownTime\":0,\"launchForwardsMultiplier\":0},"
                        + "\"friction\":1}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ReagentSchemaAudit.audit(id("lube"),
                JsonParser.parseString("{\"id\":\"lube\",\"slipData\":{\"requiredSlipSpeed\":1,"
                        + "\"stunTime\":-1}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ReagentSchemaAudit.audit(id("lube"),
                JsonParser.parseString("{\"id\":\"lube\",\"slipdata\":{\"requiredslipspeed\":1}}").getAsJsonObject()));
        assertDoesNotThrow(() -> ReagentSchemaAudit.audit(id("lube"),
                JsonParser.parseString("{\"id\":\"lube\",\"friction\":1.1}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ReagentSchemaAudit.audit(id("lube"),
                JsonParser.parseString("{\"id\":\"lube\",\"friction\":-0.1}").getAsJsonObject()));
    }

    @Test
    void pinnedDefaultsApplyWhenSlipFieldsAreOmittedAndFrictionCanExceedOne() {
        ReagentData defaults = decode("lube", "\"slipData\":{}");
        SlipData slip = defaults.slipData().orElseThrow();
        assertEquals(3.5f, slip.requiredSlipSpeed());
        assertEquals(0.5d, SlipperySolution.DEFAULT_STUN_SECONDS);
        assertEquals(1.5d, SlipperySolution.DEFAULT_KNOCKDOWN_SECONDS);
        assertEquals(1.5d, SlipperySolution.DEFAULT_LAUNCH_VELOCITY_MULTIPLIER);
        assertTrue(slip.autoStandOrDefault());
        assertEquals(0.5f, slip.slipFrictionOrDefault());
        assertEquals(0.5f, SlipData.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"slipFriction\":0.5}")).getOrThrow().slipFriction().orElseThrow());

        PrototypeCatalog<ReagentData> catalog = catalog(reagent("lube", "\"friction\":1.5"));
        assertEquals(1.5d, result(catalog, Map.of(LUBE, 2_000L)).friction(), 1e-9);
    }

    private static SlipperySolution.Outcome result(PrototypeCatalog<ReagentData> catalog,
                                                    Map<ResourceKey<ReagentData>, Long> solution) {
        return SlipperySolution.calculate(solution, catalog);
    }

    private static PrototypeCatalog<ReagentData> catalog(ReagentData... values) {
        Map<ResourceLocation, ReagentData> entries = new HashMap<>();
        for (ReagentData value : values) entries.put(id(value.id()), value);
        return new PrototypeCatalog<>(entries);
    }

    private static ReagentData reagent(String id, String fields) {
        return decode(id, fields);
    }

    private static ReagentData decode(String id, String fields) {
        String source = fields.isEmpty() ? "{\"id\":\"" + id + "\"}"
                : "{\"id\":\"" + id + "\"," + fields + "}";
        return ReagentData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(source)).getOrThrow();
    }

    private static ResourceKey<ReagentData> key(String path) {
        return ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }
}
