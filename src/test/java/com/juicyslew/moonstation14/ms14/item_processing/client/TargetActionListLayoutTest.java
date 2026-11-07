package com.juicyslew.moonstation14.ms14.item_processing.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TargetActionListLayoutTest {
    @Test void compactRowsAndOptionalFooterFitWithoutClipping(){
        for(int viewportHeight:new int[]{85,86,320,640})for(int rows=1;rows<=8;rows++)for(boolean footer:new boolean[]{false,true}){
            int required=TargetActionClientPolicy.Layout.heightFor(rows,footer);
            var layout=new TargetActionClientPolicy.Layout(50,20,viewportHeight==85?320:640,viewportHeight,rows,148,footer);
            assertEquals(Math.min(required,viewportHeight),layout.height());
            assertEquals(viewportHeight>=required,layout.usable());
            if(!layout.usable())continue;
            assertTrue(layout.usable());
            assertTrue(layout.height()>=required);
            for(int i=0;i<rows;i++){
                var row=layout.action(i);
                assertTrue(row.within(layout.window()));
                assertTrue(layout.rectWithinViewport(row));
                assertTrue(row.contains(row.x(),row.y()));
                assertFalse(row.contains(row.x(),row.y()+row.height()));
                if(i>0)assertFalse(layout.action(i-1).contains(row.x(),row.y()));
            }
            if(footer){assertTrue(layout.reason().height()>=TargetActionClientPolicy.Layout.FOOTER_HEIGHT);assertTrue(layout.reason().within(layout.window()));assertTrue(layout.rectWithinViewport(layout.reason()));assertEquals(layout.action(rows-1).y()+layout.action(rows-1).height()+TargetActionClientPolicy.Layout.ROW_GAP,layout.reason().y());}
            else assertEquals(0,layout.reason().height());
        }
        var fourAt320=new TargetActionClientPolicy.Layout(0,0,320,240,4);
        assertTrue(fourAt320.usable());
        assertEquals(TargetActionClientPolicy.Layout.heightFor(4,false),fourAt320.height());
        assertFalse(new TargetActionClientPolicy.Layout(0,0,320,TargetActionClientPolicy.Layout.heightFor(4,true)-1,4,148,true).usable());
        assertFalse(new TargetActionClientPolicy.Layout(0,0,100,220,1).usable());
        assertFalse(new TargetActionClientPolicy.Layout(0,0,240,220,0).usable());
    }
}
