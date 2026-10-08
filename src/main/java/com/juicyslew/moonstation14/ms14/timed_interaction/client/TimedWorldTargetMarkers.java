package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Selects only authoritative, active, incarnation-qualified world-work markers from the shared cache. */
public final class TimedWorldTargetMarkers {
    public record Marker(TimedInteractionSnapshot.View view,BlockPos target) { }
    private TimedWorldTargetMarkers() { }
    public static List<Marker> select(TimedInteractionSnapshot snapshot,ResourceLocation dimension,UUID body) {
        if(snapshot==null||dimension==null||body==null||!snapshot.dimension().equals(dimension)||!snapshot.bodyUuid().equals(body))return List.of();
        return snapshot.views().stream().filter(v->v.phase()==TimedOperation.Phase.ACTIVE&&v.worldTarget().isPresent())
                .map(v->new Marker(v,v.worldTarget().orElseThrow().immutable())).toList();
    }
    /** Locally owned first-person world work keeps its target marker and also gets a view-anchored bar. */
    public static List<TimedInteractionSnapshot.View> selectLocalFirstPersonBars(
            TimedInteractionSnapshot snapshot,ResourceLocation dimension,UUID body) {
        return select(snapshot,dimension,body).stream().map(Marker::view).toList();
    }
}
