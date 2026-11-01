package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity;

/** Independent firelock phase and environmental state; deliberately excludes airlock wires/bolts. */
public record FirelockState(AirlockState.DoorPhase phase, boolean powered, boolean locked, boolean pressureWarning, boolean temperatureWarning,
                            long transitionAt, long pryCooldownUntil, long revision) {
    public enum Observation { AVAILABLE, UNAVAILABLE }
    public record Environment(Observation observation,double pressureKpa,double temperatureK) {
        public Environment { if(observation==null)throw new IllegalArgumentException("observation required"); if(observation==Observation.AVAILABLE&&(!Double.isFinite(pressureKpa)||!Double.isFinite(temperatureK)))throw new IllegalArgumentException("non-finite atmosphere"); }
        public static Environment unavailable(){return new Environment(Observation.UNAVAILABLE,0,0);}
    }
    public enum Pry { VETOED, ALLOWED }
    public enum Close { UNCHANGED, CLOSING, ABORTED_OBSTRUCTION }
    public static FirelockState initial(FirelockData data){return new FirelockState(data.initiallyOpen()?AirlockState.DoorPhase.OPEN:AirlockState.DoorPhase.CLOSED,false,false,false,false,0,0,0);}
    public FirelockState withPower(boolean value){return value==powered?this:copy(phase,value,locked,pressureWarning,temperatureWarning,transitionAt,pryCooldownUntil);}
    public FirelockState observe(FirelockData data,Environment first,Environment second){
        if(phase!=AirlockState.DoorPhase.CLOSED&&phase!=AirlockState.DoorPhase.DENYING)return this;
        if(first.observation()==Observation.UNAVAILABLE||second.observation()==Observation.UNAVAILABLE)return this;
        double dp=Math.abs(first.pressureKpa()-second.pressureKpa()),dt=Math.abs(first.temperatureK()-second.temperatureK());
        boolean pw=dp>data.pressureDifferenceKpa(),tw=dt>data.temperatureDifferenceK();
        return pw==pressureWarning&&tw==temperatureWarning?this:new FirelockState(phase,powered,pw||tw,pw,tw,transitionAt,pryCooldownUntil,revision+1);
    }
    public boolean canOpen(boolean userOverride){return powered&&phase==AirlockState.DoorPhase.CLOSED&&(!locked||userOverride);}
    public FirelockState open(FirelockData data,long now,boolean userOverride){return canOpen(userOverride)?copy(AirlockState.DoorPhase.OPENING,powered,locked,pressureWarning,temperatureWarning,now+ticks(data.door().openTimeOneSeconds()),pryCooldownUntil):this;}
    public FirelockState alarm(FirelockData data,AtmosAlarmSeverity alarm,long now,boolean closeObstructed){if(!powered)return this;if(alarm==AtmosAlarmSeverity.DANGER)return emergencyPressureStop(data,now,closeObstructed).state();if(alarm==AtmosAlarmSeverity.NORMAL&&phase==AirlockState.DoorPhase.CLOSED)return open(data,now,false);return this;}
    public record CloseResult(FirelockState state,Close result){}
    /** Explicit solver closure has no generic power admission gate. */
    public CloseResult emergencyPressureStop(FirelockData data,long now,boolean obstructed){
        if(phase!=AirlockState.DoorPhase.OPEN||now<pryCooldownUntil)return new CloseResult(this,Close.UNCHANGED);
        if(obstructed)return new CloseResult(copy(AirlockState.DoorPhase.OPEN,powered,locked,pressureWarning,temperatureWarning,0,pryCooldownUntil),Close.ABORTED_OBSTRUCTION);
        // EmergencyPressureStop invokes OnPartialClose immediately: the leaf is collidable now.
        return new CloseResult(copy(AirlockState.DoorPhase.CLOSING_PASSABLE,powered,locked,pressureWarning,temperatureWarning,now+ticks(data.door().closeTimeTwoSeconds()),pryCooldownUntil),Close.CLOSING);
    }
    public Pry pryVeto(boolean poweredToolCapability,boolean strongToolCapability){return powered&&!poweredToolCapability&&!strongToolCapability?Pry.VETOED:Pry.ALLOWED;}
    public FirelockState successfulPry(FirelockData data,long now,boolean sharedCanPry,boolean welded){if(!sharedCanPry||welded)return this;if(phase!=AirlockState.DoorPhase.CLOSED&&phase!=AirlockState.DoorPhase.OPEN)return this;AirlockState.DoorPhase next=phase==AirlockState.DoorPhase.CLOSED?AirlockState.DoorPhase.OPENING:AirlockState.DoorPhase.CLOSING;double half=phase==AirlockState.DoorPhase.CLOSED?data.door().openTimeOneSeconds():data.door().closeTimeOneSeconds();return copy(next,powered,locked,pressureWarning,temperatureWarning,now+ticks(half),now+ticks(data.pryCooldownSeconds()));}
    public FirelockState tick(FirelockData data,long now){
        if(transitionAt==0||now<transitionAt)return this;
        return switch(phase){case OPENING->copy(AirlockState.DoorPhase.OPENING_PASSABLE,powered,locked,pressureWarning,temperatureWarning,now+ticks(data.door().openTimeTwoSeconds()),pryCooldownUntil);case OPENING_PASSABLE->copy(AirlockState.DoorPhase.OPEN,powered,locked,pressureWarning,temperatureWarning,0,pryCooldownUntil);case CLOSING->copy(AirlockState.DoorPhase.CLOSING_PASSABLE,powered,locked,pressureWarning,temperatureWarning,now+ticks(data.door().closeTimeTwoSeconds()),pryCooldownUntil);case CLOSING_PASSABLE->copy(AirlockState.DoorPhase.CLOSED,powered,locked,pressureWarning,temperatureWarning,0,pryCooldownUntil);default->this;};
    }
    public FirelockState partialCloseObstructed(long now,FirelockData data){return phase==AirlockState.DoorPhase.CLOSING_PASSABLE?copy(AirlockState.DoorPhase.OPEN,powered,locked,pressureWarning,temperatureWarning,now+ticks(data.door().openTimeTwoSeconds()),pryCooldownUntil):this;}
    /** Source Crush reopens after DoorStunTime, then follows normal opening phases without a power gate. */
    public FirelockState reopenAfterCrush(FirelockData data,long now){return phase==AirlockState.DoorPhase.CLOSED?copy(AirlockState.DoorPhase.OPENING,powered,locked,pressureWarning,temperatureWarning,now+ticks(data.door().openTimeOneSeconds()),pryCooldownUntil):this;}
    public boolean passable(){return phase==AirlockState.DoorPhase.OPENING_PASSABLE||phase==AirlockState.DoorPhase.OPEN||phase==AirlockState.DoorPhase.CLOSING;}
    private FirelockState copy(AirlockState.DoorPhase p,boolean pow,boolean lock,boolean pressureWarn,boolean tempWarn,long at,long cooldown){return new FirelockState(p,pow,lock,pressureWarn,tempWarn,at,cooldown,revision+1);}
    private static long ticks(double seconds){return Math.max(1,(long)Math.ceil(seconds*20));}
}
