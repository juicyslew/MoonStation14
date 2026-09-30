package com.juicyslew.moonstation14.ms14.stomach;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.thirst.ThirstSystem;
import com.juicyslew.moonstation14.ms14.character.components.StomachPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.hunger.HungerSystem;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Server-authoritative ingestion into an explicitly eligible character's stomach. */
public final class StomachSystem {
    public static final float CAPACITY = 50f;
    private static final long CAPACITY_CENTS = 5_000L;

    private StomachSystem() { }

    public static boolean isEligible(LivingEntity entity) {
        return ActiveCharacterPolicy.resolveActor(entity)
                .filter(character -> character.component(BloodstreamComponent.class).isPresent())
                .flatMap(character -> character.component(StomachPrototypeComponent.class)).isPresent();
    }

    /**
     * Moves an actual accepted dose from the source to the character stomach.
     * A missing/unsupported/full destination is a no-op and never materializes
     * an empty attachment. Digestion is handled later by the metabolism tick;
     * ingestion itself performs no reactive-touch dispatch.
     */
    public static float ingest(TraitHandler<IReagentTrait> source, LivingEntity target,
                               ServerLevel level, float requested) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(level, "level");
        if (!Float.isFinite(requested) || requested <= 0f || target.level().isClientSide
                || level != target.level() || !isEligible(target)) return 0f;

        Object sourceHolder = source.holder();
        var sourceBridge = ReagentSystem.bridgeForHolder(sourceHolder);
        if (sourceBridge == null) return 0f;
        ReagentAttachment sourceState = MS14Provider.getDetached(sourceHolder, sourceBridge);
        if (!com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation
                .hasOnlyKnownPositiveReagents(sourceState.getMap(), level,
                        "ingestion source " + sourceHolder.getClass().getSimpleName())) return 0f;
        ReagentAttachment stomach = MS14Provider.getDetached(target, MS14Bridges.STOMACH);
        if (!com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation
                .hasOnlyKnownPositiveReagents(stomach.getMap(), level,
                        "ingestion destination stomach")) return 0f;
        ReagentComponent sourceBefore = MS14Provider.snapshot(sourceState);
        ReagentComponent stomachBefore = MS14Provider.snapshot(stomach);
        Map<ResourceKey<ReagentData>, Long> sourceCents;
        Map<ResourceKey<ReagentData>, Long> stomachCents;
        Map<ResourceKey<ReagentData>, Long> nextSource = new LinkedHashMap<>();
        Map<ResourceKey<ReagentData>, Long> nextStomach = new LinkedHashMap<>();
        long acceptedCents;
        try {
            sourceCents = ReagentUnits.fromMap(sourceState.getMap());
            stomachCents = ReagentUnits.fromMap(stomach.getMap());
            long sourceTotal = ReagentUnits.total(sourceCents.values());
            long stomachTotal = ReagentUnits.total(stomachCents.values());
            if (sourceTotal == 0 || stomachTotal >= CAPACITY_CENTS) return 0f;
            long requestedCents = ReagentUnits.fromFloat(requested);
            long freeStomachUnits = CAPACITY_CENTS - stomachTotal;
            acceptedCents = ReagentUnits.admit(requestedCents, Math.min(sourceTotal, freeStomachUnits));
            if (acceptedCents < 1L) return 0f;

            Map<ResourceKey<ReagentData>, Long> removed = ReagentUnits.split(sourceCents, acceptedCents);
            for (Map.Entry<ResourceKey<ReagentData>, Long> entry : sourceCents.entrySet()) {
                long amount = removed.getOrDefault(entry.getKey(), 0L);
                long remaining = Math.subtractExact(entry.getValue(), amount);
                if (remaining > 0L) nextSource.put(entry.getKey(), remaining);
                long combined = Math.addExact(stomachCents.getOrDefault(entry.getKey(), 0L), amount);
                if (combined > 0L) nextStomach.put(entry.getKey(), combined);
            }
            for (Map.Entry<ResourceKey<ReagentData>, Long> entry : stomachCents.entrySet()) {
                if (!sourceCents.containsKey(entry.getKey()) && entry.getValue() > 0L)
                    nextStomach.put(entry.getKey(), entry.getValue());
            }
            if (ReagentUnits.total(nextStomach.values()) > CAPACITY_CENTS
                    || ReagentUnits.total(nextSource.values()) > ReagentUnits.MAX_CENTS)
                return 0f;
        } catch (IllegalArgumentException | ArithmeticException invalidAmounts) {
            return 0f;
        }

