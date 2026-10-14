package com.juicyslew.moonstation14.ms14.hands.client;

/** Pure routing rules for medical hand gestures; validation of the actual target is separate. */
public final class MedicalUseGesturePolicy {
    public enum Kind { NONE, HEALING, HEALTH_ANALYZER, SYRINGE }
    public enum InHand { NONE, SELF_TREAT, TOGGLE_ANALYZER, SELF_SCAN, CYCLE_SYRINGE_MODE, SYRINGE_SELF_USE }
    private MedicalUseGesturePolicy() { }

    public static InHand inHand(Kind kind, boolean shift, boolean ownedWorldInput) {
        if (kind == Kind.SYRINGE) return shift && ownedWorldInput ? InHand.SYRINGE_SELF_USE : InHand.CYCLE_SYRINGE_MODE;
        if (kind == Kind.HEALTH_ANALYZER && shift && ownedWorldInput) return InHand.SELF_SCAN;
        if (kind == Kind.HEALTH_ANALYZER) return InHand.TOGGLE_ANALYZER;
        if (kind == Kind.HEALING) return InHand.SELF_TREAT;
        return InHand.NONE;
    }

    public static boolean primaryBodyUse(Kind kind, boolean validMedicalBody, boolean control, boolean windowPointer) {
        return kind != Kind.NONE && validMedicalBody && !control && !windowPointer;
    }
}

