package com.juicyslew.moonstation14.ms14.blood;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamPolicy;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;

import java.util.Optional;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;

/** Server-authoritative bloodstream physiology and prototype-scheduled updates. */
public final class BloodSystem {
    private static final long POLICY_RETRY_INTERVAL_TICKS = 20L;
    private static final Map<LivingEntity, PolicyCache> POLICY_CACHE = new WeakHashMap<>();
    private static final AtomicBoolean WARNED_LEGACY_PIG = new AtomicBoolean();
    private static final ResourceLocation PIG_ID = ResourceLocation.parse("moonstation14:pig");
    private static final net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>
            OLD_PIG_BLOOD = ModReagents.createKey("sulfurblood");

    private record PolicyCache(long checkedAt, PrototypeCatalog<CharacterData> catalog,
                               ResourceLocation host, ResourceLocation identity,
                                 Optional<BloodstreamPolicy> policy) { }

    private BloodSystem() { }

    /** Result for an effect mutation, keeping unsupported eligibility separate from bad arithmetic. */
    public enum EffectAdjustmentResult { APPLIED, SKIPPED_UNSUPPORTED, FAILED }

    /** Resolve a policy only through the server actor authority (host-owned or explicit harness). */
    public static Optional<BloodstreamPolicy> resolvePolicy(LivingEntity entity) {
        return CharacterIdentitySystem.resolveForActor(entity)
                .flatMap(data -> data.component(BloodstreamComponent.class).map(BloodstreamComponent::policy));
    }

    /** Legacy pure host/identity fixture seam; runtime authority is resolveForActor, not this method. */
    public static Optional<BloodstreamPolicy> resolvePolicy(CharacterData data, ResourceLocation identity,
            ResourceLocation host, Optional<ResourceLocation> hostOwner) {
        if (data == null || identity == null || host == null || hostOwner == null) return Optional.empty();
        if (!data.hostEntityTypes().isEmpty() && !data.hostEntityTypes().contains(host)) return Optional.empty();
        if (hostOwner.isPresent() && !hostOwner.get().equals(identity)) return Optional.empty();
        if (hostOwner.isEmpty() && !data.hostEntityTypes().isEmpty()) return Optional.empty();
        return data.component(BloodstreamComponent.class).map(BloodstreamComponent::policy);
    }

    /** Join-time initialization, or valid-policy reconciliation of a saved state. Missing policy is inert. */
    public static boolean reconcile(LivingEntity entity) {
        Optional<BloodstreamPolicy> policy = resolvePolicy(entity);
        if (policy.isEmpty()) return false;
        return reconcile(entity, policy.get());
    }

