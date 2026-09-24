package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Server-authoritative operations over the status attachment. */
public class StatusEffectSystem {
    public StatusEffectSystem() {
    }

    /** The bridge remains public for existing handlers and client queries. */
    public static com.juicyslew.moonstation14.util.SystemLink<StatusEffectAttachment, StatusEffectComponent> bridge =
            MS14Bridges.STATUS_EFFECT;

    /**
     * Applies one typed operation. Unchanged reductions intentionally do not
     * call the provider, avoiding redundant attachment synchronization.
     */
    public static StatusEffectReduction apply(
            TraitHandler<IStatusEffectTrait> source,
            Level level,
            ResourceKey<StatusEffectData> statusEffect,
            StatusEffectOperation operation,
            OptionalInt durationTicks,
            int delayTicks) {
        return apply(source, level, statusEffect, operation, durationTicks, delayTicks,
                StatusEffectPayload.none());
    }

    /** Server-authoritative application overload carrying typed payload state. */
    public static StatusEffectReduction apply(
            TraitHandler<IStatusEffectTrait> source,
            Level level,
            ResourceKey<StatusEffectData> statusEffect,
            StatusEffectOperation operation,
            OptionalInt durationTicks,
            int delayTicks,
            StatusEffectPayload payload) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(statusEffect, "statusEffect");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(durationTicks, "durationTicks");
        Objects.requireNonNull(payload, "payload");

        if (level.isClientSide()) {
            // This API is server-authoritative: clients may query, but never mutate.
            StatusEffectAttachment attachment = existingOrDetached(source.holder());
            return StatusEffectReducer.reduce(operation, attachment.get(statusEffect), durationTicks, delayTicks, payload);
        }

        if (!(source.holder() instanceof LivingEntity livingEntity)
                || !(level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("status effects require a living entity on a server level");
        }
        StatusEffectAttachment attachment = existingOrDetached(source.holder());
        StatusEffectReduction reduction = apply(
                attachment,
                PrototypeRuntime.serverStatusEffects(),
                new DefaultStatusEffectLifecycle(livingEntity, serverLevel),
                statusEffect,
                operation,
                durationTicks,
                delayTicks,
                payload);
        if (reduction.kind() == StatusEffectChangeKind.CREATED
                || reduction.kind() == StatusEffectChangeKind.CHANGED
                || reduction.kind() == StatusEffectChangeKind.REMOVED) {
            MS14Provider.update(source.holder(), bridge, attachment);
        }
        return reduction;
    }

    private static StatusEffectAttachment existingOrDetached(Object holder) {
        if (holder instanceof IAttachmentHolder attachmentHolder
                && attachmentHolder.hasData(ModDataAttachments.STATUS_EFFECT.get())) {
            return MS14Provider.get(attachmentHolder, bridge);
        }
        return new StatusEffectAttachment();
    }