        ReagentAttachment stagedSource = new ReagentAttachment(toFloatMap(nextSource));
        ReagentAttachment stagedStomach = new ReagentAttachment(toFloatMap(nextStomach));
        // Refuse any conversion that fails to preserve the integer-cent transaction snapshot.
        if (!ReagentUnits.fromMap(stagedSource.getMap()).equals(nextSource)
                || !ReagentUnits.fromMap(stagedStomach.getMap()).equals(nextStomach)) return 0f;
        MS14Provider.updateIfChanged(sourceHolder, sourceBridge, sourceBefore, stagedSource);
        MS14Provider.updateIfChanged(target, MS14Bridges.STOMACH, stomachBefore, stagedStomach);
        EntityActivitySystem.update(target, EntityActivity.REAGENT_METABOLISM, true);
        return ReagentUnits.toFloat(acceptedCents);
    }

    /**
     * Applies the bounded stomach-owned portion of upstream Vomit. Eligibility
     * follows only the stomach component; other needs are independent.
     */
    public static EffectResult vomit(EffectContext context) {
        if (!(context.entity() instanceof LivingEntity target) || !isEligible(target)
                || target.isDeadOrDying()) return EffectResult.SKIPPED_UNSUPPORTED;

        boolean digestionSource = context.reagentContext().isPresent()
                && context.reagentContext().orElseThrow().stage() == MetabolismStage.DIGESTION;
        ReagentAttachment source;
        ReagentComponent before = null;
        Object holder;
        com.juicyslew.moonstation14.util.SystemLink<ReagentAttachment, ReagentComponent> bridge;
        if (digestionSource) {
            // This must remain the exact live transaction working solution; TickHooks commits it.
            source = context.reagentContext().orElseThrow().solution();
            holder = null;
            bridge = null;
        } else {
            if (!target.hasData(ModDataAttachments.STOMACH.get())) source = new ReagentAttachment();
            else {
                source = MS14Provider.getDetached(target, MS14Bridges.STOMACH);
                before = MS14Provider.snapshot(source);
            }
            holder = target;
            bridge = MS14Bridges.STOMACH;
        }

        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(source.getMap(), context.level(),
                digestionSource ? "vomit digestion solution" : "vomit stomach")) return EffectResult.FAILED;

        ReagentAttachment spill = new ReagentAttachment(source.getMap());
        source.clear();
        if (before != null) MS14Provider.updateIfChanged(holder, bridge, before, source);

        // Upstream applies these penalties even when the stomach was already empty.
        boolean needsApplied = HungerSystem.satiateIfInitialized(target, -40f, 1f);
        boolean thirstApplied = true;
        if (target.hasData(ModDataAttachments.THIRST.get())) {
            ThirstSystem.Outcome outcome = ThirstSystem.satiate(target, context.level(), -40f, 1f);
            thirstApplied = outcome != ThirstSystem.Outcome.FAILED;
            if (!thirstApplied)
                MoonStation14.LOGGER.warn("Vomit thirst penalty failed for {}; stomach remains emptied", target.getUUID());
        }

        boolean slowdownApplied = true;
        if (target instanceof IStatusEffectTrait statusTrait) {
            // Upstream derives duration from both -40 need penalties: 80 / 6 seconds.
            // UPDATE uses the status reducer's maximum-duration behavior, so a repeated
            // vomit refreshes without shortening an already longer slowdown.
            int durationTicks = StatusEffectSystem.nonNegativeSecondsToTicks(80f / 6f);
            StatusEffectSystem.apply(statusTrait.toHandleSelf(), context.level(),
                    ModStatusEffects.createKey("vomiting_slowdown"), StatusEffectOperation.UPDATE,
                    java.util.OptionalInt.of(durationTicks), 0);
        } else {
            slowdownApplied = false;
            MoonStation14.LOGGER.warn("Vomit slowdown unavailable for {}: target has no status-effect capability",
                    target.getUUID());
        }
        if (!needsApplied)
            MoonStation14.LOGGER.warn("Vomit hunger penalty failed for {}; stomach remains emptied", target.getUUID());

        // Source emptying precedes spill in upstream. Placement failure therefore loses this
        // detached spill solution; do not claim atomic egress or invent a blood flush/reagent.
        ReagentSystem.handleSpillSolution(spill, context.level(), target.getOnPos());
        return needsApplied && thirstApplied && slowdownApplied ? EffectResult.APPLIED : EffectResult.FAILED;
    }

    private static Map<ResourceKey<ReagentData>, Float> toFloatMap(Map<ResourceKey<ReagentData>, Long> cents) {
        Map<ResourceKey<ReagentData>, Float> result = new LinkedHashMap<>();
        cents.forEach((key, amount) -> result.put(key, ReagentUnits.toFloat(amount)));
        return result;
    }
}
