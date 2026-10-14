package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.BooleanSupplier;

/** Single production held-use request construction/dispatch seam; sequence authority remains BodyHandClientPolicy. */
public final class HeldUseRequestDispatch {
    private HeldUseRequestDispatch() { }

    /** Shares the caller's established screen/text/modal admission with the ordinary key route. */
    public static boolean routePressed(boolean pressed, boolean controlDown, boolean altDown,
                                       BooleanSupplier inputAdmission, BooleanSupplier dispatch) {
        return pressed && !controlDown && !altDown && inputAdmission != null && inputAdmission.getAsBoolean()
                && dispatch != null && dispatch.getAsBoolean();
    }

    /** Keep Z's established quick-equip priority, then activate a confirmed held-storage host, then use normally. */
    public static boolean routeActivation(BooleanSupplier quickEquip, BooleanSupplier toggleHeldStorage,
                                          BooleanSupplier ordinaryUse) {
        if (quickEquip != null && quickEquip.getAsBoolean()) return true;
        if (toggleHeldStorage != null && toggleHeldStorage.getAsBoolean()) return true;
        return ordinaryUse != null && ordinaryUse.getAsBoolean();
    }

    public static long dispatch(BodyHandClientPolicy policy, BodyHandStateSnapshot snapshot,
                                BodyHandStateSnapshot.Hand active, long tick, int durationTicks,
                                Consumer<BodyHandActionRequest> sender, LongConsumer predictionStart) {
        if (policy == null || snapshot == null || active == null || active.token() == null
                || active.pullTarget() != null || !active.id().equals(snapshot.activeHand())
                || sender == null || predictionStart == null) return 0;
        long sequence = policy.action(tick);
        if (sequence == 0) return 0;
        var request = new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND, active.id(),
                snapshot.epoch(), sequence, snapshot.revision(), null, active.token());
        if (durationTicks > 0) predictionStart.accept(sequence);
        sender.accept(request);
        policy.markUse(sequence);
        return sequence;
    }
}
