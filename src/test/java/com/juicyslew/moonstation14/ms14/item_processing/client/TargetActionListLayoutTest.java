package com.juicyslew.moonstation14.ms14.item_processing.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TargetActionListLayoutTest {
    @Test void oneTwoAndThreeRowsShareBoundedNonoverlappingGeometry(){
        for(int rows=1;rows<=3;rows++){
            var layout=new TargetActionClientPolicy.Layout(50,20,240,220,rows);
            assertTrue(layout.usable());
            assertEquals(92+26*(rows-1),layout.height());
            for(int i=0;i<rows;i++){
                var row=layout.action(i);
                assertTrue(row.within(layout.window()));
                assertTrue(layout.rectWithinViewport(row));
                assertTrue(row.contains(row.x(),row.y()));
                assertFalse(row.contains(row.x(),row.y()+row.height()));
                if(i>0)assertFalse(layout.action(i-1).contains(row.x(),row.y()));
            }
            assertTrue(layout.reason().within(layout.window()));
            assertTrue(layout.rectWithinViewport(layout.reason()));
            assertTrue(layout.reason().y()>=layout.action(rows-1).y()+layout.action(rows-1).height());
        }
        assertFalse(new TargetActionClientPolicy.Layout(0,0,240,120,3).usable());
        assertFalse(new TargetActionClientPolicy.Layout(0,0,240,220,0).usable());
    }
}
