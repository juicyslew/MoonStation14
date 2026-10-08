package com.juicyslew.moonstation14.ms14.doors;

import java.util.UUID;

/** Persistent authoritative airlock controls/state; every published mutation advances revision. */
public record AirlockState(UUID identity,long revision,DoorPhase phase,boolean powered,boolean boltsDown,
                           boolean safety,boolean autoClose,long transitionAt,long autoCloseAt,
                           boolean powerWireOneCut,boolean powerWireTwoCut,long powerPulseUntil,
                           boolean powerPulseLatched,boolean boltWireCut,boolean boltLightWireCut,
                           boolean timingWireCut,boolean safetyWireCut,boolean boltLightsEnabled,
                           boolean receiverPowerDisabled,double autoCloseModifier,double safetyModifier,
                            long safetyModifierUntil,long timingModifierUntil,boolean aiInteractWireCut,boolean logWireCut,
                            boolean logCircuitDisabled,long logPulseUntil) {
    public enum DoorPhase { CLOSED, OPENING, OPENING_PASSABLE, OPEN, CLOSING, CLOSING_PASSABLE, DENYING }
    public enum Wire { POWER_ONE, POWER_TWO, LOG, BOLT, BOLT_LIGHT, TIMING, SAFETY, AI_INTERACT }
    public enum WireAction { CUT, MEND, PULSE }

    public AirlockState {
        if(identity==null||revision<0||phase==null||!finitePositive(autoCloseModifier)
                ||!Double.isFinite(safetyModifier)||safetyModifier<0||safetyModifier>1)
            throw new IllegalArgumentException("invalid airlock state");
    }
    /** Source-compatible constructors default the added AI/Log physical circuit state. */
    public AirlockState(UUID identity,long revision,DoorPhase phase,boolean powered,boolean boltsDown,
                        boolean safety,boolean autoClose,long transitionAt,long autoCloseAt,
                        boolean powerWireOneCut,boolean powerWireTwoCut,long powerPulseUntil,
                        boolean powerPulseLatched,boolean boltWireCut,boolean boltLightWireCut,
                        boolean timingWireCut,boolean safetyWireCut,boolean boltLightsEnabled,
                        boolean receiverPowerDisabled,double autoCloseModifier,double safetyModifier,
                        long safetyModifierUntil,long timingModifierUntil) {
        this(identity,revision,phase,powered,boltsDown,safety,autoClose,transitionAt,autoCloseAt,
                powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,
                boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,receiverPowerDisabled,
                autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil,false,false,false,0);
    }
    public AirlockState(UUID identity,long revision,DoorPhase phase,boolean powered,boolean boltsDown,boolean safety,boolean autoClose,long transitionAt,long autoCloseAt,
                        boolean powerWireOneCut,boolean powerWireTwoCut,long powerPulseUntil,boolean powerPulseLatched,boolean boltWireCut,boolean boltLightWireCut,
                        boolean timingWireCut,boolean safetyWireCut,boolean boltLightsEnabled,boolean receiverPowerDisabled,double autoCloseModifier,double safetyModifier,
                        long safetyModifierUntil,long timingModifierUntil,boolean aiInteractWireCut,boolean logWireCut) {
        this(identity,revision,phase,powered,boltsDown,safety,autoClose,transitionAt,autoCloseAt,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,
                boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,receiverPowerDisabled,autoCloseModifier,safetyModifier,
                safetyModifierUntil,timingModifierUntil,aiInteractWireCut,logWireCut,false,0);
    }
    public static AirlockState initial(UUID identity,boolean safety,boolean autoClose){return initial(identity,safety,autoClose,true);}
    public static AirlockState initial(UUID identity,boolean safety,boolean autoClose,boolean boltLightsEnabled){
        return new AirlockState(identity,0,DoorPhase.CLOSED,false,false,safety,autoClose,0,0,
                false,false,0,false,false,false,false,false,boltLightsEnabled,false,1,1,0,0);
    }
    public boolean powerAvailable(){return powered&&!receiverPowerDisabled;}
    public AirlockState bumpRevision(){return copy(phase,boltsDown,transitionAt,autoCloseAt,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);}
    public boolean canOpenOrClose(){return powerAvailable()&&!boltsDown;}
    public boolean boltLightsVisible(){return powerAvailable()&&boltsDown&&boltLightsEnabled&&!boltLightWireCut;}
    public boolean allowsPry(boolean poweredPryCapability){return !boltsDown&&(!powerAvailable()||poweredPryCapability);}
    public boolean doorPassable(){return phase==DoorPhase.OPENING_PASSABLE||phase==DoorPhase.OPEN||phase==DoorPhase.CLOSING;}
    public long nextDeadline(){long next=Long.MAX_VALUE;for(long d:new long[]{transitionAt,autoCloseAt,powerPulseUntil,safetyModifierUntil,timingModifierUntil,logPulseUntil})if(d>0)next=Math.min(next,d);return next==Long.MAX_VALUE?0:next;}
    public AirlockState withPower(boolean actualPower){
        if(actualPower==powered)return this;
        boolean bolts=boltsDown;
        if(actualPower&&!powered&&!receiverPowerDisabled&&boltWireCut)bolts=true;
        AirlockState result=copy(phase,bolts,transitionAt,!actualPower&&phase==DoorPhase.OPEN?0:autoCloseAt,actualPower,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,actualPower?safetyModifierUntil:0,actualPower?timingModifierUntil:0);
        return !actualPower&&result.logPulseUntil>0?result.withLogCircuit(true,0):result;
    }
    public AirlockState open(long tick,boolean obstructed,double firstHalfSeconds){
        if(!canOpenOrClose()||phase!=DoorPhase.CLOSED||(obstructed&&safetyActive(tick)))return this;
        return copy(DoorPhase.OPENING,boltsDown,tick+ticks(firstHalfSeconds),0,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
    }
    /** SS14 PriedEvent toggles a closed/open door and ignores intermediate transitions. */
    public AirlockState pry(long tick,double firstHalfSeconds){
        if(powerAvailable()||boltsDown)return this;
        if(phase==DoorPhase.CLOSED)return copy(DoorPhase.OPENING,boltsDown,tick+ticks(firstHalfSeconds),0,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
        if(phase==DoorPhase.OPEN)return copy(DoorPhase.CLOSING,boltsDown,tick+ticks(firstHalfSeconds),0,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
        return this;
    }
    public AirlockState afterPartialCloseBlocked(long tick,double retrySeconds){if(phase!=DoorPhase.CLOSING_PASSABLE)return this;return copy(DoorPhase.OPEN,boltsDown,0,tick+ticks(retrySeconds),powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);}
    /** Scheduled unsafe-crush release; caller must freshly admit power and bolt gates. */
    public AirlockState reopenAfterCrush(long tick,double firstHalfSeconds){
        if(phase!=DoorPhase.CLOSED||!canOpenOrClose())return this;
        return copy(DoorPhase.OPENING,boltsDown,tick+ticks(firstHalfSeconds),0,
                powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,
                boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,
                safetyModifier,safetyModifierUntil,timingModifierUntil);
    }
    public AirlockState scheduleAutoCloseAt(long tick){return copy(phase,boltsDown,transitionAt,tick,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);}
    /** Mirrors Airlock power-change autoclose cancellation/rescheduling for actual or receiver-disabled power. */
    public AirlockState powerAvailabilityChanged(boolean wasAvailable,boolean wasBolted,long tick,double autoCloseSeconds){
        if(phase!=DoorPhase.OPEN)return this;
        if(!powerAvailable())return autoCloseAt==0?this:scheduleAutoCloseAt(0);
        if((!wasAvailable||wasBolted&&!boltsDown)&&autoClose&&!boltsDown)
            return scheduleAutoCloseAt(tick+ticks(autoCloseSeconds*autoCloseModifier));
        return this;
    }
    public AirlockState deny(long tick,double durationSeconds){
        if(phase!=DoorPhase.CLOSED||boltsDown)return this;
        return copy(DoorPhase.DENYING,boltsDown,tick+ticks(durationSeconds),0,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
    }
    public AirlockState close(long tick,boolean obstructed,double firstHalfSeconds){
        if(!canOpenOrClose()||phase!=DoorPhase.OPEN||(obstructed&&safetyActive(tick)))return this;
        return copy(DoorPhase.CLOSING,boltsDown,tick+ticks(firstHalfSeconds),0,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
    }
    public AirlockState tick(long tick,double openingSecondHalfSeconds,double closingSecondHalfSeconds){return tick(tick,openingSecondHalfSeconds,closingSecondHalfSeconds,5);}
    public AirlockState tick(long tick,double openingSecondHalfSeconds,double closingSecondHalfSeconds,double autoCloseSeconds){
        if(safetyModifierUntil>0&&tick>=safetyModifierUntil)return copySafety(true,phase,boltsDown,transitionAt,autoCloseAt,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,1,0,timingModifierUntil);
        if(timingModifierUntil>0&&tick>=timingModifierUntil)return copy(phase,boltsDown,transitionAt,autoCloseAt,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,1,safetyModifier,safetyModifierUntil,0);
        if(powerPulseUntil>0&&tick>=powerPulseUntil)return copy(phase,boltsDown,transitionAt,autoCloseAt,powered,powerWireOneCut&&powerWireTwoCut,powerWireOneCut,powerWireTwoCut,0,false,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
        if(phase==DoorPhase.OPENING&&tick>=transitionAt)return copy(DoorPhase.OPENING_PASSABLE,boltsDown,tick+ticks(openingSecondHalfSeconds),0,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
         if(phase==DoorPhase.OPENING_PASSABLE&&tick>=transitionAt)return copy(DoorPhase.OPEN,boltsDown,0,autoClose&&powerAvailable()&&!boltsDown?tick+ticks(autoCloseSeconds*autoCloseModifier):0,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
        if(phase==DoorPhase.CLOSING&&tick>=transitionAt)return copy(DoorPhase.CLOSING_PASSABLE,boltsDown,tick+ticks(closingSecondHalfSeconds),0,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
        if(phase==DoorPhase.CLOSING_PASSABLE&&tick>=transitionAt||phase==DoorPhase.DENYING&&tick>=transitionAt)return copy(DoorPhase.CLOSED,boltsDown,0,0,powered,receiverPowerDisabled,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil);
         if(logPulseUntil>0&&tick>=logPulseUntil&&!logWireCut)return withLogCircuit(false,0);
         return this;
    }
    /**
     * SS14 PowerWireAction owns one shared pulse flag/timer for both power wires. Mending mid-pulse
     * cancels that timer but deliberately leaves Pulsed=true, so disablement remains latched.
     * Pinned source: Content.Server/Power/PowerWireAction.cs (c9df5ef5d675b0d1d226828bddf6b78c28502d91).
     */
    public AirlockState applyWire(Wire wire,WireAction action,long tick){
        return applyWire(wire,action,tick,30,15,0.01,0.5,30,30,30);
    }
    public AirlockState applyWire(Wire wire,WireAction action,long tick,com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData.Wires resolved){
        if(resolved==null)throw new IllegalArgumentException("resolved wires required");
        return applyWire(wire,action,tick,resolved.powerOnePulseSeconds(),resolved.powerTwoPulseSeconds(),resolved.timingCutModifier(),resolved.timingPulseModifier(),resolved.timingPulseSeconds(),resolved.safetyPulseSeconds(),resolved.logPulseSeconds());
    }
    public AirlockState applyWire(Wire wire,WireAction action,long tick,double powerOneSeconds,double powerTwoSeconds,double timingCut,double timingPulse,double timingPulseSeconds,double safetyPulseSeconds){
        return applyWire(wire,action,tick,powerOneSeconds,powerTwoSeconds,timingCut,timingPulse,timingPulseSeconds,safetyPulseSeconds,30);
    }
    public AirlockState applyWire(Wire wire,WireAction action,long tick,double powerOneSeconds,double powerTwoSeconds,double timingCut,double timingPulse,double timingPulseSeconds,double safetyPulseSeconds,double logPulseSeconds){
        if(wire==null||action==null)throw new IllegalArgumentException("wire/action required");
        for(double d:new double[]{powerOneSeconds,powerTwoSeconds,timingCut,timingPulse,timingPulseSeconds,safetyPulseSeconds,logPulseSeconds})if(!Double.isFinite(d)||d<=0)throw new IllegalArgumentException("invalid resolved wire parameter");
        boolean alreadyCut=switch(wire){case POWER_ONE->powerWireOneCut;case POWER_TWO->powerWireTwoCut;case LOG->logWireCut;case BOLT->boltWireCut;case BOLT_LIGHT->boltLightWireCut;case TIMING->timingWireCut;case SAFETY->safetyWireCut;case AI_INTERACT->aiInteractWireCut;};
        if(action==WireAction.CUT&&alreadyCut||action==WireAction.MEND&&wire!=Wire.LOG&&!alreadyCut&&!(powerPulseLatched&&(wire==Wire.POWER_ONE||wire==Wire.POWER_TWO))||action==WireAction.PULSE&&(wire==Wire.POWER_ONE||wire==Wire.POWER_TWO)&&powerPulseLatched||action==WireAction.PULSE&&wire==Wire.LOG&&alreadyCut)return this;
        boolean p1=powerWireOneCut,p2=powerWireTwoCut,bw=boltWireCut,bl=boltLightWireCut,tw=timingWireCut,sw=safetyWireCut,ai=aiInteractWireCut,log=logWireCut;
        boolean logDisabled=logCircuitDisabled;long logUntil=logPulseUntil;
        boolean bolts=boltsDown,lights=boltLightsEnabled,disabled=receiverPowerDisabled,pulsed=powerPulseLatched,safeEnabled=safety;
        long pulseUntil=powerPulseUntil,safeUntil=safetyModifierUntil,timingUntil=timingModifierUntil;
        double time=autoCloseModifier,safe=safetyModifier;
        switch(wire){
            case POWER_ONE,POWER_TWO->{
                if(action==WireAction.CUT){if(wire==Wire.POWER_ONE)p1=true;else p2=true;disabled=pulsed||(p1&&p2);}
                else if(action==WireAction.MEND){if(wire==Wire.POWER_ONE)p1=false;else p2=false;if(pulsed){pulseUntil=0;disabled=true;}else disabled=p1&&p2;}
                // Source wire defaults are 30 s and 15 s; once Pulsed is set, the second wire's
                // Pulse returns without extending the shared timer.
                else if(!pulsed){pulsed=true;disabled=true;pulseUntil=tick+ticks(wire==Wire.POWER_ONE?powerOneSeconds:powerTwoSeconds);}
            }
            case BOLT->{bw=action==WireAction.CUT;if(action==WireAction.CUT&&powerAvailable()&&!bolts)bolts=true;else if(action==WireAction.PULSE&&powerAvailable())bolts=!bolts;else if(action==WireAction.PULSE&&!powerAvailable()&&!bolts)bolts=true;}
            case BOLT_LIGHT->{bl=action==WireAction.CUT;if(action==WireAction.PULSE)lights=!lights;else lights=!bl;}
            case TIMING->{tw=action==WireAction.CUT;time=action==WireAction.CUT?timingCut:action==WireAction.PULSE?timingPulse:1;timingUntil=action==WireAction.PULSE?tick+ticks(timingPulseSeconds):0;}
            case SAFETY->{sw=action==WireAction.CUT;safeEnabled=action!=WireAction.CUT&&action!=WireAction.PULSE;safe=action==WireAction.CUT||action==WireAction.PULSE?0:1;safeUntil=action==WireAction.PULSE?tick+ticks(safetyPulseSeconds):0;}
            // AI has no local gameplay consumer and remains an intentional pulse no-op.
            // Log owns a circuit-disable timer but no access-event service.
            case LOG->{if(action==WireAction.CUT){log=true;logDisabled=true;logUntil=0;}else if(action==WireAction.MEND){log=false;logDisabled=false;}else if(logUntil==0){logDisabled=true;logUntil=powered?tick+ticks(logPulseSeconds):0;}}
            case AI_INTERACT->{if(action!=WireAction.PULSE)ai=action==WireAction.CUT;}
        }
        return new AirlockState(identity,revision+1,phase,powered,bolts,safeEnabled,autoClose,transitionAt,autoCloseAt,
                p1,p2,pulseUntil,pulsed,bw,bl,tw,sw,lights,disabled,time,safe,safeUntil,timingUntil,ai,log,logDisabled,logUntil);
    }
    public boolean safetyActive(long tick){return safety&&safetyModifier>0&&(safetyModifierUntil==0||tick<safetyModifierUntil);}
    private AirlockState copy(DoorPhase phase,boolean bolts,long transition,long auto,boolean power,boolean disabled,boolean p1,boolean p2,long pulseUntil,boolean pulsed,boolean bw,boolean bl,boolean tw,boolean sw,boolean lights,double timing,double safe,long safeUntil,long timingUntil){
        return copySafety(safety,phase,bolts,transition,auto,power,disabled,p1,p2,pulseUntil,pulsed,bw,bl,tw,sw,lights,timing,safe,safeUntil,timingUntil);
    }
    private AirlockState copySafety(boolean safety,DoorPhase phase,boolean bolts,long transition,long auto,boolean power,boolean disabled,boolean p1,boolean p2,long pulseUntil,boolean pulsed,boolean bw,boolean bl,boolean tw,boolean sw,boolean lights,double timing,double safe,long safeUntil,long timingUntil){return new AirlockState(identity,revision+1,phase,power,bolts,safety,autoClose,transition,auto,p1,p2,pulseUntil,pulsed,bw,bl,tw,sw,lights,disabled,timing,safe,safeUntil,timingUntil,aiInteractWireCut,logWireCut,logCircuitDisabled,logPulseUntil);}
    private AirlockState withLogCircuit(boolean disabled,long until){return new AirlockState(identity,revision+1,phase,powered,boltsDown,safety,autoClose,transitionAt,autoCloseAt,powerWireOneCut,powerWireTwoCut,powerPulseUntil,powerPulseLatched,boltWireCut,boltLightWireCut,timingWireCut,safetyWireCut,boltLightsEnabled,receiverPowerDisabled,autoCloseModifier,safetyModifier,safetyModifierUntil,timingModifierUntil,aiInteractWireCut,logWireCut,disabled,until);}
    private static long ticks(double seconds){if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("invalid transition duration");return Math.max(1,(long)Math.ceil(seconds*20));}
    private static boolean finitePositive(double value){return Double.isFinite(value)&&value>0;}
}
