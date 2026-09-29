package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.ms14.fire.FireStackSystem;
import com.juicyslew.moonstation14.ms14.blood.BloodSystem;
import com.juicyslew.moonstation14.ms14.eye.EyeDamageSystem;
import com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.juicyslew.moonstation14.util.MapOperations.getTotal;

/** The small set of effect behaviors that are real in the current milestone. */
final class EffectHandlers {
    private EffectHandlers() {
    }

    static ReagentAttachment transactionSolution(ReagentEffectContext context) {
        return java.util.Objects.requireNonNull(context, "context").solution();
    }

    static void registerDefaults(EffectDispatcher dispatcher) {
        dispatcher.register(EffectData.EvenHealthChange.class, EffectHandlers::evenHealthChange);
        dispatcher.register(EffectData.HealthChange.class, EffectHandlers::healthChange);
        dispatcher.register(EffectData.Vomit.class, EffectHandlers::vomit);
        dispatcher.register(EffectData.Jitter.class, EffectHandlers::jitter);
        dispatcher.register(EffectData.MovementSpeedModifier.class, EffectHandlers::movementSpeedModifier);
        dispatcher.register(EffectData.Drunk.class, EffectHandlers::drunk);
        dispatcher.register(EffectData.ModifyStatusEffect.class, EffectHandlers::modifyStatusEffect);
        dispatcher.register(EffectData.ModifyKnockdown.class, EffectHandlers::modifyKnockdown);
        dispatcher.register(EffectData.GenericStatusEffect.class,
                GenericStatusEffectAdapter.production()::apply);
        dispatcher.register(EffectData.AdjustReagent.class, EffectHandlers::adjustReagent);
        dispatcher.register(EffectData.PopupMessage.class, PopupMessageEffect::apply);
        dispatcher.register(EffectData.Flammable.class, EffectHandlers::flammable);
        dispatcher.register(EffectData.Ignite.class, EffectHandlers::ignite);
        dispatcher.register(EffectData.Extinguish.class, EffectHandlers::extinguish);
        dispatcher.register(EffectData.AdjustTemperature.class, EffectHandlers::adjustTemperature);

        dispatcher.register(EffectData.ModifyBleed.class, EffectHandlers::modifyBleed);
        dispatcher.register(EffectData.Oxygenate.class, EffectHandlers::oxygenate);
        dispatcher.register(EffectData.ModifyLungGas.class, EffectHandlers::modifyLungGas);
        dispatcher.register(EffectData.ModifyBloodLevel.class, EffectHandlers::modifyBloodLevel);
        dispatcher.register(EffectData.SatiateThirst.class, EffectHandlers::satiateThirst);
        dispatcher.registerUnsupported(EffectData.CleanBloodstream.class,
                "Route-aware bloodstream state and reagent exclusion rules are unavailable.");
        dispatcher.registerUnsupported(EffectData.ResetNarcolepsy.class,
                "Authoritative narcolepsy incident state is unavailable.");
        dispatcher.registerUnsupported(EffectData.ReduceRotting.class,
                "Authoritative rot and death-decay state is unavailable.");
        dispatcher.registerUnsupported(EffectData.CauseZombieInfection.class,
                "Infection, immunity, and death-conversion state is unavailable.");
        dispatcher.registerUnsupported(EffectData.CureZombieInfection.class,
                "Infection, immunity, and death-conversion state is unavailable.");
        dispatcher.registerUnsupported(EffectData.ArtifactDurabilityRestore.class,
                "The artifact node and durability system is unavailable.");
        dispatcher.registerUnsupported(EffectData.ArtifactUnlock.class,
                "The artifact node and unlock system is unavailable.");
        dispatcher.registerUnsupported(EffectData.MakeSentient.class,
                "Mind and ghost-role control systems are unavailable.");
        dispatcher.registerUnsupported(EffectData.Polymorph.class,
                "Entity replacement, control, inventory, and revert systems are unavailable.");
        dispatcher.register(EffectData.AdjustAlert.class, EffectHandlers::adjustAlert);
        dispatcher.register(EffectData.SatiateHunger.class, EffectHandlers::satiateHunger);
        dispatcher.register(EffectData.Emote.class, (effect, context) -> {
            if (!(context.entity() instanceof LivingEntity)) return EffectResult.SKIPPED_UNSUPPORTED;
            return EmoteEffect.apply(effect.emote(), effect.showInChat(), context.entity(), context.level(),
                    EmoteEffect.SERVER);
        });
        dispatcher.register(EffectData.Electrocute.class, ElectrocutionSystem::apply);
        dispatcher.register(EffectData.EyeDamage.class, EffectHandlers::eyeDamage);
    }

