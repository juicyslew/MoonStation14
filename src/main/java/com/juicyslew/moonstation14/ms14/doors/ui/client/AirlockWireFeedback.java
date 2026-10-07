package com.juicyslew.moonstation14.ms14.doors.ui.client;

import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequest;

/** User-facing wording for authoritative wire-command results. */
public final class AirlockWireFeedback {
    private AirlockWireFeedback() { }

    public static String message(AirlockWireSnapshot.Rejection rejection) {
        return rejection == AirlockWireSnapshot.Rejection.STUNNED
                ? "Cannot interact while stunned" : rejection == AirlockWireSnapshot.Rejection.NONE ? null
                : "Action rejected; refreshing";
    }

    /** Passive query acknowledgements refresh authoritative state without replacing outstanding feedback. */
    public static String afterResponse(String current, AirlockWireSnapshot response, AirlockWireRequest.Operation operation) {
        if (!response.accepted()) return message(response.rejection());
        return operation == AirlockWireRequest.Operation.QUERY ? current : null;
    }
}
