package com.juicyslew.moonstation14.entities;

/** Cue admission only; damage and projectile contact handling remain in the entity. */
public final class EmitterBoltImpactPolicy {
    public enum Contact { LIVING, FIELD_GENERATOR, BLOCK, NONE }
    private EmitterBoltImpactPolicy() { }

    public static boolean emits(Contact contact, boolean removed, boolean hasCollisionShape) {
        if (removed) return false;
        return contact == Contact.LIVING || contact == Contact.FIELD_GENERATOR
                || contact == Contact.BLOCK && hasCollisionShape;
    }

    public static float spriteScale(int framePixels) {
        if (framePixels <= 0) throw new IllegalArgumentException("framePixels must be positive");
        return 16f / framePixels;
    }
}
