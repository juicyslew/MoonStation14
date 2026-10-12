package com.juicyslew.moonstation14.ms14.ingestion;

/** Monotonic request ordering scoped to one controlled-body epoch. */
final class BodyOralSequenceGate {
    private long epoch, sequence;
    boolean admit(long requestEpoch, long requestSequence) {
        if (requestEpoch <= 0 || requestSequence <= 0) return false;
        if (requestEpoch < epoch) return false;
        if (epoch != requestEpoch) { epoch = requestEpoch; sequence = 0; }
        if (requestSequence <= sequence) return false;
        sequence = requestSequence;
        return true;
    }
}
