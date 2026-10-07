package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;

import java.util.UUID;

/** Package-boundary access for GameTests exercising the trusted pickup seam. */
public final class BodyHandItemTransferGameTestAccess {
    private BodyHandItemTransferGameTestAccess() { }

    public static BodyHandItemTransfer.Result pickupOwned(LivingEntity body, UUID target, String handId,
                                                           long expectedRevision) {
        return BodyHandItemTransfer.pickupOwned(body, target, handId, expectedRevision, () -> true);
    }

    public static BodyHandItemTransfer.Result dropOwned(LivingEntity body, String handId, String token, long revision) {
        return BodyHandItemTransfer.dropOwned(body, handId, token, revision, () -> true);
    }

    /** Test-only package bridge for seeding a real body's live hand ownership snapshot. */
    public static long holdItem(LivingEntity body, String handId, String token, ItemStack stack) {
        LiveHands initial = LiveHands.initialize(body).orElseThrow();
        var held = initial.putWhole(initial.revision(), handId, new ItemToken(token), stack);
        if (!held.succeeded()) throw new IllegalStateException("could not seed test hand");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), held.state());
        return held.state().revision();
    }

    public static BodyHandItemTransfer.Result pickupSelectedOwned(LivingEntity body, UUID target, String handId,
                                                                    long expectedRevision) {
        return BodyHandItemTransfer.pickupSelectedOwned(body, target, handId, expectedRevision, () -> true);
    }

    public static BodyLooseThrow.Result throwOwned(LivingEntity body, String handId, String token, long revision) {
        return BodyLooseThrow.throwOwned(body, handId, token, revision, () -> true, ignored -> { });
    }
}