    private static EffectResult flammable(EffectData.Flammable effect, EffectContext context) {
        return FireStackSystem.flammable(context.entity(), effect.multiplier(),
                effect.multiplierOnExisting(), context.scale());
    }

    private static EffectResult ignite(EffectData.Ignite effect, EffectContext context) {
        // The common gate has already admitted this effect.  Its scale is
        // intentionally ignored, matching SS14's Ignite behavior.
        return FireStackSystem.ignite(context.entity(), context.scale());
    }

    private static EffectResult extinguish(EffectData.Extinguish effect, EffectContext context) {
        return FireStackSystem.extinguish(context.entity(), effect.fireStacksAdjustment(), context.scale());
    }

    private static EffectResult eyeDamage(EffectData.EyeDamage effect, EffectContext context) {
        return EyeDamageSystem.apply(context.entity(), effect.amount(), context.scale());
    }

    private static EffectResult adjustTemperature(EffectData.AdjustTemperature effect, EffectContext context) {
        return com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureSystem.adjustHeat(
                context.entity(), effect.amount(), context.scale());
    }

    private static EffectResult modifyBleed(EffectData.ModifyBleed effect, EffectContext context) {
        if (!(context.entity() instanceof LivingEntity living)) return EffectResult.SKIPPED_UNSUPPORTED;
        return bloodEffectResult(BloodSystem.applyEffectDelta(living, effect.amount(), context.scale(), true));
    }

    private static EffectResult modifyBloodLevel(EffectData.ModifyBloodLevel effect, EffectContext context) {
        if (!(context.entity() instanceof LivingEntity living)) return EffectResult.SKIPPED_UNSUPPORTED;
        return bloodEffectResult(BloodSystem.applyEffectDelta(living, effect.amount(), context.scale(), false));
    }

    private static EffectResult oxygenate(EffectData.Oxygenate effect, EffectContext context) {
        if (!(context.entity() instanceof LivingEntity living)) return EffectResult.SKIPPED_UNSUPPORTED;
        double amount = (double) effect.factor() * context.scale();
        return lungEffectResult(com.juicyslew.moonstation14.ms14.lung.LungSystem.oxygenate(living, amount));
    }

    private static EffectResult modifyLungGas(EffectData.ModifyLungGas effect, EffectContext context) {
        if (!(context.entity() instanceof LivingEntity living)) return EffectResult.SKIPPED_UNSUPPORTED;
        return lungEffectResult(com.juicyslew.moonstation14.ms14.lung.LungSystem.modifyLungGas(
                living, effect.ratios(), context.scale()));
    }

    private static EffectResult lungEffectResult(
            com.juicyslew.moonstation14.ms14.lung.LungSystem.EffectAdjustmentResult result) {
        return switch (result) {
            case APPLIED -> EffectResult.APPLIED;
            case SKIPPED_UNSUPPORTED -> EffectResult.SKIPPED_UNSUPPORTED;
            case FAILED -> EffectResult.FAILED;
        };
    }

    private static EffectResult bloodEffectResult(BloodSystem.EffectAdjustmentResult result) {
        return switch (result) {
            case APPLIED -> EffectResult.APPLIED;
            case SKIPPED_UNSUPPORTED -> EffectResult.SKIPPED_UNSUPPORTED;
            case FAILED -> EffectResult.FAILED;
        };
    }

    private static EffectResult adjustAlert(EffectData.AdjustAlert effect, EffectContext context) {
        if (!(context.level() instanceof ServerLevel serverLevel)
                || !(context.entity() instanceof LivingEntity living)
                || !(living instanceof IStatusEffectTrait)) return EffectResult.SKIPPED_UNSUPPORTED;
        long ticks;
        try {
            ticks = com.juicyslew.moonstation14.ms14.alert.AlertSystem.secondsToTicks(effect.time());
        } catch (IllegalArgumentException | ArithmeticException invalidTime) {
            return EffectResult.FAILED;
        }
        return switch (com.juicyslew.moonstation14.ms14.alert.AlertSystem.apply(living, serverLevel,
                effect.alertType(), effect.clear(), ticks, effect.showCooldown())) {
            case APPLIED -> EffectResult.APPLIED;
            case UNSUPPORTED_TARGET -> EffectResult.SKIPPED_UNSUPPORTED;
            case FAILED -> EffectResult.FAILED;
        };
    }

