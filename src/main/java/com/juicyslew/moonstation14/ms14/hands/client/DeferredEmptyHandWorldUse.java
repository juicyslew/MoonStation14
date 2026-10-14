package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** One-shot nomination for an ordinary empty-hand click made during a hand-query refresh. */
public final class DeferredEmptyHandWorldUse {
    /** A nomination waits only for its correlated hand query, not the broader mutation barrier. */
    public static boolean emptyHandIntentMayWait(boolean handQueryPending) { return handQueryPending; }
    /** The only primary-world route eligible for a query-refresh nomination. */
    public static boolean mayNominate(boolean queryPending, PrimaryWorldGesture.Route route,
                                      boolean confirmedEmptyHand, boolean ordinaryAdmission) {
        return queryPending && route == PrimaryWorldGesture.Route.BLOCK_USE && confirmedEmptyHand && ordinaryAdmission;
    }
    public record Intent(UUID body, long epoch, ResourceLocation dimension, BlockPos target, Direction face,
                         String hand, boolean shift, long expires) { }
    private Intent intent;
    public void nominate(Intent value) { intent = value; }
    public Intent intent() { return intent; }
    public void clear() { intent = null; }
    public Intent take() { var result = intent; intent = null; return result; }
    public boolean expired(long tick) { return intent != null && tick >= intent.expires(); }
    public static boolean contextMatches(Intent intent, UUID body, long epoch, ResourceLocation dimension,
                                         BlockPos target, Direction face, String hand, boolean empty,
                                         long tick, boolean ordinaryAdmission) {
        return intent != null && tick < intent.expires() && ordinaryAdmission
                && intent.body().equals(body) && intent.epoch() == epoch && intent.dimension().equals(dimension)
                && intent.target().equals(target) && intent.face() == face && intent.hand().equals(hand) && empty;
    }
}
