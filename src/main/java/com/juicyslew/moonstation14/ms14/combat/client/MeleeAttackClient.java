package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.*;

/** Client request producer and reply correlation, bound to the controlled-body input lifecycle. */
public final class MeleeAttackClient {
    private static final MeleeAttackClientPolicy POLICY = new MeleeAttackClientPolicy();
    private static final boolean DIAGNOSTIC = Boolean.getBoolean("ms14.meleePresentationDiagnostic");
    private static volatile MeleeAttackReply diagnosticLastReply;
    private static volatile MeleeAttackRequest diagnosticLastRequest;
    /** Read-only snapshot of the canonical production request; populated only in opt-in diagnostic runs. */
    public static java.util.Optional<MeleeAttackRequest> diagnosticLastRequest() {
        return DIAGNOSTIC ? java.util.Optional.ofNullable(diagnosticLastRequest) : java.util.Optional.empty();
    }
    /** Last production reply observed by the existing callback; absent unless diagnostic mode is enabled. */
    public static java.util.Optional<MeleeAttackReply> diagnosticLastReply() {
        return DIAGNOSTIC ? java.util.Optional.ofNullable(diagnosticLastReply) : java.util.Optional.empty();
    }
    private static final long REQUEST_TIMEOUT_TICKS = 100;
    private MeleeAttackClient() { }
    public static void handleReply(MeleeAttackReply reply) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (DIAGNOSTIC) diagnosticLastReply = reply;
            var body=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
            long epoch=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
            if(body==null || epoch<=0) POLICY.clear(); else POLICY.bind(body.getUUID(),epoch);
            boolean correlated=POLICY.acknowledge(reply,com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock());
            if (correlated && MeleeArcClient.rejectedReply(reply.status()))
                MeleeArcClient.cancelRejected(reply.bodyUUID(), reply.epoch(), reply.sequence());
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
    /** Held primary input repeats through the same admission/cooldown path, but unarmed alt-disarm
     * sources must resolve a fresh disarm nomination on every attempt. */
    public static boolean requestHeldAttack(net.minecraft.world.entity.Mob body, long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection) {
        var mode = selection == null ? null : MeleeSecondaryGesture.mode(selection.kind(),
                selection.weapon().altDisarm(), clientCanDisarm(body));
        if (mode == MeleeAttackRequest.Mode.DISARM)
            return requestSecondary(body, epoch, selection, mode);
        return requestAttack(body, epoch, selection, null);
    }
    private static Boolean clientCanDisarm(net.minecraft.world.entity.Mob body) {
        return body == null ? null : com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem
                .projectForActor(body).flatMap(actor -> actor.component(
                        com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent.class))
                .map(mode -> mode.canDisarm().orElse(false)).orElse(false);
    }
    /** Applies the same source, hand snapshot and authoritative cooldown gates as requestAttack without dispatching. */
    public static boolean canAttemptNow(net.minecraft.world.entity.Mob body, long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection) {
        var admitted = evaluateAdmission(body, epoch, selection, false);
        return admitted != null && MeleeAttackClientPolicy.repeatAdmission(
                MeleeAttackClientPolicy.sourceDeadlineReady(admitted.deadline, admitted.estimate),
                POLICY.pending(), admitted.localNow, POLICY.projectedDeadline());
    }
    public static boolean requestWideAttack(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection) {
        return requestSecondary(body, epoch, selection, MeleeAttackRequest.Mode.WIDE);
    }
    public static boolean requestSecondary(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection, MeleeAttackRequest.Mode mode) {
        if (mode == null || (mode != MeleeAttackRequest.Mode.WIDE && mode != MeleeAttackRequest.Mode.DISARM)) return false;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(body==null||selection==null||mc.level==null || mc.screen != null || !mc.mouseHandler.isMouseGrabbed()
                || !com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow.allowsBodyInput(mc.screen, mc.mouseHandler.isMouseGrabbed())) return false;
        double range=selection.weapon().range();
        double eyeReach = com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.eyeRayReach(range, body.getBoundingBox());
        if (mode == MeleeAttackRequest.Mode.DISARM) {
            var target = com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.nearest(body, eyeReach,
                    candidate -> CommonMeleeTargetEligibility.damageable(candidate)
                            && CombatMeleeTargetProbe.BodyBounds.withinRange(
                                    CombatMeleeTargetProbe.BodyBounds.distance(body.getBoundingBox(), candidate.getBoundingBox()), (float) range),
                    e -> e.getBoundingBox());
            if (target == null) return false;
            return request(body, epoch, selection, target, null, mode);
        }
        var hit=mc.hitResult;
        // Wide rays have a fixed nominal weapon radius; each ray's own terrain clip
        // determines visibility, rather than the center crosshair hit truncating all rays.
        double reach=range;
        var wideStart=com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.bodyHeightOrigin(body.getBoundingBox(), selection.weapon().wideOriginHeightRatio());
        var nominations=com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.fanContacts(body,wideStart,reach,
                Math.toRadians(selection.weapon().angleDegrees()),5,CommonMeleeTargetEligibility::damageable,e->e.getBoundingBox(),
                body.getBoundingBox(), range, state -> com.juicyslew.moonstation14.ms14.combat.StructuralContactResolver.evaluate(
                        state.getBlock(), selection.weapon(),
                        com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientConstructionEntities(),
                        com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientBlockStructuralContacts())
                        == com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT);
        return requestWideContacts(body,epoch,selection,nominations);
    }
    private static boolean requestWideContacts(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection,
            java.util.List<com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination> nominations) {
        return request(body,epoch,selection,null,null,MeleeAttackRequest.Mode.WIDE,nominations);
    }
    private static boolean request(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection,java.util.UUID target,java.util.List<java.util.UUID> wideTargets) {
        return request(body,epoch,selection,target,wideTargets,wideTargets==null?MeleeAttackRequest.Mode.LIGHT:MeleeAttackRequest.Mode.WIDE);
    }
    private static boolean request(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection,java.util.UUID target,java.util.List<java.util.UUID> wideTargets,MeleeAttackRequest.Mode requestedMode) {
        return request(body,epoch,selection,target,wideTargets,requestedMode,null);
    }
    static MeleeAttackRequest withWideNominations(MeleeAttackRequest template, java.util.List<MeleeTargetNomination> nominations) {
        return new MeleeAttackRequest(template.epoch(),template.sequence(),template.expectedKind(),template.expectedHandRevision(),
                template.expectedActiveHand(),template.expectedItemToken(),MeleeAttackRequest.Mode.WIDE,nominations,false);
    }
    private record Admission(net.minecraft.client.Minecraft mc, long localNow,
                             java.util.OptionalDouble estimate, long deadline) { }
    private static Admission evaluateAdmission(net.minecraft.world.entity.Mob body, long epoch,
            MeleeWeaponResolver.Selection selection, boolean refreshMissingProof) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (body == null || selection == null || mc.level == null || epoch <= 0
                || !body.getUUID().equals(selection.body()) || !selection.equals(ClientMeleeWeaponResolver.resolve().orElse(null))
                || mc.screen != null || !mc.mouseHandler.isMouseGrabbed()
                || !com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow.allowsBodyInput(mc.screen, mc.mouseHandler.isMouseGrabbed())
                || com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isClientActionBlocked(body)) return null;
        long localNow = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock();
        var estimate = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick();
        var actor = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(body).orElse(null);
        boolean noHands = actor != null && actor.component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class)
                .map(h -> h.hands().isEmpty()).orElse(true);
        if (noHands) {
            var clock = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.confirmedCharacterSessionClock();
            estimate = CharacterClockMeleeAdmission.estimate(body.getUUID(), epoch,
                    body.level().dimension().location().toString(), clock);
            if (estimate.isEmpty()) return null;
        }
        var proof = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.confirmedOwnedHandSnapshot();
        if ((!noHands && (proof == null || !proof.bodyId().equals(body.getUUID()) || proof.epoch() != epoch || estimate.isEmpty()))) {
            if (refreshMissingProof) com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh();
            return null;
        }
        long deadline = 0;
        if (selection.kind() == MeleeWeaponResolver.Kind.UNARMED) {
            var cooldown = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MELEE_COOLDOWN.get());
            if (cooldown != null) deadline = cooldown.data().nextAttackTick();
        } else {
            var presented = ClientMeleeWeaponResolver.presentationStack(selection).orElse(null);
            if (presented == null) {
                if (refreshMissingProof) com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh();
                return null;
            }
            var cooldown = presented.get(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get());
            if (cooldown != null) deadline = cooldown.nextAttackTick();
        }
        return new Admission(mc, localNow, estimate, deadline);
    }
    private static boolean request(net.minecraft.world.entity.Mob body,long epoch,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection,java.util.UUID target,java.util.List<java.util.UUID> wideTargets,MeleeAttackRequest.Mode requestedMode,
            java.util.List<MeleeTargetNomination> wideContacts) {
        var admission = evaluateAdmission(body, epoch, selection, true);
        if (admission == null) return false;
        var mc = admission.mc;
        long localNow = admission.localNow;
        var estimate = admission.estimate;
        if (!MeleeAttackClientPolicy.sourceDeadlineReady(admission.deadline, estimate)) return false;
        com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination blockTarget = null;
        if (target == null && requestedMode == MeleeAttackRequest.Mode.LIGHT) {
            float range = selection.weapon().range();
            double eyeReach = com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.eyeRayReach(range, body.getBoundingBox());
            target = com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.nearest(body, eyeReach,
                    candidate -> CommonMeleeTargetEligibility.damageable(candidate)
                            && CombatMeleeTargetProbe.BodyBounds.withinRange(
                                    CombatMeleeTargetProbe.BodyBounds.distance(body.getBoundingBox(), candidate.getBoundingBox()), (float) range),
                     candidate -> candidate.getBoundingBox());
            var blockHit = com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.firstBlockContact(body, eyeReach);
            if (blockHit != null) {
                var start = body.getEyePosition();
                var end = start.add(body.getLookAngle().scale(eyeReach));
                var blockDistance = start.distanceTo(blockHit.getLocation());
                var selectedTarget = target;
                var entity = selectedTarget == null ? null : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                        body.getBoundingBox().inflate(eyeReach), e -> selectedTarget.equals(e.getUUID())).stream().findFirst().orElse(null);
                double entityDistance = entity == null ? Double.POSITIVE_INFINITY :
                        com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.hit(entity.getBoundingBox(), start, end, eyeReach, blockDistance)
                                .map(start::distanceTo).orElse(Double.POSITIVE_INFINITY);
                var chosenContact = com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.chooseLightContact(
                        entityDistance < blockDistance ? selectedTarget : null, entityDistance, blockHit, blockDistance);
                double physical = com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.BodyBounds.distance(
                        body.getBoundingBox(), new net.minecraft.world.phys.AABB(blockHit.getLocation(), blockHit.getLocation()));
                var state = mc.level.getBlockState(blockHit.getBlockPos());
                if (chosenContact.block() == blockHit && physical <= range &&
                        com.juicyslew.moonstation14.ms14.combat.StructuralContactResolver.evaluate(state.getBlock(), selection.weapon(),
                                com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientConstructionEntities(),
                                com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientBlockStructuralContacts()) ==
                        com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT) {
                    blockTarget = MeleeTargetNomination.Block.fromHit(blockHit, state);
                    target = null;
                } else if (chosenContact.entity() != null) {
                    blockTarget = null;
                }
            }
        }
        boolean wide=wideContacts!=null || wideTargets!=null;
        boolean nominatedMiss = wide ? (wideContacts!=null ? wideContacts.isEmpty() : wideTargets.isEmpty()) : target == null && blockTarget == null;
        var pacificationTargetId = target;
        if (requestedMode != MeleeAttackRequest.Mode.DISARM && pacificationTargetId != null
                && !PacificationSystem.mayMeleeTarget(body,
                mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, body.getBoundingBox().inflate(64),
                        entity -> pacificationTargetId.equals(entity.getUUID())).stream().findFirst().orElse(null),
                selection.weapon().damage())) return false;
        POLICY.bind(body.getUUID(), epoch);
        var binding = new MeleeAttackClientPolicy.Binding(selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""));
        var typedNominations = new java.util.ArrayList<MeleeTargetNomination>();
        if (wideContacts != null) typedNominations.addAll(wideContacts);
        else {
            if (wideTargets != null) wideTargets.forEach(id -> typedNominations.add(new MeleeTargetNomination.Entity(id)));
            else if (blockTarget != null) typedNominations.add(blockTarget);
            else if (target != null) typedNominations.add(new MeleeTargetNomination.Entity(target));
        }
        var request = POLICY.submitTyped(binding, localNow, Math.max(1L, (long)Math.ceil(20.0/selection.weapon().attackRate())),
                 requestedMode, typedNominations);
        if (request == null) return false;
        boolean disarmFeedback = requestedMode == MeleeAttackRequest.Mode.DISARM
                && target != null && clientDisarmFeedbackAllowed(body, target, mc);
        // Presentation-only prediction is emitted only after the existing request admission policy.
        if (requestedMode != MeleeAttackRequest.Mode.DISARM || disarmFeedback)
            com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.enqueue(body, selection, wide ? (wideContacts == null ? wideTargets.stream().findFirst().orElse(null) : wideContacts.stream().filter(MeleeTargetNomination.Entity.class::isInstance).map(MeleeTargetNomination.Entity.class::cast).map(MeleeTargetNomination.Entity::entityUUID).findFirst().orElse(null)) : target, blockTarget instanceof MeleeTargetNomination.Block block ? block : null, mc.level.getGameTime(), epoch, requestedMode, request.sequence());
        boolean swingPredicted = false;
        if (((requestedMode == MeleeAttackRequest.Mode.DISARM && disarmFeedback)
                || ((!wide || nominatedMiss) && MeleeSoundPolicy.shouldPredictSwing(nominatedMiss)))) {
            var cue = selection.weapon().audio().swing();
            if (cue != null) {
                var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(cue.event()).orElse(null);
                if (event != null) {
                    float pitch = MeleeSoundPolicy.pitch(1f, mc.level.random.nextFloat(), cue.pitchVariation());
                    mc.level.playLocalSound(body.getX(), body.getY(), body.getZ(), event,
                            net.minecraft.sounds.SoundSource.PLAYERS, cue.volume(), pitch, false);
                    swingPredicted = true;
                }
            }
        }
        if (DIAGNOSTIC) diagnosticLastRequest = request;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(request.withSwingPredicted(swingPredicted));
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
