package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.ms14.hands.network.BodyAppearanceClientCache;
import com.juicyslew.moonstation14.ms14.hands.network.BodyAppearanceSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BodyAppearanceClientCacheTest {
    private static final ResourceLocation DIM=ResourceLocation.withDefaultNamespace("overworld");
    private static BodyAppearanceSnapshot snapshot(int entity,long revision,UUID uuid) {
        return new BodyAppearanceSnapshot(DIM,entity,uuid,revision,List.of(),List.of());
    }
    @Test void acceptsMonotonicRevisionAndRejectsWrongIncarnationAndStaleState() {
        var cache=new BodyAppearanceClientCache(); var id=UUID.randomUUID();
        assertTrue(cache.accept(snapshot(4,1,id),DIM));
        assertFalse(cache.accept(snapshot(4,1,id),DIM));
        assertFalse(cache.accept(snapshot(5,2,id),DIM));
        assertEquals(1,cache.get(DIM,id,4).orElseThrow().revision());
        assertTrue(cache.accept(snapshot(4,2,id),DIM));
    }
    @Test void dimensionChangeAndRemovalClearOldAppearance() {
        var cache=new BodyAppearanceClientCache(); var id=UUID.randomUUID(); cache.accept(snapshot(4,1,id),DIM);
        var nether=ResourceLocation.withDefaultNamespace("the_nether");
        assertTrue(cache.accept(new BodyAppearanceSnapshot(nether,4,id,1,List.of(),List.of()),nether));
        assertFalse(cache.accept(snapshot(4,99,id),nether), "delayed old-dimension packets cannot switch or clear active cache");
        assertTrue(cache.get(DIM,id,4).isEmpty());
        assertTrue(cache.get(nether,id,4).orElseThrow().revision()==1);
        cache.clear(); assertTrue(cache.get(nether,id,4).isEmpty());
    }
    @Test void removalIsIncarnationExactAndDimensionUnloadDropsOnlyThatDimension() {
        var cache=new BodyAppearanceClientCache(); var one=UUID.randomUUID(); var two=UUID.randomUUID();
        cache.accept(snapshot(4,1,one),DIM); cache.accept(snapshot(5,1,two),DIM);
        cache.remove(DIM,one,99);
        assertTrue(cache.get(DIM,one,4).isPresent());
        cache.remove(DIM,one,4);
        assertTrue(cache.get(DIM,one,4).isEmpty());
        assertTrue(cache.get(DIM,two,5).isPresent());
        cache.clearDimension(DIM);
        assertTrue(cache.get(DIM,two,5).isEmpty());
    }
}
