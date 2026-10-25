package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.*;

/** Client request producer and reply correlation, bound to the controlled-body input lifecycle. */
public final class MeleeAttackClient {
    private static final MeleeAttackClientPolicy POLICY = new MeleeAttackClientPolicy();
    private static final long REQUEST_TIMEOUT_TICKS = 100;
    private MeleeAttackClient() { }
    public static void handleReply(MeleeAttackReply reply) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            var body=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
            long epoch=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
            if(body==null || epoch<=0) POLICY.clear(); else POLICY.bind(body.getUUID(),epoch);
            POLICY.acknowledge(reply,com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock());
        });
    }
    public static void clearContext(){POLICY.clear(); com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.clearPredicted();}
    public static void reconcile(net.minecraft.world.entity.Mob body, long epoch, long now) {
        if (body == null || epoch <= 0) { POLICY.clear(); com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.clearPredicted(); return; }
        com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.reconcile(body.getUUID(), epoch, body.level().dimension().location());
        POLICY.bind(body.getUUID(), epoch);
        POLICY.timeout(com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock(), REQUEST_TIMEOUT_TICKS);
    }
    /** Submits an admitted primary combat gesture; target null explicitly represents a miss for now. */
    public static boolean requestAttack(net.minecraft.world.entity.Mob body, long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection, java.util.UUID target) {
        return request(body,epoch,selection,target,null);
    }
    public static boolean requestWideAttack(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection) {
        return requestSecondary(body, epoch, selection, MeleeAttackRequest.Mode.WIDE);
    }
    public static boolean requestSecondary(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection, MeleeAttackRequest.Mode mode) {
        if (mode == null || (mode != MeleeAttackRequest.Mode.WIDE && mode != MeleeAttackRequest.Mode.DISARM)) return false;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(body==null||selection==null||mc.level==null) return false;
        double range=selection.weapon().range();
        var hit=mc.hitResult;
        double aim=hit==null?range:body.getEyePosition().distanceTo(hit.getLocation());
        double reach=Math.min(range,aim);
        var nominations=com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.fan(body,reach,
                Math.toRadians(selection.weapon().angleDegrees()),5,CommonMeleeTargetEligibility::damageable,e->e.getBoundingBox());
        if (mode == MeleeAttackRequest.Mode.DISARM)
            return request(body,epoch,selection,nominations.stream().findFirst().orElse(null),null,mode);
        return request(body,epoch,selection,null,nominations);
    }
    private static boolean request(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection,java.util.UUID target,java.util.List<java.util.UUID> wideTargets) {
        return request(body,epoch,selection,target,wideTargets,wideTargets==null?MeleeAttackRequest.Mode.LIGHT:MeleeAttackRequest.Mode.WIDE);
    }
    private static boolean request(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection,java.util.UUID target,java.util.List<java.util.UUID> wideTargets,MeleeAttackRequest.Mode requestedMode) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (body == null || selection == null || mc.level == null || !body.getUUID().equals(selection.body())
                || epoch <= 0 || !selection.equals(ClientMeleeWeaponResolver.resolve().orElse(null))) return false;
        if (com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isClientActionBlocked(body)) return false;
        long localNow = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock();
        var estimate = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick();
        var proof = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.confirmedOwnedHandSnapshot();
        if (proof == null || !proof.bodyId().equals(body.getUUID()) || proof.epoch() != epoch || estimate.isEmpty()) {
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh();
            return false;
        }
        long deadline = 0;
        if (selection.kind() == MeleeWeaponResolver.Kind.UNARMED) {
            var cooldown = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MELEE_COOLDOWN.get());
            if (cooldown != null) deadline = cooldown.data().nextAttackTick();
        } else {
            var presented = ClientMeleeWeaponResolver.presentationStack(selection).orElse(null);
            if (presented == null) { com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh(); return false; }
            var cooldown = presented.get(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get());
            if (cooldown != null) deadline = cooldown.nextAttackTick();
        }
        if (!MeleeAttackClientPolicy.sourceDeadlineReady(deadline, estimate)) return false;
        if (target == null) {
            float range = selection.weapon().range();
            double eyeReach = range + body.getBoundingBox().getSize();
            target = com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.nearest(body, eyeReach,
                    CommonMeleeTargetEligibility::damageable, candidate -> candidate.getBoundingBox());
            if (target != null) {
                var selectedTarget = target;
                var targetEntity = mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                        body.getBoundingBox().inflate(range + body.getBbWidth() + 2), entity -> selectedTarget.equals(entity.getUUID())).stream().findFirst().orElse(null);
                if (targetEntity == null || !CombatMeleeTargetProbe.BodyBounds.withinRange(
                        CombatMeleeTargetProbe.BodyBounds.distance(body.getBoundingBox(), targetEntity.getBoundingBox()), range)) target = null;
            }
        }
        boolean wide=wideTargets!=null;
        boolean nominatedMiss = wide ? wideTargets.isEmpty() : target == null;
        var pacificationTargetId = target;
        if (requestedMode != MeleeAttackRequest.Mode.DISARM && pacificationTargetId != null
                && !PacificationSystem.mayMeleeTarget(body,
                mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, body.getBoundingBox().inflate(64),
                        entity -> pacificationTargetId.equals(entity.getUUID())).stream().findFirst().orElse(null),
                selection.weapon().damage())) return false;
        POLICY.bind(body.getUUID(), epoch);
        var binding = new MeleeAttackClientPolicy.Binding(selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""));
        var request = POLICY.submit(binding, localNow, Math.max(1L, (long)Math.ceil(20.0/selection.weapon().attackRate())),
                 requestedMode,
                wide ? wideTargets : target==null ? java.util.List.of() : java.util.List.of(target));
        if (request == null) return false;
        boolean disarmFeedback = requestedMode == MeleeAttackRequest.Mode.DISARM
                && target != null && clientDisarmFeedbackAllowed(body, target, mc);
        // Presentation-only prediction is emitted only after the existing request admission policy.
        if (requestedMode != MeleeAttackRequest.Mode.DISARM || disarmFeedback)
            com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.enqueue(body, selection, wide ? wideTargets.stream().findFirst().orElse(null) : target, mc.level.getGameTime(), epoch, requestedMode);
        if (((requestedMode == MeleeAttackRequest.Mode.DISARM && disarmFeedback)
                || ((!wide || wideTargets.isEmpty()) && MeleeSoundPolicy.shouldPredictSwing(nominatedMiss)))) {
            var cue = selection.weapon().audio().swing();
            if (cue != null) {
                var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(cue.event()).orElse(null);
                if (event != null) {
                    float pitch = MeleeSoundPolicy.pitch(1f, mc.level.random.nextFloat(), cue.pitchVariation());
                    mc.level.playLocalSound(body.getX(), body.getY(), body.getZ(), event,
                            net.minecraft.sounds.SoundSource.PLAYERS, cue.volume(), pitch, false);
                }
            }
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
        return true;
    }
    private static boolean clientDisarmFeedbackAllowed(net.minecraft.world.entity.Mob body, java.util.UUID targetId,
                                                       net.minecraft.client.Minecraft mc) {
        var target = mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                body.getBoundingBox().inflate(64), e -> targetId.equals(e.getUUID())).stream().findFirst().orElse(null);
        if (target == null) return false;
        var character = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(target).orElse(null);
        return ClientDisarmAdmission.allows(character, true, target == body,
                com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(target),
                target instanceof net.minecraft.world.entity.player.Player player && player.isSpectator());
    }
    public static MeleeAttackClientPolicy policy(){return POLICY;}
}
