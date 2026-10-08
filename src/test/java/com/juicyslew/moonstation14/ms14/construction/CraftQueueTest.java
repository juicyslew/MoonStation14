package com.juicyslew.moonstation14.ms14.construction;

import com.juicyslew.moonstation14.ms14.construction.domain.CraftQueue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftQueueTest {
    @Test void advancesOnlyAfterOneItemTransactionAndNeverReservesFutureItems(){
        var queue=new CraftQueue("moonstation14:metal_rod",3);
        assertTrue(queue.active());assertEquals(3,queue.remaining());
        queue.completedOne();assertEquals(2,queue.remaining());
        queue.completedOne();assertEquals(1,queue.remaining());
        queue.completedOne();assertFalse(queue.active());assertEquals(0,queue.remaining());
        assertThrows(IllegalStateException.class,queue::completedOne);
    }
    @Test void cancellationAndBoundsAreExplicit(){
        var queue=new CraftQueue("moonstation14:metal_rod",2);queue.stop();assertFalse(queue.active());assertEquals(2,queue.remaining());
        assertThrows(IllegalArgumentException.class,()->new CraftQueue("recipe",0));
        assertThrows(IllegalArgumentException.class,()->new CraftQueue("recipe",CraftQueue.MAX_QUANTITY+1));
    }
}
