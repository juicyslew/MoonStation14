package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

/** Pure paired world/private-solve clock evidence for a fixed-duration steady window. */
record TegFixtureWindowClock(long worldTick, long sampleTick) {
    TegFixtureWindowClock {
        if (worldTick < 0 || sampleTick < 0) throw new IllegalArgumentException("window clock anchors must be non-negative");
    }

    static boolean isFreshSample(long observedSampleTick, long priorSampleTick) {
        return observedSampleTick >= 0 && priorSampleTick >= 0 && observedSampleTick > priorSampleTick;
    }

    void requireExactWindow(long worldNow, long sampleNow, long matchedElapsed, long requiredTicks) {
        long worldElapsed = worldNow - worldTick;
        long privateElapsed = sampleNow - sampleTick;
        if (worldElapsed != requiredTicks || privateElapsed != requiredTicks || matchedElapsed != requiredTicks)
            throw new IllegalArgumentException("window clock mismatch: world=" + worldElapsed + " private=" + privateElapsed
                    + " matched=" + matchedElapsed + " required=" + requiredTicks);
    }
}
