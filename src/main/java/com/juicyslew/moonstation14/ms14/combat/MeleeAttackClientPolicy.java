package com.juicyslew.moonstation14.ms14.combat;

import java.util.UUID;

/** Pure presentation-side request correlation. It never establishes gameplay authority. */
public final class MeleeAttackClientPolicy {
    public static boolean sourceDeadlineReady(long deadline, java.util.OptionalDouble estimatedServerTick) {
        return estimatedServerTick != null && estimatedServerTick.isPresent()
                && deadline <= estimatedServerTick.getAsDouble();
    }
    public record Binding(MeleeWeaponResolver.Kind kind, long handRevision, String activeHand, String itemToken) { }
    private final SourceRequestTracker tracker = new SourceRequestTracker();
    private Binding binding;

    public void bind(UUID body, long epoch) {
        UUID oldBody=tracker.body(); long oldEpoch=tracker.epoch(); tracker.bind(body,epoch);
        if (oldBody != null && (!oldBody.equals(body) || oldEpoch != epoch) || body == null || epoch <= 0) binding=null;
    }
    public MeleeAttackRequest submit(Binding binding, long now, long duration, UUID target) {
        return submit(binding, now, duration, MeleeAttackRequest.Mode.LIGHT, target == null ? java.util.List.of() : java.util.List.of(target));
    }
    public MeleeAttackRequest submit(Binding binding, long now, long duration, MeleeAttackRequest.Mode mode, java.util.List<UUID> nominations) {
        if (binding==null) return null;
        var key = new SourceRequestTracker.SourceKey(binding.kind().name(),
                binding.kind()==MeleeWeaponResolver.Kind.UNARMED ? "" : binding.itemToken());
        Long seq=tracker.begin(key,now,duration); if(seq==null)return null;
        this.binding=binding;
        if (mode == null || nominations == null || nominations.size() > MeleeAttackRequest.MAX_WIDE_TARGETS) return null;
        return new MeleeAttackRequest(tracker.epoch(),seq,binding.kind(),binding.handRevision(),binding.activeHand(),binding.itemToken(),mode,
                mode == MeleeAttackRequest.Mode.LIGHT ? nominations.stream().findFirst().orElse(null) : null,
                mode == MeleeAttackRequest.Mode.WIDE ? nominations : java.util.List.of());
    }
    public boolean acknowledge(MeleeAttackReply reply, long now) {
        return reply!=null && tracker.acknowledge(reply.bodyUUID(),reply.epoch(),reply.sequence(),now,reply.cooldownRemainingTicks());
    }
    public boolean timeout(long now,long ticks) { return tracker.timeout(now,ticks); }
    public void clear() { tracker.clear(); binding=null; }
    public UUID body(){return tracker.body();} public long epoch(){return tracker.epoch();} public long sequence(){return tracker.sequence();}
    public boolean pending(){return tracker.pending();} public Binding binding(){return binding;} public long projectedDeadline(){return tracker.projectedDeadline();}
}
