package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TimedWorldTargetMarkersTest {
    private static final ResourceLocation DIM=ResourceLocation.withDefaultNamespace("overworld");
    private static final UUID BODY=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static TimedInteractionSnapshot.View view(long id,TimedOperation.Phase phase,BlockPos target) {
        return new TimedInteractionSnapshot.View(id,10,20,phase,phase==TimedOperation.Phase.ACTIVE?-1:20,id,
                Optional.ofNullable(target));
    }

    @Test void selectionShowsEachActiveAuthoritativeWorldTargetAndNothingTerminalOrUnanchored() {
        var a=new BlockPos(3,64,8);var b=new BlockPos(9,64,2);
        var snapshot=new TimedInteractionSnapshot(DIM,4,BODY,7,15,List.of(view(11,TimedOperation.Phase.ACTIVE,a),
                view(12,TimedOperation.Phase.ACTIVE,b),view(13,TimedOperation.Phase.ACTIVE,null),
                view(14,TimedOperation.Phase.CANCELLED,new BlockPos(1,1,1)),view(15,TimedOperation.Phase.COMPLETED,new BlockPos(2,2,2))));
        var markers=TimedWorldTargetMarkers.select(snapshot,DIM,BODY);
        assertEquals(List.of(a,b),markers.stream().map(TimedWorldTargetMarkers.Marker::target).toList());
        assertEquals(0,TimedWorldTargetMarkers.select(snapshot,ResourceLocation.withDefaultNamespace("nether"),BODY).size());
        assertEquals(0,TimedWorldTargetMarkers.select(snapshot,DIM,UUID.randomUUID()).size());
        assertEquals(.25f,TimedInteractionMath.progress(markers.getFirst().view().startTick(),markers.getFirst().view().durationTicks(),15));
    }

    @Test void localFirstPersonBarsShareAllSelectedWorkspotsWithoutChangingRemoteMarkerSelection() {
        var views=new ArrayList<TimedInteractionSnapshot.View>();
        for(int i=0;i<4;i++) views.add(view(20+i,TimedOperation.Phase.ACTIVE,new BlockPos(i,64,i+1)));
        var snapshot=new TimedInteractionSnapshot(DIM,4,BODY,7,10,views);
        var markers=TimedWorldTargetMarkers.select(snapshot,DIM,BODY);
        var bars=TimedWorldTargetMarkers.selectLocalFirstPersonBars(snapshot,DIM,BODY);
        assertEquals(4,markers.size());
        assertEquals(4,bars.size());
        assertEquals(markers.stream().map(TimedWorldTargetMarkers.Marker::view).toList(),bars);
        assertEquals(0,TimedWorldTargetMarkers.selectLocalFirstPersonBars(snapshot,
                ResourceLocation.withDefaultNamespace("nether"),BODY).size());
        assertEquals(0,TimedWorldTargetMarkers.selectLocalFirstPersonBars(snapshot,DIM,UUID.randomUUID()).size());
        assertEquals(0,TimedInteractionMath.fillPixels(TimedInteractionMath.progress(10,20,10)));
        assertTrue(TimedWorldTargetMarkers.select(snapshot,DIM,BODY).stream()
                .allMatch(marker->marker.view().phase()==TimedOperation.Phase.ACTIVE));
    }
}
