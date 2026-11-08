package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** One-press client request projection and acknowledgement correlation. */
public final class GunShotClientPolicy {
    public record Binding(long handRevision, String activeHand, String itemToken) { }
    private final SourceRequestTracker tracker = new SourceRequestTracker();

    public void bind(UUID body,long epoch) { tracker.bind(body,epoch); }
    public GunShotRequest submit(Binding binding,long now,long duration,Vec3 aim) {
        if(binding==null || aim==null)return null;
        Long seq=tracker.begin(new SourceRequestTracker.SourceKey("HELD_GUN",binding.itemToken()),now,duration);
        return seq==null?null:new GunShotRequest(tracker.epoch(),seq,binding.handRevision(),binding.activeHand(),binding.itemToken(),aim);
    }
    public boolean acknowledge(GunShotReply reply,long now) {
        return reply!=null && tracker.acknowledge(reply.bodyUUID(),reply.epoch(),reply.sequence(),now,reply.remainingTicks());
    }
    public boolean timeout(long now,long ticks){return tracker.timeout(now,ticks);}
    public void clear(){tracker.clear();}
    public UUID body(){return tracker.body();} public long epoch(){return tracker.epoch();} public long sequence(){return tracker.sequence();}
    public boolean pending(){return tracker.pending();} public long projectedDeadline(){return tracker.projectedDeadline();}
}