    private static EffectResult satiateHunger(EffectData.SatiateHunger effect, EffectContext context) {
        if (!(context.entity() instanceof LivingEntity living)) return EffectResult.SKIPPED_UNSUPPORTED;
        return com.juicyslew.moonstation14.ms14.hunger.HungerSystem.satiate(living, effect.factor(), context.scale())
                ? EffectResult.APPLIED : EffectResult.FAILED;
    }

    private static EffectResult satiateThirst(EffectData.SatiateThirst effect, EffectContext context) {
        if (!(context.level() instanceof ServerLevel serverLevel)
                || !(context.entity() instanceof LivingEntity living)) return EffectResult.SKIPPED_UNSUPPORTED;
        return switch (com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.satiate(
                living, serverLevel, effect.factor(), context.scale())) {
            case APPLIED -> EffectResult.APPLIED;
            case SKIPPED_UNSUPPORTED -> EffectResult.SKIPPED_UNSUPPORTED;
            case FAILED -> EffectResult.FAILED;
        };
    }

    private static EffectResult adjustReagent(EffectData.AdjustReagent effect, EffectContext context) {
        return adjustReagent(effect, context.scale(), context.reagentContext());
    }

    /**
     * Applies an AdjustReagent effect to the explicitly routed metabolism solution.
     * This overload keeps routing and arithmetic testable without constructing a level
     * or entity; normal dispatch reaches it through the full EffectContext above.
     */
    static EffectResult adjustReagent(EffectData.AdjustReagent effect, float scale,
                                      Optional<ReagentEffectContext> reagentContext) {
        java.util.Objects.requireNonNull(effect, "effect");
        java.util.Objects.requireNonNull(reagentContext, "reagentContext");
        if (reagentContext.isEmpty()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }

        float delta = effect.amount() * scale;
        if (!Float.isFinite(delta)) {
            return EffectResult.FAILED;
        }
        if (delta == 0f) {
            return EffectResult.APPLIED;
        }

        ReagentEffectContext routed = reagentContext.orElseThrow();
        ReagentAttachment solution = transactionSolution(routed);
        ReagentEffectTransactionState transactionState = routed.transactionState();
        if (Float.compare(routed.ordinaryRemoved(), transactionState.ordinaryRemoved()) != 0) {
            return EffectResult.FAILED;
        }
        if (delta > 0f) {
            float capacity = routed.source().trait().getCapacity();
            ReagentEffectTransactionState.PositiveAdjustmentPlan plan;
            try {
                plan = transactionState.previewPositiveAdjustment(effect.reagent(), solution, capacity, delta);
                // Commit the physical side first. addCapacitySafe validates and
                // stages its own writes; the reservation is committed only after
                // that transactional API succeeds.
                solution.addCapacitySafe(Map.of(effect.reagent(), plan.physicalAmount()),
                        plan.effectiveCapacity());
            } catch (IllegalArgumentException | IllegalStateException invalidTransaction) {
                return EffectResult.FAILED;
            }
            transactionState.commitPositiveAdjustment(plan);
        } else {
            float requestedRemoval = -delta;
            if (!Float.isFinite(requestedRemoval) || requestedRemoval < 0f) {
                return EffectResult.FAILED;
            }
            float removedFromLive = solution.removeUpTo(effect.reagent(), requestedRemoval);
            float remainingRemoval = requestedRemoval - removedFromLive;
            if (!Float.isFinite(remainingRemoval) || remainingRemoval < 0f) {
                return EffectResult.FAILED;
            }
            try {
                transactionState.consumeReservation(effect.reagent(), remainingRemoval);
            } catch (IllegalArgumentException | IllegalStateException invalidTransaction) {
                return EffectResult.FAILED;
            }
        }
        return EffectResult.APPLIED;
    }

    private static EffectResult evenHealthChange(EffectData.EvenHealthChange effect, EffectContext context) {
        if (!(context.entity() instanceof LivingEntity living)) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        return switch (DamageSystem.applyEvenHealthChange(living, effect.damage(), context.scale())) {
            case APPLIED -> EffectResult.APPLIED;
            case SKIPPED_UNSUPPORTED -> EffectResult.SKIPPED_UNSUPPORTED;
            case FAILED -> EffectResult.FAILED;
        };
    }

