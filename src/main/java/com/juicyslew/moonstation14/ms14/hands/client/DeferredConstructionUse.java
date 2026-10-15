package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.UUID;

/** Single captured construction click. This stores intent only; proof is revalidated before replay. */
public final class DeferredConstructionUse {
    public record Intent(UUID body, long epoch, long handsRevision, String hand, String token, String item,
                         int required, long planRevision, String recipe, int rotation, BlockPos target, Direction face) { }
    public record Current(UUID body, long epoch, long handsRevision, String hand, String token, String item,
                          int count, long planRevision, String recipe, int rotation, BlockPos target,
                          Direction face, boolean planActive, boolean screenClosed) { }
    private Intent pending;
    public boolean capture(Intent intent) {
        if (pending != null) return true;
        if (intent == null) return false;
        pending = intent;
        return true;
    }
    /** Consumes the single intent on every accepted fresh snapshot, valid or not. */
    public Intent takeForReplay(Current current) {
        Intent intent = pending;
        pending = null;
        if (intent == null || current == null || !BodyHandClientPolicy.deferredPlacementStillValid(
                intent.body(), intent.epoch(), current.body(), current.epoch(), intent.handsRevision(), current.handsRevision(),
                intent.hand(), current.hand(), intent.token(), current.token(), intent.item(), current.item(),
                intent.planRevision(), current.planRevision(), intent.target(), current.target(), intent.face(), current.face(),
                current.planActive() && intent.recipe().equals(current.recipe()) && intent.rotation() == current.rotation(),
                current.screenClosed()) || current.count() < intent.required()) return null;
        return intent;
    }
    public boolean pending() { return pending != null; }
    public Intent intent() { return pending; }
    public void clear() { pending = null; }
}

