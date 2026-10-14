package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.DamageableComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageReducer;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.mobs.MobStatePolicy;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Server-only authoritative timed topical treatment. */
public final class MedicalTreatmentService {
    private static final String KIND = "medical-treatment";
    private MedicalTreatmentService() { }

    /** Starts treatment using the authenticated actor's controlled body and exact active held source. */
    public static BodyHandActionResult.Reason start(ServerPlayer actor, String handId, String expectedToken,
                                                     long revision, long sequence, UUID targetId) {
        HandActorAuthority.Snapshot authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) return BodyHandActionResult.Reason.NO_AUTHORITY;
        LivingEntity body = authority.body();
        BooleanSupplier validAuthority = () -> HandActorAuthority.revalidate(actor, authority);
        if (!intent(body, actor, handId, expectedToken, revision, validAuthority)) return BodyHandActionResult.Reason.DENIED;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack held = hands.stackCopy(handId).orElse(ItemStack.EMPTY);
        MedicalItemData.HealingPolicy policy = healingPolicy(body.level(), held);
        if (policy == null) return BodyHandActionResult.Reason.DENIED;
        LivingEntity patient = targetId == null || targetId.equals(body.getUUID()) ? body : resolveTarget(body, targetId);
        if (patient == null || !eligible(patient, policy) || !inRange(body, patient)) return BodyHandActionResult.Reason.DENIED;
        String key = "held:" + expectedToken + ":target:" + patient.getUUID();
        if (TimedInteractionSystem.cancelDuplicate(body, KIND, key)) {
            BodyHandStateService.requireFreshQuery(actor);
            return BodyHandActionResult.Reason.OK;
        }
        int ticks = body == patient
                ? treatmentDelayTicks(policy, DamageReducer.total(DamageSystem.existingOrVanillaBaseline(patient)), criticalThreshold(patient))
                : baseDelayTicks(policy);
        if (ticks < 1) return BodyHandActionResult.Reason.DENIED;
        LivingEntity fixedPatient = patient;
        AtomicLong expectedRevision = new AtomicLong(revision);
        var result = TimedInteractionSystem.start(body, actor, sequence, KIND, key, ticks, false, true,
                1.0, () -> fixedPatient == body
                        ? treatmentDelayTicks(policy, totalDamage(fixedPatient), criticalThreshold(fixedPatient))
                        : baseDelayTicks(policy),
                () -> intent(body, actor, handId, expectedToken, expectedRevision.get(), validAuthority)
                        && patientValid(body, fixedPatient, policy),
                () -> complete(actor, authority, body, fixedPatient, handId, expectedToken, policy, validAuthority, expectedRevision));
        if (result == TimedInteractionEngine.StartResult.STARTED || result == TimedInteractionEngine.StartResult.DUPLICATE_CANCELLED) {
            BodyHandStateService.requireFreshQuery(actor);
            if (result == TimedInteractionEngine.StartResult.STARTED) play(policy.beginSound(), body);
            return BodyHandActionResult.Reason.OK;
        }
        return BodyHandActionResult.Reason.DENIED;
    }

    private static TimedInteractionSystem.Completion complete(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LivingEntity body, LivingEntity patient, String hand, String token,
            MedicalItemData.HealingPolicy policy, BooleanSupplier authorityValid, AtomicLong expectedRevision) {
        if (!intent(body, actor, hand, token, expectedRevision.get(), authorityValid)
                || !patientValid(body, patient, policy)) return TimedInteractionSystem.Completion.STOP;
        LiveHands beforeHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack before = beforeHands.stackCopy(hand).orElse(ItemStack.EMPTY);
        if (before.isEmpty() || !isHealingItem(body.level(), before, policy)) return TimedInteractionSystem.Completion.STOP;
        if (!hasEffect(patient, policy)) return TimedInteractionSystem.Completion.STOP;
        ItemStack after = before.copy(); after.shrink(1);
        if (after.isEmpty()) after = ItemStack.EMPTY;
        BodyHeldItemMutation.Result mutation = BodyHeldItemMutation.commit(actor, authority, beforeHands,
                hand, token, beforeHands.revision(), before, after);
        if (mutation != BodyHeldItemMutation.Result.SUCCESS)
            return mutation == BodyHeldItemMutation.Result.RECOVERY_REQUIRED
                    ? TimedInteractionSystem.Completion.RECOVERY_REQUIRED : TimedInteractionSystem.Completion.STOP;
        LiveHands postMutation = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (postMutation == null) return TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
        expectedRevision.set(postMutation.revision());
        MedicalTreatmentEffects.Result effects = MedicalTreatmentEffects.apply(patient, policy);
        if (!effects.changed()) return TimedInteractionSystem.Completion.STOP;
        play(policy.endSound(), patient);
        BodyHandStateService.requireFreshQuery(actor);
        return patientValid(body, patient, policy) && !after.isEmpty() && hasEffect(patient, policy)
                ? TimedInteractionSystem.Completion.REPEAT : TimedInteractionSystem.Completion.STOP;
    }

    private static boolean hasEffect(LivingEntity patient, MedicalItemData.HealingPolicy policy) {
        Map<String, Float> damage = damageSnapshot(patient);
        for (String key : policy.damage().keySet()) if (damage.getOrDefault(key, 0f) > 0) return true;
        return policy.bleedModifier() < 0 && com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(patient)
                .map(s -> s.initialized() && s.bleedRate() > 0).orElse(false);
    }

    private static boolean patientValid(LivingEntity actor, LivingEntity target, MedicalItemData.HealingPolicy policy) {
        return target != null && target.level() == actor.level() && target.level() instanceof ServerLevel level
                && BodyContainmentService.isValidBody(target, level) && inRange(actor, target) && eligible(target, policy);
    }

    private static boolean eligible(LivingEntity target, MedicalItemData.HealingPolicy policy) {
        if (target.level().isClientSide || MobStateSystem.policy(target) == null) return false;
        var character = CharacterIdentitySystem.resolveForActor(target).orElse(null);
        if (character == null) return false;
        var damageable = character.component(DamageableComponent.class).orElse(null);
        if (damageable == null || !acceptsDamageContainer(policy, damageable.damageContainer())) return false;
        Map<String, Float> existing = damageSnapshot(target);
        for (String key : policy.damage().keySet()) if (existing.getOrDefault(key, 0f) > 0) return true;
        return policy.bleedModifier() < 0 && com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(target)
                .map(s -> s.initialized() && s.bleedRate() > 0).orElse(false);
    }

    static boolean inRange(LivingEntity actor, LivingEntity target) {
        return com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess.inRange(actor,target,
                com.juicyslew.moonstation14.ms14.interaction.InteractionReach.DEFAULT_BLOCKS);
    }

    private static LivingEntity resolveTarget(LivingEntity actor, UUID id) {
        if (!(actor.level() instanceof ServerLevel level) || id == null) return null;
        var entity = level.getEntity(id);
        return entity instanceof LivingEntity living && living != actor ? living : null;
    }

    private static boolean intent(LivingEntity body, ServerPlayer actor, String hand, String token, long revision,
                                   BooleanSupplier authority) {
        if (body == null || actor == null || hand == null || token == null || revision < 0 || authority == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isSameThread()
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || !authority.getAsBoolean() || !CharacterControlSystem.canAct(body)) return false;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return hands != null && hands.compatible(body) && hands.revision() == revision && hands.activeHand().equals(hand)
                && hands.token(hand).filter(token::equals).isPresent();
    }

    private static MedicalItemData.HealingPolicy healingPolicy(net.minecraft.world.level.Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            PrototypeCatalog<MedicalItemData> catalog = ModMedicalItems.catalog(level);
            MedicalItemData data = ModMedicalItems.require(catalog, itemId);
            return data.healing().orElse(null);
        } catch (RuntimeException missing) { return null; }
    }

    private static boolean isHealingItem(net.minecraft.world.level.Level level, ItemStack stack, MedicalItemData.HealingPolicy expected) {
        return healingPolicy(level, stack) == expected || expected.equals(healingPolicy(level, stack));
    }

    public static int treatmentDelayTicks(MedicalItemData.HealingPolicy policy, double damage, double critical) {
        double multiplier = critical > 0 && Double.isFinite(critical)
                ? Math.max(1, 1 + damage / critical * (policy.selfPenalty() - 1)) : policy.selfPenalty();
        return (int) Math.ceil(policy.delay() * multiplier * 20);
    }

    public static int baseDelayTicks(MedicalItemData.HealingPolicy policy) {
        return (int) Math.ceil(policy.delay() * 20);
    }

    /** Empty resolved container means unrestricted typed damage; an absent component is rejected by the caller. */
    public static boolean acceptsDamageContainer(MedicalItemData.HealingPolicy policy, java.util.Optional<String> container) {
        return container == null || container.isEmpty() || policy.allowedDamageContainers().contains(
                container.orElseThrow().toLowerCase(java.util.Locale.ROOT));
    }

    private static Map<String, Float> damageSnapshot(LivingEntity patient) {
        var stored = patient.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        if (MobStateSystem.isDead(patient)) return stored == null ? Map.of() : stored.getMap();
        return DamageSystem.existingOrVanillaBaseline(patient);
    }

    private static double totalDamage(LivingEntity entity) {
        return DamageReducer.total(DamageSystem.existingOrVanillaBaseline(entity));
    }

    private static double criticalThreshold(LivingEntity entity) {
        MobStatePolicy policy = MobStateSystem.policy(entity);
        if (policy == null) return 0;
        return policy.thresholds().thresholds().entrySet().stream()
                .filter(e -> e.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL)
                .mapToDouble(Map.Entry::getKey).min().orElse(0);
    }

    private static void play(java.util.Optional<ResourceLocation> soundId, LivingEntity at) {
        if (soundId.isEmpty() || !(at.level() instanceof ServerLevel level)) return;
        if (BuiltInRegistries.SOUND_EVENT.getOptional(soundId.orElseThrow()).isEmpty()) return;
        com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(
                level, soundId.orElseThrow(), at.getX(), at.getY(), at.getZ());
    }
}