    private static EffectResult healthChange(EffectData.HealthChange effect, EffectContext context) {
        if (!(context.entity() instanceof LivingEntity living)) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        return switch (DamageSystem.applyHealthChange(living, effect.damage().types(),
                context.scale(), effect.ignoreResistances())) {
            case APPLIED -> EffectResult.APPLIED;
            case SKIPPED_UNSUPPORTED -> EffectResult.SKIPPED_UNSUPPORTED;
            case FAILED -> EffectResult.FAILED;
        };
    }

    private static EffectResult vomit(EffectData.Vomit effect, EffectContext context) {
        return com.juicyslew.moonstation14.ms14.stomach.StomachSystem.vomit(context);
    }

    private static EffectResult modifyStatusEffect(EffectData.ModifyStatusEffect effect, EffectContext context) {
        Optional<com.juicyslew.moonstation14.ms14.TraitHandler<IStatusEffectTrait>> target = statusTarget(context);
        if (target.isEmpty()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }

        DurationConversion duration = effect.time().isPermanent()
                ? DurationConversion.permanent()
                : scaledDuration(effect.time().requireFiniteSeconds(), context.scale());
        if (duration.zero()) {
            return EffectResult.APPLIED;
        }
        int delayTicks = StatusEffectSystem.nonNegativeSecondsToTicks(effect.delay());
        duration = cadenceDuration(duration, context, effect.time().isPermanent(), delayTicks,
                effect.subType());
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> key =
                ModStatusEffects.createKey(effect.effectKey());
        // StatusEffectSystem resolves the runtime prototype before mutation;
        // a missing definition therefore remains a controlled FAILED at the
        // EffectSystem exception boundary.
        StatusEffectSystem.apply(target.orElseThrow(), context.level(), key,
                effect.subType(), duration.ticks(), delayTicks);
        return EffectResult.APPLIED;
    }

    private static EffectResult modifyKnockdown(EffectData.ModifyKnockdown effect, EffectContext context) {
        Optional<com.juicyslew.moonstation14.ms14.TraitHandler<IStatusEffectTrait>> target = statusTarget(context);
        if (target.isEmpty()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }

        DurationConversion duration = effect.time().isPermanent()
                ? DurationConversion.permanent()
                : scaledDuration(effect.time().requireFiniteSeconds(), context.scale());
        // A supported target with an exact scaled zero is an admitted no-op,
        // before checking unavailable physical projections.
        if (duration.zero()) {
            return EffectResult.APPLIED;
        }

        // This milestone owns only the authoritative knockdown status.  It does
        // not pretend to project crawling or item dropping into vanilla state.
        if (effect.subType() != StatusEffectOperation.REMOVE
                && (effect.crawling() || effect.drop())) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }

