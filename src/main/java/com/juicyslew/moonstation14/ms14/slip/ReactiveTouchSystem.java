package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.ReactiveEffectsData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;

/** Pinned SS14 puddle Touch behavior, downstream of an admitted SlipEvent only. */
public final class ReactiveTouchSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveTouchSystem.class);
    private static final float TOUCH_CHANCE = 0.5f;
    private static final float TOUCH_FRACTION = 0.15f;
    private static final Map<ServerLevel, Set<DispatchKey>> DISPATCHED = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<DispatchKey>> IN_PROGRESS = new WeakHashMap<>();
    private static final Map<TestRandomKey, RandomSource> TEST_RANDOMS = new ConcurrentHashMap<>();
    private static boolean registered;
    private static final EffectSystem EFFECTS = EffectSystem.withDefaults();

    private ReactiveTouchSystem() { }

    /** Register the production listener once; never owned by a GameTest fixture. */
    public static synchronized void registerOnce() {
        if (registered) return;
        if (SlipSystem.addListener(ReactiveTouchSystem::onSlip)) registered = true;
        else throw new IllegalStateException("Could not register reactive puddle listener");
    }

    private static void onSlip(SlipEvent event) {
        RandomSource random = TEST_RANDOMS.get(new TestRandomKey(event.level(),
                event.sourcePosition().immutable(), event.target().getUUID()));
        handle(event, random == null ? event.level().getRandom() : random);
    }

    /** Install a test RNG for exactly one level/source/target listener dispatch. */
    public static void registerTestRandom(ServerLevel level, BlockPos source, java.util.UUID target,
                                          RandomSource random) {
        if (level == null || source == null || target == null || random == null)
            throw new IllegalArgumentException("Reactive Touch test RNG scope must be complete");
        TestRandomKey key = new TestRandomKey(level, source.immutable(), target);
        if (TEST_RANDOMS.putIfAbsent(key, random) != null)
            throw new IllegalStateException("Reactive Touch test RNG scope already registered");
    }

    /** Remove the exact test RNG scope; production dispatch always falls back to level.random. */
    public static boolean unregisterTestRandom(ServerLevel level, BlockPos source, java.util.UUID target) {
        if (level == null || source == null || target == null) return false;
        return TEST_RANDOMS.remove(new TestRandomKey(level, source.immutable(), target)) != null;
    }

    /** Injectable server RNG seam used by production and deterministic system-level fixtures. */
    public static void handle(SlipEvent event, RandomSource random) {
        if (event == null || random == null || event.wasSliding()) return;
        ServerLevel level = event.level();
        LivingEntity target = event.target();
        if (level.isClientSide || target.level() != level || !target.isAlive()) return;
        CharacterData character = ActiveCharacterPolicy.resolveActor(target).orElse(null);
        if (character == null || !admitsTouch(character.slipData())) return;
        DispatchKey dispatchKey = new DispatchKey(event.sourcePosition().immutable(), target.getUUID(), level.getGameTime());
        synchronized (DISPATCHED) {
            Set<DispatchKey> dispatched = DISPATCHED.computeIfAbsent(level, ignored -> new HashSet<>());
            dispatched.removeIf(key -> key.tick < level.getGameTime());
            if (!dispatched.add(dispatchKey)) return;
            IN_PROGRESS.computeIfAbsent(level, ignored -> new HashSet<>()).add(dispatchKey);
        }
        try {
            if (!chanceAccepted(random.nextFloat())) return;
            PuddleBlockEntity puddle = event.source();
            ReagentAttachment staged = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
            Map<ResourceKey<ReagentData>, Long> source = staged.snapshotUnits();
            Map<ResourceKey<ReagentData>, Float> validationSnapshot = new java.util.LinkedHashMap<>();
            source.forEach((key, cents) -> validationSnapshot.put(key, ReagentUnits.toFloat(cents)));
            if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(validationSnapshot, level,
                    "puddle reactive touch " + event.sourcePosition())) return;
            long total = ReagentUnits.total(source.values());
            if (total == 0) return;
            long request = touchRequestCents(total);
            if (request <= 0) return;

            List<Reaction> reactions = new ArrayList<>();
            for (Map.Entry<ResourceKey<ReagentData>, Long> entry : source.entrySet()) {
                ReagentData reagent = PrototypeRuntime.serverReagents().get(entry.getKey().location());
                if (reagent == null || entry.getValue() <= 0) return;
                for (Map.Entry<String, ReactiveEffectsData> configured : reagent.reactiveEffects().entrySet()) {
                    CharacterData.ReactiveGroup group = group(configured.getKey());
                    ReactiveEffectsData touch = configured.getValue();
                    if (group == null || !touch.methods().contains("touch")
                            || !character.slipData().reactiveGroups().contains(group)) continue;
                     if (touch.effects().stream().anyMatch(effect -> !supportsTouchPayload(effect))) return;
                    reactions.add(new Reaction(entry.getKey(), touch.effects()));
                }
            }

            // Keep the split-before-effects boundary: callbacks cannot roll back chemistry.
            var before = MS14Provider.snapshot(staged);
            Map<ResourceKey<ReagentData>, Long> removed = staged.splitUnits(request);
            if (!MS14Provider.updateIfChanged(puddle, MS14Bridges.REAGENT, before, staged)) return;
            Map<ResourceKey<ReagentData>, Float> quantities = new java.util.LinkedHashMap<>();
            removed.forEach((key, cents) -> { if (cents > 0) quantities.put(key, ReagentUnits.toFloat(cents)); });
            ConditionContext conditions = ConditionContext.builder().sourceReagentQuantities(quantities).build();
            for (Reaction reaction : reactions) {
                long dose = removed.getOrDefault(reaction.reagent, 0L);
                if (dose <= 0) continue;
                EffectContext context = new EffectContext(level, target, ReagentUnits.toFloat(dose), random,
                        conditions, EffectCause.CONTACT);
                for (EffectData effect : reaction.effects) {
                    try {
                        EffectResult result = EFFECTS.apply(effect, context);
                        if (result == EffectResult.FAILED || result == EffectResult.SKIPPED_UNSUPPORTED)
                            LOGGER.error("Reactive Touch effect {} returned {} after dose split for {}",
                                    effect.type(), result, target.getUUID());
                    } catch (RuntimeException exception) {
                        LOGGER.error("Reactive Touch callback failed after dose split for {}", target.getUUID(), exception);
                    }
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Reactive puddle Touch failed for {} at {}", target.getUUID(), event.sourcePosition(), exception);
        } finally {
            synchronized (DISPATCHED) {
                Set<DispatchKey> active = IN_PROGRESS.get(level);
                if (active != null) {
                    active.remove(dispatchKey);
                    if (active.isEmpty()) IN_PROGRESS.remove(level);
                }
            }
        }
    }

    public static long touchRequestCents(long totalCents) {
        ReagentUnits.validateCents(totalCents, "totalCents");
        // Pinned FixedPoint2 multiplication adds 0.00001f before truncating raw cents.
        return (long) Math.floor(totalCents * TOUCH_FRACTION + 0.00001f);
    }

    public static boolean chanceAccepted(float sample) {
        return Float.isFinite(sample) && sample >= 0f && sample < TOUCH_CHANCE;
    }

    private static boolean admitsTouch(CharacterData.SlipTargetData slip) {
        return slip.reactiveMethods().contains(CharacterData.ReactiveMethod.TOUCH)
                && !slip.reactiveGroups().isEmpty();
    }

    private static CharacterData.ReactiveGroup group(String name) {
        for (CharacterData.ReactiveGroup group : CharacterData.ReactiveGroup.values())
            if (group.serialized().equals(name)) return group;
        return null;
    }

    /** Unsupported payloads are rejected before splitting; target-dependent failures remain non-atomic. */
    public static boolean supportsTouchPayload(EffectData effect) {
        if (!EFFECTS.supportsHandler(effect)) return false;
        if (effect instanceof EffectData.ModifyKnockdown knockdown
                && knockdown.subType() != com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation.REMOVE
                && (knockdown.crawling() || knockdown.drop())
                // Handler admits exact scaled zero as a no-op; conservative preflight rejects other flagged cases.
                && (knockdown.time().isPermanent() || knockdown.time().finiteSeconds() != 0f)) return false;
        return true;
    }

    private record Reaction(ResourceKey<ReagentData> reagent, List<EffectData> effects) { }
    private record DispatchKey(BlockPos source, java.util.UUID target, long tick) { }
    private record TestRandomKey(ServerLevel level, BlockPos source, java.util.UUID target) { }
}
