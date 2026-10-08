package com.juicyslew.moonstation14.ms14.doors.ui.client;

import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireRequest;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirlockWireFeedbackTest {
    @Test void authoritativeRejectionMessagesAreInformativeWithoutWireIdentity() {
        assertEquals("Cannot interact while stunned",AirlockWireFeedback.message(AirlockWireSnapshot.Rejection.STUNNED));
        assertEquals("Action rejected; refreshing",AirlockWireFeedback.message(AirlockWireSnapshot.Rejection.REFRESH));
        assertNull(AirlockWireFeedback.message(AirlockWireSnapshot.Rejection.NONE));
    }
    @Test void passiveQueriesPreserveStunFeedbackUntilAcceptedAction() {
        String feedback=AirlockWireFeedback.afterResponse(null,response(false,AirlockWireSnapshot.Rejection.STUNNED),AirlockWireRequest.Operation.CLICK);
        assertEquals("Cannot interact while stunned",feedback);
        feedback=AirlockWireFeedback.afterResponse(feedback,response(true,AirlockWireSnapshot.Rejection.NONE),AirlockWireRequest.Operation.QUERY);
        assertEquals("Cannot interact while stunned",feedback);
        assertNull(AirlockWireFeedback.afterResponse(feedback,response(true,AirlockWireSnapshot.Rejection.NONE),AirlockWireRequest.Operation.CLICK));
        assertEquals("Action rejected; refreshing",AirlockWireFeedback.afterResponse(null,response(false,AirlockWireSnapshot.Rejection.REFRESH),AirlockWireRequest.Operation.CLICK));
    }
    private static AirlockWireSnapshot response(boolean accepted,AirlockWireSnapshot.Rejection rejection) {
        return new AirlockWireSnapshot(1,java.util.UUID.randomUUID(),1,accepted,rejection,1,true,
                AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.ON,
                AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.OFF,
                AirlockWireIndicators.Light.ON,true,true,java.util.List.of());
    }
}
