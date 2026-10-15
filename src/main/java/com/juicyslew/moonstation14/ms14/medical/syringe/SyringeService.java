package com.juicyslew.moonstation14.ms14.medical.syringe;

import com.juicyslew.moonstation14.ms14.medical.InjectorModeData;
import com.juicyslew.moonstation14.ms14.medical.MedicalItemData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.components.ReactiveComponent;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.medical.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.item.custom.SyringeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Syringe dose admission and deterministic exact-cent mixture splitting. */
public final class SyringeService {
    private SyringeService() { }

    private static final String KIND = "syringe-body";

    public static BodyHandActionResult.Reason useBody(ServerPlayer actor, String handId, String token,
                                                       long revision, long sequence, UUID targetId) {
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) return BodyHandActionResult.Reason.NO_AUTHORITY;
        LivingEntity body = authority.body();
        BooleanSupplier auth = () -> HandActorAuthority.revalidate(actor, authority);
        if (!intent(body, actor, handId, token, revision, auth)) return BodyHandActionResult.Reason.DENIED;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack held = hands.stackCopy(handId).orElse(ItemStack.EMPTY);
        if (!(held.getItem() instanceof SyringeItem syringe) || held.getCount() != 1) return BodyHandActionResult.Reason.DENIED;
        MedicalItemData medical;
        try { medical = ModMedicalItems.require(ModMedicalItems.catalog(body.level()), SyringeItem.PROTOTYPE_ID); }
        catch (RuntimeException missing) { return BodyHandActionResult.Reason.DENIED; }
        var policy = medical.syringePolicy().orElse(null);
        if (policy == null) return BodyHandActionResult.Reason.DENIED;
        var stackData = syringe.dataOrInitial(held, body.level());
        var mode = PrototypeRuntime.serverInjectorModes().get(stackData.mode());
        if (mode == null || !policy.allowedModes().contains(stackData.mode())
                || !mode.transferAmounts().contains(stackData.transferAmountUnits())) return BodyHandActionResult.Reason.DENIED;
        LivingEntity target = targetId == null || targetId.equals(body.getUUID()) ? body
                : body.level() instanceof ServerLevel level && level.getEntity(targetId) instanceof LivingEntity found ? found : null;
        if (target == null || target.level() != body.level() || target.isRemoved() || !target.isAddedToLevel()
                || !(target.level() instanceof ServerLevel level) || level.getEntity(target.getUUID()) != target
                || !inReach(body, target) || !CharacterControlSystem.canAct(body)) return BodyHandActionResult.Reason.DENIED;
        if (policy.ignoreMobs()) return BodyHandActionResult.Reason.DENIED;
        var bloodstream = BloodstreamSolutionAccess.inspect(target).orElse(null);
        if (bloodstream == null) return BodyHandActionResult.Reason.DENIED;
        ReagentAttachment syringeContents = MS14Provider.getDetached(held, MS14Bridges.REAGENT);
        Map<ResourceKey<ReagentData>, Long> syringeBefore = syringeContents.snapshotUnits();
        long syringeTotal = ReagentUnits.total(syringeBefore.values());
        long sourceTotal = mode.kind() == InjectorModeData.Kind.DRAW
                ? ReagentUnits.total(bloodstream.units().values()) : syringeTotal;
        long free = mode.kind() == InjectorModeData.Kind.DRAW
                ? Math.max(0, ReagentUnits.fromFloat(policy.capacityUnits()) - syringeTotal)
                : Math.max(0, bloodstream.capacityCents() - ReagentUnits.total(bloodstream.units().values()));
        Admission admitted = admit(policy, mode, stackData.transferAmountUnits(), sourceTotal, free);
        if (admitted == null) return BodyHandActionResult.Reason.DENIED;
        var posture = targetPosture(body, target);
        int ticks = SyringeTiming.ticks(mode, ReagentUnits.toFloat(admitted.doseCents()), false, posture);
        String sourceKey = "hand:" + token + ":target:" + target.getUUID();
        if (TimedInteractionSystem.cancelDuplicate(body, KIND, sourceKey)) {
            BodyHandStateService.requireFreshQuery(actor);
            return BodyHandActionResult.Reason.OK;
        }
        AtomicLong expectedRevision = new AtomicLong(revision);
        LivingEntity fixedTarget = target;
        var start = TimedInteractionSystem.start(body, actor, sequence, KIND, sourceKey, ticks, true, true,
                1.0, policy.movementThreshold(), () -> ticks,
                () -> intent(body, actor, handId, token, expectedRevision.get(), auth)
                        && fixedTarget.level() == body.level() && !fixedTarget.isRemoved()
                        && fixedTarget.getUUID().equals(targetId == null ? body.getUUID() : targetId)
                        && inReach(body, fixedTarget)
                        && BloodstreamSolutionAccess.inspect(fixedTarget).isPresent(),
                () -> complete(actor, authority, body, fixedTarget, handId, token, auth, expectedRevision,
                        hands, held, policy, stackData, mode, ReagentUnits.fromFloat(stackData.transferAmountUnits())));
        if (start == TimedInteractionEngine.StartResult.STARTED || start == TimedInteractionEngine.StartResult.DUPLICATE_CANCELLED) {
            BodyHandStateService.requireFreshQuery(actor);
            return BodyHandActionResult.Reason.OK;
        }
        return BodyHandActionResult.Reason.DENIED;
    }

    private static TimedInteractionSystem.Completion complete(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LivingEntity body, LivingEntity target, String hand, String token, BooleanSupplier auth,
            AtomicLong expectedRevision, LiveHands hands, ItemStack original, MedicalItemData.SyringePolicy policy,
            SyringeStackData data, InjectorModeData mode, long selectedDose) {
        if (!intent(body, actor, hand, token, expectedRevision.get(), auth) || target.isRemoved()
                || target.level() != body.level()) return TimedInteractionSystem.Completion.STOP;
        LiveHands currentHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack current = currentHands.stackCopy(hand).orElse(ItemStack.EMPTY);
        if (currentHands != hands || !ItemStack.isSameItemSameComponents(current, original)
                || current.getCount() != 1 || !data.equals(((SyringeItem) current.getItem()).dataOrInitial(current, body.level()))) return TimedInteractionSystem.Completion.STOP;
        var patientBefore = BloodstreamSolutionAccess.inspect(target).orElse(null);
        if (patientBefore == null) return TimedInteractionSystem.Completion.STOP;
        ReagentAttachment source = MS14Provider.getDetached(current, MS14Bridges.REAGENT);
        Map<ResourceKey<ReagentData>, Long> before = source.snapshotUnits();
        TransferPlan plan = planTransfer(mode.kind(), before, patientBefore.units(), selectedDose,
                ReagentUnits.fromFloat(policy.capacityUnits()), patientBefore.capacityCents());
        if (plan == null) return TimedInteractionSystem.Completion.STOP;
        Map<ResourceKey<ReagentData>, Long> dose = plan.dose();
        Map<ResourceKey<ReagentData>, Long> patientAfter = plan.patientAfter();
        Map<ResourceKey<ReagentData>, Long> syringeAfter = plan.syringeAfter();
        var stagedPatient = BloodstreamSolutionAccess.previewStoreChange(patientBefore, patientAfter).orElse(null);
        ItemStack replacement = current.copy();
        replacement.set(ModDataComponents.REAGENT.get(), new ReagentAttachment(asFloats(syringeAfter)).toComponent());
        if (!SyringeTransferModeTransition.apply(replacement, body.level())) return TimedInteractionSystem.Completion.STOP;
        if (stagedPatient == null) return TimedInteractionSystem.Completion.STOP;
        if (mode.kind() == InjectorModeData.Kind.DRAW
                && !BloodstreamSolutionAccess.commit(patientBefore, stagedPatient)) return TimedInteractionSystem.Completion.STOP;
        BodyHeldItemMutation.Result itemCommit = BodyHeldItemMutation.commit(actor, authority, currentHands, hand,
                token, currentHands.revision(), current, replacement);
        if (itemCommit != BodyHeldItemMutation.Result.SUCCESS) {
            if (mode.kind() == InjectorModeData.Kind.DRAW) {
                var changed = BloodstreamSolutionAccess.inspect(target).orElse(null);
                var restore = changed != null && changed.units().equals(patientAfter)
                        ? BloodstreamSolutionAccess.previewStoreChange(changed, patientBefore.units()).orElse(null) : null;
                boolean restored = restore != null && BloodstreamSolutionAccess.commit(changed, restore);
                return !restored || itemCommit == BodyHeldItemMutation.Result.RECOVERY_REQUIRED
                        ? TimedInteractionSystem.Completion.RECOVERY_REQUIRED : TimedInteractionSystem.Completion.STOP;
            }
            return itemCommit == BodyHeldItemMutation.Result.RECOVERY_REQUIRED
                    ? TimedInteractionSystem.Completion.RECOVERY_REQUIRED : TimedInteractionSystem.Completion.STOP;
        }
        LiveHands postHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (postHands == null || !auth.getAsBoolean()) return TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
        expectedRevision.set(postHands.revision());
        if (mode.kind() == InjectorModeData.Kind.INJECT) {
            ReactiveComponent reactive = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem
                    .resolveForActor(target).flatMap(c -> c.component(ReactiveComponent.class)).orElse(null);
            InjectionReactionSystem.dispatch((ServerLevel) target.level(), target, reactive, dose, target.getRandom());
        }
        if (mode.kind() == InjectorModeData.Kind.INJECT && !BloodstreamSolutionAccess.commit(patientBefore, stagedPatient)) {
            // Restore only while the hand still contains precisely our staged syringe state.
            ItemStack now = postHands.stackCopy(hand).orElse(ItemStack.EMPTY);
            if (postHands.revision() == expectedRevision.get() && ItemStack.isSameItemSameComponents(now, replacement)) {
                BodyHeldItemMutation.Result restored = BodyHeldItemMutation.commit(actor, authority, postHands,
                        hand, token, postHands.revision(), replacement, current);
                return restored == BodyHeldItemMutation.Result.SUCCESS
                        ? TimedInteractionSystem.Completion.STOP : TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
            }
            return TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
        }
        BodyHandStateService.requireFreshQuery(actor);
        return TimedInteractionSystem.Completion.STOP;
    }

    public record TransferPlan(long doseCents, Map<ResourceKey<ReagentData>, Long> dose,
                               Map<ResourceKey<ReagentData>, Long> syringeAfter,
                               Map<ResourceKey<ReagentData>, Long> patientAfter) {
        public TransferPlan { dose = Map.copyOf(dose); syringeAfter = Map.copyOf(syringeAfter); patientAfter = Map.copyOf(patientAfter); }
    }

    /** Completion-time transfer plan; reclamps to the source mixture and current destination headroom. */
    public static TransferPlan planTransfer(InjectorModeData.Kind kind,
            Map<ResourceKey<ReagentData>, Long> syringe, Map<ResourceKey<ReagentData>, Long> patient,
            long selectedCents, long syringeCapacityCents, long patientCapacityCents) {
        if (kind == null || syringe == null || patient == null || selectedCents <= 0) return null;
        try {
            long syringeTotal = ReagentUnits.total(syringe.values());
            long patientTotal = ReagentUnits.total(patient.values());
            long source = kind == InjectorModeData.Kind.DRAW ? patientTotal : syringeTotal;
            long free = kind == InjectorModeData.Kind.DRAW ? syringeCapacityCents - syringeTotal
                    : patientCapacityCents - patientTotal;
            long amount = Math.min(selectedCents, Math.min(source, Math.max(0, free)));
            if (amount <= 0) return null;
            Map<ResourceKey<ReagentData>, Long> dose = proportionalDose(kind == InjectorModeData.Kind.DRAW ? patient : syringe, amount);
            if (ReagentUnits.total(dose.values()) != amount) return null;
            Map<ResourceKey<ReagentData>, Long> syringeAfter = new java.util.LinkedHashMap<>(syringe);
            Map<ResourceKey<ReagentData>, Long> patientAfter = new java.util.LinkedHashMap<>(patient);
            if (kind == InjectorModeData.Kind.DRAW) {
                dose.forEach((key, value) -> { subtract(patientAfter, key, value); syringeAfter.merge(key, value, Math::addExact); });
            } else {
                dose.forEach((key, value) -> { subtract(syringeAfter, key, value); patientAfter.merge(key, value, Math::addExact); });
            }
            if (ReagentUnits.total(syringeAfter.values()) + ReagentUnits.total(patientAfter.values())
                    != syringeTotal + patientTotal) return null;
            return new TransferPlan(amount, dose, syringeAfter, patientAfter);
        } catch (RuntimeException invalid) { return null; }
    }

    public static SyringeTiming.TargetPosture targetPosture(LivingEntity actor, LivingEntity target) {
        boolean downed = com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(target)
                || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isDead(target)
                || com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDownPosture(target);
        return resolvePosture(actor == target, downed);
    }

    public static SyringeTiming.TargetPosture resolvePosture(boolean self, boolean downed) {
        return self ? SyringeTiming.TargetPosture.SELF
                : downed ? SyringeTiming.TargetPosture.DOWNED : SyringeTiming.TargetPosture.STANDING;
    }

    private static void subtract(Map<ResourceKey<ReagentData>, Long> map, ResourceKey<ReagentData> key, long amount) {
        long left = map.getOrDefault(key, 0L) - amount;
        if (left < 0) throw new IllegalStateException("syringe mixture changed");
        if (left == 0) map.remove(key); else map.put(key, left);
    }
    private static Map<ResourceKey<ReagentData>, Float> asFloats(Map<ResourceKey<ReagentData>, Long> values) {
        Map<ResourceKey<ReagentData>, Float> result = new java.util.LinkedHashMap<>();
        values.forEach((key, amount) -> result.put(key, ReagentUnits.toFloat(amount)));
        return result;
    }
    private static boolean intent(LivingEntity body, ServerPlayer actor, String hand, String token, long revision, BooleanSupplier auth) {
        if (!(body.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isSameThread()
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || !CharacterControlSystem.canAct(body) || !auth.getAsBoolean()) return false;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return hands != null && hands.compatible(body) && hands.revision() == revision && hands.activeHand().equals(hand)
                && hands.token(hand).filter(token::equals).isPresent();
    }
    static boolean inReach(LivingEntity actor, LivingEntity target) {
        return com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess.inRange(actor, target,
                com.juicyslew.moonstation14.ms14.interaction.InteractionReach.DEFAULT_BLOCKS);
    }

    public record Admission(InjectorModeData.Kind kind, long doseCents) {
        public Admission {
            if (kind == null || doseCents <= 0) throw new IllegalArgumentException("invalid syringe admission");
        }
    }

    /**
     * Admits the selected (mode-valid) dose against the actual source and destination
     * constraints. DRAW uses source contents and free syringe capacity; INJECT uses
     * syringe contents and patient headroom. All values are exact hundredths.
     */
    public static Admission admit(MedicalItemData.SyringePolicy policy, InjectorModeData mode,
                                  Float selectedUnits, long sourceAvailableCents,
                                  long destinationFreeCents) {
        if (policy == null || mode == null || sourceAvailableCents < 0 || destinationFreeCents < 0) return null;
        float selected = selectedUnits == null ? policy.defaultTransferUnits() : selectedUnits;
        if (!Float.isFinite(selected) || selected <= 0 || !mode.transferAmounts().contains(selected)) return null;
        long requested;
        try { requested = ReagentUnits.fromFloat(selected); }
        catch (RuntimeException invalid) { return null; }
        long bound = mode.kind() == InjectorModeData.Kind.DRAW
                ? Math.min(sourceAvailableCents, destinationFreeCents)
                : Math.min(sourceAvailableCents, destinationFreeCents);
        return Math.min(requested, bound) > 0 ? new Admission(mode.kind(), Math.min(requested, bound)) : null;
    }

    /** Proportional exact-cent split with deterministic reagent-key remainder allocation. */
    public static Map<ResourceKey<ReagentData>, Long> proportionalDose(
            Map<ResourceKey<ReagentData>, Long> mixture, long doseCents) {
        if (mixture == null || doseCents <= 0) return Map.of();
        try {
            long available = ReagentUnits.total(mixture.values());
            if (doseCents > available || available == 0) return Map.of();
            return ReagentUnits.split(mixture, doseCents);
        } catch (RuntimeException invalid) { return Map.of(); }
    }
}

