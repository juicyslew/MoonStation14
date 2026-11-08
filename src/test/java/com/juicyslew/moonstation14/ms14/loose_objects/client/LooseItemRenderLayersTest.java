package com.juicyslew.moonstation14.ms14.loose_objects.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LooseItemRenderLayersTest {
    private static LooseItemRenderLayers.Footprint f(long id, double x, double y, double half) {
        return new LooseItemRenderLayers.Footprint(new UUID(0, id), x, 0, y, half, half);
    }

    @Test void componentOrderIsUniqueStableAndIndependentOfEnumeration() {
        var a=f(1,0,0,.6); var b=f(2,.9,0,.6); var c=f(3,1.8,0,.6);
        var one=List.of(a,b,c); var reversed=List.of(c,a,b);
        assertTrue(LooseItemRenderLayers.overlaps(a,b));
        assertTrue(LooseItemRenderLayers.overlaps(b,c));
        assertFalse(LooseItemRenderLayers.overlaps(a,c));
        for (var item : one) assertEquals(LooseItemRenderLayers.offset(item.id(),one),
                LooseItemRenderLayers.offset(item.id(),reversed));
        assertTrue(LooseItemRenderLayers.offset(a.id(),one)<LooseItemRenderLayers.offset(b.id(),one));
        assertTrue(LooseItemRenderLayers.offset(b.id(),one)<LooseItemRenderLayers.offset(c.id(),one));
        assertTrue(LooseItemRenderLayers.offset(c.id(),one)<=LooseItemRenderLayers.MAX_RISE);
    }

    @Test void singlesAndDifferentGroundSurfacesDoNotRise() {
        var a=f(1,0,0,.5); var high=f(2,0,.03,.5);
        assertFalse(LooseItemRenderLayers.overlaps(a,high));
        assertEquals(0,LooseItemRenderLayers.offset(a.id(),List.of(a)));
        // offset(Collection) ranks an already-resolved connected component. The world adapter
        // performs component discovery; disjoint populations are not valid helper input.
        assertEquals(0,LooseItemRenderLayers.offset(a.id(),List.of(a)));
        assertEquals(0,LooseItemRenderLayers.offset(high.id(),List.of(high)));
    }

    @Test void renderHeightsRemainStrictlyOrderedAcrossTinyGroundDifferences() {
        // UUID order is deliberately opposite the ground-height order; the old UUID-only
        // ranking made these final heights equal when the 0.01 base difference canceled.
        var higherGround=f(2,0,.01,.5);
        var lowerGround=f(1,0,0,.5);
        var component=List.of(higherGround,lowerGround);
        double lowerFinal=lowerGround.baseY()+LooseItemRenderLayers.offset(lowerGround.id(),component);
        double higherFinal=higherGround.baseY()+LooseItemRenderLayers.offset(higherGround.id(),component);
        assertTrue(lowerFinal<higherFinal);
    }
}
