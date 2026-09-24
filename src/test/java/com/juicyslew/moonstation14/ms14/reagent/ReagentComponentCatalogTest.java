package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReagentComponentCatalogTest {
    @Test
    void blendsColorsFromTheSuppliedResolvedCatalog() {
        ResourceKey<ReagentData> red = ModReagents.createKey("red");
        ResourceKey<ReagentData> green = ModReagents.createKey("green");
        PrototypeCatalog<ReagentData> catalog = catalog(
                reagent("red", "0xFF0000"), reagent("green", "0x00FF00"));

        int blended = ReagentComponent.getBlendedColorFromCatalog(Map.of(red, 1f, green, 3f), catalog);

        assertEquals(0xFF3FBF00, blended);
    }

    @Test
    void preservesSingleReagentColorAtSubnormalPositiveAmounts() {
        ResourceKey<ReagentData> blue = ModReagents.createKey("blue");
        PrototypeCatalog<ReagentData> catalog = catalog(reagent("blue", "0x123456"));

        assertEquals(ReagentComponent.getBlendedColorFromCatalog(Map.of(blue, 1f), catalog),
                ReagentComponent.getBlendedColorFromCatalog(Map.of(blue, Float.MIN_VALUE), catalog));
        assertEquals(0xFF123456, ReagentComponent.getBlendedColorFromCatalog(
                Map.of(blue, Float.MIN_VALUE), catalog));
    }

    @Test
    void ignoresNonPositiveAndNonFiniteWeightsAndReturnsNoTintWhenNoPositiveWeightExists() {
        ResourceKey<ReagentData> red = ModReagents.createKey("red");
        PrototypeCatalog<ReagentData> catalog = catalog(reagent("red", "0xFF0000"));
        Map<ResourceKey<ReagentData>, Float> weights = new HashMap<>();
        weights.put(red, 0f);

        assertEquals(-1, ReagentComponent.getBlendedColorFromCatalog(weights, catalog));
        weights.put(red, -1f);
        assertEquals(-1, ReagentComponent.getBlendedColorFromCatalog(weights, catalog));
        weights.put(red, Float.NaN);
        assertEquals(-1, ReagentComponent.getBlendedColorFromCatalog(weights, catalog));
        weights.put(red, Float.POSITIVE_INFINITY);
        assertEquals(-1, ReagentComponent.getBlendedColorFromCatalog(weights, catalog));
    }

    @Test
    void suppliedCatalogsRemainIsolated() {
        ResourceKey<ReagentData> reagent = ModReagents.createKey("same-id");
        PrototypeCatalog<ReagentData> serverCatalog = catalog(reagent("same-id", "0xFF0000"));
        PrototypeCatalog<ReagentData> clientCatalog = catalog(reagent("same-id", "0x0000FF"));

        assertEquals(0xFFFF0000, ReagentComponent.getBlendedColorFromCatalog(Map.of(reagent, 1f), serverCatalog));
        assertEquals(0xFF0000FF, ReagentComponent.getBlendedColorFromCatalog(Map.of(reagent, 1f), clientCatalog));
    }

    @Test
    void missingReagentIsSkippedAndWarnedOnceWhileValidEntriesStillBlend() {
        ResourceKey<ReagentData> missing = ModReagents.createKey("missing");
        ResourceKey<ReagentData> red = ModReagents.createKey("red");
        java.util.List<String> warnings = new java.util.ArrayList<>();
        ReagentComponent.resetColorWarningsForTests();
        ReagentComponent.setColorWarningSinkForTests(warnings::add);
        PrototypeCatalog<ReagentData> catalog = catalog(reagent("red", "0xFF0000"));

        assertEquals(-1, ReagentComponent.getBlendedColorFromCatalog(Map.of(missing, 1f), new PrototypeCatalog<>(Map.of())));
        assertEquals(0xFFFF0000, ReagentComponent.getBlendedColorFromCatalog(Map.of(missing, 1f, red, 2f), catalog));
        ReagentComponent.getBlendedColorFromCatalog(Map.of(missing, 1f), catalog);

        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("moonstation14:missing"));
        assertTrue(warnings.getFirst().contains("resolved prototype catalog"));
        ReagentComponent.resetColorWarningsForTests();
    }

    @Test
    void emptyContentsAndNullLevelHaveNoTintFallback() {
        assertEquals(-1, ReagentComponent.getBlendedColor(Map.of(), null));
        assertEquals(-1, ReagentComponent.getBlendedColor(
                Map.of(ModReagents.createKey("not-yet-loaded"), 1f), null));
    }

    @Test
    void componentAttachmentAndSnapshotsDefensivelyCopyAndCodecRoundTrip() {
        ResourceKey<ReagentData> reagent = ModReagents.createKey("copy-test");
        Map<ResourceKey<ReagentData>, Float> source = new HashMap<>();
        source.put(reagent, 2f);

        ReagentComponent component = new ReagentComponent(source);
        source.put(ModReagents.createKey("later"), 3f);
        assertEquals(Map.of(reagent, 2f), component.contents());
        assertThrows(UnsupportedOperationException.class, () -> component.contents().put(reagent, 4f));

        ReagentAttachment attachment = new ReagentAttachment(source);
        source.put(ModReagents.createKey("after-attachment"), 4f);
        assertFalse(attachment.getMap().containsKey(ModReagents.createKey("after-attachment")));
        assertThrows(UnsupportedOperationException.class,
                () -> attachment.getMap().put(reagent, 4f));

        ReagentComponent snapshot = attachment.toComponent();
        attachment.specificAdd(ModReagents.createKey("after-snapshot"), 5f, 200_000f);
        assertFalse(snapshot.contents().containsKey(ModReagents.createKey("after-snapshot")));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.contents().put(reagent, 6f));

        var encoded = ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, component).getOrThrow();
        assertEquals(component, ReagentComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        var attachmentEncoded = ReagentAttachment.CODEC.encodeStart(JsonOps.INSTANCE, attachment).getOrThrow();
        assertEquals(attachment, ReagentAttachment.CODEC.parse(JsonOps.INSTANCE, attachmentEncoded).getOrThrow());
    }

    @Test
    void codecPreservesAnUnknownReagentIdForRepair() {
        ResourceKey<ReagentData> missing = ModReagents.createKey("does_not_exist");
        ReagentComponent component = new ReagentComponent(Map.of(missing, 30f));
        var encoded = ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, component).getOrThrow();

        assertEquals(component, ReagentComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(30f, ReagentComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()
                .contents().get(missing));
    }

    @Test
    void runtimeValidationRejectsUnknownPositiveEntriesAndLogsOnce() {
        ResourceKey<ReagentData> missing = ModReagents.createKey("does_not_exist");
        java.util.List<String> warnings = new java.util.ArrayList<>();
        ReagentCatalogValidation.resetWarningsForTests();
        ReagentCatalogValidation.setWarningSinkForTests(warnings::add);

        assertFalse(ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                Map.of(missing, 1f), new PrototypeCatalog<>(Map.of()), "test bottle"));
        assertFalse(ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                Map.of(missing, 1f), new PrototypeCatalog<>(Map.of()), "test bottle"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("moonstation14:does_not_exist"));
        ReagentCatalogValidation.resetWarningsForTests();
    }

    @Test
    void runtimeValidationRejectsUnknownDestinationWithoutDroppingStoredEntry() {
        ResourceKey<ReagentData> missing = ModReagents.createKey("invalid_destination");
        java.util.List<String> warnings = new java.util.ArrayList<>();
        ReagentCatalogValidation.resetWarningsForTests();
        ReagentCatalogValidation.setWarningSinkForTests(warnings::add);
        Map<ResourceKey<ReagentData>, Float> destination = Map.of(missing, 7f);

        assertFalse(ReagentCatalogValidation.hasOnlyKnownPositiveReagents(destination,
                new PrototypeCatalog<>(Map.of()), "transfer destination fixture"));
        assertEquals(Map.of(missing, 7f), destination, "validation must not discard invalid destination data");
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("transfer destination fixture"));
        assertTrue(warnings.getFirst().contains("moonstation14:invalid_destination"));
        ReagentCatalogValidation.resetWarningsForTests();
    }

    @Test
    void attachmentConstructionRejectsInvalidQuantities() {
        Map<ResourceKey<ReagentData>, Float> invalid = new HashMap<>();
        invalid.put(ModReagents.createKey("invalid-quantity"), Float.NaN);

        assertThrows(IllegalArgumentException.class, () -> new ReagentAttachment(invalid));
    }

    private static PrototypeCatalog<ReagentData> catalog(ReagentData... reagents) {
        return new PrototypeCatalog<>(java.util.Arrays.stream(reagents)
                .collect(java.util.stream.Collectors.toMap(
                        value -> ResourceLocation.fromNamespaceAndPath("moonstation14", value.id()),
                        value -> value)));
    }

    private static ReagentData reagent(String id, String color) {
        return ReagentData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"id\":\"" + id + "\",\"color\":\"" + color + "\"}"))
                .getOrThrow();
    }
}
