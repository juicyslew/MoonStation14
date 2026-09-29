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
        if (entity == null || entity.level().isClientSide()) {
            return Result.SKIPPED_UNSUPPORTED;
        }
        if (!Float.isFinite(scale) || scale < 0f) {
            return Result.FAILED;
        }
        Map<String, Float> delta = scaleDelta(rawDelta, scale);
        if (delta.isEmpty()) {
            return Result.APPLIED;
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
                    ? reagentBypassSource(entity.level())
                    : reagentSource(entity.level());
            PendingTransaction transaction = new PendingTransaction(positives, negatives);
            DamageTransactionStack<DamageSource, PendingTransaction> stack = pendingFor(entity);
            stack.push(source, transaction);
            try {
                entity.hurt(source, DamageReducer.total(positives) / TYPED_PER_HEALTH);
            } finally {
                removePending(entity, source);
            }
            // A canceled/rejected mixed mutation is canceled as a whole. The
            // negative-only path below remains direct and authoritative.
            return Result.APPLIED;
        }

        commit(entity, DamageReducer.applyDelta(existing(entity), delta));
        return Result.APPLIED;
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
        DamageData stored = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        if (stored != null && !stored.isEmpty()) {
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
        if (stored != null && !stored.isEmpty()) {
            return false;
        }
        if (stored != null) {
            entity.removeData(ModDataAttachments.DAMAGE.get());
        }
        Map<String, Float> baseline = missingVanillaBaseline(entity);
        if (baseline.isEmpty()) {
            return false;
        }
        commit(entity, baseline);
        return true;
    }

    /** Previews the ledger a health change would start from, without creating damage state. */
    public static Map<String, Float> existingOrVanillaBaseline(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide() || !entity.isAlive()) {
            return Map.of();
        }
        DamageData stored = entity.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        return stored != null && !stored.isEmpty() ? stored.getMap() : missingVanillaBaseline(entity);
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

        PendingTransaction pending = takePending(entity, source);
        if (pending != null) {
            Map<String, Float> committedPositive = DamageReducer.mitigatedPositiveDelta(pending.positive, appliedDamage);
            commit(entity, DamageReducer.applyMitigated(existing(entity), pending.positive,
                    appliedDamage, pending.negative));
            com.juicyslew.moonstation14.ms14.blood.BloodSystem.observeTypedDamage(entity, committedPositive);
            return;
        }

        if (appliedDamage <= 0f) {
            return;
        }
        String key = classify(source);
        float typed = appliedDamage * TYPED_PER_HEALTH;
        commit(entity, DamageReducer.applyDelta(existing(entity), Map.of(key, typed)));
        com.juicyslew.moonstation14.ms14.blood.BloodSystem.observeTypedDamage(entity, Map.of(key, typed));
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

    private static PendingTransaction takePending(LivingEntity entity, DamageSource source) {
        DamageTransactionStack<DamageSource, PendingTransaction> stack = PENDING.get(entity);
        if (stack == null) {
            return null;
        }
        PendingTransaction transaction = stack.removeMatching(source);
        if (stack.isEmpty()) {
            PENDING.remove(entity);
        }
        return transaction;
    }

    private static Map<String, Float> scaleDelta(Map<String, Float> raw, float scale) {
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
            if (before != null) entity.removeData(ModDataAttachments.DAMAGE.get());
            if (before != null) project(entity, clean);
            return;
        }
        entity.setData(ModDataAttachments.DAMAGE.get(), new DamageData(new DamageMap(clean)));
        project(entity, clean);
    }

    private static void project(LivingEntity entity, Map<String, Float> state) {
        float maxHealth = entity.getMaxHealth();
        if (!Float.isFinite(maxHealth) || maxHealth < 0f) return;
        float projected = Math.max(0f, Math.min(maxHealth,
                maxHealth - DamageReducer.total(state) / TYPED_PER_HEALTH));
        PROJECTING.add(entity);
        try {
            entity.setHealth(projected);
        } finally {
            PROJECTING.remove(entity);
        }
    }

    private static DamageSource reagentSource(Level level) {
        return damageSource(level, REAGENT_DAMAGE);
    }

    private static DamageSource reagentBypassSource(Level level) {
        return damageSource(level, REAGENT_BYPASS_DAMAGE);
    }

    private static DamageSource damageSource(Level level, ResourceKey<DamageType> key) {
        Registry<DamageType> registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder.Reference<DamageType> holder = registry.getHolderOrThrow(key);
        return new DamageSource(holder);
    }

    private record PendingTransaction(Map<String, Float> positive,
                                      Map<String, Float> negative) {
    }

    public enum Result {
        APPLIED,
        SKIPPED_UNSUPPORTED,
        FAILED
    }
}