    private static boolean reconcile(LivingEntity entity, BloodstreamPolicy policy) {
        try {
            var reference = BloodReducer.reference(policy);
            for (var key : reference.keySet()) ModReagents.require(entity.level(), key.location());
            long cap = BloodReducer.capacity(policy);
            boolean hasLegacy = entity.hasData(ModDataAttachments.REAGENT.get());
            boolean hasStream = entity.hasData(ModDataAttachments.BLOODSTREAM.get());
            ReagentAttachment legacyCandidate = hasLegacy
                    ? MS14Provider.getDetached(entity, MS14Bridges.REAGENT) : new ReagentAttachment();
            ReagentAttachment streamCandidate = hasStream
                    ? MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM) : new ReagentAttachment();
            if (!legacyCandidate.isEmpty() && !streamCandidate.isEmpty()
                    && !legacyCandidate.snapshotUnits().equals(streamCandidate.snapshotUnits())) return false;
            ReagentAttachment candidate = !legacyCandidate.isEmpty() ? legacyCandidate : streamCandidate;
            if (legacyPigBlood(entity, legacyCandidate, streamCandidate)) return false;
            if (candidate.totalUnits() > cap) return false;
            // Validate policy and registered constituents before legacy storage can move.
            if (!BloodstreamStorage.reconcile(entity)) return false;
            BloodAttachment state = entity.hasData(ModDataAttachments.BLOOD.get())
                    ? MS14Provider.getDetached(entity, MS14Bridges.BLOOD)
                    : new BloodAttachment(new BloodComponent(0, false));
            if (state.initialized()) return validMixture(entity);
            if (!entity.isAlive()) return false;
            boolean hadStream = entity.hasData(ModDataAttachments.BLOODSTREAM.get());
            ReagentAttachment mix = hadStream
                    ? MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM) : new ReagentAttachment();
            // Existing bloodstream, including an intentionally empty one, is authoritative.
            // This also makes retry after solution commit but before marker commit idempotent.
            if (!hadStream) {
                for (var e : reference.entrySet()) mix.admitUnits(e.getKey(), e.getValue(), cap);
            }
            if (mix.totalUnits() > cap) return false;
            if (!hadStream) MS14Provider.update(entity, MS14Bridges.BLOODSTREAM, mix);
            state.set(0, true); // legacy bleed_rate/current_volume are intentionally discarded.
            MS14Provider.update(entity, MS14Bridges.BLOOD, state);
            return validMixture(entity);
        } catch (RuntimeException invalidPolicyOrMixture) { return false; }
    }

    private static boolean validMixture(LivingEntity entity) {
        if (!entity.hasData(ModDataAttachments.BLOODSTREAM.get())) return false;
        try {
            var units = MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM).snapshotUnits();
            if (legacyPigBlood(entity, new ReagentAttachment(),
                    MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM))) return false;
            for (var key : units.keySet()) ModReagents.require(entity.level(), key.location());
            var policy = resolvePolicy(entity);
            return policy.isPresent() && ReagentUnits.total(units.values()) <= BloodReducer.capacity(policy.get());
        } catch (RuntimeException invalidMixture) { return false; }
    }

    /** A pre-correction pig's saved sulfur fill is not evidence of a new Blood reference fill. */
    private static boolean legacyPigBlood(LivingEntity entity, ReagentAttachment legacy, ReagentAttachment stream) {
        var identity = entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity == null || !identity.isBound() || !PIG_ID.equals(identity.characterId())) return false;
        if (!legacy.snapshotUnits().containsKey(OLD_PIG_BLOOD)
                && !stream.snapshotUnits().containsKey(OLD_PIG_BLOOD)) return false;
        if (WARNED_LEGACY_PIG.compareAndSet(false, true))
            MoonStation14.LOGGER.warn("Saved pig {} contains legacy sulfurblood; preserving stores and disabling blood " +
                    "physiology/routing until an explicit operator-approved migration is performed", entity.getUUID());
        return true;
    }

    static boolean reconcileLegacyPigGuard(LivingEntity entity) {
        return !legacyPigBlood(entity,
                entity.hasData(ModDataAttachments.REAGENT.get())
                        ? MS14Provider.getDetached(entity, MS14Bridges.REAGENT) : new ReagentAttachment(),
                entity.hasData(ModDataAttachments.BLOODSTREAM.get())
                        ? MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM) : new ReagentAttachment());
    }

    /** Read-only lookup; this never materializes state or triggers initialization. */
    public static Optional<BloodComponent> state(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.hasData(ModDataAttachments.BLOOD.get())
                || !entity.hasData(ModDataAttachments.BLOODSTREAM.get())
                || entity.hasData(ModDataAttachments.REAGENT.get())) {
            return Optional.empty();
        }
        try {
            BloodComponent component = MS14Provider.getDetached(entity, MS14Bridges.BLOOD).toComponent();
            return component.initialized() && validMixture(entity) ? Optional.of(component) : Optional.empty();
        } catch (RuntimeException invalidState) { return Optional.empty(); }
    }

    /** Changes existing or policy-initialized blood state after valid policy resolution. */
    public static boolean adjustBleed(LivingEntity entity, double delta) {
        return adjust(entity, delta, true);
    }

    public static boolean adjustVolume(LivingEntity entity, double delta) {
        return adjust(entity, delta, false);
    }

    /**
     * Applies an effect delta to existing, prototype-eligible state only. Effects must not
     * initialize blood implicitly: first state creation remains a lifecycle reconciliation.
     */
    public static EffectAdjustmentResult applyEffectDelta(LivingEntity entity, float amount, float scale,
                                                           boolean bleed) {
        if (entity == null || entity.level().isClientSide) {
            return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;
        }
        Optional<BloodstreamPolicy> policy = resolvePolicy(entity);
        if (policy.isEmpty()) return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;

        float scaled = amount * scale;
        if (!Float.isFinite(amount) || !Float.isFinite(scale) || !Float.isFinite(scaled)) {
            return EffectAdjustmentResult.FAILED;
        }
        if (!entity.hasData(ModDataAttachments.BLOOD.get()) || !entity.hasData(ModDataAttachments.BLOODSTREAM.get())
                || entity.hasData(ModDataAttachments.REAGENT.get())) return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;

        BloodAttachment state = MS14Provider.getDetached(entity, MS14Bridges.BLOOD);
        BloodComponent before = state.toComponent();
        if (!before.initialized() || !validMixture(entity)) return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;
        long cap;
        ReagentAttachment currentMix;
        try {
            cap = BloodReducer.capacity(policy.get());
            currentMix = MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM);
            if (currentMix.totalUnits() > cap) return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;
        } catch (RuntimeException invalidMixtureOrCapacity) { return EffectAdjustmentResult.SKIPPED_UNSUPPORTED; }
        if (bleed) {
            double adjusted = Math.max(0, Math.min(policy.get().maxBleedRate(), before.bleedRate() + (double)scaled));
            if (!Double.isFinite(adjusted)) return EffectAdjustmentResult.FAILED;
            state.set(adjusted, before.initialized());
        } else {
            var mix = currentMix;
            var reference = BloodReducer.reference(policy.get());
            long wanted = BloodReducer.scaledEffectUnits(amount, scale);
            var oldMix = mix.toComponent();
            if (scaled >= 0) {
                BloodReducer.restoreTowardReference(mix, reference, wanted, cap);
            } else {
                long removal = Math.min(wanted, mix.totalUnits());
                mix.splitUnits(removal);
            }
            MS14Provider.updateIfChanged(entity, MS14Bridges.BLOODSTREAM, oldMix, mix);
            return EffectAdjustmentResult.APPLIED;
        }
        return MS14Provider.updateIfChanged(entity, MS14Bridges.BLOOD, before, state)
                ? EffectAdjustmentResult.APPLIED : EffectAdjustmentResult.FAILED;
    }

    /** Applies only committed positive typed ledger changes; healing and cancellation pass no positive delta. */
    public static void observeTypedDamage(LivingEntity entity, java.util.Map<String, Float> committedPositive) {
        if (entity == null || committedPositive == null || committedPositive.isEmpty()) return;
        Optional<BloodstreamPolicy> policy = resolvePolicy(entity);
        if (policy.isEmpty()) return;
        reconcile(entity);
        if (state(entity).isEmpty()) return;
        BloodAttachment state = MS14Provider.getDetached(entity, MS14Bridges.BLOOD);
        BloodComponent before = state.toComponent();
        state.set(typedDamageBleedRate(before.bleedRate(), committedPositive,
                policy.get().damageBleedMultipliers(), policy.get().maxBleedRate()), before.initialized());
        MS14Provider.updateIfChanged(entity, MS14Bridges.BLOOD, before, state);
    }

    /** Pure arithmetic seam: signed coefficients may reduce bleed, but never below zero. */
    static double typedDamageBleedRate(double current, Map<String, Float> committedPositive,
                                       Map<String, Double> multipliers, double maximum) {
        double increment = 0;
        for (var entry : committedPositive.entrySet()) {
            if (com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLOODLOSS.equals(entry.getKey())) continue;
            Double multiplier = multipliers.get(entry.getKey());
            Float damage = entry.getValue();
            if (multiplier != null && damage != null && damage > 0 && Float.isFinite(damage))
                increment += damage * multiplier;
        }
        return Math.max(0, Math.min(maximum, current + increment));
    }

    /** Per-prototype cadence, staggered by entity id; missed ticks are intentionally not replayed. */
    public static boolean tickIfDue(LivingEntity entity, long gameTime) {
        Optional<BloodstreamPolicy> policy = policyAtCadence(entity, gameTime);
        if (policy.isEmpty()) return false;
        int interval = intervalTicks(policy.get().updateIntervalSeconds());
        if (!isDue(gameTime, entity.getId(), interval)) return false;
        boolean alive = entity.isAlive();
        // Dead actors may continue bleeding, but must never initialize or migrate a store.
        if (alive) reconcile(entity, policy.get());
        BloodComponent current = state(entity).orElse(null);
        if (current == null || !current.initialized() || !entity.hasData(ModDataAttachments.BLOODSTREAM.get())) return false;
        ReagentAttachment mix = MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM);
        long cap = BloodReducer.capacity(policy.get());
        // A hot-reloaded capacity can fall below a saved solution. Preserve it untouched and
        // keep all physiology inert until an operator restores a compatible policy.
        if (mix.totalUnits() > cap) return false;
        var oldMix = mix.toComponent();
        ReagentAttachment pending = entity.hasData(ModDataAttachments.PENDING_BLOOD_SPILL.get())
                ? new ReagentAttachment(entity.getData(ModDataAttachments.PENDING_BLOOD_SPILL.get()).toComponent())
                : new ReagentAttachment();
        ReagentAttachment pendingBefore = new ReagentAttachment(pending.toComponent());
        long threshold = ReagentUnits.fromDouble(policy.get().bleedPuddleThreshold());
        // Invalid saved temporary state fails closed; valid legacy batches above the new
        // threshold get one immediate attempt on this due tick, then are cleared.
        if (!validTemporaryMixture(entity, pending)) return false;
        var stagedMix = new ReagentAttachment(mix.toComponent());
        var reference = BloodReducer.reference(policy.get());
        if (alive) BloodReducer.restoreTowardReference(stagedMix, reference,
                ReagentUnits.fromDouble(policy.get().bloodRefreshPerUpdate()), cap);
        BloodReducer.BleedStage bleedStage;
        try {
            bleedStage = BloodReducer.stageBleed(stagedMix, pending,
                    ReagentUnits.fromDouble(current.bleedRate()));
        } catch (RuntimeException invalidBatch) { return false; }
        stagedMix = bleedStage.bloodstream();
        pending = bleedStage.pending();
        // Both stores are prepared in detached copies before either authoritative write.
        MS14Provider.updateIfChanged(entity, MS14Bridges.BLOODSTREAM, oldMix, stagedMix);
        if (pending.totalUnits() > threshold) {
            try {
                ReagentSystem.handleSpillSolution(pending, entity.level(), entity.getOnPos());
            } catch (RuntimeException rejectedSpill) {
                // A rejected/invalid destination must not convert the batch into a retry queue.
            } finally {
                // Exactly one attempt: a full/unsupported destination discards the remainder.
                pending = new ReagentAttachment();
            }
        }
        if (!pendingBefore.equals(pending)) {
            entity.setData(ModDataAttachments.PENDING_BLOOD_SPILL.get(), pending);
        }
        double bleed = Math.max(0, current.bleedRate()-policy.get().bleedDecayPerUpdate());
        var next = new BloodComponent(bleed, true);
        MS14Provider.updateIfChanged(entity, MS14Bridges.BLOOD, current, next.toAttachment());
        if (!alive) return true;
        mix = MS14Provider.getDetached(entity, MS14Bridges.BLOODSTREAM);
        double fraction = BloodReducer.usableFraction(mix, reference, policy.get().maxVolumeModifier());
        var damage = BloodReducer.bloodloss(fraction, policy.get(), false);
        var healing = fraction >= policy.get().bloodlossThresholdFraction()
                ? BloodReducer.bloodloss(fraction, policy.get(), true) : java.util.Map.<String,Float>of();
        if (!damage.isEmpty()) DamageSystem.applyHealthChange(entity, damage, 1f,
                policy.get().bloodlossIgnoreResistances());
        // Negative-only healing commits directly to the existing typed ledger; the resistance flag
        // is not consulted for healing, and the ledger cannot heal beyond extant matching damage.
        if (!healing.isEmpty()) DamageSystem.applyHealthChange(entity, healing, 1f,
                policy.get().bloodlossIgnoreResistances());
        return true;
    }

    private static boolean validTemporaryMixture(LivingEntity entity, ReagentAttachment mixture) {
        try {
            for (var key : mixture.snapshotUnits().keySet()) ModReagents.require(entity.level(), key.location());
            return ReagentCatalogValidation.hasOnlyKnownPositiveReagents(mixture.getMap(), entity.level(), "temporary blood");
        } catch (RuntimeException invalid) { return false; }
    }

    /**
     * Refreshes host mapping at a bounded cadence unless a newly published catalog forces an
     * immediate refresh. The cached immutable policy supplies the configured update interval.
     */
    static Optional<BloodstreamPolicy> policyAtCadence(LivingEntity entity, long gameTime) {
        if (entity == null || entity.level().isClientSide || !(entity.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        ResourceLocation host = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        PrototypeCatalog<CharacterData> catalog = ModCharacters.catalog(level);
        var existing = entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        ResourceLocation identity = existing != null && existing.isBound() ? existing.characterId() : null;
        synchronized (POLICY_CACHE) {
            PolicyCache cached = POLICY_CACHE.get(entity);
            if (cached != null && cacheCurrent(cached.catalog(), catalog, gameTime, cached.checkedAt())
                    && cached.host().equals(host)
                    && java.util.Objects.equals(cached.identity(), identity)) {
                // A saved harness binding can disappear or become invalid without changing
                // the catalog, host, or character identity. Never reuse its old grant.
                return CharacterIdentitySystem.resolveForActor(entity, catalog)
                        .flatMap(data -> data.component(BloodstreamComponent.class).map(BloodstreamComponent::policy));
            }
        }

        // A dangling bound identity is authoritative even when its catalog entry is absent.
        // Re-resolve it on refresh, but never attempt host enrollment over an existing binding.
        if (entity.isAlive() && identity == null && existing == null) {
            CharacterIdentitySystem.enrollSupportedActor(entity, level);
            // Enrollment may have observed a newer publication than the first lookup.
            catalog = ModCharacters.catalog(level);
        }
        var bound = entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        ResourceLocation boundId = bound != null && bound.isBound() ? bound.characterId() : null;
        Optional<BloodstreamPolicy> policy = CharacterIdentitySystem.resolveForActor(entity, catalog)
                .flatMap(data -> data.component(BloodstreamComponent.class).map(BloodstreamComponent::policy));
        if (entity.isAlive() && policy.isPresent()) reconcile(entity, policy.get());
        synchronized (POLICY_CACHE) {
            POLICY_CACHE.put(entity, new PolicyCache(gameTime, catalog, host, boundId, policy));
        }
        return policy;
    }

    static boolean cacheCurrent(PrototypeCatalog<CharacterData> cachedCatalog,
                                PrototypeCatalog<CharacterData> currentCatalog, long gameTime, long checkedAt) {
        return cachedCatalog == currentCatalog && !retryDue(gameTime, checkedAt);
    }

    static boolean retryDue(long gameTime, long lastCheckedAt) {
        return gameTime < lastCheckedAt || gameTime - lastCheckedAt >= POLICY_RETRY_INTERVAL_TICKS;
    }

    /** Server tick rate is structural; interval remains entirely prototype-configured. */
    public static int intervalTicks(double seconds) {
        long ticks = Math.round(seconds * 20.0);
        if (ticks < 1 || ticks > Integer.MAX_VALUE) throw new IllegalArgumentException("blood interval ticks out of range");
        return (int) ticks;
    }

    public static boolean isDue(long gameTime, int entityId, int intervalTicks) {
        if (intervalTicks < 1) throw new IllegalArgumentException("interval ticks must be positive");
        return Math.floorMod(Math.floorMod(gameTime, (long) intervalTicks) + entityId, intervalTicks) == 0;
    }

    private static boolean adjust(LivingEntity entity, double delta, boolean bleed) {
        return applyEffectDelta(entity, (float)delta, 1f, bleed) == EffectAdjustmentResult.APPLIED;
    }

    /** Explicit clone rule: copy old scalar values to death and non-death player clones. */
    public static boolean copyToClone(LivingEntity original, LivingEntity clone) {
        if (clone.level().isClientSide || !original.hasData(ModDataAttachments.BLOOD.get())) return false;
        BloodComponent previous = MS14Provider.getDetached(original, MS14Bridges.BLOOD).toComponent();
        boolean changed = !clone.hasData(ModDataAttachments.BLOOD.get())
                || !previous.equals(MS14Provider.getDetached(clone, MS14Bridges.BLOOD).toComponent());
        if (changed) MS14Provider.update(clone, MS14Bridges.BLOOD, previous.toAttachment());
        if (original.hasData(ModDataAttachments.BLOODSTREAM.get())) {
            var mixture = MS14Provider.getDetached(original, MS14Bridges.BLOODSTREAM);
            changed |= !clone.hasData(ModDataAttachments.BLOODSTREAM.get())
                    || !mixture.equals(MS14Provider.getDetached(clone, MS14Bridges.BLOODSTREAM));
            if (changed) MS14Provider.update(clone, MS14Bridges.BLOODSTREAM, mixture);
        }
        if (original.hasData(ModDataAttachments.PENDING_BLOOD_SPILL.get())) {
            ReagentAttachment pending = original.getData(ModDataAttachments.PENDING_BLOOD_SPILL.get());
            if (!clone.hasData(ModDataAttachments.PENDING_BLOOD_SPILL.get())
                    || !pending.equals(clone.getData(ModDataAttachments.PENDING_BLOOD_SPILL.get()))) {
                clone.setData(ModDataAttachments.PENDING_BLOOD_SPILL.get(), new ReagentAttachment(pending.toComponent()));
                changed = true;
            }
        }
        return changed;
    }
}
