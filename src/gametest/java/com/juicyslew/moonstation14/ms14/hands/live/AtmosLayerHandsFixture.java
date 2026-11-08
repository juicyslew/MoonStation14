package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Narrow test-source bridge to LiveHands' package-scoped owner mutation API. */
public final class AtmosLayerHandsFixture {
    private AtmosLayerHandsFixture() { }
    public static boolean set(LivingEntity body, ItemStack stack) {
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null) return false;
        var result = before.putWhole(before.revision(), before.activeHand(), new ItemToken("atmos-layer-tool"), stack);
        return result.succeeded() && LiveHandsCommitGateway.commitTrusted(body, before, result.state());
    }
    public static boolean replace(LivingEntity body, ItemStack stack, String token) {
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null) return false;
        String hand = before.activeHand();
        var cleared = before.clearHeldWhole(before.revision(), hand, new ItemToken(before.token(hand).orElseThrow())).orElse(null);
        if (cleared == null) return false;
        var result = cleared.putWhole(cleared.revision(), hand, new ItemToken(token), stack);
        return result.succeeded() && LiveHandsCommitGateway.commitTrusted(body, before, result.state());
    }
}
