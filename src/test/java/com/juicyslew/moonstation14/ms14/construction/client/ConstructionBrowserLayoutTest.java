package com.juicyslew.moonstation14.ms14.construction.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionBrowserLayoutTest {
    @Test void quantityLabelsForCommonCountsUseCenteredPaintOriginInsideSharedActionBounds() {
        var actions=ConstructionBrowserLayout.itemActions(ConstructionBrowserLayout.calculate(0,0,240,140,true,true,false,false));
        for(String label:List.of("×1","×9","×64")) {
            int measuredWidth=label.length()*7;
            var origin=ConstructionQuantityPlacement.centered(measuredWidth,9,actions.quantity());
            double paintedWidth=measuredWidth*.5,paintedHeight=9*.5;
            assertEquals(actions.quantity().x()+(actions.quantity().width()-paintedWidth)/2,origin.x(),.001);
            assertEquals(actions.quantity().y()+(actions.quantity().height()-paintedHeight)/2,origin.y(),.001);
            assertTrue(origin.x()>=actions.minus().x()+actions.minus().width());
            assertTrue(origin.x()+paintedWidth<=actions.plus().x());
            assertTrue(origin.y()>=actions.quantity().y());
            assertTrue(origin.y()+paintedHeight<=actions.quantity().y()+actions.quantity().height());
        }
    }
    @Test void listExtentAndColumnsRemainIndependentOfSelectionAndQueue() {
        var empty=ConstructionBrowserLayout.calculate(10,20,240,140,false,false,false,false);
        var active=ConstructionBrowserLayout.calculate(10,20,240,140,true,true,true,true);
        assertEquals(empty.listHeight(),active.listHeight());
        assertEquals(81,empty.leftWidth());
        assertTrue(empty.instructions().width()>empty.leftWidth());
        assertTrue(empty.search().width()<=85);
    }
    @Test void actionsFollowGuideAndRemainDisjointAtCompactWidth() {
        var l=ConstructionBrowserLayout.calculate(0,0,240,140,true,true,false,false);
        var a=ConstructionBrowserLayout.itemActions(l);
        assertTrue(a.minus().y()>=l.instructions().y()+l.instructions().height());
        assertTrue(a.minus().x()+a.minus().width()<=a.quantity().x());
        assertTrue(a.quantity().x()+a.quantity().width()<=a.plus().x());
        assertTrue(a.plus().x()+a.plus().width()<=a.craft().x());
        assertTrue(a.craft().x()+a.craft().width()<=l.actions().x()+l.actions().width());
    }
    @Test void longGuideCanScrollToLastRenderedFontRowBeforeQueuedActions() {
        var l=ConstructionBrowserLayout.calculate(0,0,240,140,true,true,true,true);
         int visible=ConstructionBrowserLayout.guideVisibleRows(l,9),total=ConstructionBrowserLayout.guideVisibleRows(l,9)+4;
        int lastScroll=ConstructionBrowserLayout.maxScroll(total,visible);
        int lastBaseline=ConstructionBrowserLayout.GUIDE_TITLE_LOGICAL_HEIGHT+
                (visible-1)*ConstructionBrowserLayout.GUIDE_ROW_LOGICAL_HEIGHT;
        assertTrue(visible>0);
        assertEquals(total-1,lastScroll+visible-1);
        assertTrue(lastBaseline+9<=l.instructions().height()*2);
        assertTrue(l.instructions().y()+l.instructions().height()<l.actions().y());
         assertTrue(l.status().y()+l.status().height()<=l.actions().y());
         assertTrue(l.queue().y()+l.queue().height()<=l.actions().y());
        assertTrue(l.actions().x()>=l.instructions().x());
        var noStatus=ConstructionBrowserLayout.calculate(0,0,240,140,true,true,false,false);
        assertEquals(l.listHeight(),noStatus.listHeight());
    }
    @Test void compactHeaderAndCloseAreOutsideSearchHit() {
        var l=ConstructionBrowserLayout.calculate(20,30,240,140,false,false,false,false);
        assertEquals(13,ConstructionBrowserLayout.HEADER_HEIGHT);
        assertTrue(l.search().y()>=l.y()+ConstructionBrowserLayout.HEADER_HEIGHT);
        assertFalse(l.search().contains(l.close().x(),l.close().y()));
        assertEquals(9,l.close().width());
        assertEquals(18,l.close().width()*2); // normal toolkit widget projected at half scale
        assertEquals(.5f,ConstructionBrowserLayout.CONTENT_SCALE);
    }
    @Test void viewportRowsAndScrollBoundsExposeLastEntry() {
        var l=ConstructionBrowserLayout.calculate(0,0,240,140,false,false,false,false);
        int page=ConstructionBrowserLayout.visibleRows(l),last=ConstructionBrowserLayout.maxScroll(20,page);
        assertEquals(19,last+page-1);
         assertEquals(7,ConstructionBrowserLayout.ROW_HEIGHT);
    }

    @Test void productionRootBoundsApplyMinimaBeforeAreaBudgetAndCenterActualGeometry() {
        for(int[] screen:new int[][]{{480,270},{640,360},{320,240},{320,180},{100,60}}) {
            var preferred=ConstructionBrowserLayout.preferredBounds(screen[0],screen[1]);
            var geometry=new com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController(
                    screen[0],screen[1],preferred.x(),preferred.y(),preferred.width(),preferred.height(),
                    preferred.minWidth(),preferred.minHeight(),false);
            assertEquals(preferred.width(),geometry.width());assertEquals(preferred.height(),geometry.height());
            assertEquals((screen[0]-geometry.width())/2,geometry.x());assertEquals((screen[1]-geometry.height())/2,geometry.y());
            if(screen[0]>=320&&screen[1]>=180) assertTrue((double)geometry.width()*geometry.height()/(screen[0]*screen[1])<=.13);
            else assertTrue(geometry.width()<=screen[0]&&geometry.height()<=screen[1]);
            var l=ConstructionBrowserLayout.calculate(geometry.x(),geometry.y(),geometry.width(),geometry.height(),true,true,false,true);
            var a=ConstructionBrowserLayout.itemActions(l);
            assertTrue(a.craft().height()<=9);
            assertTrue(l.status().y()+l.status().height()<=a.craft().y());
            assertEquals(l.instructions().y(),l.y()+ConstructionBrowserLayout.HEADER_HEIGHT+ConstructionBrowserLayout.GUIDE_TOP_MARGIN);
            int rows=ConstructionBrowserLayout.visibleRows(l);
            int renderedHeight=rows*ConstructionBrowserLayout.ROW_HEIGHT;
            assertTrue(l.list().height()-renderedHeight<ConstructionBrowserLayout.ROW_HEIGHT);
        }
    }

    @Test void structureSelectBoundsAreContentSizedWhileItemActionsUseThePane() {
        var structure=ConstructionBrowserLayout.calculate(0,0,240,140,true,false,false,false);
        var item=ConstructionBrowserLayout.calculate(0,0,240,140,true,true,false,false);
        assertEquals(40,structure.actions().width());assertEquals(9,structure.actions().height());
        assertEquals(structure.instructions().x(),structure.actions().x());
        assertEquals(item.instructions().width(),item.actions().width());
    }

    @Test void guideRowsUseActualPaintMarginAndRemainClippedAtCompactHeights() {
        int fontHeight=9;
        var narrow=ConstructionBrowserLayout.calculate(0,0,144,66,true,false,false,false);
        int visible=ConstructionBrowserLayout.guideVisibleRows(narrow,fontHeight),total=12;
        int lastScroll=ConstructionBrowserLayout.maxScroll(total,visible);
        assertTrue(visible>0);
        assertEquals(total-1,lastScroll+visible-1);
        int lastLogicalBaseline=ConstructionBrowserLayout.GUIDE_TITLE_LOGICAL_HEIGHT+
                (visible-1)*ConstructionBrowserLayout.GUIDE_ROW_LOGICAL_HEIGHT;
        int paintedBottom=narrow.instructions().y()+ConstructionBrowserLayout.GUIDE_TOP_MARGIN+
                (lastLogicalBaseline+fontHeight+ConstructionBrowserLayout.GUIDE_SHADOW_GUARD_LOGICAL+1)/2;
        assertTrue(paintedBottom<=narrow.instructions().y()+narrow.instructions().height());

        var crowded=ConstructionBrowserLayout.calculate(0,0,144,64,true,true,true,true);
        assertEquals(0,ConstructionBrowserLayout.guideVisibleRows(crowded,fontHeight));
        assertTrue(ConstructionBrowserLayout.maxScroll(total,0)>=0);
    }
    @Test void draggedWindowCarriesListClipSearchAndCloseHitRegionsTogether() {
        var a=ConstructionBrowserLayout.calculate(10,15,240,140,false,false,false,false);
        var b=ConstructionBrowserLayout.calculate(80,55,240,140,false,false,false,false);
        assertEquals(70,b.list().x()-a.list().x());
        assertEquals(40,b.search().y()-a.search().y());
        assertEquals(70,b.close().x()-a.close().x());
        assertEquals(a.listHeight(),b.listHeight());
    }
    @Test void compactCloseIsExcludedFromTitleDragButTitleBodyIsDraggable() {
        var l=ConstructionBrowserLayout.calculate(20,30,240,140,false,false,false,false);
        var geometry=new com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController(
                400,300,l.x(),l.y(),l.width(),l.height(),180,112,false);
        assertFalse(geometry.beginPointer(l.close().x()+2,l.close().y()+2,0,13,14));
        assertTrue(geometry.beginPointer(l.x()+24,l.y()+5,0,13,14));
    }
    @Test void normalizedFactoryCloseWidgetBoundsProjectExactlyOntoCompactLayoutHitRect() throws NoSuchMethodException {
        var l=ConstructionBrowserLayout.calculate(20,30,240,140,false,false,false,false);
         var button=WindowChromeButtons.close(0,0,18,()->{});
         assertTrue(button.getClass().getName().endsWith("ChromeButton"));
         assertNotNull(button.getClass().getDeclaredMethod("renderWidget",net.minecraft.client.gui.GuiGraphics.class,int.class,int.class,float.class));
        // Browser uses this exact normalization before both drawing and inverse-coordinate input.
        button.setX(0);button.setY(0);
        assertEquals(0,button.getX());assertEquals(0,button.getY());
        assertEquals(18,button.getWidth());assertEquals(18,button.getHeight());
        assertEquals(l.close().x(),l.close().x()+button.getX()*.5);
        assertEquals(l.close().y(),l.close().y()+button.getY()*.5);
        assertEquals(l.close().width(),button.getWidth()*.5);
        assertEquals(l.close().height(),button.getHeight()*.5);
        assertTrue(button.isMouseOver(0,0));
        assertTrue(button.isMouseOver(17.99,17.99));
        assertFalse(button.isMouseOver(18,18));
    }
}
