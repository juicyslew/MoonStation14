package com.juicyslew.moonstation14.ms14.atmos.analyzer.client;

import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerCellSelection;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerSample;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerPipeSamples;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerClientState;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GasAnalyzerWorkspaceTest {
    private final GasAnalyzerWorkspace workspace=GasAnalyzerWorkspace.INSTANCE;
    @AfterEach void clear(){workspace.clear();}

    @Test void windowControlsRemainBoundedAtCompactAndWideViewports() {
        for(int[] viewport:new int[][]{{320,240},{640,360}})for(int scale=1;scale<=3;scale++) {
            var l=GasAnalyzerLayout.calculate(viewport[0],viewport[1]);
            assertTrue(l.width()<=viewport[0]&&l.height()<=viewport[1]);
            assertTrue(l.close().x()+l.close().width()<=l.width());
            assertTrue(l.gasList().height()>0);
            assertEquals(6,GasAnalyzerLayout.visibleGasRows(l),"reclaimed compact-window space should expose six rows");
            assertTrue(l.metrics().y()+l.metrics().height()<=l.gasList().y(),"metrics and graph list must not overlap");
            assertEquals(GasAnalyzerLayout.clampScroll(l,9,99),GasAnalyzerLayout.maxScroll(l,9));
            assertTrue(scale>=1); // GUI scaling changes raster dimensions, not logical hit geometry.
        }
    }

    @Test void distanceChangesSelectSingleSampleAndRespectRangeBounds() {
        workspace.sync(snapshot(1,1,0,false,new BlockPos(4,65,6),GasAnalyzerSnapshot.Status.READY,gasList()),true,true,320,240);
        workspace.adjustDistance(-1,6);
        assertEquals(0,workspace.distance());
        workspace.adjustDistance(1,6);
        assertEquals(1,workspace.distance());
        for(int i=0;i<10;i++)workspace.adjustDistance(1,6);
        assertEquals(6,workspace.distance());
    }

    @Test void gasListScrollUsesSelectedSampleAndItsOwnBounds() {
        var local=gasList();
        var target=gasList();
        workspace.sync(snapshotWithSamples(2,local,target),true,true,320,240);
        var layout=GasAnalyzerLayout.calculate(320,240);
        double mx=workspace.x()+layout.gasList().x()+2, my=workspace.y()+layout.gasList().y()+2;
        assertTrue(workspace.scroll(mx,my,-1,320,240));
        assertEquals(1,workspace.scroll());
        for(int i=0;i<100;i++)workspace.scroll(mx,my,-1,320,240);
        assertEquals(GasAnalyzerLayout.maxScroll(layout,local.size()),workspace.scroll());
    }

    @Test void dragAndGasScrollingUseThePersistentNonmodalWorkspace() {
        var s=snapshot(1,1,0,false,new BlockPos(4,65,6),GasAnalyzerSnapshot.Status.READY,gasList());
        workspace.sync(s,true,true,320,240);
        assertTrue(workspace.visible());
        var l=GasAnalyzerLayout.calculate(320,240);
        var close=com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.closeBounds(
                workspace.x()+l.close().x(),workspace.y()+l.close().y(),l.close().width(),l.close().height());
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.contains(close,
                workspace.x()+l.close().x()+1,workspace.y()+l.close().y()+1));
        assertTrue(workspace.scroll(workspace.x()+l.gasList().x()+2,workspace.y()+l.gasList().y()+3,-1,320,240));
        assertEquals(1,workspace.scroll());
        int oldX=workspace.x(),oldY=workspace.y();
        workspace.click(oldX+10,oldY+3,0,320,240);
        assertTrue(workspace.drag(1000,1000,320,240));
        workspace.endDrag();
        assertEquals(320-l.width(),workspace.x());assertEquals(240-l.height(),workspace.y());
    }

    @Test void staleSnapshotsAndWrongTargetsNeverReinterpretOldGasAsNewCell() {
        BlockPos oldCell=new BlockPos(4,65,6),newCell=new BlockPos(5,65,6);
        var state=snapshot(3,8,4,false,oldCell,GasAnalyzerSnapshot.Status.READY,gasList());
        var selectedNew=new GasAnalyzerCellSelection.Result(GasAnalyzerCellSelection.Status.SELECTED,newCell);
        assertFalse(GasAnalyzerWorkspace.responseMatchesTarget(state,1,0,selectedNew));
        assertFalse(GasAnalyzerWorkspace.responseMatchesTarget(state,1,5,
                new GasAnalyzerCellSelection.Result(GasAnalyzerCellSelection.Status.SELECTED,oldCell)));
        assertTrue(GasAnalyzerWorkspace.responseMatchesTarget(state,1,4,
                new GasAnalyzerCellSelection.Result(GasAnalyzerCellSelection.Status.SELECTED,oldCell)));
        workspace.sync(state,true,true,320,240);
        workspace.sync(snapshot(3,7,4,false,newCell,GasAnalyzerSnapshot.Status.READY,gasList()),true,true,320,240);
        assertEquals(8,workspace.snapshot().sequence());
        var closed=snapshot(3,9,4,true,oldCell,GasAnalyzerSnapshot.Status.INVALIDATED,null);
        workspace.sync(closed,true,true,320,240);
        assertFalse(workspace.visible());
        workspace.sync(snapshot(3,10,4,false,oldCell,GasAnalyzerSnapshot.Status.READY,gasList()),true,true,320,240);
        assertFalse(workspace.visible(),"a later same-generation state cannot reopen a closed window");
    }

    @Test void legitimateOriginOpaqueBlockedResponseCanCorrelateWithoutLeakingASample() {
        var state=new GasAnalyzerSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000051"),1,1,0,10,3,
                UUID.fromString("00000000-0000-0000-0000-000000000052"),"source-token",ResourceLocation.withDefaultNamespace("overworld"),
                BlockPos.ZERO,null,0,GasAnalyzerSnapshot.Status.BLOCKED,null,null,false);
        assertTrue(GasAnalyzerWorkspace.responseMatchesTarget(state,0,0,
                new GasAnalyzerCellSelection.Result(GasAnalyzerCellSelection.Status.BLOCKED,null)));
    }

    @Test void namedNodeRowsDriveCompactLayoutScrollAndPinnedHighlight() {
        var target=new BlockPos(7,65,8);var nodes=new java.util.ArrayList<GasAnalyzerPipeSamples.Entry>();
        for(String port:List.of("inlet","outlet","bypass")){
            var sample=new GasAnalyzerSample(100,20,293,gasList());
            nodes.add(new GasAnalyzerPipeSamples.Entry(new GasAnalyzerPipeSamples.Stamp(
                    new PipeTopology.NodeKey(target,PipeTopology.Layer.PRIMARY,port),UUID.randomUUID(),1,.1),sample));
        }
        var state=new GasAnalyzerSnapshot(UUID.randomUUID(),1,1,0,10,1,UUID.randomUUID(),"source",
                ResourceLocation.withDefaultNamespace("overworld"),BlockPos.ZERO,target,1,GasAnalyzerSnapshot.Status.READY,
                null,null,false,GasAnalyzerPipeSamples.Source.DEVICE,nodes);
        workspace.sync(state,true,true,320,240);
        var layout=GasAnalyzerLayout.calculate(320,240,true);
        assertEquals(20,layout.gasList().y());
        assertEquals(8,GasAnalyzerLayout.visibleGasRows(layout));
        assertEquals(target,workspace.highlight(null));
        assertEquals(nodes.size()* (2+gasList().size()),GasAnalyzerRows.project(state).size());
        assertTrue(workspace.isOverGasList(workspace.x()+layout.gasList().x()+2,
                workspace.y()+22,320,240),"node list begins at its 20px body origin");
        assertTrue(workspace.isOverGasList(workspace.x()+layout.gasList().x()+2,
                workspace.y()+45,320,240),"lower named-node body rows remain in the list hit region");
        var mx=workspace.x()+layout.gasList().x()+2;var my=workspace.y()+layout.gasList().y()+2;
        assertTrue(workspace.scroll(mx,my,-1,320,240));
        assertEquals(1,workspace.scroll());
        var air=snapshot(2,1,0,false,target,GasAnalyzerSnapshot.Status.READY,gasList());
        workspace.sync(air,true,true,320,240);
        assertFalse(workspace.isOverGasList(workspace.x()+6,workspace.y()+25,320,240),
                "air pressure/temperature metrics above the 40px list origin do not consume list scroll");
        assertTrue(workspace.isOverGasList(workspace.x()+8,workspace.y()+45,320,240),
                "air gas list retains its existing 40px origin");
    }

    @Test void airProjectionKeepsNormalAndScientificGasQuantitiesInOneSpeciesRow() {
        var target=new BlockPos(2,65,3);
        var ordinary=snapshot(1,1,0,false,target,GasAnalyzerSnapshot.Status.READY,gasList());
        assertEquals(ordinary.target().gases().size(),GasAnalyzerRows.project(ordinary).size());
        assertTrue(GasAnalyzerRows.project(ordinary).stream().allMatch(row->row.kind()==GasAnalyzerRows.Kind.GAS));
        var huge=new java.util.ArrayList<GasAnalyzerSample.GasAmount>();
        for(var gas:gasList())huge.add(new GasAnalyzerSample.GasAmount(gas.gas(),1.25e120,100d/GasType.values().length));
        var extreme=new GasAnalyzerSnapshot(UUID.randomUUID(),1,1,0,10,1,UUID.randomUUID(),"source",
                ResourceLocation.withDefaultNamespace("overworld"),BlockPos.ZERO,target,1,GasAnalyzerSnapshot.Status.READY,
                null,new GasAnalyzerSample(1000,101,293,huge),false);
        assertEquals(extreme.target().gases().size(),GasAnalyzerRows.project(extreme).size());
        assertTrue(GasAnalyzerRows.project(extreme).stream().allMatch(row->row.kind()==GasAnalyzerRows.Kind.GAS),
                "scrolling never starts with a quantity-only row");
    }

    private static List<GasAnalyzerSample.GasAmount> gasList(){
        var types=GasType.values();var values=new java.util.ArrayList<GasAnalyzerSample.GasAmount>();
        for(int i=0;i<types.length;i++)values.add(new GasAnalyzerSample.GasAmount(types[i],types.length-i,100d/types.length));
        return List.copyOf(values);
    }
    private static GasAnalyzerSnapshot snapshot(long generation,long sequence,long control,boolean closed,BlockPos target,
                                                GasAnalyzerSnapshot.Status status,List<GasAnalyzerSample.GasAmount> gases){
        GasAnalyzerSample sample=gases==null?null:new GasAnalyzerSample(1000,101.325,293.15,gases);
        return new GasAnalyzerSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000021"),generation,sequence,control,10,3,
                UUID.fromString("00000000-0000-0000-0000-000000000022"),"source-token",ResourceLocation.withDefaultNamespace("overworld"),
                new BlockPos(1,65,1),target,1,status,sample,sample,closed);
    }
    private static GasAnalyzerSnapshot snapshotWithSamples(long generation,List<GasAnalyzerSample.GasAmount> local,
                                                            List<GasAnalyzerSample.GasAmount> target) {
        var localSample=new GasAnalyzerSample(500,50,280,local);
        var targetSample=new GasAnalyzerSample(100,10,300,target);
        return new GasAnalyzerSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000031"),generation,1,0,10,3,
                UUID.fromString("00000000-0000-0000-0000-000000000032"),"source-token",ResourceLocation.withDefaultNamespace("overworld"),
                new BlockPos(1,65,1),new BlockPos(4,65,6),1,GasAnalyzerSnapshot.Status.READY,localSample,targetSample,false);
    }
}
