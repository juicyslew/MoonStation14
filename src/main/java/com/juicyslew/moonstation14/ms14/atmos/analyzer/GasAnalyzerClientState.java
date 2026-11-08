package com.juicyslew.moonstation14.ms14.atmos.analyzer;

/** Small data-only facade for the later window; rejects stale generations and duplicate snapshots. */
public final class GasAnalyzerClientState {
    private static GasAnalyzerSnapshot current;
    private static long controlSequence;
    private GasAnalyzerClientState() { }
    public static synchronized void accept(GasAnalyzerSnapshot snapshot) {
        if (snapshot == null) return;
        if (current != null && (snapshot.generation() < current.generation()
                || snapshot.generation() == current.generation() && !snapshot.session().equals(current.session())
                || snapshot.generation() == current.generation() && snapshot.sequence() <= current.sequence())) return;
        current = snapshot;
    }
    public static synchronized GasAnalyzerSnapshot current() { return current; }
    public static synchronized long sendControl(int distance, boolean close) {
        if (current == null || current.closed()) return -1;
        long sequence = ++controlSequence;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new GasAnalyzerControl(current.session(),
                current.generation(), current.epoch(), sequence, distance, close));
        return sequence;
    }
    public static synchronized void clear() { current = null; controlSequence = 0; }
}
