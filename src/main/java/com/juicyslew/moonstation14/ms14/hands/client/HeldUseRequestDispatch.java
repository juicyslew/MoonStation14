package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.BooleanSupplier;

/** Single production held-use request construction/dispatch seam; sequence authority remains BodyHandClientPolicy. */
public final class HeldUseRequestDispatch {
    /** A distinct instant magazine-rack press captured while an earlier rack request is unresolved. */
    public record RackIntent(java.util.UUID body, long epoch, String dimension, long revision,
                             String hand, String token, long pressedAt) { }

    /** Bounded FIFO; intent is nomination only and can be consumed only against fresh authoritative proof. */
    public static final class RackIntentQueue {
        public static final int CAPACITY = 8;
        public static final long TTL_TICKS = 40;
        private final java.util.ArrayDeque<RackIntent> intents = new java.util.ArrayDeque<>();

        public boolean capture(boolean instantMagazineRack, java.util.UUID body, long epoch, String dimension,
                               long revision, String hand, String token, long tick) {
            if (!instantMagazineRack || body == null || epoch <= 0 || dimension == null || revision < 0
                    || hand == null || token == null || tick < 0 || intents.size() >= CAPACITY) return false;
            intents.addLast(new RackIntent(body, epoch, dimension, revision, hand, token, tick));
            return true;
        }

        /** accepted is the correlated original rack result; false drops every queued press. */
        public RackIntent peekReady(boolean accepted, boolean contextValid, long tick,
                                    java.util.UUID body, long epoch, String dimension,
                                    BodyHandStateSnapshot fresh) {
            if (!accepted || !contextValid) { clear(); return null; }
            while (!intents.isEmpty() && (tick < intents.peekFirst().pressedAt()
                    || tick - intents.peekFirst().pressedAt() >= TTL_TICKS)) intents.removeFirst();
            if (intents.isEmpty() || fresh == null) return null;
            RackIntent intent = intents.peekFirst();
            if (!intent.body().equals(body) || intent.epoch() != epoch || !intent.dimension().equals(dimension)) {
                clear(); return null;
            }
            if (!fresh.bodyId().equals(body) || fresh.epoch() != epoch
                    || fresh.reason() != BodyHandStateSnapshot.Reason.OK || !fresh.activeHand().equals(intent.hand())) {
                clear(); return null;
            }
            if (fresh.revision() < intent.revision()) return null;
            var active = fresh.hands().stream().filter(hand -> hand.id().equals(intent.hand())).findFirst().orElse(null);
            if (active == null || !intent.token().equals(active.token())) { clear(); return null; }
            return intent;
        }

        public boolean dispatched(RackIntent intent) { return intent != null && intents.removeFirstOccurrence(intent); }
        /** Production two-phase drain: preserve nomination unless the real dispatcher succeeds. */
        public boolean dispatchReady(boolean accepted, boolean contextValid, long tick, java.util.UUID body,
                                     long epoch, String dimension, BodyHandStateSnapshot fresh,
                                     BooleanSupplier dispatch) {
            RackIntent intent = peekReady(accepted, contextValid, tick, body, epoch, dimension, fresh);
            if (intent == null || dispatch == null || !dispatch.getAsBoolean()) return false;
            return dispatched(intent);
        }
        public RackIntent takeReady(boolean accepted, boolean contextValid, long tick, java.util.UUID body,
                                    long epoch, String dimension, BodyHandStateSnapshot fresh) {
            var intent = peekReady(accepted, contextValid, tick, body, epoch, dimension, fresh);
            if (intent != null) dispatched(intent);
            return intent;
        }

        public void clear() { intents.clear(); }
        public int size() { return intents.size(); }
    }

    private HeldUseRequestDispatch() { }

    /** Instant magazine rack classification from the resolved typed gun profile. */
    public static boolean isInstantMagazineRack(com.juicyslew.moonstation14.ms14.combat.GunProfile profile,
                                                boolean alreadyWielded) {
        if (profile == null || com.juicyslew.moonstation14.ms14.combat.GunUsePolicy.operation(profile)
                != com.juicyslew.moonstation14.ms14.combat.GunUsePolicy.Operation.MAGAZINE) return false;
        var provider = profile.magazineChamberProvider().orElse(null);
        return provider != null && provider.canRack()
                && (profile.wieldable().isEmpty() || alreadyWielded);
    }

    public static int predictionDuration(boolean instantUse, int ordinaryDuration) {
        return instantUse ? 0 : ordinaryDuration;
    }

    /** Shares the caller's established screen/text/modal admission with the ordinary key route. */
    public static boolean routePressed(boolean pressed, boolean controlDown, boolean altDown,
                                       BooleanSupplier inputAdmission, BooleanSupplier dispatch) {
        return pressed && !controlDown && !altDown && inputAdmission != null && inputAdmission.getAsBoolean()
                && dispatch != null && dispatch.getAsBoolean();
    }

    /** Deferred rack capture follows the ordinary Z route's modifier admission. */
    public static boolean mayCaptureRackPress(boolean pressed, boolean handled, boolean controlDown, boolean altDown) {
        return pressed && !handled && !controlDown && !altDown;
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
