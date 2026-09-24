package com.juicyslew.moonstation14.ms14.electrocution;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectChangeKind;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectReduction;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The narrow Minecraft execution boundary for {@code Electrocute}.
 *
 * <p>This deliberately models neither equipment conductivity nor intrinsic
 * insulation.  A request which does not bypass insulation is therefore
 * rejected whenever it could have an effect, rather than guessing from
 * armor, potion, or entity traits.  The authoritative state supplied here is
 * shock damage and the local stunned status marker; physical stun projection,
 * stutter, jitter, popups, sounds, and events remain outside this effect
 * boundary.</p>
 */
public final class ElectrocutionSystem {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> STUNNED =
            com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects.createKey("statuseffectstunned");
    private static final AtomicBoolean WARNED_INSULATION_POLICY = new AtomicBoolean();
    private static volatile Consumer<String> warningSink = message -> MoonStation14.LOGGER.warn("{}", message);

    private ElectrocutionSystem() {
    }

    /** Executes status first, then one resistible typed shock-damage request. */
    public static EffectResult apply(EffectData.Electrocute effect, EffectContext context) {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(context, "context");

        // Admission is intentionally before arithmetic and before any state
        // lookup/mutation.  Nonliving targets and targets without the local
        // status capability are quiet unsupported effects.
        if (!(context.level() instanceof ServerLevel)
                || !(context.entity() instanceof LivingEntity living)
                || !(living instanceof IStatusEffectTrait statusHolder)) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        TraitHandler<IStatusEffectTrait> target = statusHolder.toHandleSelf();

        Plan plan = plan(effect, context.scale());
        if (plan.result() != EffectResult.APPLIED) {
            if (plan.result() == EffectResult.SKIPPED_UNSUPPORTED && plan.insulationRejected()) {
                warnInsulationPolicyOnce();
            }
            return plan.result();
        }
        if (plan.effectiveDamage() == 0) {
            return EffectResult.APPLIED;
        }

        // The status reducer owns UPDATE maximum/no-shortening and ADD
        // accumulation.  Do not maintain a competing electrocution timer.
        if (plan.stunTicks().isPresent()) {
            boolean hadStun = StatusEffectSystem.hasStatus(target, context.level(), STUNNED);
            StatusEffectReduction reduction = StatusEffectSystem.apply(target, context.level(), STUNNED,
                    plan.operation(), plan.stunTicks(), 0);
            // An absent status whose lifecycle rejects creation is reported by
            // the status core as UNCHANGED with no next instance.  Existing
            // statuses may also produce UNCHANGED (for example, a shorter
            // UPDATE); those remain eligible for the damage half.
            if (!hadStun && reduction.kind() == StatusEffectChangeKind.UNCHANGED
                    && reduction.next().isEmpty()) {
                return EffectResult.SKIPPED_UNSUPPORTED;
            }
        }

        // Insulation bypass is deliberately not resistance bypass.  The
        // DamageSystem receives scale 1 exactly once and the typed delta is
        // already fully reduced by the electrocution arithmetic plan.
        return switch (DamageSystem.applyHealthChange((LivingEntity) context.entity(),
                Map.of(DamageKeys.SHOCK, (float) plan.effectiveDamage()), 1f, false)) {
            case APPLIED -> EffectResult.APPLIED;
            case SKIPPED_UNSUPPORTED -> EffectResult.SKIPPED_UNSUPPORTED;
            case FAILED -> EffectResult.FAILED;
        };
    }

