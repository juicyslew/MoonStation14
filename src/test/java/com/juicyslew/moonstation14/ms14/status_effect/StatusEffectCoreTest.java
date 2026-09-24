package com.juicyslew.moonstation14.ms14.status_effect;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusEffectCoreTest {
    @Test
    void closedEnumsAndStrictOperationCodec() {
        assertArrayEquals(new StatusEffectState[]{StatusEffectState.PENDING, StatusEffectState.ACTIVE},
                StatusEffectState.values());
        assertArrayEquals(new StatusEffectOperation[]{
                        StatusEffectOperation.UPDATE,
                        StatusEffectOperation.ADD,
                        StatusEffectOperation.REMOVE,
                        StatusEffectOperation.SET
                },
                StatusEffectOperation.values());

        for (StatusEffectOperation operation : StatusEffectOperation.values()) {
            var json = StatusEffectOperation.CODEC.encodeStart(JsonOps.INSTANCE, operation).getOrThrow();
            assertEquals(operation.serializedName(), json.getAsString());
            assertEquals(operation, StatusEffectOperation.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        assertTrue(StatusEffectOperation.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"UPDATE\"")).error().isPresent());
        assertTrue(StatusEffectOperation.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"unknown\"")).error().isPresent());
    }

    @Test
    void instanceIsAnImmutablePayloadBearingRecordWithValidatedFactories() {
        assertTrue(StatusEffectInstance.class.isRecord());
        RecordComponent[] components = StatusEffectInstance.class.getRecordComponents();
        assertEquals(4, components.length);
        assertEquals("delayTicks", components[0].getName());
        assertEquals("remainingDurationTicks", components[1].getName());
        assertEquals("state", components[2].getName());
        assertEquals("payload", components[3].getName());
        assertEquals(OptionalInt.class, components[1].getType());

        StatusEffectInstance active = StatusEffectInstance.activeFinite(5);
        StatusEffectInstance pending = StatusEffectInstance.pendingPermanent(2);
        assertEquals(StatusEffectState.ACTIVE, active.state());
        assertEquals(StatusEffectState.PENDING, pending.state());
        assertTrue(active.isActive());
        assertTrue(pending.isPending());
        assertTrue(pending.isPermanent());
        assertEquals(OptionalInt.of(5), active.remainingDurationTicks());
        assertEquals(StatusEffectPayload.none(), active.payload());

        assertThrows(IllegalArgumentException.class,
                () -> StatusEffectInstance.finite(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> StatusEffectInstance.finite(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StatusEffectInstance.pendingFinite(0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StatusEffectInstance(1, OptionalInt.empty(), StatusEffectState.ACTIVE));
        assertThrows(IllegalArgumentException.class,
                () -> new StatusEffectInstance(0, OptionalInt.of(-1), StatusEffectState.ACTIVE));
    }

    @Test
    void everyOperationHandlesAnAbsentStatus() {
        assertReduction(StatusEffectOperation.UPDATE, Optional.empty(), OptionalInt.of(4), 0,
                StatusEffectChangeKind.CREATED, StatusEffectInstance.activeFinite(4));
        assertReduction(StatusEffectOperation.ADD, Optional.empty(), OptionalInt.empty(), 2,
                StatusEffectChangeKind.CREATED, StatusEffectInstance.pendingPermanent(2));
        assertReduction(StatusEffectOperation.REMOVE, Optional.empty(), OptionalInt.empty(), 0,
                StatusEffectChangeKind.UNCHANGED, null);
        assertReduction(StatusEffectOperation.SET, Optional.empty(), OptionalInt.of(7), 1,
                StatusEffectChangeKind.CREATED, StatusEffectInstance.pendingFinite(1, 7));
    }

    @Test
    void operationsCoverFiniteActiveAndResultKinds() {
        StatusEffectInstance current = StatusEffectInstance.activeFinite(5);
        assertReduction(StatusEffectOperation.UPDATE, Optional.of(current), OptionalInt.of(3), 0,
                StatusEffectChangeKind.UNCHANGED, current);
        assertReduction(StatusEffectOperation.UPDATE, Optional.of(current), OptionalInt.of(8), 4,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.activeFinite(8));
        assertReduction(StatusEffectOperation.ADD, Optional.of(current), OptionalInt.of(2), 4,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.activeFinite(7));
        assertReduction(StatusEffectOperation.REMOVE, Optional.of(current), OptionalInt.of(2), 4,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.activeFinite(3));
        assertReduction(StatusEffectOperation.REMOVE, Optional.of(current), OptionalInt.of(5), 4,
                StatusEffectChangeKind.REMOVED, null);
        assertReduction(StatusEffectOperation.SET, Optional.of(current), OptionalInt.of(2), 4,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.activeFinite(2));
    }

    @Test
    void operationsCoverPermanentActiveAndPendingStatuses() {
        StatusEffectInstance permanent = StatusEffectInstance.activePermanent();
        assertReduction(StatusEffectOperation.UPDATE, Optional.of(permanent), OptionalInt.of(2), 3,
                StatusEffectChangeKind.UNCHANGED, permanent);
        assertReduction(StatusEffectOperation.ADD, Optional.of(permanent), OptionalInt.of(2), 3,
                StatusEffectChangeKind.UNCHANGED, permanent);
        assertReduction(StatusEffectOperation.REMOVE, Optional.of(permanent), OptionalInt.of(2), 3,
                StatusEffectChangeKind.REMOVED, null);
        assertReduction(StatusEffectOperation.REMOVE, Optional.of(permanent), OptionalInt.empty(), 3,
                StatusEffectChangeKind.REMOVED, null);
        assertReduction(StatusEffectOperation.SET, Optional.of(permanent), OptionalInt.of(2), 3,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.activeFinite(2));

        StatusEffectInstance pending = StatusEffectInstance.pendingFinite(5, 4);
        assertReduction(StatusEffectOperation.UPDATE, Optional.of(pending), OptionalInt.of(7), 3,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.pendingFinite(3, 7));
        assertReduction(StatusEffectOperation.ADD, Optional.of(pending), OptionalInt.of(2), 8,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.pendingFinite(5, 6));
        assertReduction(StatusEffectOperation.REMOVE, Optional.of(pending), OptionalInt.of(1), 0,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.pendingFinite(5, 3));
        assertReduction(StatusEffectOperation.SET, Optional.of(pending), OptionalInt.empty(), 8,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.pendingPermanent(5));
        assertReduction(StatusEffectOperation.SET, Optional.of(pending), OptionalInt.of(2), 0,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.activeFinite(2));

        StatusEffectInstance pendingPermanent = StatusEffectInstance.pendingPermanent(5);
        assertReduction(StatusEffectOperation.UPDATE, Optional.of(pendingPermanent), OptionalInt.of(2), 3,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.pendingPermanent(3));
        assertReduction(StatusEffectOperation.ADD, Optional.of(pendingPermanent), OptionalInt.of(2), 3,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.pendingPermanent(3));
        assertReduction(StatusEffectOperation.REMOVE, Optional.of(pendingPermanent), OptionalInt.of(2), 3,
                StatusEffectChangeKind.REMOVED, null);
        assertReduction(StatusEffectOperation.REMOVE, Optional.of(pendingPermanent), OptionalInt.empty(), 3,
                StatusEffectChangeKind.REMOVED, null);
        assertReduction(StatusEffectOperation.SET, Optional.of(pendingPermanent), OptionalInt.of(2), 3,
                StatusEffectChangeKind.CHANGED, StatusEffectInstance.pendingFinite(3, 2));
    }

    @Test
    void pendingUsesEarliestDelayAndActiveNeverRepends() {
        StatusEffectInstance pending = StatusEffectInstance.pendingFinite(5, 4);
        assertEquals(StatusEffectInstance.pendingFinite(2, 4), reduce(
                StatusEffectOperation.UPDATE, pending, OptionalInt.of(4), 2).next().orElseThrow());
        assertEquals(StatusEffectInstance.pendingFinite(2, 6), reduce(
                StatusEffectOperation.ADD, pending, OptionalInt.of(2), 2).next().orElseThrow());
        assertEquals(StatusEffectInstance.pendingFinite(2, 9), reduce(
                StatusEffectOperation.SET, pending, OptionalInt.of(9), 2).next().orElseThrow());

        StatusEffectInstance active = StatusEffectInstance.activeFinite(4);
        assertEquals(StatusEffectInstance.activeFinite(4), reduce(
                StatusEffectOperation.UPDATE, active, OptionalInt.of(4), 9).next().orElseThrow());
        assertEquals(StatusEffectInstance.activeFinite(5), reduce(
                StatusEffectOperation.ADD, active, OptionalInt.of(1), 9).next().orElseThrow());
        assertEquals(StatusEffectInstance.activeFinite(2), reduce(
                StatusEffectOperation.SET, active, OptionalInt.of(2), 9).next().orElseThrow());
    }

    @Test
    void addUsesCheckedOverflowAndAllInputsAreValidatedBeforeReduction() {
        assertThrows(ArithmeticException.class, () -> reduce(
                StatusEffectOperation.ADD, StatusEffectInstance.activeFinite(Integer.MAX_VALUE),
                OptionalInt.of(1), 0));

        for (StatusEffectOperation operation : StatusEffectOperation.values()) {
            assertThrows(IllegalArgumentException.class, () -> reduce(
                    operation, null, OptionalInt.of(0), 0));
            assertThrows(IllegalArgumentException.class, () -> reduce(
                    operation, null, OptionalInt.of(-1), 0));
            assertThrows(IllegalArgumentException.class, () -> reduce(
                    operation, null, OptionalInt.of(1), -1));
        }
    }

    @Test
    void oneTickTransitionsHaveExactBoundaries() {
        StatusEffectTickResult waiting = StatusEffectInstance.pendingFinite(3, 2).tick();
        assertEquals(StatusEffectTransition.NONE, waiting.transition());
        assertEquals(StatusEffectInstance.pendingFinite(2, 2), waiting.next().orElseThrow());

        StatusEffectTickResult activation = StatusEffectInstance.pendingFinite(1, 2).tick();
        assertEquals(StatusEffectTransition.ACTIVATED, activation.transition());
        assertEquals(StatusEffectInstance.activeFinite(2), activation.next().orElseThrow());

        StatusEffectTickResult decrement = StatusEffectInstance.activeFinite(2).tick();
        assertEquals(StatusEffectTransition.NONE, decrement.transition());
        assertEquals(StatusEffectInstance.activeFinite(1), decrement.next().orElseThrow());

        StatusEffectTickResult expiry = StatusEffectInstance.activeFinite(1).tick();
        assertEquals(StatusEffectTransition.EXPIRED, expiry.transition());
        assertTrue(expiry.next().isEmpty());

        StatusEffectTickResult permanent = StatusEffectInstance.activePermanent().tick();
        assertEquals(StatusEffectTransition.NONE, permanent.transition());
        assertEquals(StatusEffectInstance.activePermanent(), permanent.next().orElseThrow());
        StatusEffectTickResult permanentActivation = StatusEffectInstance.pendingPermanent(1).tick();
        assertEquals(StatusEffectTransition.ACTIVATED, permanentActivation.transition());
        assertEquals(StatusEffectInstance.activePermanent(), permanentActivation.next().orElseThrow());
    }

    @Test
    void typedPayloadsAreValidatedAndReducerMergesJitterWithoutErasingIt() {
        StatusEffectPayload.Jitter existingPayload = new StatusEffectPayload.Jitter(10f, 4f);
        StatusEffectPayload.Jitter weakerPayload = new StatusEffectPayload.Jitter(3f, 2f);
        StatusEffectPayload.Jitter strongerPayload = new StatusEffectPayload.Jitter(12f, 5f);
        StatusEffectInstance existing = StatusEffectInstance.activeFinite(8, existingPayload);

        assertEquals(existingPayload, StatusEffectReducer.reduce(
                StatusEffectOperation.SET, Optional.of(existing), OptionalInt.of(2), 0).next().orElseThrow().payload());
        assertEquals(existingPayload, StatusEffectReducer.reduce(
                StatusEffectOperation.REMOVE, Optional.of(existing), OptionalInt.of(2), 0).next().orElseThrow().payload());
        assertEquals(existingPayload, StatusEffectReducer.reduce(
                StatusEffectOperation.UPDATE, Optional.of(existing), OptionalInt.of(9), 0, weakerPayload)
                .next().orElseThrow().payload());
        assertEquals(strongerPayload, StatusEffectReducer.reduce(
                StatusEffectOperation.ADD, Optional.of(existing), OptionalInt.of(2), 0, strongerPayload)
                .next().orElseThrow().payload());

        assertEquals(existingPayload, existing.tick().next().orElseThrow().payload());
        assertThrows(IllegalArgumentException.class, () -> new StatusEffectPayload.Jitter(0f, 2f));
        assertThrows(IllegalArgumentException.class, () -> new StatusEffectPayload.Jitter(2f, 11f));
        assertThrows(IllegalArgumentException.class, () -> new StatusEffectPayload.Jitter(Float.NaN, 2f));
    }

    @Test
    void jitterPayloadMergeTakesPointwiseMaximaAcrossCoordinates() {
        StatusEffectPayload.Jitter merged = (StatusEffectPayload.Jitter) StatusEffectReducer.reduce(
                StatusEffectOperation.ADD,
                Optional.of(StatusEffectInstance.activeFinite(10,
                        new StatusEffectPayload.Jitter(10f, 4f))),
                OptionalInt.of(2), 0,
                new StatusEffectPayload.Jitter(12f, 2f))
                .next().orElseThrow().payload();
        assertEquals(new StatusEffectPayload.Jitter(12f, 4f), merged);
    }

    @Test
    void movementPayloadIsLatestForUpdateAndAddButDurationOnlyOperationsPreserveIt() {
        StatusEffectPayload.MovementSpeedModifier first = new StatusEffectPayload.MovementSpeedModifier(.65f);
        StatusEffectPayload.MovementSpeedModifier second = new StatusEffectPayload.MovementSpeedModifier(1.25f);
        StatusEffectInstance current = StatusEffectInstance.activeFinite(10, first);

        assertEquals(second, StatusEffectReducer.reduce(StatusEffectOperation.UPDATE, Optional.of(current),
                OptionalInt.of(4), 0, second).next().orElseThrow().payload());
        assertEquals(second, StatusEffectReducer.reduce(StatusEffectOperation.ADD, Optional.of(current),
                OptionalInt.of(4), 0, second).next().orElseThrow().payload());
        assertEquals(first, StatusEffectReducer.reduce(StatusEffectOperation.REMOVE, Optional.of(current),
                OptionalInt.of(2), 0, second).next().orElseThrow().payload());
        assertEquals(first, StatusEffectReducer.reduce(StatusEffectOperation.SET, Optional.of(current),
                OptionalInt.of(2), 0, second).next().orElseThrow().payload());
        assertEquals(StatusEffectPayload.none(), StatusEffectReducer.reduce(StatusEffectOperation.SET, Optional.empty(),
                OptionalInt.of(2), 0, second).next().orElseThrow().payload());
    }

    @Test
    void reductionKindAlwaysMatchesPresenceAndValueChange() {
        assertKindConsistency(Optional.empty(), reduce(StatusEffectOperation.REMOVE,
                null, OptionalInt.empty(), 0));
        for (StatusEffectOperation operation : StatusEffectOperation.values()) {
            Optional<StatusEffectInstance> current = Optional.of(StatusEffectInstance.pendingFinite(2, 5));
            assertKindConsistency(current, reduce(operation, current.orElseThrow(), OptionalInt.of(3), 0));
        }
    }

    private static StatusEffectReduction reduce(
            StatusEffectOperation operation,
            StatusEffectInstance current,
            OptionalInt duration,
            int delay
    ) {
        return StatusEffectReducer.reduce(operation, Optional.ofNullable(current), duration, delay);
    }

    private static void assertReduction(
            StatusEffectOperation operation,
            Optional<StatusEffectInstance> current,
            OptionalInt duration,
            int delay,
            StatusEffectChangeKind expectedKind,
            StatusEffectInstance expectedNext
    ) {
        StatusEffectReduction result = StatusEffectReducer.reduce(operation, current, duration, delay);
        assertEquals(expectedKind, result.kind());
        if (expectedNext == null) {
            assertTrue(result.next().isEmpty());
        } else {
            assertEquals(Optional.of(expectedNext), result.next());
        }
        assertNotNull(result.next());
    }

    private static void assertKindConsistency(Optional<StatusEffectInstance> current,
                                              StatusEffectReduction result) {
        Optional<StatusEffectInstance> next = result.next();
        StatusEffectChangeKind expected;
        if (current.isEmpty() && next.isEmpty()) {
            expected = StatusEffectChangeKind.UNCHANGED;
        } else if (current.isEmpty()) {
            expected = StatusEffectChangeKind.CREATED;
        } else if (next.isEmpty()) {
            expected = StatusEffectChangeKind.REMOVED;
        } else if (current.equals(next)) {
            expected = StatusEffectChangeKind.UNCHANGED;
        } else {
            expected = StatusEffectChangeKind.CHANGED;
        }
        assertEquals(expected, result.kind());
    }
}
