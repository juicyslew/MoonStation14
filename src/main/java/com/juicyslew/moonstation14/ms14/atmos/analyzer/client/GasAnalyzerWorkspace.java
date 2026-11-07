package com.juicyslew.moonstation14.ms14.atmos.analyzer.client;

import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerCellSelection;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerClientState;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.ModGasAnalyzers;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerProfile;
import com.juicyslew.moonstation14.ms14.hands.client.PopupSiblingOrder;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Persistent nonmodal window state; server snapshots remain the only gas-data source. */
public final class GasAnalyzerWorkspace {
    public static final GasAnalyzerWorkspace INSTANCE=new GasAnalyzerWorkspace();
    private GasAnalyzerSnapshot snapshot;
    private long dismissedGeneration=-1,pendingControl;
    private int x,y,distance=1,scroll;
    private long z;
    private boolean visible,dragging;
    private int grabX,grabY;
    private GasAnalyzerWorkspace() { }
    public boolean visible(){return visible&&snapshot!=null&&!snapshot.closed();}
    public GasAnalyzerSnapshot snapshot(){return snapshot;}
    public int distance(){return distance;}
    public int scroll(){return scroll;}
    long pendingControlSequence(){return pendingControl;}
    public int x(){return x;} public int y(){return y;} public long z(){return z;}
    private boolean namedNodes(){return snapshot!=null&&snapshot.source()!=null;}
    private GasAnalyzerLayout.Layout layout(int sw,int sh){return GasAnalyzerLayout.calculate(sw,sh,namedNodes());}
    public int width(int sw,int sh){return layout(sw,sh).width();}
    public int height(int sw,int sh){return layout(sw,sh).height();}
    public void resize(int sw,int sh){var l=layout(sw,sh);x=Math.max(0,Math.min(x,Math.max(0,sw-l.width())));y=Math.max(0,Math.min(y,Math.max(0,sh-l.height())));}