    /**
     * Computes the complete electrocution admission and arithmetic plan.
     * Multiplication and truncation are intentionally separate float stages:
     * {@code truncate((float) damage * scale)}, then
     * {@code truncate((float) scaledDamage * coefficient)}.
     */
    public static Plan plan(EffectData.Electrocute effect, float scale) {
        Objects.requireNonNull(effect, "effect");
        if (!Float.isFinite(scale) || scale < 0f
                || !Float.isFinite(effect.electrocuteTime()) || effect.electrocuteTime() < 0f
                || effect.shockDamage() < 0
                || !Float.isFinite(effect.siemensCoefficient()) || effect.siemensCoefficient() < 0f) {
            return Plan.failed();
        }

        float scaledProduct = (float) effect.shockDamage() * scale;
        if (!inIntegerRange(scaledProduct)) {
            return Plan.failed();
        }
        int scaledDamage = truncateTowardZero(scaledProduct);
        if (scaledDamage == 0 || effect.siemensCoefficient() == 0f) {
            return Plan.appliedNoOp(scaledDamage);
        }

        // There is no local conductivity model.  Do not infer insulation from
        // equipment, fire immunity, lightning traits, potions, or generic
        // invulnerability.  Positive first-stage damage plus a positive
        // configured coefficient is a potentially effective request.
        if (!effect.bypassInsulation()) {
            return Plan.insulationRejected(scaledDamage);
        }

        float effectiveProduct = (float) scaledDamage * effect.siemensCoefficient();
        if (!inIntegerRange(effectiveProduct)) {
            return Plan.failed();
        }
        int effectiveDamage = truncateTowardZero(effectiveProduct);
        if (effectiveDamage == 0) {
            return Plan.appliedNoOp(scaledDamage);
        }

        OptionalInt stunTicks = OptionalInt.empty();
        if (effect.siemensCoefficient() > .5f && effect.electrocuteTime() > 0f) {
            try {
                stunTicks = OptionalInt.of(StatusEffectSystem.nonNegativeSecondsToTicks(
                        effect.electrocuteTime()));
            } catch (IllegalArgumentException invalidDuration) {
                return Plan.failed();
            }
        }
        StatusEffectOperation operation = effect.refresh()
                ? StatusEffectOperation.UPDATE : StatusEffectOperation.ADD;
        return new Plan(EffectResult.APPLIED, scaledDamage, effectiveDamage, stunTicks, operation, false);
    }

    private static boolean inIntegerRange(float value) {
        return Float.isFinite(value) && value >= 0f
                && (double) value < (double) Integer.MAX_VALUE + 1d;
    }

    private static int truncateTowardZero(float value) {
        // All accepted values are nonnegative, but the cast is still the
        // specified Java truncation operation rather than Math.round/ceil.
        return (int) value;
    }

    private static void warnInsulationPolicyOnce() {
        if (WARNED_INSULATION_POLICY.compareAndSet(false, true)) {
            warningSink.accept("Electrocute with bypassInsulation=false is unsupported: MoonStation14 has no "
                    + "intrinsic or equipment conductivity/insulation model, so it cannot determine whether "
                    + "a potentially effective shock should pass.");
        }
    }

    /** Testable immutable result of electrocution arithmetic and policy. */
    public record Plan(EffectResult result, int scaledDamage, int effectiveDamage,
                       OptionalInt stunTicks, StatusEffectOperation operation,
                       boolean insulationRejected) {
        public Plan {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(stunTicks, "stunTicks");
            Objects.requireNonNull(operation, "operation");
        }

        static Plan failed() {
            return new Plan(EffectResult.FAILED, 0, 0, OptionalInt.empty(),
                    StatusEffectOperation.UPDATE, false);
        }

        static Plan appliedNoOp(int scaledDamage) {
            return new Plan(EffectResult.APPLIED, scaledDamage, 0, OptionalInt.empty(),
                    StatusEffectOperation.UPDATE, false);
        }

        static Plan insulationRejected(int scaledDamage) {
            return new Plan(EffectResult.SKIPPED_UNSUPPORTED, scaledDamage, 0,
                    OptionalInt.empty(), StatusEffectOperation.UPDATE, true);
        }
    }

    /** Allows focused tests to assert the once-only policy diagnostic. */
    public static void setWarningSink(Consumer<String> sink) {
        warningSink = Objects.requireNonNull(sink, "sink");
    }

    public static void resetWarnings() {
        WARNED_INSULATION_POLICY.set(false);
    }

    public static void resetWarningSink() {
        warningSink = message -> MoonStation14.LOGGER.warn("{}", message);
    }
}
