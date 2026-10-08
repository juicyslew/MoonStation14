package com.juicyslew.moonstation14.ms14.construction;

import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.domain.CraftWorkSchedule;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CraftWorkScheduleTest {
    @Test void zeroDurationStaysImmediateAndPositivePerItemTimeIsNotCollapsed(){
        var immediate=List.<ConstructionGraph.Step>of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,1,0));
        assertEquals(0,CraftWorkSchedule.durationTicks(immediate));
        var timed=List.<ConstructionGraph.Step>of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,1,.1));
        assertEquals(2,CraftWorkSchedule.durationTicks(timed));
        // The server starts a fresh timed operation only after the preceding item commits.
        var queue=new com.juicyslew.moonstation14.ms14.construction.domain.CraftQueue("fixture",2);
        int completed=0;
        for(int item=0;item<2;item++){assertTrue(queue.active());assertEquals(2,CraftWorkSchedule.durationTicks(timed));completed++;queue.completedOne();}
        assertEquals(2,completed);assertFalse(queue.active());
    }
}
