package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** One-press client request projection and acknowledgement correlation. */
public final class GunShotClientPolicy {
    public record Binding(long handRevision, String activeHand, String itemToken) { }
    private final SourceRequestTracker tracker = new SourceRequestTracker();
    private Long pendingTrigger;
    private long submittedSequence, minimumConfirmedRevision;
    private Binding submittedBinding;

    public void bind(UUID body,long epoch) {
        if (!java.util.Objects.equals(tracker.body(), body) || tracker.epoch() != epoch) {
            pendingTrigger=null;
            submittedBinding=null;
            minimumConfirmedRevision=0;
        }
        tracker.bind(body,epoch);
    }
    public GunShotRequest submit(Binding binding,long now,long duration,Vec3 aim) {
        return submit(binding,now,duration,aim,tracker.sequence()+1,true);
    }
    public GunShotRequest submit(Binding binding,long now,long duration,Vec3 aim,long triggerId,boolean start) {
        if(binding==null || aim==null)return null;
        Long seq=tracker.begin(new SourceRequestTracker.SourceKey("HELD_GUN",binding.itemToken()),now,duration);
        if (seq==null) return null;
        pendingTrigger=triggerId;
        submittedSequence=seq; submittedBinding=binding;
        return new GunShotRequest(tracker.epoch(),seq,binding.handRevision(),binding.activeHand(),binding.itemToken(),aim,triggerId,start);
    }
    public boolean acknowledge(GunShotReply reply,long now) {
        long remaining = reply == null ? 0 : Math.max(reply.remainingTicks(), Math.max(0, tracker.projectedDeadline() - now));
        boolean matched=reply!=null && tracker.acknowledge(reply.bodyUUID(),reply.epoch(),reply.sequence(),now,remaining);
        if(matched) {
            pendingTrigger=null;
            if (reply.sequence()==submittedSequence && submittedBinding!=null
                    && java.util.Objects.equals(reply.itemToken(), submittedBinding.itemToken())
                    && reply.handRevision()>submittedBinding.handRevision())
                minimumConfirmedRevision=Math.max(minimumConfirmedRevision, reply.handRevision());
        }
        return matched;
    }
    public boolean timeout(long now,long ticks){boolean timed=tracker.timeout(now,ticks); if(timed) pendingTrigger=null; return timed;}
    public void clear(){tracker.clear(); pendingTrigger=null; submittedBinding=null; minimumConfirmedRevision=0;}
    public boolean snapshotFresh(UUID body,long epoch,String hand,String token,long revision) {
        if (!java.util.Objects.equals(tracker.body(),body) || tracker.epoch()!=epoch) return true;
        if (submittedBinding!=null && !java.util.Objects.equals(submittedBinding.itemToken(),token)) {
            minimumConfirmedRevision=0; submittedBinding=null;
        }
        return revision>=minimumConfirmedRevision;
    }
    public Long pendingTrigger(){return tracker.pending()?pendingTrigger:null;}
    public UUID body(){return tracker.body();} public long epoch(){return tracker.epoch();} public long sequence(){return tracker.sequence();}
    public boolean pending(){return tracker.pending();} public long projectedDeadline(){return tracker.projectedDeadline();}
}
