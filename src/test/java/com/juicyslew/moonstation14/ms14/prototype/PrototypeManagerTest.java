package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrototypeManagerTest {
    private static final String NAMESPACE = "moonstation14";
    private static final Codec<NumberPrototype> NUMBER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("value").forGetter(NumberPrototype::value)
    ).apply(instance, NumberPrototype::new));
    private static final Codec<RichPrototype> RICH_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(RichPrototype::name),
            Codec.STRING.listOf().fieldOf("tags").forGetter(RichPrototype::tags)
    ).apply(instance, RichPrototype::new));

    @Test
    void resolvesInheritedJsonAndPublishesTypedValues() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = type("number", "number");
        manager.register(type);

        manager.reload(type, raw(
                entry("base", "{\"value\":4}"),
                entry("child", "{\"parent\":\"base\"}")));

        assertEquals(new NumberPrototype(4), manager.snapshot(type).get(id("child")));
    }

    @Test
    void malformedCodecInputIncludesLoadContextAndDiagnostic() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = type("number", "number");
        manager.register(type);

        PrototypeLoadException exception = assertThrows(PrototypeLoadException.class,
                () -> manager.reload(type, raw(entry("broken", "{\"value\":\"not-a-number\"}"))));

        assertEquals(type.typeId(), exception.typeId());
        assertEquals(id("broken"), exception.prototypeId());
        assertEquals("data/moonstation14/number/broken.json", exception.resourcePath());
        assertFalse(exception.diagnostic().isBlank());
        assertTrue(exception.getMessage().contains(exception.diagnostic()));
        assertTrue(exception.getMessage().contains("broken"));
    }

    @Test
    void failedDecodePreservesPreviousSnapshot() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = type("number", "number");
        manager.register(type);
        manager.reload(type, raw(entry("stable", "{\"value\":7}")));

        assertThrows(PrototypeLoadException.class,
                () -> manager.reload(type, raw(entry("stable", "{\"value\":false}"))));

        assertEquals(new NumberPrototype(7), manager.snapshot(type).get(id("stable")));
    }

    @Test
    void validatorFailurePreservesPreviousSnapshot() {
        ResourceLocation required = id("required");
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = new PrototypeType<>(
                id("number"), "number", NUMBER_CODEC,
                catalog -> {
                    if (!catalog.contains(required)) {
                        throw new IllegalArgumentException("required prototype is missing");
                    }
                });
        manager.register(type);
        manager.reload(type, raw(entry("required", "{\"value\":1}")));

        PrototypeLoadException exception = assertThrows(PrototypeLoadException.class,
                () -> manager.reload(type, raw(entry("other", "{\"value\":2}"))));

        assertTrue(exception.getMessage().contains("catalog validation failed"));
        assertEquals(new NumberPrototype(1), manager.snapshot(type).get(required));
        assertFalse(manager.snapshot(type).contains(id("other")));
    }

    @Test
    void jsonValidationFailureRollsBackEveryType() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> first = new PrototypeType<>(
                id("first"), "first", NUMBER_CODEC,
                (owned, resolved) -> {
                    if (resolved.contains(id("bad"))) {
                        throw new IllegalArgumentException("bad JSON catalog");
                    }
                });
        PrototypeType<NumberPrototype> second = type("second", "second");
        manager.register(first);
        manager.register(second);
        manager.reload(Map.of(
                first, raw(entry("stable", "{\"value\":1}")),
                second, raw(entry("stable", "{\"value\":2}"))));

        assertThrows(PrototypeLoadException.class, () -> manager.reload(Map.of(
                first, raw(entry("bad", "{\"value\":3}")),
                second, raw(entry("replacement", "{\"value\":4}")))));

        assertEquals(new NumberPrototype(1), manager.snapshot(first).get(id("stable")));
        assertEquals(new NumberPrototype(2), manager.snapshot(second).get(id("stable")));
    }

    @Test
    void resolverFailureRollsBackEveryType() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> first = type("first", "first");
        PrototypeType<NumberPrototype> second = type("second", "second");
        manager.register(first);
        manager.register(second);
        manager.reload(Map.of(
                first, raw(entry("stable", "{\"value\":1}")),
                second, raw(entry("stable", "{\"value\":2}"))));

        assertThrows(PrototypeResolutionException.class, () -> manager.reload(Map.of(
                first, raw(entry("replacement", "{\"value\":3}")),
                second, raw(entry("child", "{\"parent\":\"missing\"}")))));

        assertEquals(new NumberPrototype(1), manager.snapshot(first).get(id("stable")));
        assertEquals(new NumberPrototype(2), manager.snapshot(second).get(id("stable")));
    }

    @Test
    void jsonValidatorSeesOwnedAbstractsAndResolvedConcreteJsonBeforeDecode() {
        AtomicBoolean validated = new AtomicBoolean();
        Codec<NumberPrototype> trackingCodec = NUMBER_CODEC.flatXmap(
                value -> {
                    assertTrue(validated.get());
                    return DataResult.success(value);
                },
                DataResult::success);
        PrototypeType<NumberPrototype> type = new PrototypeType<>(
                id("number"), "number", trackingCodec,
                (owned, resolved) -> {
                    assertTrue(owned.contains(id("abstract")));
                    assertTrue(owned.get(id("abstract")).get("abstract").getAsBoolean());
                    assertTrue(resolved.contains(id("child")));
                    assertFalse(resolved.contains(id("abstract")));
                    assertEquals(4, resolved.get(id("child")).get("value").getAsInt());
                    validated.set(true);
                });
        PrototypeManager manager = new PrototypeManager();
        manager.register(type);
        manager.reload(type, raw(entry("abstract", "{\"abstract\":true,\"value\":4}"),
                entry("child", "{\"parent\":\"abstract\"}")));
    }

    @Test
    void clearPublishedCatalogsKeepsRegistrationsAndSkipsValidators() {
        int[] validations = {0};
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = new PrototypeType<>(
                id("number"), "number", NUMBER_CODEC, catalog -> validations[0]++);
        manager.register(type);
        manager.reload(type, raw(entry("stable", "{\"value\":7}")));
        int beforeClear = validations[0];

        manager.clearPublishedCatalogs();

        assertTrue(manager.registeredTypes().contains(type));
        assertTrue(manager.snapshot(type).asMap().isEmpty());
        assertEquals(beforeClear, validations[0]);
    }

    @Test
    void registrationRejectsDuplicateIdsAndDirectories() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(type("number", "number"));

        IllegalArgumentException duplicateId = assertThrows(IllegalArgumentException.class,
                () -> manager.register(type("number", "other")));
        assertTrue(duplicateId.getMessage().contains("type ID"));

        IllegalArgumentException duplicateDirectory = assertThrows(IllegalArgumentException.class,
                () -> manager.register(type("other", "number")));
        assertTrue(duplicateDirectory.getMessage().contains("resource directory"));
    }

    @Test
    void managerInstancesDoNotShareCatalogs() {
        PrototypeType<NumberPrototype> type = type("number", "number");
        PrototypeManager first = new PrototypeManager();
        PrototypeManager second = new PrototypeManager();
        first.register(type);
        second.register(type);
        first.publishDecoded(type, Map.of(id("only-first"), new NumberPrototype(1)));

        assertTrue(first.snapshot(type).contains(id("only-first")));
        assertFalse(second.snapshot(type).contains(id("only-first")));
    }

    @Test
    void publishDecodedReplacesTheCatalogInsteadOfMerging() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = type("number", "number");
        manager.register(type);
        manager.publishDecoded(type, Map.of(id("stable"), new NumberPrototype(1),
                id("removed"), new NumberPrototype(2)));

        manager.publishDecoded(type, Map.of(id("replacement"), new NumberPrototype(3)));

        assertEquals(new NumberPrototype(3), manager.snapshot(type).get(id("replacement")));
        assertFalse(manager.snapshot(type).contains(id("stable")));
        assertFalse(manager.snapshot(type).contains(id("removed")));
    }

    @Test
    void publishDecodedOwnsInputAndExposesAnImmutableSnapshot() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = type("number", "number");
        manager.register(type);
        Map<ResourceLocation, NumberPrototype> entries = new LinkedHashMap<>();
        entries.put(id("stable"), new NumberPrototype(1));

        manager.publishDecoded(type, entries);
        entries.clear();
        entries.put(id("mutated"), new NumberPrototype(2));

        PrototypeCatalog<NumberPrototype> snapshot = manager.snapshot(type);
        assertTrue(snapshot.contains(id("stable")));
        assertFalse(snapshot.contains(id("mutated")));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.asMap().put(id("new"), new NumberPrototype(3)));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.keys().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.values().clear());
    }

    @Test
    void publishDecodedLeavesUnrelatedCatalogsUnchanged() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> first = type("first", "first");
        PrototypeType<NumberPrototype> second = type("second", "second");
        manager.register(first);
        manager.register(second);
        manager.publishDecoded(first, Map.of(id("first"), new NumberPrototype(1)));
        manager.publishDecoded(second, Map.of(id("second"), new NumberPrototype(2)));

        manager.publishDecoded(first, Map.of(id("replacement"), new NumberPrototype(3)));

        assertEquals(new NumberPrototype(2), manager.snapshot(second).get(id("second")));
        assertFalse(manager.snapshot(second).contains(id("replacement")));
    }

    @Test
    void publishDecodedRunsTypedValidatorAndWrapsFailureWithoutPublishing() {
        AtomicInteger validations = new AtomicInteger();
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = new PrototypeType<>(
                id("number"), "number", NUMBER_CODEC,
                catalog -> {
                    validations.incrementAndGet();
                    if (!catalog.contains(id("required"))) {
                        throw new IllegalArgumentException("required prototype is missing");
                    }
                });
        manager.register(type);
        manager.publishDecoded(type, Map.of(id("required"), new NumberPrototype(1)));

        PrototypeLoadException exception = assertThrows(PrototypeLoadException.class,
                () -> manager.publishDecoded(type, Map.of(id("other"), new NumberPrototype(2))));

        assertEquals(2, validations.get());
        assertEquals(type.typeId(), exception.typeId());
        assertEquals(null, exception.prototypeId());
        assertEquals("data/*/number/*.json", exception.resourcePath());
        assertTrue(exception.getMessage().contains("catalog validation failed"));
        assertEquals(new NumberPrototype(1), manager.snapshot(type).get(id("required")));
        assertFalse(manager.snapshot(type).contains(id("other")));
    }

    @Test
    void publishDecodedRejectsNullEntriesWithoutPublishing() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = type("number", "number");
        manager.register(type);
        manager.publishDecoded(type, Map.of(id("stable"), new NumberPrototype(1)));
        Map<ResourceLocation, NumberPrototype> entries = new LinkedHashMap<>();
        entries.put(id("replacement"), null);

        assertThrows(NullPointerException.class, () -> manager.publishDecoded(type, entries));

        assertEquals(new NumberPrototype(1), manager.snapshot(type).get(id("stable")));
        assertFalse(manager.snapshot(type).contains(id("replacement")));
    }

    @Test
    void publishDecodedRequiresTheRegisteredDescriptorInstance() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> registered = type("number", "number");
        PrototypeType<NumberPrototype> sameIdDifferentDescriptor = type("number", "other");
        PrototypeType<NumberPrototype> unregistered = type("unregistered", "unregistered");
        manager.register(registered);

        IllegalArgumentException differentDescriptor = assertThrows(IllegalArgumentException.class,
                () -> manager.publishDecoded(sameIdDifferentDescriptor,
                        Map.of(id("value"), new NumberPrototype(1))));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> manager.publishDecoded(unregistered, Map.of(id("value"), new NumberPrototype(1))));

        assertTrue(differentDescriptor.getMessage().contains("registered instance"));
        assertTrue(missing.getMessage().contains("not registered"));
        assertTrue(manager.snapshot(registered).asMap().isEmpty());
    }

    @Test
    void stagedReloadDoesNotPublishUntilItsCommitPoint() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = type("number", "number");
        manager.register(type);
        manager.reload(type, raw(entry("stable", "{\"value\":1}")));

        PrototypeManager.PreparedReload prepared = manager.stage(
                Map.of(type, raw(entry("replacement", "{\"value\":2}"))));

        assertTrue(manager.hasStagedReload());
        assertEquals(new NumberPrototype(1), manager.snapshot(type).get(id("stable")));
        assertTrue(manager.commit(prepared));
        assertEquals(new NumberPrototype(2), manager.snapshot(type).get(id("replacement")));
        assertFalse(manager.snapshot(type).contains(id("stable")));
        assertFalse(manager.hasStagedReload());
    }

    @Test
    void reloadTransactionsRejectForeignStaleAndDoubleCommit() {
        PrototypeManager first = new PrototypeManager();
        PrototypeManager second = new PrototypeManager();
        PrototypeType<NumberPrototype> firstType = type("first", "first");
        PrototypeType<NumberPrototype> secondType = type("second", "second");
        first.register(firstType);
        second.register(secondType);

        PrototypeManager.PreparedReload foreign = first.stage(
                Map.of(firstType, raw(entry("value", "{\"value\":1}"))));
        assertFalse(second.commit(foreign));

        PrototypeManager.PreparedReload stale = first.stage(
                Map.of(firstType, raw(entry("old", "{\"value\":2}"))));
        PrototypeManager.PreparedReload current = first.stage(
                Map.of(firstType, raw(entry("new", "{\"value\":3}"))));
        assertFalse(first.commit(stale));
        assertTrue(first.commit(current));
        assertFalse(first.commit(current));
        assertTrue(first.snapshot(firstType).contains(id("new")));
    }

    @Test
    void failedCandidateValidationLeavesPublishedCatalogAndNoStagedCandidate() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = type("number", "number");
        manager.register(type);
        manager.reload(type, raw(entry("stable", "{\"value\":7}")));

        assertThrows(IllegalArgumentException.class, () -> manager.stage(
                Map.of(type, raw(entry("replacement", "{\"value\":8}"))),
                ignored -> {
                    throw new IllegalArgumentException("does not fit transport");
                }));

        assertFalse(manager.hasStagedReload());
        assertEquals(new NumberPrototype(7), manager.snapshot(type).get(id("stable")));
        assertFalse(manager.snapshot(type).contains(id("replacement")));
    }

    @Test
    void multiTypeReloadPublishesAllOrNothing() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> first = type("first", "first");
        PrototypeType<NumberPrototype> second = type("second", "second");
        manager.register(first);
        manager.register(second);
        manager.reload(Map.of(
                first, raw(entry("value", "{\"value\":1}")),
                second, raw(entry("value", "{\"value\":2}"))));

        assertThrows(PrototypeLoadException.class, () -> manager.reload(Map.of(
                first, raw(entry("value", "{\"value\":10}")),
                second, raw(entry("broken", "{\"value\":\"bad\"}")))));

        assertEquals(new NumberPrototype(1), manager.snapshot(first).get(id("value")));
        assertEquals(new NumberPrototype(2), manager.snapshot(second).get(id("value")));
    }

    @Test
    void encodedCatalogRoundTripPreservesOrderAndEmptyCatalogs() {
        PrototypeType<NumberPrototype> first = type("first", "first");
        PrototypeType<NumberPrototype> second = type("second", "second");
        PrototypeType<NumberPrototype> empty = type("empty", "empty");
        PrototypeManager server = new PrototypeManager();
        server.register(first);
        server.register(second);
        server.register(empty);

        server.publishDecoded(first, decoded(numberEntry("first", 1), numberEntry("second", 2)));
        server.publishDecoded(second, decoded(numberEntry("only", 3)));

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encoded = server.encodePublishedCatalogs();

        assertIterableEquals(List.of(first.typeId(), second.typeId(), empty.typeId()), encoded.keySet());
        assertIterableEquals(List.of(id("first"), id("second")), encoded.get(first.typeId()).keySet());
        assertTrue(encoded.get(empty.typeId()).isEmpty());

        PrototypeManager client = new PrototypeManager();
        PrototypeType<NumberPrototype> clientFirst = type("first", "client-first");
        PrototypeType<NumberPrototype> clientSecond = type("second", "client-second");
        PrototypeType<NumberPrototype> clientEmpty = type("empty", "client-empty");
        client.register(clientFirst);
        client.register(clientSecond);
        client.register(clientEmpty);
        client.publishEncodedCatalogs(encoded);

        assertEquals(new NumberPrototype(1), client.snapshot(clientFirst).get(id("first")));
        assertEquals(new NumberPrototype(2), client.snapshot(clientFirst).get(id("second")));
        assertEquals(new NumberPrototype(3), client.snapshot(clientSecond).get(id("only")));
        assertTrue(client.snapshot(clientEmpty).asMap().isEmpty());
    }

    @Test
    void encodedExportAndImportOwnNestedJson() {
        PrototypeType<RichPrototype> type = new PrototypeType<>(id("rich"), "rich", RICH_CODEC);
        PrototypeManager server = new PrototypeManager();
        server.register(type);
        server.publishDecoded(type, Map.of(id("value"), new RichPrototype("stable", List.of("one"))));

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encoded = server.encodePublishedCatalogs();
        encoded.get(type.typeId()).get(id("value")).getAsJsonArray("tags").add("caller");
        encoded.get(type.typeId()).get(id("value")).addProperty("name", "changed");

        assertEquals(new RichPrototype("stable", List.of("one")),
                server.snapshot(type).get(id("value")));
        assertEquals("stable", server.encodePublishedCatalogs().get(type.typeId()).get(id("value"))
                .get("name").getAsString());
        assertEquals(1, server.encodePublishedCatalogs().get(type.typeId()).get(id("value"))
                .getAsJsonArray("tags").size());

        PrototypeManager client = new PrototypeManager();
        PrototypeType<RichPrototype> clientType = new PrototypeType<>(id("rich"), "other-rich", RICH_CODEC);
        client.register(clientType);
        client.publishEncodedCatalogs(server.encodePublishedCatalogs());
        encoded = server.encodePublishedCatalogs();
        encoded.get(type.typeId()).get(id("value")).addProperty("name", "input-mutated");

        assertEquals(new RichPrototype("stable", List.of("one")), client.snapshot(clientType).get(id("value")));
    }

    @Test
    void encodedImportReplacesEveryCatalogAtomically() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> first = type("first", "first");
        PrototypeType<NumberPrototype> second = type("second", "second");
        manager.register(first);
        manager.register(second);
        manager.publishDecoded(first, decoded(numberEntry("old-first", 1)));
        manager.publishDecoded(second, decoded(numberEntry("old-second", 2)));

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encoded = new LinkedHashMap<>();
        encoded.put(first.typeId(), raw(entry("new-first", "{\"value\":3}")));
        encoded.put(second.typeId(), new LinkedHashMap<>());
        manager.publishEncodedCatalogs(encoded);

        assertEquals(new NumberPrototype(3), manager.snapshot(first).get(id("new-first")));
        assertFalse(manager.snapshot(first).contains(id("old-first")));
        assertTrue(manager.snapshot(second).asMap().isEmpty());
        assertFalse(manager.snapshot(second).contains(id("old-second")));
    }

    @Test
    void encodedImportShapeCodecAndValidatorFailuresPreserveEverything() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> first = type("first", "first");
        PrototypeType<NumberPrototype> second = new PrototypeType<>(id("second"), "second", NUMBER_CODEC,
                (PrototypeCatalogValidator<NumberPrototype>) catalog -> {
                    if (catalog.contains(id("bad"))) {
                        throw new IllegalArgumentException("bad typed catalog");
                    }
                });
        manager.register(first);
        manager.register(second);
        manager.publishDecoded(first, decoded(numberEntry("stable-first", 1)));
        manager.publishDecoded(second, decoded(numberEntry("stable-second", 2)));
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> stable = manager.encodePublishedCatalogs();

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> missing = new LinkedHashMap<>(stable);
        missing.remove(first.typeId());
        assertThrows(IllegalArgumentException.class, () -> manager.publishEncodedCatalogs(missing));
        assertStable(manager, first, second);

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> unknown = new LinkedHashMap<>(stable);
        unknown.put(id("unknown"), new LinkedHashMap<>());
        assertThrows(IllegalArgumentException.class, () -> manager.publishEncodedCatalogs(unknown));
        assertStable(manager, first, second);

        assertThrows(NullPointerException.class, () -> manager.publishEncodedCatalogs(null));
        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> nullCatalog = new LinkedHashMap<>(stable);
        nullCatalog.put(second.typeId(), null);
        assertThrows(NullPointerException.class, () -> manager.publishEncodedCatalogs(nullCatalog));
        assertStable(manager, first, second);

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> brokenCodec = new LinkedHashMap<>(stable);
        brokenCodec.put(first.typeId(), raw(entry("broken", "{\"value\":\"bad\"}")));
        assertThrows(PrototypeLoadException.class, () -> manager.publishEncodedCatalogs(brokenCodec));
        assertStable(manager, first, second);

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> brokenValidator = new LinkedHashMap<>(stable);
        brokenValidator.put(second.typeId(), raw(entry("bad", "{\"value\":4}")));
        assertThrows(PrototypeLoadException.class, () -> manager.publishEncodedCatalogs(brokenValidator));
        assertStable(manager, first, second);
    }

    @Test
    void encodedImportRunsTypedButNotJsonCatalogValidators() {
        AtomicInteger jsonValidations = new AtomicInteger();
        AtomicInteger typedValidations = new AtomicInteger();
        PrototypeType<NumberPrototype> type = new PrototypeType<>(id("number"), "number", NUMBER_CODEC,
                (PrototypeJsonCatalogValidator) (owned, resolved) -> jsonValidations.incrementAndGet(),
                (PrototypeCatalogValidator<NumberPrototype>) catalog -> typedValidations.incrementAndGet());
        PrototypeManager manager = new PrototypeManager();
        manager.register(type);
        manager.publishEncodedCatalogs(Map.of(type.typeId(), raw(entry("value", "{\"value\":5}"))));

        assertEquals(1, typedValidations.get());
        assertEquals(0, jsonValidations.get());
        assertEquals(new NumberPrototype(5), manager.snapshot(type).get(id("value")));
    }

    @Test
    void nonObjectExportPreservesPreviousCatalog() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<Integer> type = new PrototypeType<>(id("number"), "number", Codec.INT);
        manager.register(type);
        manager.publishDecoded(type, Map.of(id("stable"), 7));

        assertThrows(PrototypeLoadException.class, manager::encodePublishedCatalogs);
        assertEquals(7, manager.snapshot(type).get(id("stable")));
    }

    @Test
    void snapshotsAreReadOnly() {
        PrototypeManager manager = new PrototypeManager();
        PrototypeType<NumberPrototype> type = type("number", "number");
        manager.register(type);

        assertThrows(UnsupportedOperationException.class, () -> manager.snapshot().clear());
        assertThrows(UnsupportedOperationException.class, () -> manager.registeredTypes().clear());
    }

    private static PrototypeType<NumberPrototype> type(String typeId, String directory) {
        return new PrototypeType<>(id(typeId), directory, NUMBER_CODEC);
    }

    @SafeVarargs
    private static Map<ResourceLocation, NumberPrototype> decoded(Map.Entry<String, Integer>... entries) {
        Map<ResourceLocation, NumberPrototype> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            result.put(id(entry.getKey()), new NumberPrototype(entry.getValue()));
        }
        return result;
    }

    private static Map.Entry<String, Integer> numberEntry(String id, int value) {
        return Map.entry(id, value);
    }

    private static void assertStable(PrototypeManager manager,
                                     PrototypeType<NumberPrototype> first,
                                     PrototypeType<NumberPrototype> second) {
        assertEquals(new NumberPrototype(1), manager.snapshot(first).get(id("stable-first")));
        assertEquals(new NumberPrototype(2), manager.snapshot(second).get(id("stable-second")));
    }

    @SafeVarargs
    private static Map<ResourceLocation, com.google.gson.JsonObject> raw(Map.Entry<String, String>... entries) {
        Map<ResourceLocation, com.google.gson.JsonObject> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            result.put(id(entry.getKey()),
                    com.google.gson.JsonParser.parseString(entry.getValue()).getAsJsonObject());
        }
        return result;
    }

    private static Map.Entry<String, String> entry(String id, String json) {
        return Map.entry(id, json);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    private record NumberPrototype(int value) {
    }

    private record RichPrototype(String name, List<String> tags) {
    }
}
