package com.juicyslew.moonstation14.entities;

/** Per-impact frame selection matching ParticleEngine.MutableSpriteSet's age mapping. */
public final class EmitterImpactFramePolicy {
    public static final int FRAME_COUNT = 5;
    private EmitterImpactFramePolicy() { }
    public static int index(int age) {
        if (age < 0) throw new IllegalArgumentException("Invalid particle age");
        return age % FRAME_COUNT;
    }
    public static boolean shouldRemoveAfterTick(int age, int lifetime) { return lifetime < 1 || age >= lifetime; }
}
