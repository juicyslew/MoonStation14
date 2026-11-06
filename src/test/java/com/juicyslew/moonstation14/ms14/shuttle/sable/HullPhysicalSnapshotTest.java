package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HullPhysicalSnapshotTest {
    private static HullPhysicalSnapshot.Box box(double min,double max) {
        return new HullPhysicalSnapshot.Box(min,0,0,max,1,1);
    }

    @Test void translatedWorldOriginsWithSameRelativePhysicalInputsMatch() {
        var source = new HullPhysicalSnapshot(List.of(
                HullPhysicalSnapshot.cellAtWorld(102,51,203,100,50,200,"steel",List.of(box(0,1))),
                HullPhysicalSnapshot.cellAtWorld(104,51,204,100,50,200,"window",List.of(box(.125,.875)))));
        // Production capture supplies world coordinates from either origin to this same normalizer.
        var assembled = new HullPhysicalSnapshot(List.of(
                HullPhysicalSnapshot.cellAtWorld(8102,-49,9203,8100,-50,9200,"steel",List.of(box(0,1))),
                HullPhysicalSnapshot.cellAtWorld(8104,-49,9204,8100,-50,9200,"window",List.of(box(.125,.875)))));
        assertEquals(2,source.cells().size());
        assertEquals(2,assembled.cells().size());
        assertNull(source.mismatch(assembled));
        assertEquals(source.fingerprint(),assembled.fingerprint());
        assertEquals(source.sha256Fingerprint(),assembled.sha256Fingerprint());
    }

    @Test void canonicalShapeOrderMatchesButShapeCoordinatesRemainExact() {
        var forward = HullPhysicalSnapshot.cell(1,2,3,"steel",List.of(box(0,.5),box(.5,1)));
        var reverse = HullPhysicalSnapshot.cell(1,2,3,"steel",List.of(box(.5,1),box(0,.5)));
        assertEquals(forward,reverse);
        var changed = HullPhysicalSnapshot.cell(1,2,3,"steel",List.of(box(0,.5),box(.5,.99)));
        var originalSnapshot=new HullPhysicalSnapshot(List.of(forward));
        var changedShapeSnapshot=new HullPhysicalSnapshot(List.of(changed));
        assertNotNull(originalSnapshot.mismatch(changedShapeSnapshot));
        assertNotEquals(originalSnapshot.sha256Fingerprint(),changedShapeSnapshot.sha256Fingerprint());
        var changedStateSnapshot=new HullPhysicalSnapshot(List.of(
                HullPhysicalSnapshot.cell(1,2,3,"steel[axis=x]",List.of(box(0,.5),box(.5,1)))));
        assertNotEquals(originalSnapshot.sha256Fingerprint(),changedStateSnapshot.sha256Fingerprint());
        var changedCoordinateSnapshot=new HullPhysicalSnapshot(List.of(
                HullPhysicalSnapshot.cell(2,2,3,"steel",List.of(box(0,.5),box(.5,1)))));
        assertNotEquals(originalSnapshot.sha256Fingerprint(),changedCoordinateSnapshot.sha256Fingerprint());
        var reversedCells=new HullPhysicalSnapshot(List.of(
                HullPhysicalSnapshot.cell(4,0,0,"wall",List.of(box(0,1))),forward));
        var orderedCells=new HullPhysicalSnapshot(List.of(forward,
                HullPhysicalSnapshot.cell(4,0,0,"wall",List.of(box(0,1)))));
        assertEquals(reversedCells.sha256Fingerprint(),orderedCells.sha256Fingerprint());
    }

    @Test void stateAndMissingOrExtraCellsAreReported() {
        var physical = HullPhysicalSnapshot.cell(0,0,0,"steel",List.of(box(0,1)));
        var baseline = new HullPhysicalSnapshot(List.of(physical));
        assertNotNull(baseline.mismatch(new HullPhysicalSnapshot(List.of(
                HullPhysicalSnapshot.cell(0,0,0,"steel[axis=x]",List.of(box(0,1)))))));
        var extra=HullPhysicalSnapshot.cell(1,0,0,"steel",List.of(box(0,1)));
        assertTrue(baseline.mismatch(new HullPhysicalSnapshot(List.of(physical,extra))).startsWith("cell-count"));
        assertTrue(new HullPhysicalSnapshot(List.of(physical,extra)).mismatch(baseline).startsWith("cell-count"));
    }

    @Test void duplicateRelativeCellCoordinatesAreRejected() {
        var first=HullPhysicalSnapshot.cell(3,4,5,"steel",List.of(box(0,1)));
        var duplicate=HullPhysicalSnapshot.cell(3,4,5,"window",List.of(box(.1,.9)));
        assertThrows(IllegalArgumentException.class,()->new HullPhysicalSnapshot(List.of(first,duplicate)));
    }

    @Test void snapshotDefensivelyCopiesAndEnforcesTheProductionCellLimit() {
        var mutable = new ArrayList<HullPhysicalSnapshot.Cell>();
        mutable.add(HullPhysicalSnapshot.cell(0,0,0,"steel",List.of(box(0,1))));
        var captured = new HullPhysicalSnapshot(mutable);
        mutable.clear();
        assertEquals(1,captured.cells().size());
        var tooMany = new ArrayList<HullPhysicalSnapshot.Cell>();
        for(int i=0;i<=HullPhysicalSnapshot.MAX_CELLS;i++)
            tooMany.add(HullPhysicalSnapshot.cell(i,0,0,"steel",List.of(box(0,1))));
        assertThrows(IllegalArgumentException.class,()->new HullPhysicalSnapshot(tooMany));
        tooMany.remove(tooMany.size()-1);
        assertEquals(HullPhysicalSnapshot.MAX_CELLS,new HullPhysicalSnapshot(tooMany).cells().size());
    }
}
