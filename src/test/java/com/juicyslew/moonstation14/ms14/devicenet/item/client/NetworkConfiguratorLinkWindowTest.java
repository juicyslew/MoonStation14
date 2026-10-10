package com.juicyslew.moonstation14.ms14.devicenet.item.client;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorSnapshot;
import com.juicyslew.moonstation14.ms14.devicenet.item.ConfiguratorOperationSequence;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClientPolicy;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;

class NetworkConfiguratorLinkWindowTest {
    @Test void selectionFeedbackNamesAuthoritativeEndpointRoles() {
        var source=DeviceIdentity.random(); var sink=DeviceIdentity.random();
        var onlySource=new NetworkConfiguratorSnapshot(UUID.randomUUID(),1,2,true,source,null,"button","",
                List.of(),List.of(),List.of());
        assertEquals("Network Configurator: button selected as source; select a receiver",
                com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.selectionFeedback(onlySource));
        var paired=new NetworkConfiguratorSnapshot(UUID.randomUUID(),2,2,true,source,sink,"button","door",
                List.of(),List.of(),List.of());
        assertEquals("Network Configurator: button → door selected; opening link editor",
                com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.selectionFeedback(paired));
    }
    @Test void confirmedConfiguratorWindowSurvivesPendingBlockUseAndKeepsPointerOpen() {
        var policy=new BodyHandClientPolicy(); var account=UUID.randomUUID(); var body=UUID.randomUUID();
        policy.bind(account,body,2);
        var state=new BodyHandStateSnapshot(0,policy.query(1),BodyHandStateSnapshot.Reason.OK,account,body,2,1,"left",List.of(
                new BodyHandStateSnapshot.Hand("left","token","moonstation14:network_configurator",1),
                new BodyHandStateSnapshot.Hand("right",null,null,0)));
        assertTrue(policy.accept(state));
        long use=policy.blockUseSequence(2); assertTrue(use>0); assertNull(policy.inventoryView());
        var proof=policy.confirmedSnapshot(); // same read-only accessor used by networkConfiguratorProof/receiver
        assertNotNull(proof); assertEquals(2,proof.epoch());
        var stateWindow=NetworkConfiguratorLinkWindowState.INSTANCE; stateWindow.clear();
        stateWindow.accept(snapshot(UUID.randomUUID(),1,true),800,600,ignored->{ });
        assertTrue(stateWindow.window().visible()); assertTrue(NetworkConfiguratorWindows.anyVisible()); // before block-use result packet
        assertTrue(policy.blockUseResult(new com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult(2,use,
                com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE)));
        assertTrue(stateWindow.window().visible()); assertNotNull(policy.inventoryView());
        policy.suspend(); assertNull(policy.confirmedSnapshot());
        stateWindow.clear(); assertFalse(NetworkConfiguratorWindows.anyVisible());
    }
    private NetworkConfiguratorSnapshot snapshot(long request){return new NetworkConfiguratorSnapshot(UUID.randomUUID(),request,2,true,
            DeviceIdentity.random(),DeviceIdentity.random(),"a","b",List.of(new NetworkConfiguratorSnapshot.Port("out","Output","Source port")),
            List.of(new NetworkConfiguratorSnapshot.Port("in","Input","Sink port")),List.of());}
    @Test void selectionIssuesImmediatePairAndButtonsDoNotDraft(){
        var w=new NetworkConfiguratorLinkWindow();var sent=new ArrayList<NetworkConfiguratorLinkWindow.Command>();var s=snapshot(1);
        w.accept(s,800,600,sent::add);
        assertTrue(w.click(w.x()+20,w.y()+60));assertTrue(w.click(w.x()+180,w.y()+60));
        assertEquals(List.of("out","in"),List.of(sent.get(0).sourcePort(),sent.get(0).sinkPort()));
        assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.TOGGLE_PAIR,sent.get(0).operation());
        assertTrue(w.click(w.x()+43,w.y()+231));assertTrue(w.click(w.x()+123,w.y()+231));
        assertEquals(List.of(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.TOGGLE_PAIR,
                com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.CLEAR,
                com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.LINK_DEFAULTS),sent.stream().map(NetworkConfiguratorLinkWindow.Command::operation).toList());
    }
    @Test void resizesClampAndDoneCloses(){var w=new NetworkConfiguratorLinkWindow();var sent=new ArrayList<NetworkConfiguratorLinkWindow.Command>();
        w.accept(snapshot(1),800,600,sent::add);w.resize(170,140);assertEquals(0,w.x());assertEquals(0,w.y());
         assertFalse(w.click(267,231));assertTrue(w.visible());w.resize(800,600);assertTrue(w.click(w.x()+267,w.y()+231));assertFalse(w.visible());assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.DONE,sent.get(0).operation());}
    @Test void titleDragMovesAndClampsUsingSharedGeometry(){var w=new NetworkConfiguratorLinkWindow();w.accept(snapshot(1),800,600,c->{ });
        assertTrue(w.beginDrag(w.x()+10,w.y()+10,0));assertTrue(w.drag(900,700));assertEquals(500,w.x());assertEquals(350,w.y());
        w.endDrag();assertFalse(w.drag(100,100));}
    @Test void pendingSelectionTogglesAndClearDropsOnlyLocalPendingSelection(){var w=new NetworkConfiguratorLinkWindow();var sent=new ArrayList<NetworkConfiguratorLinkWindow.Command>();
        w.accept(snapshot(1),800,600,sent::add);
        w.click(w.x()+20,w.y()+60);assertEquals("out",w.selectedSource());w.click(w.x()+20,w.y()+60);assertNull(w.selectedSource());
        w.click(w.x()+20,w.y()+60);w.click(w.x()+20,w.y()+79);assertEquals("out",w.selectedSource());
        w.click(w.x()+180,w.y()+60);assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.TOGGLE_PAIR,sent.get(0).operation());assertNull(w.selectedSource());
        w.click(w.x()+20,w.y()+60);w.click(w.x()+20,w.y()+231);assertNull(w.selectedSource());assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.CLEAR,sent.get(1).operation());}
    @Test void panesFitAreaAndPointerMappingAtSmallAndLargeViewports(){for(int[] size:new int[][]{{320,180},{640,360},{1280,720},{160,90}}){
        var l=ConfiguratorPaneLayout.fit(size[0],size[1],300,250);assertTrue(l.width*l.height<=size[0]*size[1]/6.0+size[0]+size[1]);
        for(double logicalX:new double[]{0,150,299}){double gui=l.x+logicalX*l.scale;assertEquals(logicalX,l.logicalX(gui),.01);}
        var w=new NetworkConfiguratorLinkWindow();w.accept(snapshot(1),size[0],size[1],ignored->{});assertTrue(w.x()>=0&&w.y()>=0);assertTrue(w.x()+w.width()<=size[0]);assertTrue(w.y()+w.height()<=size[1]);
        assertTrue(6L*w.width()*w.height()<=size[0]*(long)size[1]);
    }}
    @Test void configuratorTextUsesChatSizedEffectiveGlyphsWithoutClippingRows(){for(int[] size:new int[][]{{320,180},{640,360},{1280,720},{160,90}}){
        double frame=ConfiguratorPaneLayout.fit(size[0],size[1],300,250).scale;
        double body=frame*ConfiguratorPaneLayout.textGlyphScale(frame,.5f,1.5f);
        double title=frame*ConfiguratorPaneLayout.textGlyphScale(frame,.65f,2f);
        assertEquals(Math.min(.5,frame*1.5),body,1e-6);assertEquals(Math.min(.65,frame*2),title,1e-6);
        assertTrue(9*body<=19*frame);assertTrue(9*title<=23*frame);
        assertTrue(ConfiguratorPaneLayout.textWidthBudget(137,ConfiguratorPaneLayout.textGlyphScale(frame,.5f,1.5f))>0);
    }}
    @Test void scaledButtonHitRegionsMatchRenderedButtonCentersAndDragUsesGuiCoordinates(){var w=new NetworkConfiguratorLinkWindow();var sent=new ArrayList<NetworkConfiguratorLinkWindow.Command>();w.accept(snapshot(1),320,180,sent::add);
        double s=w.displayScale();assertTrue(s<1);assertTrue(w.beginDrag(w.x()+4,w.y()+4,0));assertTrue(w.drag(300,170));assertTrue(w.x()+w.width()<=320);w.endDrag();
        w.click(w.x()+(83+40)*s,w.y()+(222+9)*s);assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.LINK_DEFAULTS,sent.get(0).operation());
        w.click(w.x()+(242+25)*s,w.y()+(222+9)*s);assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.DONE,sent.get(1).operation());}
    @Test void linkButtonRenderAndHitRectanglesShareExactEdgesAndInitialBoundsAreCentered(){
        for(int[] viewport:new int[][]{{320,180},{640,360},{1280,720}}){var w=new NetworkConfiguratorLinkWindow();var sent=new ArrayList<NetworkConfiguratorLinkWindow.Command>();w.accept(snapshot(1),viewport[0],viewport[1],sent::add);
            assertEquals((viewport[0]-w.width())/2,w.x());assertEquals((viewport[1]-w.height())/2,w.y());double scale=w.displayScale();
            assertTrue(w.click(w.x()+ConfiguratorPaneLayout.LINK_CLEAR.x()*scale,w.y()+ConfiguratorPaneLayout.LINK_CLEAR.y()*scale));
            assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.CLEAR,sent.get(0).operation());
            w.click(w.x()+(ConfiguratorPaneLayout.LINK_CLEAR.x()+ConfiguratorPaneLayout.LINK_CLEAR.width()+.01)*scale,w.y()+(ConfiguratorPaneLayout.LINK_CLEAR.y()+1)*scale);
            w.click(w.x()+ConfiguratorPaneLayout.LINK_DEFAULTS.x()*scale,w.y()+ConfiguratorPaneLayout.LINK_DEFAULTS.y()*scale);
            assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.LINK_DEFAULTS,sent.get(1).operation());
            w.click(w.x()+(ConfiguratorPaneLayout.LINK_DEFAULTS.x()+1)*scale,w.y()+(ConfiguratorPaneLayout.LINK_DEFAULTS.y()+ConfiguratorPaneLayout.LINK_DEFAULTS.height()+.01)*scale);
            w.click(w.x()+(ConfiguratorPaneLayout.LINK_DONE.x()+10)*scale,w.y()+245*scale);
            assertEquals(2,sent.size());
            w.click(w.x()+ConfiguratorPaneLayout.LINK_DONE.x()*scale,w.y()+ConfiguratorPaneLayout.LINK_DONE.y()*scale);
            assertEquals(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.DONE,sent.get(2).operation());assertFalse(w.visible());
        }
    }
    @Test void defaultsClearsEitherPendingLocalPortSelectionAndPreservesEndpointSnapshot(){
        for(boolean sourcePending:new boolean[]{true,false}){
            var w=new NetworkConfiguratorLinkWindow();var sent=new ArrayList<NetworkConfiguratorLinkWindow.Command>();var s=snapshot(1);
            w.accept(s,320,180,sent::add);
            w.click(w.x()+(sourcePending?20:180)*w.displayScale(),w.y()+60*w.displayScale());
            assertEquals(sourcePending?"out":null,w.selectedSource());assertEquals(sourcePending?null:"in",w.selectedSink());
            double scale=w.displayScale();var button=ConfiguratorPaneLayout.LINK_DEFAULTS;
            assertTrue(w.click(w.x()+(button.x()+button.width()/2.0)*scale,w.y()+(button.y()+button.height()/2.0)*scale));
            assertNull(w.selectedSource());assertNull(w.selectedSink());
            assertEquals(List.of(com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest.Operation.LINK_DEFAULTS),sent.stream().map(NetworkConfiguratorLinkWindow.Command::operation).toList());
            assertEquals(s.source(),w.snapshot().source());assertEquals(s.sink(),w.snapshot().sink());assertEquals(s.session(),w.snapshot().session());
        }
    }
    @Test void scrolledPortsUseRenderedRowsForSelectionAndConnectionGeometry(){var w=new NetworkConfiguratorLinkWindow();var ports=new ArrayList<NetworkConfiguratorSnapshot.Port>();for(int i=0;i<10;i++)ports.add(new NetworkConfiguratorSnapshot.Port("p"+i,"Port "+i,""));
        var snap=new NetworkConfiguratorSnapshot(UUID.randomUUID(),1,2,true,DeviceIdentity.random(),DeviceIdentity.random(),"a","b",ports,ports,List.of());w.accept(snap,320,180,c->{});w.scrollPorts(-1);
        int visibleY=NetworkConfiguratorLinkWindow.portRowTop(0)+6;double scale=w.displayScale();w.click(w.x()+20*scale,w.y()+visibleY*scale);assertEquals("p1",w.selectedSource());
        var line=NetworkConfiguratorLinkRenderer.connectionSegment(5,7,3);assertNotNull(line);assertEquals(142,line.x1());assertEquals(162,line.x2());
        assertEquals(103,line.y1());assertEquals(141,line.y2());
        var clipped=NetworkConfiguratorLinkRenderer.connectionSegment(7,10,2);assertNotNull(clipped);assertTrue(clipped.y1()>=56&&clipped.y1()<=209);assertEquals(209,clipped.y2());
        assertNull(NetworkConfiguratorLinkRenderer.connectionSegment(0,1,10));}
    @Test void sameSessionSelectionsAndOperationsRequireMonotonicSequence(){
        var w=new NetworkConfiguratorLinkWindow();var id=UUID.randomUUID();var sent=new ArrayList<NetworkConfiguratorLinkWindow.Command>();
        long first=ConfiguratorOperationSequence.nextPublication(-1),second=ConfiguratorOperationSequence.nextPublication(first);
        var closed=snapshot(id,first,false);w.accept(closed,800,600,sent::add);assertFalse(w.visible());
        var open=snapshot(id,second,true);w.accept(open,800,600,sent::add);assertTrue(w.visible());
        assertEquals(open,w.snapshot());
        var stale=snapshot(id,first,false);var staleSent=new ArrayList<NetworkConfiguratorLinkWindow.Command>();
        w.accept(stale,800,600,staleSent::add);assertTrue(w.visible());assertEquals(open,w.snapshot());
        assertEquals(ConfiguratorOperationSequence.nextCommand(0,second),2);
        w.click(w.x()+20,w.y()+60);w.click(w.x()+180,w.y()+60);assertEquals(1,sent.size());
        assertTrue(staleSent.isEmpty());
        long operationId=ConfiguratorOperationSequence.nextCommand(0,second);
        var operation=snapshot(id,operationId,true);w.accept(operation,800,600,sent::add);
        assertEquals(operation,w.snapshot());
        long third=ConfiguratorOperationSequence.nextPublication(operation.requestId());
        assertEquals(ConfiguratorOperationSequence.nextCommand(0,third),third+1);
        assertEquals(1,ConfiguratorOperationSequence.nextCommand(0,ConfiguratorOperationSequence.nextPublication(-1)));
    }
    private NetworkConfiguratorSnapshot snapshot(UUID session,long request,boolean open){return new NetworkConfiguratorSnapshot(session,request,2,open,
            DeviceIdentity.random(),DeviceIdentity.random(),"a","b",List.of(new NetworkConfiguratorSnapshot.Port("out","Output","Source port")),
            List.of(new NetworkConfiguratorSnapshot.Port("in","Input","Sink port")),List.of());}
}
