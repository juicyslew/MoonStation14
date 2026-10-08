package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Trusted server adapter for whole-stack transfer from the exact live body-hand attachment. */
public final class DisposalUnitHandAdapter {
    private DisposalUnitHandAdapter() { }
    public static boolean insert(LivingEntity body, String hand, long expectedRevision, DisposalUnitBlockEntity unit) {
        return LiveHandsCommitGateway.batch(() -> insertCommitted(body, hand, expectedRevision, unit));
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
