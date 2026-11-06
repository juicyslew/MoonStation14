package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HullStateMismatchEvidenceTest {
    @Test void mismatchCopiesPositionAndBoxLists() {
        var pos=new BlockPos.MutableBlockPos(4,5,6);
        var boxes=new ArrayList<>(List.of(new HullTerrainImpactProbe.Box(0,0,0,1,1,1)));
        var mismatch=new HullTerrainImpactProbe.StateMismatch(12,pos,"authored","observed",boxes,
                List.of(),false,false);
        pos.set(9,9,9);
        boxes.clear();
        assertEquals(new BlockPos(4,5,6),mismatch.position());
        assertEquals(1,mismatch.authoredBoxes().size());
        assertThrows(UnsupportedOperationException.class,()->mismatch.authoredBoxes().clear());
    }

    @Test void firstMismatchWitnessIsNeverReplaced() {
        var first=new HullTerrainImpactProbe.FirstMismatch();
        var witness=new HullTerrainImpactProbe.StateMismatch(12,new BlockPos(4,5,6),"a","b",List.of(),List.of(),false,false);
        first.capture(witness);
        first.capture(new HullTerrainImpactProbe.StateMismatch(13,new BlockPos(7,8,9),"c","d",List.of(),List.of(),false,false));
        assertSame(witness,first.get());
    }

    @Test void canonicalBoxComparisonIgnoresInputOrderingButNotBounds() {
        var left=new HullTerrainImpactProbe.Box(0,0,0,1,1,1);
        var right=new HullTerrainImpactProbe.Box(1,0,0,2,1,1);
        assertEquals(HullTerrainImpactProbe.canonicalBoxes(List.of(left,right)),
                HullTerrainImpactProbe.canonicalBoxes(List.of(right,left)));
        assertNotEquals(HullTerrainImpactProbe.canonicalBoxes(List.of(left)),
                HullTerrainImpactProbe.canonicalBoxes(List.of(right)));
    }
}
