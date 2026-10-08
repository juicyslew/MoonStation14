package com.juicyslew.moonstation14.ms14.construction.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

class ConstructionHudModelTest {
    @Test void planModelUsesHumanRecipeLabelAndSharedMaterialFields() {
        var disposal=ConstructionHudRenderer.plan("Plastic Wall","Steel",2,4,"","G Change · H Exit");
        var girder=ConstructionHudRenderer.plan("Plastic Wall","Steel",2,4,"","G Change · H Exit");
        assertEquals(disposal,girder);
        assertEquals("Plastic Wall",disposal.recipeName());
        assertFalse(disposal.recipeName().contains("plastic_wall"));
        assertEquals(2,disposal.held());
        assertEquals(4,disposal.required());
        var rows=ConstructionHudRenderer.planRows(disposal);
        assertEquals(2,rows.size());
        assertTrue(rows.get(0).getString().contains("Steel 2/4"));
        assertTrue(rows.get(1).getString().contains("G Change"));
    }

    @Test void topCenteredBoundsStayWithinCompactHudArea() {
        int[] b=ConstructionHudRenderer.bounds(320,180,360,44);
        assertEquals(4,b[0]);
        assertEquals(18,b[1]);
        assertTrue(b[0]+b[2]<=320);
        assertTrue(b[1]+b[3]<=180);
        assertTrue(b[1]<60);
        int[] tiny=ConstructionHudRenderer.bounds(80,24,300,40);
        assertTrue(tiny[0]>=0&&tiny[0]+tiny[2]<=80);
        assertTrue(tiny[1]>=0&&tiny[1]+tiny[3]<=24);
    }

    @Test void materialRowsAreCountsOnlyAndRequirementColorIsCountDriven() {
        var empty=ConstructionHudRenderer.plan("Steel Wall","Steel",0,2,"","G Change");
        var one=ConstructionHudRenderer.plan("Steel Wall","Steel",1,2,"","G Change");
        var enough=ConstructionHudRenderer.plan("Steel Wall","Steel",2,2,"","G Change");
        assertEquals("Steel 0/2",ConstructionHudRenderer.planRows(empty).get(0).getString());
        assertFalse(ConstructionHudRenderer.planRows(empty).get(0).getString().contains("Ready"));
        assertFalse(ConstructionHudRenderer.planRows(one).get(0).getString().contains("Not enough"));
        assertEquals(0xffff5555,ConstructionHudRenderer.materialColor(0,2));
        assertEquals(0xffffcc44,ConstructionHudRenderer.materialColor(1,2));
        assertEquals(0xff55dd66,ConstructionHudRenderer.materialColor(2,2));
        var zeroRequirement=ConstructionHudRenderer.plan("Rod","",0,0,"","G Change");
        assertEquals("",ConstructionHudRenderer.planRows(zeroRequirement).get(0).getString());
        assertEquals("Steel 0/2 · moonstation14.construction.hud.blocked",ConstructionHudRenderer.planRows(
                ConstructionHudRenderer.plan("Wall","Steel",0,2,"moonstation14.construction.hud.blocked","G Change")).get(0).getString());
    }

    @Test void chatAvoidanceUsesPublishedBoundsAndFitsCompactContentAtCommonViewports() {
        for(int[] screen:List.of(new int[]{640,360},new int[]{320,180})) {
            var chat=com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.chat(screen[0],screen[1]);
            int[] b=ConstructionHudRenderer.layoutBounds(screen[0],screen[1],210,38,18,chat);
            assertTrue(b[2]>0&&b[3]>0);
            assertTrue(b[0]>=0&&b[0]+b[2]<=screen[0]);
            assertTrue(b[1]>=0&&b[1]+b[3]<=screen[1]);
            assertFalse(b[0]<chat.right()&&b[0]+b[2]>chat.left()&&b[1]<chat.bottom()&&b[1]+b[3]>chat.top());
        }
        var chat=com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.chat(100,50);
        int[] narrow=ConstructionHudRenderer.layoutBounds(100,50,210,38,18,chat);
        assertTrue(narrow[2]>0);
        assertTrue(narrow[0]>=0&&narrow[0]+narrow[2]<=100&&narrow[1]>=0&&narrow[1]+narrow[3]<=50);
        var nearlyFull=new com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.Rect(0,0,116,140);
        int[] below=ConstructionHudRenderer.layoutBounds(120,180,110,36,18,nearlyFull);
        assertTrue(below[2]>0&&below[1]>=nearlyFull.bottom()+4);
        assertTrue(below[0]>=0&&below[0]+below[2]<=120&&below[1]+below[3]<=180);
        assertFalse(below[0]<nearlyFull.right()&&below[0]+below[2]>nearlyFull.left()
                &&below[1]<nearlyFull.bottom()&&below[1]+below[3]>nearlyFull.top());
    }

