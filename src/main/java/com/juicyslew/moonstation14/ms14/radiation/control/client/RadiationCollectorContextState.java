package com.juicyslew.moonstation14.ms14.radiation.control.client;

import com.juicyslew.moonstation14.ms14.radiation.control.RadiationCollectorControlSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Bounded correlation reducer: only the latest explicitly expected query may become ready. */
public final class RadiationCollectorContextState {
    public enum ScreenDecision { OPEN, REPLACE, NO_OPEN }
    private Query pending;
    private RadiationCollectorControlSnapshot ready;
    private record Query(long sequence, UUID body, long epoch, ResourceLocation dimension, BlockPos position) { }
    public void expectQuery(long sequence, UUID body, long epoch, ResourceLocation dimension, BlockPos position) {
        pending = new Query(sequence, body, epoch, dimension, position);
        ready = null;
    }
    public void beginAction() { pending = null; ready = null; }
    public void close() { beginAction(); }
    public boolean owns(RadiationCollectorControlSnapshot snapshot) { return ready == snapshot; }
    /** Decide whether a ready reply may claim the screen without displacing unrelated UI. */
    public static ScreenDecision screenDecision(boolean screenOccupied, boolean ownedCollectorScreen) {
        if (ownedCollectorScreen) return ScreenDecision.REPLACE;
        return screenOccupied ? ScreenDecision.NO_OPEN : ScreenDecision.OPEN;
    }
    public void closeOwned(RadiationCollectorControlSnapshot snapshot) {
        if (owns(snapshot)) close();
    }
    public void cancelIfAuthorityChanged(UUID body, long epoch, ResourceLocation dimension) {
        if (pending != null && (!pending.body.equals(body) || pending.epoch != epoch || !pending.dimension.equals(dimension))) close();
        if (ready != null && (!ready.bodyId().equals(body) || ready.authorityEpoch() != epoch || !ready.dimension().equals(dimension))) ready = null;
    }
    public void receive(RadiationCollectorControlSnapshot snapshot) {
        Query q = pending;
        if (q == null || snapshot.requestSequence() != q.sequence || !snapshot.accepted()
                || !q.body.equals(snapshot.bodyId()) || q.epoch != snapshot.authorityEpoch()
                || !q.dimension.equals(snapshot.dimension()) || !q.position.equals(snapshot.position())) return;
        ready = snapshot; pending = null;
    }
    public RadiationCollectorControlSnapshot ready() { return ready; }
}
