package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;

/** Pure presentation policy shared by authored geometry and renderer consumers. */
public final class FirelockMotion {
    private FirelockMotion() { }
    public static double openness(FirelockController.Presentation presentation, FirelockData data, double doubleTick) {
        if(presentation==null||presentation.state()==null||data==null||!Double.isFinite(doubleTick))return 0;
        var phase=presentation.state().phase();
        if(phase==AirlockState.DoorPhase.OPEN)return 1;
        if(phase==AirlockState.DoorPhase.CLOSED||phase==AirlockState.DoorPhase.DENYING)return 0;
        boolean opening=phase==AirlockState.DoorPhase.OPENING||phase==AirlockState.DoorPhase.OPENING_PASSABLE;
        boolean closing=phase==AirlockState.DoorPhase.CLOSING||phase==AirlockState.DoorPhase.CLOSING_PASSABLE;
        if(!opening&&!closing)return presentation.authoritativeLatch()?0:1;
        double duration=(opening?data.openingAnimationSeconds():data.closingAnimationSeconds())*20.0;
        if(!Double.isFinite(duration)||duration<=0)return opening?1:0;
        double amount=Math.max(0,Math.min(1,(doubleTick-presentation.motionStartedAt())/duration));
        return opening?amount:1-amount;
    }
}
