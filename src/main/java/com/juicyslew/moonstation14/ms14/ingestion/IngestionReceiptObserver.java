package com.juicyslew.moonstation14.ms14.ingestion;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Ephemeral server-side observation of completed timed-ingestion transactions. */
public final class IngestionReceiptObserver {
    private static final CopyOnWriteArrayList<Consumer<Receipt>> LISTENERS = new CopyOnWriteArrayList<>();
    private IngestionReceiptObserver() { }
    public record Receipt(UUID actorId, UUID bodyId, String sourceKey, long acceptedCents) { }
    public static AutoCloseable observe(Consumer<Receipt> listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }
    static void publish(Receipt receipt) {
        for (var listener : LISTENERS) try { listener.accept(receipt); } catch (RuntimeException ignored) { }
    }
}
