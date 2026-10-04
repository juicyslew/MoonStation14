package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

/** Pure minimum-delay/interruption predicate for self-entry. */
public final class DisposalUnitEntryPolicy {
    private DisposalUnitEntryPolicy(){}
    public enum TickResult { WAIT, CANCEL, COMPLETE }
    public static TickResult tick(long elapsed,long requiredTicks,boolean exactMenu,boolean sameUnit,boolean sameBody,
                                      boolean authority,boolean canAct,boolean unmoved,boolean undamaged,boolean capabilityUnchanged) {
        if(!exactMenu||!sameUnit||!sameBody||!authority||!canAct||!unmoved||!undamaged||!capabilityUnchanged)return TickResult.CANCEL;
        return elapsed<requiredTicks?TickResult.WAIT:TickResult.COMPLETE;
    }
    public static boolean mayOperate(boolean menuAuthority,boolean reachable,boolean contained,boolean exactOwner,boolean otherOwner){
        return menuAuthority&&reachable&&!otherOwner&&(!contained||exactOwner);
    }
}
