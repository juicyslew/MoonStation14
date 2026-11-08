package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.entities.ItemAttachment;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Server-authoritative timed removal of an attached loose item; the loose entity remains the owner. */
public final class AttachedItemRemovalSystem {
    private static final double REACH = 5.0;
    public enum SelfRemovalResult { STARTED, IN_PROGRESS, DENIED }
    private AttachedItemRemovalSystem() { }

    public static TimedInteractionEngine.StartResult begin(ServerPlayer actor, LooseItemEntity source,
                                                            long originSequence) {
        return begin(actor, source, originSequence, false);
    }

    public static SelfRemovalResult beginSelf(ServerPlayer actor, long originSequence) {
        if (actor == null || !(actor.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return SelfRemovalResult.DENIED;
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) return SelfRemovalResult.DENIED;
        var state = authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
        SelfRemovalResult disposition = activeOperationDisposition(state);
        if (disposition != null) return disposition;
        var source = com.juicyslew.moonstation14.entities.ItemAttachmentQueries.selfRemovalTarget(authority.body()).orElse(null);
        if (source == null) return SelfRemovalResult.DENIED;
        return begin(actor, source, originSequence, true) == TimedInteractionEngine.StartResult.STARTED ? SelfRemovalResult.STARTED : SelfRemovalResult.DENIED;
    }

    /** Returns null when no live operation blocks a new self-removal attempt. */
    public static SelfRemovalResult activeOperationDisposition(com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperationState state) {
        if (state == null) return null;
        boolean active = false;
        for (var operation : state.operations()) {
            if (operation.phase() != com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE) continue;
            active = true;
            if (operation.kind().equals("self-attachment-removal")) return SelfRemovalResult.IN_PROGRESS;
        }
        return active ? SelfRemovalResult.DENIED : null;
    }

    public static boolean hasSelfRemovable(LivingEntity body) {
        return body != null && com.juicyslew.moonstation14.entities.ItemAttachmentQueries.selfRemovalTarget(body).isPresent();
    }

    private static TimedInteractionEngine.StartResult begin(ServerPlayer actor, LooseItemEntity source,
                                                            long originSequence, boolean internal) {
        if (actor == null || source == null || !(actor.level() instanceof ServerLevel level)
                || source.level() != level || level.isClientSide || !level.getServer().isSameThread())
            return TimedInteractionEngine.StartResult.DENIED;
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) return TimedInteractionEngine.StartResult.DENIED;
        LivingEntity body = authority.body();
        ItemAttachment attachment = source.attachment().orElse(null);
        var profile = ThrownItemProfile.resolveForStack(source.getItem(), false).orElse(null);
        if (attachment == null || profile == null || source.isRemoved() || level.getEntity(source.getUUID()) != source
                || !valid(actor, authority, source, attachment, attachment.kind(), null, null, internal))
            return TimedInteractionEngine.StartResult.DENIED;

        boolean embedded = attachment.kind() == ItemAttachment.AttachmentKind.EMBEDDED;
        double seconds;
        boolean breakMove = false;
        String kind;
        if (embedded) {
            var data = profile.embedding().orElse(null);
            if (data == null) return TimedInteractionEngine.StartResult.DENIED;
            seconds = data.removeSeconds();
            kind = internal ? "self-attachment-removal" : "embedded-removal";
        } else {
            var data = profile.ensnaring().orElse(null);
            if (data == null) return TimedInteractionEngine.StartResult.DENIED;
            boolean self = body.getUUID().equals(attachment.targetUUID());
            seconds = self ? data.breakoutSeconds() : data.freeSeconds();
            breakMove = !data.canMoveBreakout();
            kind = internal ? "self-attachment-removal" : self ? "ensnare-breakout" : "ensnare-removal";
        }
        int ticks = durationTicks(seconds);
        if (ticks < 1) return TimedInteractionEngine.StartResult.DENIED;

        LiveHands hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)) return TimedInteractionEngine.StartResult.DENIED;
        String active = hands.activeHand();
        String token = hands.token(active).orElse(null);
        boolean needsHand = !embedded;
        AtomicReference<ItemAttachment> expected = new AtomicReference<>(attachment);
        java.util.function.BooleanSupplier valid = () -> valid(actor, authority, source, expected.get(),
                attachment.kind(), needsHand ? active : null, needsHand ? token : null, internal);
        return TimedInteractionSystem.start(body, actor, originSequence, kind, source.getUUID().toString(), ticks,
                false, breakMove, valid, () -> {
                    if (!valid.getAsBoolean()) return TimedInteractionSystem.Completion.DENIED;
                    var currentAttachment = source.attachment().orElse(null);
                    if (currentAttachment == null || !currentAttachment.equals(expected.get()))
                        return TimedInteractionSystem.Completion.DENIED;
                    var expectedStack = source.getItem().copy();
                    if (!source.detachAttachment()) return TimedInteractionSystem.Completion.DENIED;
                    var result = BodyHandItemTransfer.pickupTrustedCreated(authority, source, expectedStack,
                            () -> valid(actor, authority, source, null, null, needsHand ? active : null,
                                    needsHand ? token : null, internal));
                    if (result == BodyHandItemTransfer.Result.RECOVERY_REQUIRED)
                        return TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
                    // DENIED means no fitting empty hand (or pickup admission failed): detached source remains.
                    return TimedInteractionSystem.Completion.STOP;
                });
    }

    /** Seconds are interpreted at Minecraft's 20 authoritative ticks per second, rounding up. */
    public static int durationTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(seconds * 20.0));
    }

    private static boolean valid(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                 LooseItemEntity source, ItemAttachment expected, ItemAttachment.AttachmentKind kind,
                                 String activeHand, String initialToken, boolean selfAdmission) {
        if (!HandActorAuthority.revalidate(actor, authority)
                || !CharacterControlSystem.canAct(authority.body())
                || !(authority.body().level() instanceof ServerLevel level)
                || source.level() != level || source.isRemoved() || source.getItem().isEmpty() || level.getEntity(source.getUUID()) != source) return false;
        ItemAttachment current = source.attachment().orElse(null);
        boolean selfAttached = selfAdmission && current != null && current.targetUUID().equals(authority.body().getUUID()) && current.targetEntityId() == authority.body().getId();
        if (selfAdmission) {
            if (expected != null && !selfAttached) return false;
            if (expected == null && current != null) return false;
        } else if (!BodyItemTargetProbe.probeSelectedGeometry(level, authority.body(), source, REACH)) return false;
        if (expected != null && !source.attachment().filter(expected::equals).isPresent()) return false;
        if (kind != null && source.attachment().map(ItemAttachment::kind).filter(kind::equals).isEmpty()) return false;
        if (activeHand != null) {
            LiveHands hands = authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if (hands == null || !hands.compatible(authority.body()) || !activeHand.equals(hands.activeHand())) return false;
            // Dropping the originally held item is allowed; replacing it during the interaction is not.
            String currentToken = hands.token(activeHand).orElse(null);
            if (initialToken != null && currentToken != null && !initialToken.equals(currentToken)) return false;
            if (initialToken == null && currentToken != null) return false;
        }
        return true;
    }
}
