package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Test-only bridge to LiveHands' package-scoped value transition plus the normal actor commit gateway. */
public final class LiveHandsFixtureTransfer {
    private LiveHandsFixtureTransfer() { }

    public static boolean put(ServerPlayer actor, LivingEntity body, String hand, String token, ItemStack stack) {
        LiveHands current = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (current == null) return false;
        LiveHands.WholeResult staged = current.putWhole(current.revision(), hand, new ItemToken(token), stack);
        if (!staged.succeeded()) return false;
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        return authority != null && authority.body() == body
                && LiveHandsCommitGateway.commit(actor, authority, staged.state());
    }
}
