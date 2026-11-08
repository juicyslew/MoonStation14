package com.juicyslew.moonstation14.ms14.testsupport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Explicit-clock test transport with controllable delay, drop, duplicate, and reorder delivery. */
public final class DeterministicPacketScheduler<T> {
    private record Packet<T>(long due, long order, T value) { }
    private final List<Packet<T>> queue = new ArrayList<>();
    private long now;
    private long order;

    public long now() { return now; }
    public void send(long delay, T value) { queue.add(new Packet<>(now + Math.max(0, delay), order++, value)); }
    public void duplicate(long delay, T value) { send(delay, value); send(delay, value); }
    public void dropNext() { if (!queue.isEmpty()) queue.removeFirst(); }
    public void advance(long tick, Consumer<T> receiver) {
        now = tick;
        var ready = queue.stream().filter(packet -> packet.due() <= now)
                .sorted(Comparator.comparingLong(Packet<T>::due).thenComparingLong(Packet<T>::order)).toList();
        queue.removeAll(ready);
        ready.forEach(packet -> receiver.accept(packet.value()));
    }
    public int pending() { return queue.size(); }
}