        int delayTicks = StatusEffectSystem.nonNegativeSecondsToTicks(effect.delay());
        StatusEffectSystem.apply(target.orElseThrow(), context.level(),
                ModStatusEffects.createKey("knockdown"), effect.subType(), duration.ticks(), delayTicks);
        return EffectResult.APPLIED;
    }

    private static EffectResult drunk(EffectData.Drunk effect, EffectContext context) {
        Optional<com.juicyslew.moonstation14.ms14.TraitHandler<IStatusEffectTrait>> target = statusTarget(context);
        if (target.isEmpty()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        DurationConversion duration = scaledDuration(effect.boozePower(), context.scale());
        if (duration.zero()) {
            return EffectResult.APPLIED;
        }
        StatusEffectSystem.apply(target.orElseThrow(), context.level(),
                ModStatusEffects.createKey("statuseffectdrunk"), StatusEffectOperation.ADD,
                duration.ticks(), 0);
        return EffectResult.APPLIED;
    }

    private static EffectResult jitter(EffectData.Jitter effect, EffectContext context) {
        Optional<com.juicyslew.moonstation14.ms14.TraitHandler<IStatusEffectTrait>> target = statusTarget(context);
        if (target.isEmpty()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        DurationConversion duration = scaledDuration(effect.time(), context.scale());
        StatusEffectOperation operation = effect.refresh()
                ? StatusEffectOperation.UPDATE : StatusEffectOperation.ADD;
        duration = cadenceDuration(duration, context, false, 0, operation);
        if (duration.zero()) {
            return EffectResult.APPLIED;
        }

        // Match SharedJitteringSystem's bounds before constructing the
        // validated closed payload type. NaN/infinite source values fail
        // deterministically rather than entering attachment state.
        float amplitude = clamp(effect.amplitude(), StatusEffectPayload.Jitter.MIN_AMPLITUDE,
                StatusEffectPayload.Jitter.MAX_AMPLITUDE);
        float frequency = clamp(effect.frequency(), StatusEffectPayload.Jitter.MIN_FREQUENCY,
                StatusEffectPayload.Jitter.MAX_FREQUENCY);
        StatusEffectPayload payload = new StatusEffectPayload.Jitter(amplitude, frequency);
        StatusEffectSystem.apply(target.orElseThrow(), context.level(), ModStatusEffects.createKey("jitter"),
                operation, duration.ticks(), 0, payload);
        return EffectResult.APPLIED;
    }

    private static EffectResult movementSpeedModifier(EffectData.MovementSpeedModifier effect,
                                                       EffectContext context) {
        if (!MovementSpeedCompatibility.supports(effect.walkSpeedModifier(), effect.sprintSpeedModifier())) {
            MovementSpeedCompatibility.warnUnequalOnce(effect.walkSpeedModifier(), effect.sprintSpeedModifier());
            return EffectResult.SKIPPED_UNSUPPORTED;
        }

        Optional<com.juicyslew.moonstation14.ms14.TraitHandler<IStatusEffectTrait>> target = statusTarget(context);
        if (target.isEmpty() || !(context.entity() instanceof LivingEntity living)
                || living.getAttribute(Attributes.MOVEMENT_SPEED) == null) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }

        DurationConversion duration = effect.time().isPermanent()
                ? DurationConversion.permanent()
                : scaledDuration(effect.time().requireFiniteSeconds(), context.scale());
        if (duration.zero()) {
            return EffectResult.APPLIED;
        }
        int delayTicks = StatusEffectSystem.nonNegativeSecondsToTicks(effect.delay());
        duration = cadenceDuration(duration, context, effect.time().isPermanent(), delayTicks,
                effect.subType());

        ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> key =
                ModStatusEffects.createKey(effect.effectProto());
        StatusEffectPayload payload = effect.subType() == StatusEffectOperation.UPDATE
                || effect.subType() == StatusEffectOperation.ADD
                ? new StatusEffectPayload.MovementSpeedModifier(effect.walkSpeedModifier())
                : StatusEffectPayload.none();
        StatusEffectSystem.apply(target.orElseThrow(), context.level(), key, effect.subType(), duration.ticks(),
                delayTicks, payload);
        return EffectResult.APPLIED;
    }

    static DurationConversion cadenceDuration(DurationConversion duration, EffectContext context,
                                                       boolean permanent, int delayTicks,
                                                       StatusEffectOperation operation) {
        Optional<ReagentEffectContext> reagent = context.reagentContext();
        OptionalInt adjusted = StatusDurationCadence.floor(duration.ticks(),
                reagent.filter(ReagentEffectContext::hasOngoingReservoir).isPresent(), context.cause(),
                reagent.isPresent(), !permanent, delayTicks, operation);
        return adjusted.equals(duration.ticks()) ? duration : new DurationConversion(adjusted, false);
    }

    static Optional<com.juicyslew.moonstation14.ms14.TraitHandler<IStatusEffectTrait>> statusTarget(
            EffectContext context) {
        if (!(context.level() instanceof ServerLevel)
                || !(context.entity() instanceof LivingEntity living)
                || !(living instanceof IStatusEffectTrait statusEffectHolder)) {
            return Optional.empty();
        }
        return Optional.of(statusEffectHolder.toHandleSelf());
    }

    /** A finite scaled duration, or an exact-zero no-op marker. */
    record DurationConversion(OptionalInt ticks, boolean zero) {
        DurationConversion {
            java.util.Objects.requireNonNull(ticks, "ticks");
            if (zero && ticks.isPresent()) {
                throw new IllegalArgumentException("zero duration conversion must carry no ticks");
            }
        }

        static DurationConversion permanent() {
            return new DurationConversion(OptionalInt.empty(), false);
        }
    }

    static DurationConversion scaledDuration(float seconds, float scale) {
        if (!Float.isFinite(seconds) || seconds < 0f) {
            throw new IllegalArgumentException("duration must be finite and nonnegative");
        }
        if (!Float.isFinite(scale) || scale < 0f) {
            throw new IllegalArgumentException("scale must be finite and nonnegative");
        }
        float scaledSeconds = seconds * scale;
        if (!Float.isFinite(scaledSeconds) || scaledSeconds < 0f) {
            throw new IllegalArgumentException("scaled duration must be finite and nonnegative");
        }
        if (scaledSeconds == 0f) {
            return new DurationConversion(OptionalInt.empty(), true);
        }
        return new DurationConversion(OptionalInt.of(StatusEffectSystem.secondsToTicks(scaledSeconds)), false);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