    /**
     * Reads status presence without obtaining a default attachment.  This is
     * intentionally query-only for compatibility handlers whose legacy
     * operation does not create an absent status.
     */
    public static boolean hasStatus(TraitHandler<IStatusEffectTrait> source,
                                    Level level,
                                    ResourceKey<StatusEffectData> statusEffect) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(statusEffect, "status effect key");
        if (!(source.holder() instanceof IAttachmentHolder holder)
                || !holder.hasData(ModDataAttachments.STATUS_EFFECT.get())) {
            return false;
        }
        return MS14Provider.get(holder, bridge).contains(statusEffect);
    }

    /** Advances one living holder on the server and synchronizes only lifecycle transitions. */
    public static StatusEffectTickReport advanceOneTick(
            TraitHandler<IStatusEffectTrait> source,
            Level level) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(level, "level");
        if (level.isClientSide()) {
            return new StatusEffectTickReport(List.of(), false);
        }

        if (!(source.holder() instanceof LivingEntity livingEntity)
                || !(level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("status effects require a living entity on a server level");
        }
        if (!(source.holder() instanceof IAttachmentHolder holder)
                || !holder.hasData(ModDataAttachments.STATUS_EFFECT.get())) {
            return new StatusEffectTickReport(List.of(), false);
        }
        StatusEffectAttachment attachment = MS14Provider.get(source.holder(), bridge);
        StatusEffectTickReport report = advanceOneTick(
                attachment,
                PrototypeRuntime.serverStatusEffects(),
                new DefaultStatusEffectLifecycle(livingEntity, serverLevel));
        if (report.synchronizationRequested()) {
            MS14Provider.update(source.holder(), bridge, attachment);
        }
        return report;
    }

    /** Pure storage half of advancement, useful for deterministic unit tests. */
    public static StatusEffectTickReport advanceOneTick(StatusEffectAttachment attachment) {
        Objects.requireNonNull(attachment, "attachment");
        List<StatusEffectTickChange> changes = attachment.advanceOneTick();
        boolean synchronizationRequested = changes.stream()
                .anyMatch(change -> change.transition() != StatusEffectTransition.NONE);
        return new StatusEffectTickReport(changes, synchronizationRequested);
    }

    /**
     * Clears all statuses in canonical key order and dispatches exactly one
     * removal callback for each entry.  Missing definitions are invalidated
     * instead, since there is no definition whose behavior can be removed.
     *
     * <p>This overload is deliberately world-independent.  It is the pure
     * lifecycle boundary used by tests and by the server-side policy below;
     * the attachment is mutated through its removal API rather than through
     * its backing map.</p>
     */
    public static StatusEffectClearReport clearAll(
            StatusEffectAttachment attachment,
            PrototypeCatalog<StatusEffectData> catalog,
            StatusEffectLifecycle lifecycle) {
        Objects.requireNonNull(attachment, "status effect attachment");
        Objects.requireNonNull(catalog, "status effect catalog");
        Objects.requireNonNull(lifecycle, "status effect lifecycle");

        List<ResourceKey<StatusEffectData>> keys = new ArrayList<>(attachment.snapshot().keySet());
        keys.sort(java.util.Comparator.comparing(key -> key.location().toString()));
        List<StatusEffectClearChange> changes = new ArrayList<>(keys.size());
        for (ResourceKey<StatusEffectData> key : keys) {
            Optional<StatusEffectInstance> removed = attachment.remove(key);
            if (removed.isEmpty()) {
                continue;
            }
            StatusEffectInstance before = removed.orElseThrow();
            StatusEffectData definition = catalog.get(key.location());
            if (definition == null) {
                invoke(lifecycle, "onInvalidated",
                        () -> lifecycle.onInvalidated(key, before), key);
                changes.add(new StatusEffectClearChange(key, before, false));
            } else {
                invoke(lifecycle, "onRemoved",
                        () -> lifecycle.onRemoved(key, definition, before), key);
                changes.add(new StatusEffectClearChange(key, before, true));
            }
        }
        return new StatusEffectClearReport(changes, !changes.isEmpty());
    }

    /**
     * Server-side status death policy for a living status holder.  All status
     * callbacks run before the attachment is removed, then one attachment
     * synchronization and one derived-activity reconciliation are performed.
     * An absent holder is a true no-op and does not materialize any data.
     */
    public static StatusEffectClearReport clearAll(
            TraitHandler<IStatusEffectTrait> source,
            Level level) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(level, "level");
        if (level.isClientSide()) {
            return new StatusEffectClearReport(List.of(), false);
        }
        if (!(source.holder() instanceof LivingEntity livingEntity)
                || !(level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("status effects require a living entity on a server level");
        }
        if (!(source.holder() instanceof IAttachmentHolder holder)
                || !holder.hasData(ModDataAttachments.STATUS_EFFECT.get())) {
            EntityActivitySystem.reconcile(livingEntity);
            return new StatusEffectClearReport(List.of(), false);
        }

        StatusEffectAttachment attachment = MS14Provider.get(source.holder(), bridge);
        StatusEffectClearReport report = clearAll(
                attachment,
                PrototypeRuntime.serverStatusEffects(),
                new LivingEntityStatusEffectLifecycle(livingEntity, serverLevel));
        // Removing the attachment is the one server-to-client synchronization
        // for this all-at-once operation and avoids retaining an empty status
        // attachment on a clean respawn.
        holder.removeData(ModDataAttachments.STATUS_EFFECT.get());
        EntityActivitySystem.reconcile(livingEntity);
        return report;
    }

    /**
     * Applies one operation against an injected attachment, catalog, and
     * lifecycle.  This overload has no world or provider dependency and is the
     * boundary used by deterministic tests.
     */
    public static StatusEffectReduction apply(
            StatusEffectAttachment attachment,
            PrototypeCatalog<StatusEffectData> catalog,
            StatusEffectLifecycle lifecycle,
            ResourceKey<StatusEffectData> statusEffect,
            StatusEffectOperation operation,
            OptionalInt durationTicks,
            int delayTicks) {
        return apply(attachment, catalog, lifecycle, statusEffect, operation, durationTicks, delayTicks,
                StatusEffectPayload.none());
    }

    /** Pure application overload carrying typed payload state. */
    public static StatusEffectReduction apply(
            StatusEffectAttachment attachment,
            PrototypeCatalog<StatusEffectData> catalog,
            StatusEffectLifecycle lifecycle,
            ResourceKey<StatusEffectData> statusEffect,
            StatusEffectOperation operation,
            OptionalInt durationTicks,
            int delayTicks,
            StatusEffectPayload payload) {
        Objects.requireNonNull(attachment, "attachment");
        Objects.requireNonNull(catalog, "status effect catalog");
        Objects.requireNonNull(lifecycle, "status effect lifecycle");
        ResourceKey<StatusEffectData> checkedKey = Objects.requireNonNull(statusEffect, "status effect key");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(durationTicks, "durationTicks");
        Objects.requireNonNull(payload, "payload");

        // Resolve before reduction or mutation, including for REMOVE.  A
        // dangling reference is a caller/data error, not a lifecycle event.
        StatusEffectData definition = requireDefinition(catalog, checkedKey);
        Optional<StatusEffectInstance> current = attachment.get(checkedKey);
        StatusEffectReduction planned = StatusEffectReducer.reduce(
                operation, current, durationTicks, delayTicks, payload);

        if (current.isEmpty() && planned.next().isPresent()
                && !canApply(lifecycle, checkedKey, definition, planned.next().orElseThrow())) {
            return new StatusEffectReduction(StatusEffectChangeKind.UNCHANGED, Optional.empty());
        }

        // The second reduction is intentional: StatusEffectAttachment owns all
        // attachment mutation and keeps its central invariants in one place.
        StatusEffectReduction reduction = attachment.apply(checkedKey, operation, durationTicks, delayTicks, payload);
        invokeAfterMutation(reduction, current, checkedKey, definition, lifecycle);
        return reduction;
    }

    /** Convenience pure overload with the catalog before the lifecycle. */
    public static StatusEffectReduction apply(
            StatusEffectAttachment attachment,
            PrototypeCatalog<StatusEffectData> catalog,
            ResourceKey<StatusEffectData> statusEffect,
            StatusEffectOperation operation,
            OptionalInt durationTicks,
            int delayTicks,
            StatusEffectLifecycle lifecycle) {
        return apply(attachment, catalog, lifecycle, statusEffect, operation, durationTicks, delayTicks,
                StatusEffectPayload.none());
    }

    public static StatusEffectReduction apply(
            StatusEffectAttachment attachment,
            PrototypeCatalog<StatusEffectData> catalog,
            ResourceKey<StatusEffectData> statusEffect,
            StatusEffectOperation operation,
            OptionalInt durationTicks,
            int delayTicks,
            StatusEffectLifecycle lifecycle,
            StatusEffectPayload payload) {
        return apply(attachment, catalog, lifecycle, statusEffect, operation, durationTicks, delayTicks, payload);
    }

    /**
     * Advances one attachment with lifecycle callbacks while preserving the
     * attachment's canonical key ordering and exact tick arithmetic.
     */
    public static StatusEffectTickReport advanceOneTick(
            StatusEffectAttachment attachment,
            PrototypeCatalog<StatusEffectData> catalog,
            StatusEffectLifecycle lifecycle) {
        Objects.requireNonNull(attachment, "attachment");
        Objects.requireNonNull(catalog, "status effect catalog");
        Objects.requireNonNull(lifecycle, "status effect lifecycle");

        List<StatusEffectTickChange> storageChanges = attachment.advanceOneTick();
        List<StatusEffectTickChange> changes = new ArrayList<>(storageChanges.size());
        for (StatusEffectTickChange change : storageChanges) {
            StatusEffectData definition = catalog.get(change.key().location());
            if (definition == null) {
                // advanceOneTick may already have removed an expiring entry;
                // remove also covers a still-counting dangling entry.
                attachment.remove(change.key());
                changes.add(new StatusEffectTickChange(
                        change.key(), change.before(), Optional.empty(), StatusEffectTransition.INVALIDATED));
                invoke(lifecycle, "onInvalidated",
                        () -> lifecycle.onInvalidated(change.key(), change.before()), change.key());
                continue;
            }

            changes.add(change);
            Optional<StatusEffectInstance> after = change.after();
            switch (change.transition()) {
                case ACTIVATED -> {
                    StatusEffectInstance activated = after.orElseThrow();
                    invoke(lifecycle, "onApplied", () -> lifecycle.onApplied(change.key(), definition, activated),
                            change.key());
                    invoke(lifecycle, "ensureApplied",
                            () -> lifecycle.ensureApplied(change.key(), definition, activated), change.key());
                }
                case NONE -> {
                    if (change.before().isActive()) {
                        StatusEffectInstance active = after.orElseThrow();
                        invoke(lifecycle, "ensureApplied",
                                () -> lifecycle.ensureApplied(change.key(), definition, active), change.key());
                    }
                }
                case EXPIRED -> invoke(lifecycle, "onRemoved",
                        () -> lifecycle.onRemoved(change.key(), definition, change.before()), change.key());
                case INVALIDATED -> {
                    // Undefined prototypes have no behavior to remove.
                }
            }
        }
        boolean synchronizationRequested = changes.stream()
                .anyMatch(change -> change.transition() != StatusEffectTransition.NONE);
        return new StatusEffectTickReport(changes, synchronizationRequested);
    }

    /** Convenience entry point for a living entity implementing the status trait. */
    public static StatusEffectTickReport advanceOneTick(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!(entity instanceof IStatusEffectTrait trait)) {
            return new StatusEffectTickReport(List.of(), false);
        }
        return advanceOneTick(trait.toHandleSelf(), entity.level());
    }

    private static StatusEffectData requireDefinition(
            PrototypeCatalog<StatusEffectData> catalog,
            ResourceKey<StatusEffectData> key) {
        StatusEffectData definition = catalog.get(key.location());
        if (definition == null) {
            throw new IllegalArgumentException("Missing status effect '" + key.location()
                    + "' in resolved prototype catalog");
        }
        return definition;
    }

    private static boolean canApply(StatusEffectLifecycle lifecycle,
                                    ResourceKey<StatusEffectData> key,
                                    StatusEffectData definition,
                                    StatusEffectInstance instance) {
        try {
            return lifecycle.canApply(key, definition, instance);
        } catch (RuntimeException exception) {
            logHookFailure("canApply", key, exception);
            return false;
        }
    }

    private static void invokeAfterMutation(StatusEffectReduction reduction,
                                            Optional<StatusEffectInstance> previous,
                                            ResourceKey<StatusEffectData> key,
                                            StatusEffectData definition,
                                            StatusEffectLifecycle lifecycle) {
        switch (reduction.kind()) {
            case CREATED -> {
                StatusEffectInstance instance = reduction.next().orElseThrow();
                if (instance.isActive()) {
                    invoke(lifecycle, "onApplied", () -> lifecycle.onApplied(key, definition, instance), key);
                    invoke(lifecycle, "ensureApplied",
                            () -> lifecycle.ensureApplied(key, definition, instance), key);
                }
            }
            case CHANGED -> {
                StatusEffectInstance instance = reduction.next().orElseThrow();
                invoke(lifecycle, "onChanged", () -> lifecycle.onChanged(key, definition, instance), key);
                if (instance.isActive()) {
                    if (previous.orElseThrow().isPending()) {
                        invoke(lifecycle, "onApplied", () -> lifecycle.onApplied(key, definition, instance), key);
                    }
                    invoke(lifecycle, "ensureApplied",
                            () -> lifecycle.ensureApplied(key, definition, instance), key);
                }
            }
            case REMOVED -> invoke(lifecycle, "onRemoved",
                    () -> lifecycle.onRemoved(key, definition, previous.orElseThrow()), key);
            case UNCHANGED -> {
                if (reduction.next().isPresent() && reduction.next().orElseThrow().isActive()) {
                    StatusEffectInstance instance = reduction.next().orElseThrow();
                    invoke(lifecycle, "ensureApplied",
                            () -> lifecycle.ensureApplied(key, definition, instance), key);
                }
            }
        }
    }

    private static void invoke(StatusEffectLifecycle lifecycle, String hook,
                               Runnable callback, ResourceKey<StatusEffectData> key) {
        try {
            callback.run();
        } catch (RuntimeException exception) {
            logHookFailure(hook, key, exception);
        }
    }

    private static void logHookFailure(String hook, ResourceKey<StatusEffectData> key,
                                       RuntimeException exception) {
        MoonStation14.LOGGER.error("Status effect lifecycle hook {} failed for {}", hook, key.location(), exception);
    }

    /** Transitional wrapper: adds a positive finite number of seconds. */
    public static void addTime(TraitHandler<IStatusEffectTrait> source, Level level,
                               ResourceKey<StatusEffectData> statusEffect, float amount) {
        int ticks = secondsToTicks(amount);
        apply(source, level, statusEffect, StatusEffectOperation.ADD, OptionalInt.of(ticks), 0);
    }

    public static void addTime(TraitHandler<IStatusEffectTrait> source, Level level,
                               ResourceKey<StatusEffectData> statusEffect, float amount,
                               StatusEffectPayload payload) {
        int ticks = secondsToTicks(amount);
        apply(source, level, statusEffect, StatusEffectOperation.ADD, OptionalInt.of(ticks), 0, payload);
    }

    /** Transitional wrapper: removes a positive finite number of seconds. */
    public static void removeTime(TraitHandler<IStatusEffectTrait> source, Level level,
                                  ResourceKey<StatusEffectData> statusEffect, float amount) {
        int ticks = secondsToTicks(amount);
        apply(source, level, statusEffect, StatusEffectOperation.REMOVE, OptionalInt.of(ticks), 0);
    }

    public static void removeTime(TraitHandler<IStatusEffectTrait> source, Level level,
                                  ResourceKey<StatusEffectData> statusEffect, float amount,
                                  StatusEffectPayload payload) {
        int ticks = secondsToTicks(amount);
        apply(source, level, statusEffect, StatusEffectOperation.REMOVE, OptionalInt.of(ticks), 0, payload);
    }

    /** Transitional wrapper: sets a positive finite number of seconds. */
    public static void setTime(TraitHandler<IStatusEffectTrait> source, Level level,
                               ResourceKey<StatusEffectData> statusEffect, float amount) {
        int ticks = secondsToTicks(amount);
        apply(source, level, statusEffect, StatusEffectOperation.SET, OptionalInt.of(ticks), 0);
    }

    public static void setTime(TraitHandler<IStatusEffectTrait> source, Level level,
                               ResourceKey<StatusEffectData> statusEffect, float amount,
                               StatusEffectPayload payload) {
        int ticks = secondsToTicks(amount);
        apply(source, level, statusEffect, StatusEffectOperation.SET, OptionalInt.of(ticks), 0, payload);
    }

    /** Pure conversion used by status-effect handlers and the transitional seconds API. */
    public static int secondsToTicks(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0f) {
            throw new IllegalArgumentException("seconds must be finite and positive");
        }
        // Multiply as a float first so common decimal boundaries such as 0.05
        // seconds retain their expected one-tick result after float rounding.
        float scaledTicks = seconds * 20f;
        if (!Float.isFinite(scaledTicks) || scaledTicks <= 0f) {
            throw new IllegalArgumentException("seconds duration exceeds integer tick range");
        }
        double ticks = Math.ceil((double) scaledTicks);
        if (ticks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("seconds duration exceeds integer tick range");
        }
        return (int) ticks;
    }

    /** Converts a finite, nonnegative delay in seconds to ticks. */
    public static int nonNegativeSecondsToTicks(float seconds) {
        if (!Float.isFinite(seconds) || seconds < 0f) {
            throw new IllegalArgumentException("seconds must be finite and nonnegative");
        }
        return seconds == 0f ? 0 : secondsToTicks(seconds);
    }
}
