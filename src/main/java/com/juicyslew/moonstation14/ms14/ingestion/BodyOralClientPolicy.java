package com.juicyslew.moonstation14.ms14.ingestion;

/** Client-side gesture eligibility only; server-side capability remains authoritative. */
public final class BodyOralClientPolicy {
    private BodyOralClientPolicy() { }
    public enum AlternateTarget { ITEM, PUDDLE, NONE }
    /** A supported item actually nominated by the existing ray selector wins; ambient items do not. */
    public static AlternateTarget alternateTarget(boolean eligibleItemOnRay, boolean eligiblePuddleOnRay) {
        if (eligibleItemOnRay) return AlternateTarget.ITEM;
        return eligiblePuddleOnRay ? AlternateTarget.PUDDLE : AlternateTarget.NONE;
    }
    public static boolean mayUseHandlessAlternate(boolean committedCharacterBody, boolean mouth,
                                                   boolean hasHands, boolean stateAvailable) {
        return committedCharacterBody && mouth && !hasHands && !stateAvailable;
    }
}
