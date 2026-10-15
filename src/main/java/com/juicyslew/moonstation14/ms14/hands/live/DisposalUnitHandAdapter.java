package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyReference;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.function.BooleanSupplier;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Trusted server adapter for whole-stack transfer from the exact live body-hand attachment. */
public final class DisposalUnitHandAdapter {
    private static volatile String lastPortableAdmissionDiagnostic = "not-run";
    private DisposalUnitHandAdapter() { }
    static String lastPortableAdmissionDiagnostic() { return lastPortableAdmissionDiagnostic; }
    public static boolean insert(LivingEntity body, String hand, long expectedRevision, DisposalUnitBlockEntity unit) {
        return LiveHandsCommitGateway.batch(() -> insertCommitted(body, hand, expectedRevision, unit));
    }

    /** Exact-held-key path; transfer preserves the original live body instead of admitting the key as payload. */
    public static boolean insertPortableBody(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                             String hand, String expectedToken, long expectedRevision,
                                             BlockPos targetPos, Direction face, BooleanSupplier externalAdmission,
                                             DisposalUnitBlockEntity unit) {
        if (actor == null || authority == null || authority.actor() != actor || hand == null
                || expectedToken == null || targetPos == null || face == null || externalAdmission == null
                || unit == null || !HandActorAuthority.revalidate(actor, authority)) return false;
        lastPortableAdmissionDiagnostic = "capturing";
        LivingEntity carrier = authority.body();
        if (!(carrier.level() instanceof ServerLevel level) || actor.level() != level || unit.getLevel() != level
                || !level.getServer().isSameThread()) return false;
        LiveHands hands = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(carrier) || hands.revision() != expectedRevision
                || !hands.token(hand).filter(expectedToken::equals).isPresent()) return false;
        ItemStack key = hands.stackCopy(hand).orElse(ItemStack.EMPTY);
        Vec3 carrierPosition = carrier.position();
        PortableBodyReference reference = PortableBodyKeys.reference(key);
        if (!PortableBodyKeys.isKey(key) || reference == null || !reference.dimension().equals(level.dimension().location())
                || !(level.getEntity(reference.body()) instanceof LivingEntity target)) return false;
        var snapshot = PortableBodyReleaseTransaction.capture(target);
        if (snapshot == null || !reference.equals(snapshot.reference())
                || !snapshot.rootId().equals(carrier.getUUID()) || !snapshot.locationToken().equals(expectedToken)
                || !snapshot.rootToken().equals(expectedToken)
                || !(snapshot.rootOwner() instanceof PortableBodyCarrierOwner portableOwner)
                || portableOwner.carrier() != carrier) return false;
        var flags = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                .export(target, snapshot.rootOwner()).orElse(null);
        if (flags == null) return false;
        return unit.insertPortableBody(target, snapshot, flags, () -> {
            if (!HandActorAuthority.revalidate(actor, authority)) return portableAdmissionDenied("authority");
            if (actor.level() != level || carrier.level() != level || !carrier.isAlive() || carrier.isRemoved()
                    || !carrier.position().equals(carrierPosition) || target.level() != level || target.isRemoved())
                return portableAdmissionDenied("source-position");
            if (!com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(carrier))
                return portableAdmissionDenied("action-permission");
            if (!externalAdmission.getAsBoolean()) return portableAdmissionDenied("external-admission");
            if (level.getBlockEntity(targetPos) != unit || !unit.isValid()) return portableAdmissionDenied("unit-identity");
            if (!com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe
                    .withinReach(level, carrier, targetPos)) return portableAdmissionDenied("range");
            if (!com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe
                    .lineOfSight(level, carrier, targetPos)) return portableAdmissionDenied("line-of-sight");
            LiveHands current = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            boolean valid = sourceHandStateValid(current, hands, carrier, hand, expectedToken, expectedRevision, key);
            lastPortableAdmissionDiagnostic = valid ? "admitted" : "source-hand-state revision="
                    + (current == null ? "null" : current.revision()) + ",before=" + expectedRevision;
            return valid;
        });
    }

    private static boolean portableAdmissionDenied(String reason) {
        lastPortableAdmissionDiagnostic = reason;
        return false;
    }

    /** Accepts only the captured key state or its exact one-revision prepared direct-hand removal. */
    private static boolean sourceHandStateValid(LiveHands current, LiveHands before, LivingEntity carrier, String hand,
                                                String token, long revision, ItemStack key) {
        if (current == null || before == null || !current.compatible(carrier) || !before.compatible(carrier)) return false;
        if (current == before)
            return current.revision() == revision && current.token(hand).filter(token::equals).isPresent()
                    && current.stackCopy(hand).filter(stack -> ItemStack.matches(stack, key)).isPresent();
        if (!current.compatible(carrier) || current.revision() != revision + 1
                || !current.handIds().equals(before.handIds()) || !current.activeHand().equals(before.activeHand())
                || current.token(hand).isPresent() || current.stackCopy(hand).isPresent()) return false;
        for (String other : before.handIds()) {
            if (other.equals(hand)) continue;
            if (!current.token(other).equals(before.token(other))) return false;
            ItemStack oldStack = before.stackCopy(other).orElse(ItemStack.EMPTY);
            ItemStack newStack = current.stackCopy(other).orElse(ItemStack.EMPTY);
            if (!ItemStack.matches(oldStack, newStack)) return false;
        }
        return true;
    }
    private static boolean insertCommitted(LivingEntity body, String hand, long expectedRevision, DisposalUnitBlockEntity unit) {
        if (body == null || hand == null || unit == null || !(body.level() instanceof ServerLevel level)
                || level != unit.getLevel() || !level.getServer().isSameThread() || body.isRemoved()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body)) return false;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != expectedRevision) return false;
        String tokenValue = before.token(hand).orElse(null);
        if (tokenValue == null) return false;
        ItemToken token = new ItemToken(tokenValue);
        ItemStack stack = before.stackCopy(hand).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) return false;
        LiveHands.WholeResult take = before.takeWhole(expectedRevision, hand, token);
        if (!take.succeeded() || !ItemStack.matches(stack, take.takenStack().orElse(ItemStack.EMPTY))) return false;
        try { LiveHandsCommitGateway.commitOwned(body, take.state()); }
        catch (RuntimeException failure) {
            if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == take.state()) LiveHandsCommitGateway.commitOwned(body, before);
            return false;
        }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != take.state()) {
            LiveHandsCommitGateway.commitOwned(body, before);
            return false;
        }
        if (body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != take.state()
                || !take.state().compatible(body) || !unit.isValid()
                || !unit.completeBodyAdmission(token, new ItemLocation.BodyHand(body.getUUID(), hand), stack)) {
            LiveHandsCommitGateway.commitOwned(body, before);
            return false;
        }
        return true;
    }
}
