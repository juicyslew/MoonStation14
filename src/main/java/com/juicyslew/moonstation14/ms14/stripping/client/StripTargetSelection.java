package com.juicyslew.moonstation14.ms14.stripping.client;

import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Narrow client nomination policy: the foremost visible resolved body owns the aimed ray. */
public final class StripTargetSelection {
    public record Candidate(UUID id,Vec3 root,double rayDistance,boolean eligible,boolean excluded) { }
    private StripTargetSelection() { }
    public static UUID select(List<Candidate> candidates,Vec3 actorRoot,double rootReach,double wallDistance) {
        if(candidates==null||actorRoot==null||!Double.isFinite(wallDistance)||wallDistance<0)return null;
        Candidate first=candidates.stream().filter(c->c!=null&&!c.excluded()&&c.id()!=null&&c.root()!=null
                        &&Double.isFinite(c.rayDistance())&&c.rayDistance()>=0&&c.rayDistance()<=wallDistance)
                .min(Comparator.comparingDouble(Candidate::rayDistance)).orElse(null);
        if(first==null||!first.eligible()||!com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess
                .rootWithinReach(actorRoot,first.root(),rootReach))return null;
        return first.id();
    }
}
