package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;

/** Keeps the local server-clock anchor stable for duplicate same-tick state snapshots. */
public final class TimedInteractionClockPolicy {
    private TimedInteractionClockPolicy() { }

    public static long receivedAt(TimedInteractionSnapshot previous, long previousReceivedAt,
                                  TimedInteractionSnapshot incoming, long now) {
        if (previous != null && incoming != null
                && previous.bodyUuid().equals(incoming.bodyUuid())
                && previous.dimension().equals(incoming.dimension())
                && previous.serverTick() == incoming.serverTick()) return previousReceivedAt;
        return now;
    }

    public static double estimatedServerTick(long snapshotTick, long receivedAt, long now, float partialTick) {
        return snapshotTick + now - receivedAt + partialTick;
    }
}
