package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Central common gate and typed effect execution system. */
public final class EffectSystem {
    private final EffectDispatcher dispatcher;

    public EffectSystem(EffectDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public static EffectSystem withDefaults() {
        EffectDispatcher dispatcher = new EffectDispatcher();
        EffectHandlers.registerDefaults(dispatcher);
        return new EffectSystem(dispatcher);
    }

    public EffectResult apply(EffectData effect, EffectContext context) {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(context, "context");

        try {
            if (context.entity() instanceof net.minecraft.world.entity.LivingEntity living) {
                context = context.withConditionContext(context.conditionContext().withHungerIfUnavailable(
                        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.read(living)));
            }
            GateDecision decision = evaluateGates(
                    effect, context.scale(), context.random(), context.conditionContext());
            if (!decision.admitted()) {
                return decision.result();
            }
            return dispatcher.dispatch(effect, context.withScale(decision.scale()));
        } catch (RuntimeException exception) {
            MoonStation14.LOGGER.error("Effect {} failed for {} (cause={}, scale={})",
                    effect.type(), context.entity().getUUID(), context.cause(), context.scale(), exception);
            return EffectResult.FAILED;
        }
    }

    /** Read-only handler-registration query; does not evaluate gates or invoke a handler. */
    public boolean supportsHandler(EffectData effect) {
        return dispatcher.supportsHandler(effect);
    }

    public List<EffectResult> applyAll(Iterable<? extends EffectData> effects, EffectContext context) {
        Objects.requireNonNull(context, "context");
        return applyEach(effects, effect -> apply(effect, context));
    }

    static List<EffectResult> applyEach(Iterable<? extends EffectData> effects,
                                          Function<EffectData, EffectResult> application) {
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(application, "application");
        List<EffectResult> results = new ArrayList<>();
        for (EffectData effect : effects) {
            results.add(application.apply(effect));
        }
        return results;
    }

    /** Pure common gate decision; it has no world or entity requirement. */
    public static GateDecision evaluateGates(EffectData effect, float inputScale,
                                             RandomSource random, ConditionContext conditions) {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(random, "random");
        if (inputScale < effect.minScale()) {
            return new GateDecision(EffectResult.SKIPPED_SCALE, inputScale, false);
        }

        float sample = random.nextFloat();
        if (sample >= effect.probability()) {
            return new GateDecision(EffectResult.SKIPPED_PROBABILITY, inputScale, true);
        }
        if (!ConditionSystem.allPass(effect.conditions(), conditions)) {
            return new GateDecision(EffectResult.SKIPPED_CONDITION, inputScale, true);
        }

        float adjustedScale = effect.scaling() ? inputScale : Math.min(inputScale, 1f);
        return new GateDecision(EffectResult.APPLIED, adjustedScale, true);
    }

    public static GateDecision gate(EffectData effect, float inputScale,
                                    RandomSource random, ConditionContext conditions) {
        return evaluateGates(effect, inputScale, random, conditions);
    }

    public record GateDecision(EffectResult result, float scale, boolean randomConsumed) {
        public boolean admitted() {
            return result == EffectResult.APPLIED;
        }
    }
}