    @Test void guidanceBudgetAndFinalPanelTextGeometryKeepWrappedInstructionsReadable() {
        assertEquals(4,ConstructionHudRenderer.GUIDANCE_MAX_LINES);
        assertEquals(4,ConstructionHudRenderer.guidanceLineBudget(180,58,9));
        assertEquals(1,ConstructionHudRenderer.guidanceLineBudget(80,58,9));
        int panel=144;
        int textWidth=ConstructionHudRenderer.panelTextWidth(panel);
        assertTrue(textWidth>0);
        int titleWidth=72;
        int titleX=ConstructionHudRenderer.centeredTitleX(panel,titleWidth);
        assertEquals((textWidth-titleWidth)/2,titleX);
        assertTrue(titleX+titleWidth<=textWidth);
    }
    @Test void guidanceLayoutUsesRealFontWrappingToSizeRowsAndClearPanelBottom() {
        var font=new MachineSearchWidgetTest.DeterministicFont() {
            @Override public java.util.List<net.minecraft.util.FormattedCharSequence> split(
                    net.minecraft.network.chat.FormattedText text,int width) {
                String value=text.getString();
                int chars=Math.max(1,width/6);
                var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
                for(int start=0;start<value.length();start+=chars)
                    lines.add(net.minecraft.util.FormattedCharSequence.forward(value.substring(start,Math.min(value.length(),start+chars)),net.minecraft.network.chat.Style.EMPTY));
                return lines;
            }
        };
        var text=net.minecraft.network.chat.Component.literal("Bring enough steel sheets to complete this construction requirement safely");
        var shortLayout=ConstructionHudRenderer.guidanceContentLayout(
                net.minecraft.network.chat.Component.literal("2 Steel"),60,5,4,font::split);
        var longLayout=ConstructionHudRenderer.guidanceContentLayout(text,60,5,4,font::split);
        assertEquals(1,shortLayout.lines().size());
        assertTrue(longLayout.lines().size()>=2&&longLayout.lines().size()<=4);
        assertEquals(ConstructionHudRenderer.guidancePanelHeight(5,longLayout.lines().size()),longLayout.metrics().panelHeight());
        int lastGlyphBottom=longLayout.metrics().bodyStart()+(longLayout.lines().size()-1)*longLayout.metrics().rowStride()+5;
        assertTrue(lastGlyphBottom<=longLayout.metrics().panelHeight()-2);
        assertTrue(longLayout.metrics().panelHeight()>shortLayout.metrics().panelHeight());
    }
    @Test void guidanceUsesGoalHeaderOneCompactRequirementAndPaletteHasNoFooterSpace() {
        var guidance=new ConstructionHudRenderer.Guidance("Steel Wall","2 Steel");
        var rows=ConstructionHudRenderer.guidanceRows(guidance);
        assertEquals("2 Steel",rows.get(0).getString());
        assertFalse(rows.toString().contains("Stage"));assertFalse(rows.toString().contains("Next"));
        assertEquals(.5f,ConstructionHudRenderer.guidanceTextScale());
        assertEquals(28+5*9,ConstructionHudRenderer.paletteHeight(5));
        assertEquals(28+2*9,ConstructionHudRenderer.paletteHeight(2));
        assertEquals((int)Math.ceil(Math.max(48,32)*.5)+8,ConstructionHudRenderer.guidanceContentWidth(48,32));
        assertTrue(ConstructionHudRenderer.guidanceContentWidth(48,32)<48*.75+16);
        assertEquals(1,ConstructionHudRenderer.guidanceHeaderGap());
        assertEquals(2,ConstructionHudRenderer.guidanceBodyTop(5)-ConstructionHudRenderer.guidanceTopPadding()-5);
        assertEquals(ConstructionHudRenderer.guidanceMetrics(5,1).panelHeight(),ConstructionHudRenderer.guidancePanelHeight(5,1));
    }
}
