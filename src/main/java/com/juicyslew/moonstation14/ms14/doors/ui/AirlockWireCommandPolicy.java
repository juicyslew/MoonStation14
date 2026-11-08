package com.juicyslew.moonstation14.ms14.doors.ui;

/** Small pure command gates; identity, actor and active ItemStack authority stay in the service. */
public final class AirlockWireCommandPolicy {
    private AirlockWireCommandPolicy() { }
    public static boolean revisionMatches(long current, long expected) { return current >= 0 && current == expected; }
    public static boolean toolAllows(AirlockWireRequest.Operation operation, boolean cutting, boolean pulsing,
                                     boolean wireCut) {
        return switch (operation) {
            case CLICK -> cutting || pulsing;
            case CUT, MEND -> cutting;
            case PULSE -> pulsing && !wireCut;
            case QUERY -> true;
        };
    }

    /** Resolve a click from the tool currently held by the authenticated body, never client intent.
     * When a tool has both qualities, cutting takes precedence over pulsing.
     */
    public static AirlockWireRequest.Operation resolveClick(boolean cutting, boolean pulsing, boolean wireCut) {
        if (cutting) return wireCut ? AirlockWireRequest.Operation.MEND : AirlockWireRequest.Operation.CUT;
        if (pulsing && !wireCut) return AirlockWireRequest.Operation.PULSE;
        return null;
    }
}

