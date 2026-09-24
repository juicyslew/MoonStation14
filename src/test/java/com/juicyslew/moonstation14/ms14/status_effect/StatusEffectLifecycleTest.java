package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectEligibility;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusEffectLifecycleTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "lifecycle_test");
    private static final ResourceLocation OTHER_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "other");
    private static final ResourceLocation PERMANENT_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "permanent");
    private static final ResourceLocation MISSING_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "missing");
    private static final ResourceKey<StatusEffectData> KEY = ModStatusEffects.createKey("lifecycle_test");
    private static final ResourceKey<StatusEffectData> OTHER_KEY = ModStatusEffects.createKey("other");
    private static final ResourceKey<StatusEffectData> PERMANENT_KEY = ModStatusEffects.createKey("permanent");
    private static final ResourceKey<StatusEffectData> MISSING_KEY = ModStatusEffects.createKey("missing");
    private static final StatusEffectData DEFINITION = new StatusEffectData(
            "status.lifecycle_test", false, 0x123456,
            List.of(StatusEffectBehavior.MARKER, StatusEffectBehavior.CLIENT_JITTER),
            List.of(StatusEffectEligibility.LIVING_ENTITY));

    @Test
    void denialHappensBeforeMutation() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(false);
        StatusEffectAttachment attachment = new StatusEffectAttachment();

        StatusEffectReduction result = apply(attachment, lifecycle, OptionalInt.of(4), 0);

        assertEquals(StatusEffectChangeKind.UNCHANGED, result.kind());
        assertTrue(attachment.get(KEY).isEmpty());
        assertEquals(List.of("canApply"), lifecycle.events);
    }

    @Test
    void absentRemoveIsUnchangedBeforeLifecycleOrAttachmentMutation() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        StatusEffectAttachment attachment = new StatusEffectAttachment();

        StatusEffectReduction result = apply(
                attachment, lifecycle, OptionalInt.of(1), 0, StatusEffectOperation.REMOVE);

        assertEquals(StatusEffectChangeKind.UNCHANGED, result.kind());
        assertTrue(attachment.get(KEY).isEmpty());
        assertTrue(lifecycle.events.isEmpty());
    }

    @Test
    void missingDefinitionFailsBeforeMutation() {
        StatusEffectAttachment attachment = new StatusEffectAttachment();
        assertThrows(IllegalArgumentException.class, () -> StatusEffectSystem.apply(
                attachment, new PrototypeCatalog<>(Map.of()), new RecordingLifecycle(true), KEY,
                StatusEffectOperation.SET, OptionalInt.of(2), 0));
        assertTrue(attachment.get(KEY).isEmpty());
    }

    @Test
    void applicationCallbacksCoverCreationChangeRemovalAndPendingActivation() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        StatusEffectAttachment attachment = new StatusEffectAttachment();

        apply(attachment, lifecycle, OptionalInt.of(4), 0);
        apply(attachment, lifecycle, OptionalInt.of(5), 0);
        apply(attachment, lifecycle, OptionalInt.empty(), 0, StatusEffectOperation.REMOVE);
        assertEquals(List.of("canApply", "onApplied", "ensureApplied", "onChanged", "ensureApplied", "onRemoved"),
                lifecycle.events);

        lifecycle.events.clear();
        apply(attachment, lifecycle, OptionalInt.of(3), 2);
        assertEquals(List.of("canApply"), lifecycle.events);
        apply(attachment, lifecycle, OptionalInt.of(3), 0, StatusEffectOperation.SET);
        assertEquals(List.of("canApply", "onChanged", "onApplied", "ensureApplied"), lifecycle.events);

        lifecycle.events.clear();
        apply(attachment, lifecycle, OptionalInt.empty(), 0, StatusEffectOperation.REMOVE);
        assertEquals(List.of("onRemoved"), lifecycle.events);
    }

    @Test
    void finiteRemoveRemovesActiveAndPendingPermanentStatuses() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        StatusEffectAttachment attachment = new StatusEffectAttachment();

        apply(attachment, lifecycle, OptionalInt.empty(), 0, StatusEffectOperation.SET);
        lifecycle.events.clear();
        StatusEffectReduction activeRemoval = apply(
                attachment, lifecycle, OptionalInt.of(1), 0, StatusEffectOperation.REMOVE);
        assertEquals(StatusEffectChangeKind.REMOVED, activeRemoval.kind());
        assertTrue(attachment.get(KEY).isEmpty());
        assertEquals(List.of("onRemoved"), lifecycle.events);

        lifecycle.events.clear();
        apply(attachment, lifecycle, OptionalInt.empty(), 3, StatusEffectOperation.SET);
        lifecycle.events.clear();
        StatusEffectReduction pendingRemoval = apply(
                attachment, lifecycle, OptionalInt.of(1), 0, StatusEffectOperation.REMOVE);
        assertEquals(StatusEffectChangeKind.REMOVED, pendingRemoval.kind());
        assertTrue(attachment.get(KEY).isEmpty());
        assertEquals(List.of("onRemoved"), lifecycle.events);
    }

    @Test
    void unchangedActiveEnsuresWithoutSynchronizationAndExpiryIsExact() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        StatusEffectAttachment attachment = new StatusEffectAttachment();
        apply(attachment, lifecycle, OptionalInt.of(2), 0);
        lifecycle.events.clear();

        StatusEffectReduction unchanged = apply(attachment, lifecycle, OptionalInt.of(2), 0);
        assertEquals(StatusEffectChangeKind.UNCHANGED, unchanged.kind());
        assertEquals(List.of("ensureApplied"), lifecycle.events);

        lifecycle.events.clear();
        StatusEffectTickReport first = tick(attachment, lifecycle);
        assertEquals(StatusEffectTransition.NONE, first.changes().get(0).transition());
        assertFalse(first.synchronizationRequested());
        assertEquals(List.of("ensureApplied"), lifecycle.events);

        lifecycle.events.clear();
        StatusEffectTickReport second = tick(attachment, lifecycle);
        assertEquals(StatusEffectTransition.EXPIRED, second.changes().get(0).transition());
        assertTrue(second.synchronizationRequested());
        assertEquals(List.of("onRemoved"), lifecycle.events);
    }

    @Test
    void payloadOnlyChangesRunChangedLifecycle() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        StatusEffectAttachment attachment = new StatusEffectAttachment();
        apply(attachment, lifecycle, OptionalInt.of(5), 0);
        lifecycle.events.clear();

        StatusEffectPayload.Jitter payload = new StatusEffectPayload.Jitter(8f, 3f);
        StatusEffectReduction reduction = StatusEffectSystem.apply(
                attachment, catalog(), lifecycle, KEY, StatusEffectOperation.SET,
                OptionalInt.of(5), 0, payload);

        assertEquals(StatusEffectChangeKind.CHANGED, reduction.kind());
        assertEquals(payload, attachment.get(KEY).orElseThrow().payload());
        assertEquals(List.of("onChanged", "ensureApplied"), lifecycle.events);
    }

    @Test
    void pendingTicksAreQuietAndActivationRunsAppliedThenEnsure() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        StatusEffectAttachment attachment = new StatusEffectAttachment(
                Map.of(KEY, StatusEffectInstance.pendingFinite(1, 3)));

        StatusEffectTickReport report = tick(attachment, lifecycle);

        assertEquals(StatusEffectTransition.ACTIVATED, report.changes().get(0).transition());
        assertEquals(List.of("onApplied", "ensureApplied"), lifecycle.events);
    }

    @Test
    void undefinedPrototypeIsRemovedWithoutABehaviorHook() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        StatusEffectAttachment attachment = new StatusEffectAttachment(
                Map.of(KEY, StatusEffectInstance.activePermanent()));

        StatusEffectTickReport report = StatusEffectSystem.advanceOneTick(
                attachment, new PrototypeCatalog<>(Map.of()), lifecycle);

        assertEquals(StatusEffectTransition.INVALIDATED, report.changes().get(0).transition());
        assertEquals(StatusEffectTransition.INVALIDATED, StatusEffectTransition.UNDEFINED);
        assertTrue(attachment.get(KEY).isEmpty());
        assertTrue(report.synchronizationRequested());
        assertEquals(List.of("onInvalidated"), lifecycle.events);
    }

    @Test
    void clearAllRemovesActivePendingAndPermanentStatesInCanonicalOrder() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        StatusEffectAttachment attachment = new StatusEffectAttachment(Map.of(
                PERMANENT_KEY, StatusEffectInstance.activePermanent(),
                OTHER_KEY, StatusEffectInstance.pendingFinite(3, 2),
                KEY, StatusEffectInstance.activeFinite(4)));
        PrototypeCatalog<StatusEffectData> catalog = new PrototypeCatalog<>(Map.of(
                ID, DEFINITION, OTHER_ID, DEFINITION, PERMANENT_ID, DEFINITION));

        StatusEffectClearReport report = StatusEffectSystem.clearAll(attachment, catalog, lifecycle);

        assertTrue(attachment.isEmpty());
        assertTrue(report.synchronizationRequested());
        assertEquals(List.of(KEY, OTHER_KEY, PERMANENT_KEY),
                report.changes().stream().map(StatusEffectClearChange::key).toList());
        assertEquals(List.of(StatusEffectInstance.activeFinite(4),
                        StatusEffectInstance.pendingFinite(3, 2),
                        StatusEffectInstance.activePermanent()),
                report.changes().stream().map(StatusEffectClearChange::before).toList());
        assertEquals(List.of("onRemoved", "onRemoved", "onRemoved"), lifecycle.events);
        assertEquals(List.of(KEY, OTHER_KEY, PERMANENT_KEY), lifecycle.removedKeys);
    }

    @Test
    void clearAllInvalidatesDanglingDefinitionsAndEmptyClearIsAQuietNoOp() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        StatusEffectAttachment attachment = new StatusEffectAttachment(Map.of(
                MISSING_KEY, StatusEffectInstance.activePermanent(),
                KEY, StatusEffectInstance.pendingPermanent(2)));
        PrototypeCatalog<StatusEffectData> catalog = new PrototypeCatalog<>(Map.of(ID, DEFINITION));

        StatusEffectClearReport report = StatusEffectSystem.clearAll(attachment, catalog, lifecycle);

        assertTrue(attachment.isEmpty());
        assertEquals(List.of("onRemoved", "onInvalidated"), lifecycle.events);
        assertEquals(List.of(KEY), lifecycle.removedKeys);
        assertEquals(List.of(MISSING_KEY), lifecycle.invalidatedKeys);
        assertFalse(report.changes().stream()
                .filter(change -> change.key().equals(MISSING_KEY))
                .findFirst().orElseThrow().definitionPresent());

        lifecycle.events.clear();
        lifecycle.removedKeys.clear();
        lifecycle.invalidatedKeys.clear();
        StatusEffectClearReport empty = StatusEffectSystem.clearAll(
                new StatusEffectAttachment(), catalog, lifecycle);
        assertTrue(empty.changes().isEmpty());
        assertFalse(empty.synchronizationRequested());
        assertTrue(lifecycle.events.isEmpty());
    }

    @Test
    void hookFailuresAreIndependentAndDoNotUndoMutationOrStopLaterKeys() {
        RecordingLifecycle lifecycle = new RecordingLifecycle(true);
        lifecycle.throwFromHooks = true;
        StatusEffectAttachment attachment = new StatusEffectAttachment(new LinkedHashMap<>(Map.of(
                KEY, StatusEffectInstance.activePermanent(),
                OTHER_KEY, StatusEffectInstance.activePermanent())));
        PrototypeCatalog<StatusEffectData> catalog = new PrototypeCatalog<>(Map.of(ID, DEFINITION, OTHER_ID, DEFINITION));

        StatusEffectTickReport report;
        try {
            report = StatusEffectSystem.advanceOneTick(attachment, catalog, lifecycle);
        } catch (RuntimeException exception) {
            throw new AssertionError("lifecycle failure escaped", exception);
        }

        assertEquals(2, report.changes().size());
        assertTrue(lifecycle.events.stream().anyMatch("ensureApplied"::equals));
        assertTrue(attachment.contains(KEY));
        assertTrue(attachment.contains(OTHER_KEY));
    }

    @Test
    void lifecycleIsWorldIndependentAndDefaultBehaviorShapeIsDeclarationOrdered() {
        assertEquals(List.of(StatusEffectBehavior.MARKER, StatusEffectBehavior.CLIENT_JITTER), DEFINITION.behaviors());
        assertTrue(StatusEffectLifecycle.class.isInterface());
        assertTrue(StatusEffectLifecycle.class.isAssignableFrom(DefaultStatusEffectLifecycle.class));
        assertThrows(NullPointerException.class,
                () -> new DefaultStatusEffectLifecycle((net.minecraft.world.entity.LivingEntity) null, null));
    }

    private static StatusEffectReduction apply(StatusEffectAttachment attachment,
                                               RecordingLifecycle lifecycle,
                                               OptionalInt duration,
                                               int delay) {
        return apply(attachment, lifecycle, duration, delay, StatusEffectOperation.SET);
    }

    private static StatusEffectReduction apply(StatusEffectAttachment attachment,
                                               RecordingLifecycle lifecycle,
                                               OptionalInt duration,
                                               int delay,
                                               StatusEffectOperation operation) {
        return StatusEffectSystem.apply(attachment, catalog(), lifecycle, KEY, operation, duration, delay);
    }

    private static StatusEffectTickReport tick(StatusEffectAttachment attachment, RecordingLifecycle lifecycle) {
        return StatusEffectSystem.advanceOneTick(attachment, catalog(), lifecycle);
    }

    private static PrototypeCatalog<StatusEffectData> catalog() {
        return new PrototypeCatalog<>(Map.of(ID, DEFINITION));
    }

    private static final class RecordingLifecycle implements StatusEffectLifecycle {
        private final boolean allowed;
        private final List<String> events = new ArrayList<>();
        private final List<net.minecraft.resources.ResourceKey<StatusEffectData>> removedKeys = new ArrayList<>();
        private final List<net.minecraft.resources.ResourceKey<StatusEffectData>> invalidatedKeys = new ArrayList<>();
        private boolean throwFromHooks;

        private RecordingLifecycle(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean canApply(net.minecraft.resources.ResourceKey<StatusEffectData> key,
                                StatusEffectData definition, StatusEffectInstance instance) {
            events.add("canApply");
            if (throwFromHooks) throw new RuntimeException("canApply");
            return allowed;
        }

        @Override
        public void onApplied(net.minecraft.resources.ResourceKey<StatusEffectData> key,
                              StatusEffectData definition, StatusEffectInstance instance) {
            event("onApplied", key);
        }

        @Override
        public void onChanged(net.minecraft.resources.ResourceKey<StatusEffectData> key,
                              StatusEffectData definition, StatusEffectInstance instance) {
            event("onChanged", key);
        }

        @Override
        public void ensureApplied(net.minecraft.resources.ResourceKey<StatusEffectData> key,
                                  StatusEffectData definition, StatusEffectInstance instance) {
            event("ensureApplied", key);
        }

        @Override
        public void onRemoved(net.minecraft.resources.ResourceKey<StatusEffectData> key,
                               StatusEffectData definition, StatusEffectInstance instance) {
            event("onRemoved", key);
            removedKeys.add(key);
        }

        @Override
        public void onInvalidated(net.minecraft.resources.ResourceKey<StatusEffectData> key,
                                  StatusEffectInstance instance) {
            event("onInvalidated", key);
            invalidatedKeys.add(key);
        }

        private void event(String name, net.minecraft.resources.ResourceKey<StatusEffectData> key) {
            events.add(name);
            if (throwFromHooks) throw new RuntimeException(name);
        }
    }
}
