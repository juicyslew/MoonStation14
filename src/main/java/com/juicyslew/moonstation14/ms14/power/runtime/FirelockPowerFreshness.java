package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;
import java.util.UUID;

/** Pure acceptance predicate for fresh, topology-reconciled firelock delivery knowledge. */
public final class FirelockPowerFreshness {
    private FirelockPowerFreshness() { }
    /** Empty, fully reconciled receiver space is known-off; nonempty space needs a known selected receiver. */
    public static boolean receiverKnowledge(boolean topologyReady,boolean hasCandidates,boolean hasKnownReceiver){
        return topologyReady&&(!hasCandidates||hasKnownReceiver);
    }
    public static boolean accepts(UUID expectedIdentity,UUID sampledIdentity,FirelockData expectedPrototype,
            FirelockData sampledPrototype,long sampleGeneration,long currentGeneration,long sampleTick,long now,
            long maxAge,boolean providerLoadedAndValid,boolean breakerClosed,long sampleBreakerRevision,
            long currentBreakerRevision,double deliveredWatts){
        if(expectedIdentity==null||!expectedIdentity.equals(sampledIdentity)||expectedPrototype==null
                ||expectedPrototype!=sampledPrototype||sampleGeneration!=currentGeneration||sampleTick>now
                ||now-sampleTick>maxAge||!Double.isFinite(deliveredWatts)||deliveredWatts<0
                ||deliveredWatts>expectedPrototype.apcPowerReceiver().requestedWatts())return false;
        if(!providerLoadedAndValid)
            return sampleBreakerRevision==-1&&currentBreakerRevision==-1&&deliveredWatts==0;
        if(sampleBreakerRevision!=currentBreakerRevision)return false;
        return breakerClosed||deliveredWatts==0;
    }
}
