package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("PointerOverlayRegistry")
class ChemMasterLayoutTest {
    private static com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState state(java.util.UUID session,java.util.UUID machine,long frame,boolean open){
        return state(session,machine,frame,open,-1);
    }
    private static com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState state(java.util.UUID session,java.util.UUID machine,long frame,boolean open,long ack){
        return new com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState(frame,frame,0,ack,
                java.util.Optional.empty(),session,machine,java.util.UUID.nameUUIDFromBytes("body".getBytes(java.nio.charset.StandardCharsets.UTF_8)),1,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"),0,64,0,open,
                open?java.util.Optional.empty():java.util.Optional.of("closed"),java.util.Optional.empty(),0,0,java.util.List.of(),java.util.List.of(),
                com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest.Mode.TRANSFER,
                com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest.Sort.OLDEST_FIRST);
    }
    @Test void compactAndSmallViewportsKeepAllControlsBounded() {
        for (int[] size : new int[][]{{640,360},{320,240},{120,80},{1,1}}) {
            var g=ChemMasterLayout.geometry(size[0],size[1]);
            assertTrue(g.width()<=size[0]*ChemMasterLayout.SCALE); assertTrue(g.height()<=size[1]*ChemMasterLayout.SCALE);
            assertTrue(g.close().width()>=1&&g.close().x()+g.close().width()<=g.width());
            assertTrue(g.close().contains(g.close().x(),g.close().y()));
            for(var r:new ChemMasterLayout.Rect[]{g.close(),g.inputTab(),g.outputTab(),g.eject(),g.inputViewport(),g.bufferViewport(),g.inputScrollbar(),g.bufferScrollbar()}) {
                assertTrue(r.x()>=0&&r.y()>=0&&r.x()+r.width()<=g.width()&&r.y()+r.height()<=g.height());
            }
            for(var r:new ChemMasterLayout.Rect[]{g.sort(),g.transferMode(),g.discardMode(),g.inputHeading(),g.bufferHeading()})
                assertTrue(r.x()>=0&&r.y()>=0&&r.x()+r.width()<=g.width()&&r.y()+r.height()<=g.height());
            for(var view:new ChemMasterLayout.Rect[]{g.inputViewport(),g.bufferViewport()})for(int row=0;row<3;row++)
                for(var button:ChemMasterLayout.amountButtons(ChemMasterLayout.reagentRow(view,0,row)))
                    assertTrue(button.x()>=view.x()&&button.x()+button.width()<=view.x()+view.width());
        }
    }
    @Test void compactWindowShowsFiveInputAndEightBufferRowsAtHalfScale(){
        var g=ChemMasterLayout.geometry(500,277);
         assertEquals(338,g.width());assertEquals(272,g.height());
        assertEquals(5,(g.inputViewport().height()+ChemMasterLayout.ROW_HEIGHT-1)/ChemMasterLayout.ROW_HEIGHT);
        assertEquals(8,(g.bufferViewport().height()+ChemMasterLayout.ROW_HEIGHT-1)/ChemMasterLayout.ROW_HEIGHT);
        assertTrue(g.width()/2*g.height()/2.0/(500*277)<=1.0/6.0,"compact client footprint");
        var row=ChemMasterLayout.reagentRow(g.inputViewport(),0,0);
        var buttons=ChemMasterLayout.amountButtons(row);
        assertEquals(10,buttons.size());
        assertTrue(buttons.get(0).x()>=row.x()+96,"dose controls sit beside the label");
        assertTrue(buttons.get(0).x()>=row.x()+8+64,"name and dose controls do not overlap");
        var quantity=ChemMasterLayout.quantityCell(row);
        assertTrue(buttons.get(9).x()+buttons.get(9).width()<=quantity.x()-4,"dose buttons do not overlap the amount cell");
        assertEquals(quantity.x()+quantity.width()-72,ChemMasterLayout.quantityTextX(quantity,72));
        assertTrue(ChemMasterLayout.quantityTextX(quantity,72)+72<=row.x()+row.width()-2,"maximum formatted quantity stays inside row");
        assertTrue(g.caption().width()>0&&g.eject().x()+g.eject().width()<=g.caption().x());
        assertEquals(34,g.inputTab().width());assertEquals(41,g.outputTab().width());
         assertEquals(0,g.inputHeading().y()-(g.eject().y()+g.eject().height()),"container heading follows beaker info closely");
         assertEquals(5*ChemMasterLayout.ROW_HEIGHT,g.inputViewport().height());assertEquals(8*ChemMasterLayout.ROW_HEIGHT,g.bufferViewport().height());
         assertTrue(g.bufferHeading().y()>=g.inputPanel().y()+g.inputPanel().height()+2);
         assertTrue(g.bufferPanel().y()>=g.bufferHeading().y()+g.bufferHeading().height());
         assertTrue(g.bufferPanel().y()+g.bufferPanel().height()<=g.height()-15,"footer and bottom margin remain reserved");
        assertTrue(g.inputScrollbar().x()+g.inputScrollbar().width()<=g.width()-ChemMasterLayout.MARGIN);
        assertTrue(g.transferMode().width()<=50);assertTrue(g.discardMode().width()<=54);
         assertEquals(4,g.inputTab().y(),"title and tabs share the compact header row");
         assertEquals(5*ChemMasterLayout.ROW_HEIGHT,g.inputViewport().height());assertEquals(8*ChemMasterLayout.ROW_HEIGHT,g.bufferViewport().height());
        assertEquals(g.inputPanel().y()+2,g.inputViewport().y());
        assertEquals(g.bufferPanel().y()+g.bufferPanel().height()-2,g.bufferViewport().y()+g.bufferViewport().height());
    }
    @Test void scrollbarOnlyAppearsOnOverflowAndThumbTracksClampedEndpoints(){
        var g=ChemMasterLayout.geometry(500,277);
        assertEquals(0,ChemMasterLayout.scrollbarThumb(g.inputScrollbar(),5,ChemMasterLayout.ROW_HEIGHT,0).height());
        assertEquals(0,ChemMasterLayout.scrollbarThumb(g.bufferScrollbar(),8,ChemMasterLayout.ROW_HEIGHT,0).height());
        var inputTop=ChemMasterLayout.scrollbarThumb(g.inputScrollbar(),6,ChemMasterLayout.ROW_HEIGHT,0);
        var inputBottom=ChemMasterLayout.scrollbarThumb(g.inputScrollbar(),6,ChemMasterLayout.ROW_HEIGHT,999);
        assertEquals(g.inputScrollbar().y(),inputTop.y());
        assertEquals(g.inputScrollbar().y()+g.inputScrollbar().height()-inputBottom.height(),inputBottom.y());
        var bufferTop=ChemMasterLayout.scrollbarThumb(g.bufferScrollbar(),9,ChemMasterLayout.ROW_HEIGHT,0);
        var bufferBottom=ChemMasterLayout.scrollbarThumb(g.bufferScrollbar(),9,ChemMasterLayout.ROW_HEIGHT,999);
        assertEquals(g.bufferScrollbar().y(),bufferTop.y());
        assertEquals(g.bufferScrollbar().y()+g.bufferScrollbar().height()-bufferBottom.height(),bufferBottom.y());
    }
    @Test void scrolledRowsStayWithinViewportAndDoseHitUsesSameReservedGeometry(){
        var g=ChemMasterLayout.geometry(500,277);var view=g.inputViewport();int scroll=7;
        var first=ChemMasterLayout.reagentRow(view,scroll,0);
        assertTrue(first.y()<view.y());assertTrue(first.y()+first.height()>view.y());
        var last=ChemMasterLayout.reagentRow(view,scroll,5);
        assertTrue(last.y()+last.height()>view.y()+view.height());
        assertTrue(last.x()+last.width()<=view.x()+view.width());
        var hit=ChemMasterLayout.amountButtons(ChemMasterLayout.reagentRow(view,0,0)).get(9);
        assertTrue(hit.x()+hit.width()<=view.x()+view.width());
        assertEquals(new ChemMasterLayout.DoseHit(0,9),ChemMasterLayout.doseHit(view,0,6,hit.x()+hit.width()/2d,hit.y()+hit.height()/2d));
    }
    @Test void wheelOwnershipIsLimitedToInnerListViewport(){
        var g=ChemMasterLayout.geometry(500,277);
        assertFalse(g.inputViewport().contains(g.inputPanel().x(),g.inputPanel().y()));
        assertFalse(g.bufferViewport().contains(g.bufferPanel().x(),g.bufferPanel().y()));
        assertEquals(ChemMasterLayout.maxScroll(100,ChemMasterLayout.ROW_HEIGHT,g.inputViewport().height()),
                ChemMasterLayout.clampScroll(Integer.MAX_VALUE,100,ChemMasterLayout.ROW_HEIGHT,g.inputViewport().height()));
    }
    @Test void localMouseAndScissorUseSameTransformAcrossWindowOrigins(){
        var viewport=new ChemMasterLayout.Rect(7,90,326,128);
        for(int[] origin:new int[][]{{0,0},{165,58},{330,117}}){
            var screen=ChemMasterLayout.toScreen(viewport,origin[0],origin[1]);
            assertEquals(origin[0]+3,screen.left());assertEquals(origin[1]+45,screen.top());
            int buttonX=viewport.x()+110,buttonY=viewport.y()+9;
            double sx=origin[0]+buttonX/2d,sy=origin[1]+buttonY/2d;
            assertEquals(buttonX,ChemMasterLayout.toLocal(sx,origin[0]));
            assertEquals(buttonY,ChemMasterLayout.toLocal(sy,origin[1]));
            assertTrue(viewport.contains(ChemMasterLayout.toLocal(sx,origin[0]),ChemMasterLayout.toLocal(sy,origin[1])));
            var actualButton=ChemMasterLayout.amountButtons(ChemMasterLayout.reagentRow(viewport,0,0)).get(2);
            double doseX=origin[0]+(actualButton.x()+actualButton.width()/2d)/2d;
            double doseY=origin[1]+(actualButton.y()+actualButton.height()/2d)/2d;
            assertEquals(new ChemMasterLayout.DoseHit(0,2),ChemMasterLayout.doseHit(viewport,0,5,
                    ChemMasterLayout.toLocal(doseX,origin[0]),ChemMasterLayout.toLocal(doseY,origin[1])));
        }
    }
    @Test void eachListClipUsesWindowAbsoluteGuiCoordinatesAtMovedOrigins(){
        var geometry=ChemMasterLayout.geometry(500,277);
        for(int[] origin:new int[][]{{0,0},{137,41},{251,99},{399,165}}){
            var calls=new java.util.ArrayList<ChemMasterLayout.ScreenRect>();
            ChemMasterOverlay.enableRowScissor(geometry.inputViewport(),origin[0],origin[1],calls::add);
            ChemMasterOverlay.enableRowScissor(geometry.bufferViewport(),origin[0],origin[1],calls::add);
            assertEquals(2,calls.size());var input=calls.get(0);var buffer=calls.get(1);
            assertEquals(origin[0]+Math.floorDiv(geometry.inputViewport().x()+1,2),input.left());
            assertEquals(origin[1]+Math.floorDiv(geometry.inputViewport().y()+1,2),input.top());
            assertEquals(origin[0]+Math.floorDiv(geometry.bufferViewport().x()+1,2),buffer.left());
            assertEquals(origin[1]+Math.floorDiv(geometry.bufferViewport().y()+1,2),buffer.top());
            assertNotEquals(input,buffer);
        }
    }
    @Test void inputAndBufferScrollOffsetsRemainIndependent(){
        var workspace=new ChemMasterWorkspace();var machine=java.util.UUID.randomUUID();
        workspace.accept(state(java.util.UUID.randomUUID(),machine,1,true),500,277);
        var window=workspace.get(machine);window.scrollInput(18,90);window.scrollBuffer(42,160);
        assertEquals(18,window.inputScroll());assertEquals(42,window.bufferScroll());
        window.scrollInput(-18,90);assertEquals(0,window.inputScroll());assertEquals(42,window.bufferScroll());workspace.clear();
    }
    @Test void reagentNamesUseTranslationAndFriendlyFallback(){
        assertNotEquals("reagent-name-water",ChemMasterOverlay.localizedReagentName(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","water")).getString());
        assertNotEquals("reagent-name-sugar",ChemMasterOverlay.localizedReagentName(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","sugar")).getString());
        assertEquals("unknown reagent",ChemMasterOverlay.localizedReagentName(net.minecraft.resources.ResourceLocation.withDefaultNamespace("unknown_reagent")).getString());
    }
    @Test void rendererCaptionKeepsInsertedNameAndCurrentCapacity(){
        var base=state(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),1,true);
        var inserted=new com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState(1,1,0,-1,java.util.Optional.empty(),base.sessionId(),base.machineId(),java.util.UUID.randomUUID(),1,
                base.dimension(),0,64,0,true,java.util.Optional.empty(),java.util.Optional.of(net.minecraft.network.chat.Component.literal("Large Beaker")),10000,1000,
                java.util.List.of(new com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState.ReagentRow(com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water"),1000)),base.bufferReagents(),base.mode(),base.sort());
        var geo=ChemMasterLayout.geometry(500,277);
        assertTrue(geo.caption().width()>0);
        assertEquals("Large Beaker  10 / 100u",ChemMasterOverlay.containerCaption(inserted));
        assertEquals("No container",ChemMasterOverlay.containerCaption(base));
    }
    @Test void doseButtonStylesSeparateHoverSelectionAndDisabled(){
        assertNotEquals(com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer.faceColor(false,true,false),
                com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer.faceColor(true,true,false));
        assertNotEquals(com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer.faceColor(false,true,false),
                com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer.faceColor(false,false,false));
        assertNotEquals(com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer.faceColor(false,true,false),
                com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer.faceColor(false,true,true));
    }
    @Test void sectionHeadersSortAndModesHaveDistinctHitTargets(){
        var g=ChemMasterLayout.geometry(640,360);
        assertTrue(g.inputViewport().y()>g.inputHeading().y());
        assertTrue(g.bufferHeading().y()>g.inputViewport().y()+g.inputViewport().height());
        assertTrue(g.bufferViewport().y()>g.bufferHeading().y());
        assertFalse(intersects(g.sort(),g.transferMode()));assertFalse(intersects(g.transferMode(),g.discardMode()));
        assertTrue(g.sort().x()>=g.bufferHeading().x()+g.bufferHeading().width()+2);
        assertEquals(g.bufferPanel().x()+g.bufferPanel().width(),g.discardMode().x()+g.discardMode().width());
    }
    @Test void doseGeometryUsesPerRowMeasuredAmountAndSharesDrawHitRects(){
        var view=new ChemMasterLayout.Rect(7,90,326,128);var row=ChemMasterLayout.reagentRow(view,0,0);
        int typical=24,longAmount=43;
        var typicalButtons=ChemMasterLayout.amountButtons(row,typical);
        var longButtons=ChemMasterLayout.amountButtons(row,longAmount);
        assertEquals(row.x()+96,typicalButtons.getFirst().x());
        assertTrue(longButtons.getLast().x()+longButtons.getLast().width()<typicalButtons.getLast().x()+typicalButtons.getLast().width());
        assertTrue(longButtons.getLast().x()+longButtons.getLast().width()<=ChemMasterLayout.quantityCell(row,longAmount).x()-4);
        assertTrue(typicalButtons.get(0).width()>ChemMasterLayout.amountButtons(row,78).get(0).width());
        var last=longButtons.getLast();
        assertEquals(new ChemMasterLayout.DoseHit(0,9),ChemMasterLayout.doseHit(view,0,1,last.x()+last.width()/2d,last.y()+last.height()/2d,i->longAmount));
    }
    @Test void bufferActionsStayLeftOfSortAndTabsJoinTheirPane(){
        for(int width:new int[]{640,320,120,1}) {
            var g=ChemMasterLayout.geometry(width,360,24,31);
            if(g.transferMode().width()>0&&g.discardMode().width()>0&&g.sort().width()>0) {
                assertTrue(g.sort().x()+g.sort().width()+4<=g.transferMode().x());
                assertTrue(g.transferMode().x()+g.transferMode().width()+4<=g.discardMode().x());
                assertTrue(g.discardMode().x()+g.discardMode().width()<=g.width()-ChemMasterLayout.MARGIN);
                assertTrue(g.sort().x()+g.sort().width()<=g.width()-ChemMasterLayout.MARGIN);
            } else {
                assertEquals(0,g.transferMode().width());assertEquals(0,g.discardMode().width());assertEquals(0,g.sort().width());
            }
        }
        var g=ChemMasterLayout.geometry(500,277);
        assertEquals(g.inputTab().x()+g.inputTab().width(),g.outputTab().x());
        assertEquals(g.inputTab().y(),g.outputTab().y());
        assertEquals(g.inputTab().y()+g.inputTab().height(),ChemMasterLayout.HEADER);
        assertTrue(g.inputTab().x()>90,"title owns the left header; tabs occupy right side");
        assertFalse(intersects(g.inputTab(),g.outputTab()));
        assertEquals(ChemMasterLayout.HEADER,g.inputTab().y()+g.inputTab().height());
        assertEquals(105,g.sort().width());
    }
    private static boolean intersects(ChemMasterLayout.Rect a,ChemMasterLayout.Rect b){return a.x()<b.x()+b.width()&&b.x()<a.x()+a.width()&&a.y()<b.y()+b.height()&&b.y()<a.y()+a.height();}
    @Test void rowsExposeEveryDoseAndHitTestingTracksIndependentScroll() {
        assertArrayEquals(new int[]{1,5,10,15,20,25,30,50,100,-1},ChemMasterLayout.AMOUNTS);
        var viewport=new ChemMasterLayout.Rect(10,30,200,44);
        assertEquals(1,ChemMasterLayout.visibleRowAt(viewport,ChemMasterLayout.ROW_HEIGHT,11,31,20));
        assertEquals(-1,ChemMasterLayout.visibleRowAt(viewport,12,9,31,20));
        assertEquals(0,ChemMasterLayout.clampScroll(-2,100,ChemMasterLayout.ROW_HEIGHT,44));
         assertEquals(1256,ChemMasterLayout.clampScroll(9999,100,ChemMasterLayout.ROW_HEIGHT,44));
        var row=ChemMasterLayout.reagentRow(viewport,0,0);var buttons=ChemMasterLayout.amountButtons(row);
        assertEquals(10,buttons.size());
        assertTrue(buttons.get(9).x()+buttons.get(9).width()<=220);
        var clicked=buttons.get(8);var mapping=ChemMasterLayout.doseHit(viewport,ChemMasterLayout.ROW_HEIGHT,20,clicked.x()+clicked.width()/2d,clicked.y()+clicked.height()/2d);
        assertEquals(new ChemMasterLayout.DoseHit(1,8),mapping);
    }
    @Test void sortCycleMatchesServerContract() {
        assertEquals(ChemMasterLayout.Sort.ALPHABETICAL,ChemMasterLayout.Sort.OLDEST_FIRST.next());
        assertEquals(ChemMasterLayout.Sort.RECENT_FIRST,ChemMasterLayout.Sort.QUANTITY.next());
        assertEquals(ChemMasterLayout.Sort.OLDEST_FIRST,ChemMasterLayout.Sort.RECENT_FIRST.next());
    }
    @Test void inputKeepsSnapshotOrderWhileBufferSortingIsDisplayOnly(){
        var keyA=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("test_a");
        var keyB=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("test_b");
        var rows=java.util.List.of(new com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState.ReagentRow(keyA,500),
                new com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState.ReagentRow(keyB,100));
        var session=java.util.UUID.randomUUID();var machine=java.util.UUID.randomUUID();var actor=java.util.UUID.randomUUID();
        var state=new com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState(1,1,0,-1,java.util.Optional.empty(),session,machine,actor,1,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"),0,64,0,true,java.util.Optional.empty(),java.util.Optional.empty(),600,600,rows,rows,
                com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest.Mode.TRANSFER,
                com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest.Sort.QUANTITY);
        var workspace=new ChemMasterWorkspace();workspace.accept(state,640,360);var window=workspace.get(machine);
        assertEquals(rows,workspace.presentationRows(window,true));
        assertEquals(java.util.List.of(keyB,keyA),workspace.presentationRows(window,false).stream().map(r->r.reagent()).toList());
        assertEquals(rows,window.state().bufferReagents());workspace.clear();
    }
    @Test void authoritativeFramesRejectStaleAndExplicitCloseDoesNotRemoveSibling(){
        var workspace=new ChemMasterWorkspace();
        var session=java.util.UUID.randomUUID();var a=java.util.UUID.randomUUID();var b=java.util.UUID.randomUUID();
        workspace.accept(state(session,a,2,true),640,360);workspace.accept(state(java.util.UUID.randomUUID(),b,1,true),640,360);
        var sibling=workspace.get(b);assertNotNull(workspace.get(a));
        workspace.accept(state(session,a,1,false),640,360);
        assertNotNull(workspace.get(a)); // stale close cannot end a newer frame
        workspace.close(workspace.get(a));
        assertNull(workspace.get(a));assertSame(sibling,workspace.get(b));
        workspace.accept(state(session,a,3,true),640,360);assertNull(workspace.get(a)); // dismissed session cannot resurrect
        workspace.clear();
    }
    @Test void pendingIsScopedToOneWindowAndWorkspaceAllowsMoreThanThreeSiblings(){
        var workspace=new ChemMasterWorkspace();var sent=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest>();workspace.sender(sent::add);
        for(int i=0;i<5;i++)workspace.accept(state(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),1,true),640,360);
        assertEquals(5,workspace.windows().size());
        var first=workspace.windows().get(0);var second=workspace.windows().get(1);
        workspace.send(first,com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest.Operation.EJECT,null,0);
        assertTrue(first.pending());assertFalse(second.pending());assertEquals(1,sent.size());
        workspace.accept(state(first.state().sessionId(),first.state().machineId(),2,true,sent.get(0).requestId()),640,360);
        assertFalse(first.pending());
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.paintOrder().size()>=5);
        var layers=new java.util.HashSet<Integer>();for(int i=0;i<5;i++)layers.add(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerWorkspaceLayers.browserLayer(i,false,5));
        assertEquals(5,layers.size());assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerWorkspaceLayers.browserLayer(0,true,5)
                >com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerWorkspaceLayers.storageLayer(0,false,5));
        // Input/mouse-lock and focus state do not own or expire a machine session.
        assertSame(first,workspace.get(first.state().machineId()));workspace.clear();
    }
    @Test void delayedOpenFromRetiredSessionCannotReplaceNewerSession(){
        var workspace=new ChemMasterWorkspace();var machine=java.util.UUID.randomUUID();var old=java.util.UUID.randomUUID();var current=java.util.UUID.randomUUID();
        workspace.accept(state(old,machine,8,true),640,360);workspace.accept(state(current,machine,1,true),640,360);
        var live=workspace.get(machine);assertEquals(current,live.state().sessionId());
        workspace.accept(state(old,machine,9,true),640,360);assertSame(live,workspace.get(machine));
        workspace.clear();
    }
    @Test void outputLayoutKeepsTwentyPaletteTargetsAndEditorsInsideOutputPane(){
        var pane=new ChemMasterOutputLayout.Rect(10,32,318,232);var g=ChemMasterOutputLayout.geometry(pane,9);
        assertEquals(20,g.pills().size());
        assertTrue(g.sourceSelection().width()<=90,"source switches stay compact instead of filling the pane");
        assertTrue(g.sourceSelection().height()>=14,"source switches remain easy to click");
        assertEquals(32,g.pillPalette().height(),"two icon rows use existing pane whitespace for button-sized art");
        for(var r:java.util.List.of(g.label(),g.sourceCaption(),g.eject(),g.sourceSelection(),g.outputCaption(),g.pillPalette(),
                g.pillCountLabel(),g.pillCount(),g.pillCountMinus(),g.pillCountPlus(),g.pillDoseLabel(),g.pillDose(),g.pillDoseMinus(),g.pillDosePlus(),g.createPills(),
                g.bottleDoseLabel(),g.bottleDose(),g.bottleDoseMinus(),g.bottleDosePlus(),g.fillBottle(),g.outputInfo())){
            assertTrue(r.x()>=pane.x()&&r.y()>=pane.y()&&r.x()+r.width()<=pane.x()+pane.width()&&r.y()+r.height()<=pane.y()+pane.height(),r.toString());
        }
        for(var cell:g.pills())assertTrue(g.pillPalette().contains(cell.x(),cell.y()));
        assertFalse(overlaps(g.label(),g.pillCount()));assertFalse(overlaps(g.pillPalette(),g.pillCountLabel()));
        assertFalse(overlaps(g.pillCountLabel(),g.pillCount()));assertFalse(overlaps(g.pillPalette(),g.pillCount()));
        assertFalse(overlaps(g.pillCount(),g.pillCountMinus()));assertFalse(overlaps(g.pillCount(),g.pillCountPlus()));
        assertFalse(overlaps(g.pillDoseLabel(),g.pillDose()));assertFalse(overlaps(g.bottleDoseLabel(),g.bottleDose()));
        assertFalse(overlaps(g.pillDose(),g.pillDoseMinus()));assertFalse(overlaps(g.pillDose(),g.pillDosePlus()));
        assertFalse(overlaps(g.bottleDose(),g.bottleDoseMinus()));assertFalse(overlaps(g.bottleDose(),g.bottleDosePlus()));
        assertFalse(overlaps(g.sourceCaption(),g.eject()));
        for(int row=0;row<2;row++){
            int total=g.pills().subList(row*10,row*10+10).stream().mapToInt(ChemMasterOutputLayout.Rect::width).sum();
            assertTrue(total<=g.pillPalette().width());
            int height=g.pills().get(row*10).height();
            for(int col=0;col<10;col++)assertEquals(height,g.pills().get(row*10+col).height());
            for(int col=0;col<9;col++)assertEquals(g.pills().get(row*10).width(),g.pills().get(row*10+col).width());
        }
    }
    @Test void narrowOutputPaletteGeometryRemainsNonnegative(){
        var pane=new ChemMasterOutputLayout.Rect(0,0,12,24);var g=ChemMasterOutputLayout.geometry(pane,9);
        assertEquals(20,g.pills().size());
        for(var cell:g.pills()){assertTrue(cell.width()>=0);assertTrue(cell.height()>=0);}
    }
    private static boolean overlaps(ChemMasterOutputLayout.Rect a,ChemMasterOutputLayout.Rect b){
        return a.x()<b.x()+b.width()&&b.x()<a.x()+a.width()&&a.y()<b.y()+b.height()&&b.y()<a.y()+a.height();
    }

    @Test void realWorkspaceOpenRefreshRequestDragResizeAndCloseKeepSessionAndRegistryConsistent(){
        var pointerAvailable=new java.util.concurrent.atomic.AtomicBoolean(true);
        var workspace=new ChemMasterWorkspace(text->text.length()*6,pointerAvailable::get);var sent=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest>();
        var lastClosed=new java.util.concurrent.atomic.AtomicInteger();workspace.lastWindowClosedHook(lastClosed::incrementAndGet);
        workspace.sender(sent::add);
        var machine=java.util.UUID.randomUUID();var session=java.util.UUID.randomUUID();
        var opened=state(session,machine,1,true);
        workspace.accept(opened,640,360);
        var window=workspace.get(machine);assertNotNull(window);
        var overlay=com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.paintOrder().stream()
                .filter(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterOverlay.class::isInstance).findFirst().orElseThrow();
        assertTrue(overlay.visible());
        for(int frame=2;frame<=4;frame++)workspace.accept(state(session,machine,frame,true),640,360);
        assertSame(window,workspace.get(machine));assertSame(overlay,
                com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.paintOrder().stream()
                        .filter(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterOverlay.class::isInstance).findFirst().orElseThrow());
        var geo=ChemMasterLayout.geometry(640,360,"Input".length()*6,"Output".length()*6);
        double ejectX=window.x()+geo.eject().x()/2d+geo.eject().width()/4d;
        double ejectY=window.y()+geo.eject().y()/2d+geo.eject().height()/4d;
        pointerAvailable.set(false);
        for(int frame=5;frame<=7;frame++)workspace.accept(state(session,machine,frame,true),640,360);
        assertSame(window,workspace.get(machine));assertFalse(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(ejectX,ejectY,0));
        pointerAvailable.set(true);
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(ejectX,ejectY,0));
        assertEquals(1,sent.size());assertEquals(session,sent.getFirst().sessionId());assertTrue(window.pending());
        assertEquals(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest.Operation.EJECT,sent.getFirst().operation());
        double titleX=window.x()+2,titleY=window.y()+2;
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(titleX,titleY,0));
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.drag(-1000,-1000));
        assertEquals(0,window.x());assertEquals(0,window.y());
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.release(0));
        ((ChemMasterOverlay)overlay).viewportSize(120,80);assertTrue(window.x()+Math.min(ChemMasterLayout.WIDTH/2,120)<=120);
        var closeGeo=ChemMasterLayout.geometry(120,80,"Input".length()*6,"Output".length()*6);
        double closeX=window.x()+closeGeo.close().x()/2d+closeGeo.close().width()/4d;
        double closeY=window.y()+closeGeo.close().y()/2d+closeGeo.close().height()/4d;
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(closeX,closeY,0));
        assertNull(workspace.get(machine));assertFalse(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.paintOrder().contains(overlay));
        assertEquals(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest.Operation.CLOSE,sent.getLast().operation());
        assertEquals(1,lastClosed.get());
        workspace.close(window);workspace.closeLocal(window);
        assertEquals(1,lastClosed.get());assertEquals(2,sent.size());
        workspace.accept(state(session,machine,8,true),120,80);assertNull(workspace.get(machine));
        workspace.clear();
    }

    @Test void overlappingRealChemMasterOverlaysRouteOnlyToPaintedFrontSessionAndContextRetiresBoth(){
        var workspace=new ChemMasterWorkspace(text->text.length()*6,()->true);
        workspace.lastWindowClosedHook(()->{});
        var machineA=java.util.UUID.randomUUID();var machineB=java.util.UUID.randomUUID();
        workspace.accept(state(java.util.UUID.randomUUID(),machineA,1,true),640,360);
        workspace.accept(state(java.util.UUID.randomUUID(),machineB,1,true),640,360);
        var under=workspace.get(machineA);var front=workspace.get(machineB);
        workspace.beginDrag(front,front.x()+2,front.y()+2);workspace.drag(under.x()+2,under.y()+2,640,360);workspace.endDrag();
        var frontOverlay=com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.paintOrder().stream()
                .filter(o->o instanceof ChemMasterOverlay).toList();
        assertEquals(2,frontOverlay.size());
        var geo=ChemMasterLayout.geometry(640,360,30,36);
        var point=geo.eject();double px=front.x()+point.x()/2d+point.width()/4d,py=front.y()+point.y()/2d+point.height()/4d;
        var requests=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest>();workspace.sender(requests::add);
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(px,py,0));
        assertEquals(1,requests.size());assertEquals(front.state().sessionId(),requests.getFirst().sessionId());
        var actor=front.state().bodyId();workspace.contextChanged(java.util.UUID.randomUUID(),1,front.state().dimension());
        assertNull(workspace.get(machineA));assertNull(workspace.get(machineB));
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.paintOrder().stream()
                .noneMatch(o->o instanceof ChemMasterOverlay));
        workspace.clear();
    }

    @Test void registeredOverlayRoutesTabsBeforeTitleDragAndPreservesCloseAndLockedCursor(){
        var pointerAvailable=new java.util.concurrent.atomic.AtomicBoolean(true);
        var workspace=new ChemMasterWorkspace(text->text.length()*6,pointerAvailable::get);workspace.lastWindowClosedHook(()->{});
        var session=java.util.UUID.randomUUID();var machine=java.util.UUID.randomUUID();workspace.accept(state(session,machine,1,true),640,360);
        var window=workspace.get(machine);var overlay=(ChemMasterOverlay)com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.paintOrder().stream()
                .filter(ChemMasterOverlay.class::isInstance).findFirst().orElseThrow();
        var geometry=ChemMasterLayout.geometry(640,360,30,36);
        double outputX=window.x()+(geometry.outputTab().x()+geometry.outputTab().width()/2d)/2d;
        double outputY=window.y()+(geometry.outputTab().y()+geometry.outputTab().height()/2d)/2d;
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(outputX,outputY,0));
        assertTrue(overlay.outputTabSelected());assertFalse(overlay.dragging());
        double inputX=window.x()+(geometry.inputTab().x()+geometry.inputTab().width()/2d)/2d;
        double inputY=window.y()+(geometry.inputTab().y()+geometry.inputTab().height()/2d)/2d;
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(inputX,inputY,0));
        assertFalse(overlay.outputTabSelected());assertFalse(overlay.dragging());
        pointerAvailable.set(false);
        assertFalse(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(outputX,outputY,0));
        assertFalse(overlay.outputTabSelected());assertFalse(overlay.dragging());
        pointerAvailable.set(true);
        double titleX=window.x()+10,titleY=window.y()+4;
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(titleX,titleY,0));
        assertTrue(overlay.dragging());assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.release(0));
        assertFalse(overlay.dragging());
        double closeX=window.x()+geometry.close().x()/2d+geometry.close().width()/4d;
        double closeY=window.y()+geometry.close().y()/2d+geometry.close().height()/4d;
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.click(closeX,closeY,0));
        assertNull(workspace.get(machine));workspace.clear();
    }
}
