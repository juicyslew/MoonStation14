package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.component.codec.component.DamageMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.NeoForge;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/** The only world-mutation boundary for the character damage ledger. */
public final class DamageSystem {
    public static final float TYPED_PER_HEALTH = 5f;
    private static final ResourceKey<DamageType> REAGENT_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "reagent"));
    private static final ResourceKey<DamageType> REAGENT_BYPASS_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "reagent_bypass"));

    private static final Map<LivingEntity, DamageTransactionStack<DamageSource, PendingTransaction>> PENDING = new IdentityHashMap<>();
    private static final Set<LivingEntity> PROJECTING = Collections.newSetFromMap(new IdentityHashMap<>());

    private DamageSystem() {
    }

    /** Applies a HealthChange, scaling its typed values exactly once. */
    public static Result applyHealthChange(LivingEntity entity, Map<String, Float> rawDelta,
                                            float scale, boolean ignoreResistances) {
        return applyHealthChange(entity, rawDelta, scale, ignoreResistances, Cause.CHEMICAL);
    }

    public static Result applyHealthChange(LivingEntity entity, Map<String, Float> rawDelta,
                                             float scale, boolean ignoreResistances, Cause cause) {
        return applyHealthChangeDetailed(entity, rawDelta, scale, ignoreResistances, cause).status();
    }

    public static ChangeResult applyHealthChangeDetailed(LivingEntity entity, Map<String, Float> rawDelta,
                                             float scale, boolean ignoreResistances, Cause cause) {
        return applyHealthChangeDetailed(entity, rawDelta, scale, ignoreResistances, cause, null);
    }

    public static ChangeResult applyHealthChangeDetailed(LivingEntity entity, Map<String, Float> rawDelta,
                                             float scale, boolean ignoreResistances, Cause cause,
                                             net.minecraft.world.entity.Entity origin) {
        if (entity == null || entity.level().isClientSide()) {
            return new ChangeResult(Result.SKIPPED_UNSUPPORTED, Map.of());
        }
        if (!Float.isFinite(scale) || scale < 0f) {
            return new ChangeResult(Result.FAILED, Map.of());
        }
        Map<String, Float> delta = scaleDelta(rawDelta, scale);
        if (!ignoreResistances && !delta.isEmpty())
            delta = com.juicyslew.moonstation14.ms14.equipment.EquipmentArmorSystem.apply(entity, delta);
        if (delta.isEmpty()) {
            return new ChangeResult(Result.APPLIED, Map.of());
        }
        ensureBaseline(entity);

        Map<String, Float> positives = new LinkedHashMap<>();
        Map<String, Float> negatives = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : delta.entrySet()) {
            if (entry.getValue() > 0f) {
                positives.put(entry.getKey(), entry.getValue());
            } else if (entry.getValue() < 0f) {
                negatives.put(entry.getKey(), entry.getValue());
            }
        }

        if (!positives.isEmpty()) {
            DamageSource source = ignoreResistances
                    ? reagentBypassSource(entity.level(), origin)
                    : reagentSource(entity.level(), origin);
            PendingTransaction transaction = new PendingTransaction(positives, negatives,
                    ModDamageFeedback.server(cause.profile));
            DamageTransactionStack<DamageSource, PendingTransaction> stack = pendingFor(entity);
            stack.push(source, transaction);
            try {
                boolean retainedDead = com.juicyslew.moonstation14.ms14.mobs.MobNativeHealthProjection
                        .shouldCommitRetainedDead(!entity.level().isClientSide(), entity.isRemoved(),
                                entity.level().getEntity(entity.getId()) == entity,
                                com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isDead(entity),
                                entity.getHealth() <= 0f);
                if (retainedDead) {
                    observePost(entity, source, DamageReducer.total(positives) / TYPED_PER_HEALTH);
                } else {
                    entity.hurt(source, DamageReducer.total(positives) / TYPED_PER_HEALTH);
                }
            } finally {
                removePending(entity, source);
            }
            // A canceled/rejected mixed mutation is canceled as a whole. The
            // negative-only path below remains direct and authoritative.
            return new ChangeResult(Result.APPLIED, transaction.committedPositive);
        }

        commit(entity, DamageReducer.applyDelta(existing(entity), delta));
        return new ChangeResult(Result.APPLIED, Map.of());
    }

    /** Applies EvenHealthChange's stable, group-sequential healing policy. */
    public static Result applyEvenHealthChange(LivingEntity entity, Map<String, Float> rawGroups,
                                                float scale) {
        if (entity == null || entity.level().isClientSide()) {
            return Result.SKIPPED_UNSUPPORTED;
        }
        if (!Float.isFinite(scale) || scale < 0f || rawGroups == null) {
            return Result.FAILED;
        }
        Map<String, Float> scaled = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : rawGroups.entrySet()) {
            if (!DamageKeys.GROUPS.containsKey(entry.getKey()) || entry.getValue() == null
                    || !Float.isFinite(entry.getValue())) {
                return Result.FAILED;
            }
            float value = entry.getValue() * scale;
            if (!Float.isFinite(value)) {
                return Result.FAILED;
            }
            scaled.put(entry.getKey(), value);
        }
        if (scaled.values().stream().noneMatch(value -> value < 0f)) {
            return Result.APPLIED;
        }
        ensureBaseline(entity);
        DamageData stored = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        if (stored == null) {
            return Result.APPLIED;
        }
        commit(entity, DamageReducer.healGroups(stored.getMap(), scaled));
        return Result.APPLIED;
    }

    /** Trusted operator recovery primitive; clears only the authoritative typed ledger. */
    public static boolean clearTypedDamageForRecovery(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide() || entity.isRemoved()) return false;
        DamageData stored = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        if (stored == null) return false;
        commit(entity, Map.of());
        return true;
    }

    /** Mirrors generic vanilla healing as even healing over every damaged type. */
    public static Result applyVanillaHealing(LivingEntity entity, float healthAmount) {
        if (entity == null || entity.level().isClientSide()) {
            return Result.SKIPPED_UNSUPPORTED;
        }
        if (!Float.isFinite(healthAmount) || healthAmount < 0f) {
            return Result.FAILED;
        }
        if (healthAmount == 0f) {
            return Result.APPLIED;
        }
        ensureBaseline(entity);
        DamageData stored = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        float maxHealth = entity.getMaxHealth();
        float acceptedAmount = Math.min(healthAmount, Math.max(0f, maxHealth - entity.getHealth()));
        if (stored == null || acceptedAmount <= 0f) {
            return Result.APPLIED;
        }
        commit(entity, DamageReducer.healEvenly(stored.getMap(), DamageKeys.ORDER,
                acceptedAmount * TYPED_PER_HEALTH));
        return Result.APPLIED;
    }

    /** Reprojects persisted damage once, when a living server entity joins. */
    public static void reconcile(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.ensureProjection(entity);
        DamageData stored = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        if (stored != null) {
            project(entity, stored.getMap());
        } else {
            ensureBaseline(entity);
        }
    }

    /** Imports missing vanilla health as blunt damage without materializing full-health state. */
    public static boolean ensureBaseline(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide() || !entity.isAlive()) {
            return false;
        }
        DamageData stored = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        if (stored != null) {
            return false;
        }
        Map<String, Float> baseline = missingVanillaBaseline(entity);
        if (baseline.isEmpty()) {
            return false;
        }
        commit(entity, baseline);
        return true;
    }

    /** Publishes the server-owned typed health ledger to the entity's current tracking clients. */
    public static boolean publishHealthState(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide() || entity.isRemoved() || !entity.isAlive()) return false;
        // A present empty ledger is authoritative known-zero, not a cue to import vanilla health.
        if (entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()) == null) {
            ensureBaseline(entity);
        }
        if (entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()) == null)
            entity.setData(ModDataAttachments.DAMAGE.get(), new DamageData());
        entity.syncData(ModDataAttachments.DAMAGE.get());
        return true;
    }

    /** Previews the ledger a health change would start from, without creating damage state. */
    public static Map<String, Float> existingOrVanillaBaseline(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide() || !entity.isAlive()) {
            return Map.of();
        }
        DamageData stored = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        return stored != null ? stored.getMap() : missingVanillaBaseline(entity);
    }

    private static Map<String, Float> missingVanillaBaseline(LivingEntity entity) {
        float maxHealth = entity.getMaxHealth();
        float health = entity.getHealth();
        if (!Float.isFinite(maxHealth) || maxHealth <= 0f || !Float.isFinite(health)
                || health <= 0f || health >= maxHealth) {
            return Map.of();
        }
        float blunt = (maxHealth - health) * TYPED_PER_HEALTH;
        return Float.isFinite(blunt) ? DamageKeys.validateState(Map.of(DamageKeys.BLUNT, blunt)) : Map.of();
    }

    /** Mirrors a completed vanilla damage sequence exactly once. */
    public static void observePost(LivingEntity entity, DamageSource source, float finalDamage) {
        if (entity == null || entity.level().isClientSide() || source == null || !Float.isFinite(finalDamage)
                || PROJECTING.contains(entity)) {
            return;
        }
        float appliedDamage = Math.max(0f, finalDamage);

        DamageTransactionStack<DamageSource, PendingTransaction> pendingStack = PENDING.get(entity);
        PendingTransaction pending = pendingStack == null ? null : pendingStack.peekMatching(source);
        if (pending != null) {
            if (!pending.claimCommit()) return;
            Map<String, Float> committedPositive = DamageReducer.mitigatedPositiveDelta(pending.positive, appliedDamage);
            commit(entity, DamageReducer.applyMitigated(existing(entity), pending.positive,
                    appliedDamage, pending.negative));
            pending.committedPositive = Map.copyOf(committedPositive);
            com.juicyslew.moonstation14.ms14.blood.BloodSystem.observeTypedDamage(entity, committedPositive);
            double committedTypedDamage = committedPositive.values().stream()
                    .mapToDouble(Float::doubleValue).sum();
            if (committedTypedDamage > 0d) {
                NeoForge.EVENT_BUS.post(new AppliedDamageEvent(entity, committedTypedDamage, pending.feedback, source.getEntity()));
            }
            if (!committedPositive.isEmpty()) NeoForge.EVENT_BUS.post(new TypedDamageAppliedEvent(entity, committedPositive, source.getEntity()));
            return;
        }

        if (appliedDamage <= 0f) {
            return;
        }
        String key = classify(source);
        float typed = appliedDamage * TYPED_PER_HEALTH;
        commit(entity, DamageReducer.applyDelta(existing(entity), Map.of(key, typed)));
        com.juicyslew.moonstation14.ms14.blood.BloodSystem.observeTypedDamage(entity, Map.of(key, typed));
        NeoForge.EVENT_BUS.post(new AppliedDamageEvent(entity, typed,
                ModDamageFeedback.server(causeFor(source).profile), source.getEntity()));
        NeoForge.EVENT_BUS.post(new TypedDamageAppliedEvent(entity, Map.of(key, typed), source.getEntity()));
    }

    /** Returns an unambiguous canonical fallback for an external source. */
    public static String classify(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) return DamageKeys.HEAT;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return DamageKeys.PIERCING;
        if (source.is(DamageTypeTags.IS_FREEZING)) return DamageKeys.COLD;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return DamageKeys.SHOCK;
        if (source.is(DamageTypeTags.IS_DROWNING)) return DamageKeys.ASPHYXIATION;
        // Explosion and all other sources are intentionally blunt. There is no
        // safe source-level inference for caustic, holy, toxin, or cellular.
        return DamageKeys.BLUNT;
    }

    /** Converts one external native hit through the same worn-armor kernel; empty means bypass or typed transaction. */
    public static java.util.Optional<Float> armorNativeIncomingDamage(LivingEntity entity, DamageSource source, float healthAmount) {
        if (entity == null || entity.level().isClientSide() || source == null || !Float.isFinite(healthAmount)
                || healthAmount <= 0f || source.is(DamageTypeTags.BYPASSES_ARMOR)) return java.util.Optional.empty();
        var pending = PENDING.get(entity);
        if (pending != null && pending.peekMatching(source) != null) return java.util.Optional.empty();
        String type = classify(source);
        float typed = healthAmount * TYPED_PER_HEALTH;
        if (!Float.isFinite(typed)) return java.util.Optional.empty();
        float modified = com.juicyslew.moonstation14.ms14.equipment.EquipmentArmorSystem.apply(entity, Map.of(type, typed))
                .getOrDefault(type, 0f);
        return java.util.Optional.of(modified / TYPED_PER_HEALTH);
    }

    private static Map<String, Float> existing(LivingEntity entity) {
        DamageData stored = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        return stored == null ? Map.of() : stored.getMap();
    }

    private static DamageTransactionStack<DamageSource, PendingTransaction> pendingFor(LivingEntity entity) {
        return PENDING.computeIfAbsent(entity, ignored -> new DamageTransactionStack<>());
    }

    private static void removePending(LivingEntity entity, DamageSource source) {
        DamageTransactionStack<DamageSource, PendingTransaction> stack = PENDING.get(entity);
        if (stack != null) {
            stack.removeMatching(source);
            if (stack.isEmpty()) {
                PENDING.remove(entity);
            }
        }
    }

    public static Map<String, Float> scaleDelta(Map<String, Float> raw, float scale) {
        Map<String, Float> input = DamageKeys.validateDelta(raw);
        Map<String, Float> result = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : input.entrySet()) {
            float value = entry.getValue() * scale;
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("scaled damage must be finite");
            }
            if (value != 0f) result.put(entry.getKey(), value);
        }
        return DamageKeys.validateDelta(result);
    }

    private static void commit(LivingEntity entity, Map<String, Float> next) {
        Map<String, Float> clean = DamageKeys.validateState(next);
        DamageData before = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        if (before != null && before.getMap().equals(clean)) {
            return;
        }
        if (clean.isEmpty()) {
            if (before != null) {
                entity.setData(ModDataAttachments.DAMAGE.get(), new DamageData(new DamageMap(clean)));
                entity.syncData(ModDataAttachments.DAMAGE.get());
                project(entity, clean);
            }
            com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.reconcile(entity);
            return;
        }
        entity.setData(ModDataAttachments.DAMAGE.get(), new DamageData(new DamageMap(clean)));
        entity.syncData(ModDataAttachments.DAMAGE.get());
        project(entity, clean);
        com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.reconcile(entity);
    }

    private static void project(LivingEntity entity, Map<String, Float> state) {
        com.juicyslew.moonstation14.ms14.mobs.MobStatePolicy policy =
                com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.policy(entity);
        if (policy != null) {
            double dead = policy.thresholds().thresholds().entrySet().stream()
                    .filter(e -> e.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD)
                    .mapToDouble(Map.Entry::getKey).min().orElse(Double.NaN);
            var saved = entity.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
            var result = com.juicyslew.moonstation14.ms14.mobs.MobNativeHealthProjection.project(dead,
                    DamageReducer.total(state), policy.states().allowedStates().contains(
                            com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD),
                    saved != null && saved.data().state() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD,
                    entity.getHealth() <= 0 || entity.isDeadOrDying(), true);
            var attribute = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (attribute != null) attribute.setBaseValue(result.maxHealth());
            PROJECTING.add(entity);
            try { entity.setHealth((float) result.health()); }
            finally { PROJECTING.remove(entity); }
            return;
        }
        float maxHealth = entity.getMaxHealth();
        if (!Float.isFinite(maxHealth) || maxHealth < 0f) return;
        float projected = Math.max(0f, Math.min(maxHealth,
                maxHealth - DamageReducer.total(state) / TYPED_PER_HEALTH));
        if (com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isDead(entity)) projected = 0f;
        PROJECTING.add(entity);
        try {
            entity.setHealth(projected);
        } finally {
            PROJECTING.remove(entity);
        }
    }

    private static DamageSource reagentSource(Level level, net.minecraft.world.entity.Entity origin) {
        return damageSource(level, REAGENT_DAMAGE, origin);
    }

    private static DamageSource reagentBypassSource(Level level, net.minecraft.world.entity.Entity origin) {
        return damageSource(level, REAGENT_BYPASS_DAMAGE, origin);
    }

    private static DamageSource damageSource(Level level, ResourceKey<DamageType> key, net.minecraft.world.entity.Entity origin) {
        Registry<DamageType> registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder.Reference<DamageType> holder = registry.getHolderOrThrow(key);
        return damageSource(holder, origin);
    }

    static DamageSource damageSource(Holder<DamageType> holder, net.minecraft.world.entity.Entity origin) {
        return origin == null ? new DamageSource(holder) : new DamageSource(holder, origin);
    }

    public enum Cause {
        CHEMICAL(ModDamageFeedback.id("chemical")),
        RESPIRATION(ModDamageFeedback.id("respiration")),
        TEMPERATURE(ModDamageFeedback.id("temperature")),
        BLOOD_LOSS(ModDamageFeedback.id("blood_loss")),
        FIRE(ModDamageFeedback.id("fire")),
        ELECTRICAL(ModDamageFeedback.id("electrical")),
        EXPLOSION(ModDamageFeedback.id("explosion")),
        COMBAT(ModDamageFeedback.id("combat"));
        private final net.minecraft.resources.ResourceLocation profile;
        Cause(net.minecraft.resources.ResourceLocation profile) { this.profile = profile; }
        public net.minecraft.resources.ResourceLocation profileId() { return profile; }
    }

    /** Feedback resolved before vanilla emits its damage-event packet. */
    public static DamageFeedbackProfile feedbackBeforeNativePacket(LivingEntity entity, DamageSource source) {
        DamageTransactionStack<DamageSource, PendingTransaction> stack = PENDING.get(entity);
        PendingTransaction pending = stack == null ? null : stack.peekMatching(source);
        return pending == null ? ModDamageFeedback.server(causeFor(source).profile) : pending.feedback;
    }

    /** Explicit vanilla source semantics; unrecognized native hits retain combat feedback. */
    private static Cause causeFor(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) return Cause.FIRE;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return Cause.ELECTRICAL;
        if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING)) return Cause.RESPIRATION;
        ResourceLocation id = source.typeHolder().unwrapKey().map(ResourceKey::location).orElse(null);
        if (id != null && id.getNamespace().equals(MoonStation14.MOD_ID)
                && (id.getPath().equals("reagent") || id.getPath().equals("reagent_bypass"))) return Cause.CHEMICAL;
        if (id != null && id.getPath().equals("explosion")) return Cause.EXPLOSION;
        if (id != null && (id.getPath().equals("poison") || id.getPath().equals("wither")
                || id.getPath().equals("starve"))) return Cause.CHEMICAL;
        return Cause.COMBAT;
    }

    private static final class PendingTransaction {
        private final Map<String, Float> positive;
        private final Map<String, Float> negative;
        private final DamageFeedbackProfile feedback;
        private boolean committed;
        private Map<String, Float> committedPositive = Map.of();
        private PendingTransaction(Map<String, Float> positive, Map<String, Float> negative,
                                   DamageFeedbackProfile feedback) {
            this.positive = positive;
            this.negative = negative;
            this.feedback = feedback;
        }
        private boolean claimCommit() {
            if (committed) return false;
            committed = true;
            return true;
        }
    }

    public record ChangeResult(Result status, Map<String, Float> committedPositive) {
        public ChangeResult { committedPositive = DamageKeys.validateState(committedPositive); }
    }

    public enum Result {
        APPLIED,
        SKIPPED_UNSUPPORTED,
        FAILED
    }
}
