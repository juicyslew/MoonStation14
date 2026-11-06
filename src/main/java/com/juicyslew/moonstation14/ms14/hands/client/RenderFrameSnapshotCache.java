package com.juicyslew.moonstation14.ms14.hands.client;

import java.util.function.Supplier;

/** One render-frame-only memo; explicit fresh suppliers are deliberately never memoized. */
final class RenderFrameSnapshotCache<T> {
    private long frame = Long.MIN_VALUE;
    private Object body;
    private Object level;
    private long epoch = Long.MIN_VALUE;
    private T value;
    private boolean present;

    void beginFrame(long frame) {
        if (this.frame != frame) clear();
        this.frame = frame;
    }

    T get(long frame, Object body, long epoch, Supplier<T> capture) {
        return get(frame, body, null, epoch, capture);
    }

    T get(long frame, Object body, Object level, long epoch, Supplier<T> capture) {
        if (this.frame != frame || this.body != body || this.level != level || this.epoch != epoch) {
            this.frame = frame;
            this.body = body;
            this.level = level;
            this.epoch = epoch;
            value = null;
            present = false;
        }
        if (!present) {
            value = capture.get();
            present = true;
        }
        return value;
    }

    void clear() {
        frame = Long.MIN_VALUE;
        body = null;
        level = null;
        epoch = Long.MIN_VALUE;
        value = null;
        present = false;
    }
}