    /** Called from the ordinary body render/update route with authoritative current source proof. */
    public void sync(GasAnalyzerSnapshot next,boolean bodyCurrent,boolean sourceHeld,int sw,int sh) {
        if(next==null)return;
        if(next.generation()<dismissedGeneration)return;
        if(!bodyCurrent)return;
        if(snapshot!=null&&next.generation()==snapshot.generation()&&!next.session().equals(snapshot.session()))return;
        if(next.closed()) {
            if(snapshot==null||next.generation()>=snapshot.generation()) {
                boolean was=visible; snapshot=next; visible=false; dragging=false;
                dismissedGeneration=Math.max(dismissedGeneration,next.generation());
                if(was) com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.analyzerWindowClosed();
            }
            return;
        }
        if(!sourceHeld) {
            if(snapshot!=null&&snapshot.session().equals(next.session())) close();
            return;
        }
        if(next.generation()==dismissedGeneration)return;
        if(snapshot==null||next.generation()>snapshot.generation()) {
            snapshot=next; visible=true; distance=next.requestedDistance(); pendingControl=0; scroll=0;
            var layout=layout(sw,sh); x=Math.max(0,(sw-layout.width())/2);y=Math.max(0,(sh-layout.height())/2);
         z=PopupSiblingOrder.front();
        } else if(next.sequence()>snapshot.sequence()) {
            if(next.source()!=snapshot.source()||!java.util.Objects.equals(next.targetCell(),snapshot.targetCell())
                    ||!sameBinding(snapshot,next))scroll=0;
            snapshot=next;
            if(pendingControl>0&&next.controlSequence()>=pendingControl)pendingControl=0;
        }
    }
    private static boolean sameBinding(GasAnalyzerSnapshot a,GasAnalyzerSnapshot b){
        if(a.source()==null)return true;
        if(a.nodeEntries().size()!=b.nodeEntries().size())return false;
        for(int i=0;i<a.nodeEntries().size();i++){
            var x=a.nodeEntries().get(i).stamp();var y=b.nodeEntries().get(i).stamp();
            if(!x.node().equals(y.node())||!x.incarnation().equals(y.incarnation())
                    ||Double.compare(x.volumeCubicMeters(),y.volumeCubicMeters())!=0)return false;
        }
        return true;
    }
    public void clear() { snapshot=null;visible=false;dragging=false;pendingControl=0;dismissedGeneration=-1;scroll=0; }
    public void dismissFromAuthority() { if(visible())close(); else {visible=false;dragging=false;} }
    public void close() {
        if(!visible())return;
        GasAnalyzerClientState.sendControl(distance,true);
        dismissedGeneration=Math.max(dismissedGeneration,snapshot.generation());
        visible=false;dragging=false;
        com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.analyzerWindowClosed();
    }
    public void adjustDistance(int delta,int maxRange) {
        if(!visible())return;
        var request=GasAnalyzerInputPolicy.distanceRequest(distance,maxRange,delta,snapshot!=null&&snapshot.source()!=null);
        if(!request.shouldSend())return;
        distance=request.distance(); scroll=0;
        pendingControl=GasAnalyzerClientState.sendControl(distance,false);
    }
    public void setDistance(int value,int maxRange) { adjustDistance(Integer.compare(value,distance),maxRange); }
    public GasAnalyzerProfile profile() {
        var mc=net.minecraft.client.Minecraft.getInstance();
        try{return mc.level==null?null:ModGasAnalyzers.resolve(mc.level);}catch(RuntimeException ignored){return null;}
    }
    public int maxRange(){var p=profile();return p==null?(snapshot==null?1:snapshot.requestedDistance()):p.maximumRange();}
    public GasAnalyzerCellSelection.Result predicted(Mob body) {
        if(!visible()||body==null||body.level()!=net.minecraft.client.Minecraft.getInstance().level)return new GasAnalyzerCellSelection.Result(GasAnalyzerCellSelection.Status.UNLOADED,null);
        return GasAnalyzerCellSelection.select(body.level(),body.getEyePosition(),body.getLookAngle(),distance);
    }
    public boolean waitingForCurrentTarget(Mob body) {
        if(snapshot==null)return true;
        return !responseMatchesTarget(snapshot,distance,pendingControl,predicted(body));
    }
    public static boolean responseMatchesTarget(GasAnalyzerSnapshot state,int desiredDistance,long expectedControl,
                                                GasAnalyzerCellSelection.Result predicted) {
        return com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerSampleAcceptancePolicy
                .responseMatchesTarget(state,desiredDistance,expectedControl,predicted);
    }
    public BlockPos highlight(Mob body) {
        if(snapshot!=null&&snapshot.source()!=null&&pendingControl==0)return snapshot.targetCell();
        var result=predicted(body);
        return result.status()==GasAnalyzerCellSelection.Status.UNLOADED?null:result.cell();
    }
    public boolean contains(double mx,double my,int sw,int sh){return visible()&&mx>=x&&mx<x+width(sw,sh)&&my>=y&&my<y+height(sw,sh);}
    /** Cursor hit test shared with wheel routing; uses the active air or named-node layout. */
    public boolean isOverGasList(double mx,double my,int sw,int sh){
        return contains(mx,my,sw,sh)&&layout(sw,sh).gasList().contains(mx-x,my-y);
    }
    public boolean topmostAt(double mx,double my,int sw,int sh){return contains(mx,my,sw,sh);}
    public boolean click(double mx,double my,int button,int sw,int sh) {
        if(!contains(mx,my,sw,sh))return false;
        z=PopupSiblingOrder.front();
        var l=layout(sw,sh);double lx=mx-x,ly=my-y;
        var close=WindowChromeButtons.closeBounds(x+l.close().x(),y+l.close().y(),l.close().width(),l.close().height());
        if(button==0&&WindowChromeButtons.contains(close,mx,my)){close();return true;}
        if(button==0&&ly<GasAnalyzerLayout.HEADER){dragging=true;grabX=(int)mx-x;grabY=(int)my-y;return true;}
        return true;
    }
    public boolean drag(double mx,double my,int sw,int sh) {
        if(!dragging)return false;var l=layout(sw,sh);x=Math.max(0,Math.min((int)mx-grabX,Math.max(0,sw-l.width())));y=Math.max(0,Math.min((int)my-grabY,Math.max(0,sh-l.height())));return true;
    }
    public void endDrag(){dragging=false;}
    public boolean scroll(double mx,double my,double amount,int sw,int sh) {
        if(!contains(mx,my,sw,sh)||snapshot==null)return false;
        var l=layout(sw,sh);var box=l.gasList();
        if(!box.contains(mx-x,my-y))return false;
        int count=GasAnalyzerRows.project(snapshot).size();
        scroll=Math.max(0,Math.min(Math.max(0,count-GasAnalyzerLayout.visibleGasRows(l)),scroll-(int)Math.signum(amount)));return true;
    }
    public GasAnalyzerPaint.View view(int sw,int sh,int mx,int my,Mob body) {
        return new GasAnalyzerPaint.View(snapshot,x,y,sw,sh,mx-x,my-y,distance,maxRange(),scroll,
                waitingForCurrentTarget(body));
    }
}
