package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.*;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;

/** Client presentation only: authoritative snapshots are the sole source of chemistry values. */
public final class ChemMasterWorkspace {
    public static final ChemMasterWorkspace INSTANCE=new ChemMasterWorkspace();
    public static final int MAX_WINDOWS=8;
    public static final class Window {
        private ChemMasterWindowState state; private int x,y,inputScroll,bufferScroll,grabX,grabY;
        private long z,lastRequest=-1; private boolean dragging; private ChemMasterOverlay overlay;
        private Window(ChemMasterWindowState s,int x,int y,long z){state=s;this.x=x;this.y=y;this.z=z;}
        public ChemMasterWindowState state(){return state;} public int x(){return x;} public int y(){return y;} public long z(){return z;}
        public int inputScroll(){return inputScroll;} public int bufferScroll(){return bufferScroll;}
        public boolean pending(){return lastRequest>state.acknowledgedRequestId();}
        public int inputReagentCount(){return state.inputReagents().size();} public int bufferReagentCount(){return state.bufferReagents().size();}
        public void scrollInput(int n,int max){inputScroll=Math.max(0,Math.min(max,inputScroll+n));}
        public void scrollBuffer(int n,int max){bufferScroll=Math.max(0,Math.min(max,bufferScroll+n));}
        public void raise(){z=com.juicyslew.moonstation14.ms14.hands.client.PopupSiblingOrder.front();}
    }
    private final Map<UUID,Window> windows=new LinkedHashMap<>();
    private final Set<UUID> retiredSessions=new HashSet<>();
    private final Map<UUID,Long> dismissedFrames=new HashMap<>();
    private Consumer<ChemMasterControlRequest> sender=ignored->{ };
    private long requestSequence;
    public void sender(Consumer<ChemMasterControlRequest> callback){sender=Objects.requireNonNull(callback);}
    public List<Window> windows(){return List.copyOf(windows.values());}
    /**
     * Display ordering only. Pinned SS14 ChemMasterWindow.xaml.cs BuildContainerUI sorts only
     * BufferInfo; InputInfo remains in source order. Never mutate the authoritative snapshot.
     */
    public List<ChemMasterWindowState.ReagentRow> presentationRows(Window w,boolean input){
        var rows=new ArrayList<>(input?w.state.inputReagents():w.state.bufferReagents());
        if(input)return List.copyOf(rows);
        switch(w.state.sort()){
            case RECENT_FIRST -> Collections.reverse(rows);
            case QUANTITY -> rows.sort(Comparator.comparingLong(ChemMasterWindowState.ReagentRow::amountCents));
            case ALPHABETICAL -> rows.sort(Comparator.comparing(r->localizedName(r.reagent().location()),String.CASE_INSENSITIVE_ORDER));
            case OLDEST_FIRST -> { }
        }
        return List.copyOf(rows);
    }
    static String localizedName(net.minecraft.resources.ResourceLocation id){return ChemMasterOverlay.localizedReagentName(id).getString();}
    public void resize(int width,int height){for(Window w:windows.values())clamp(w,width,height);}
    public Window get(UUID machine){return windows.get(machine);}
    public boolean owns(Window w){return w!=null&&windows.get(w.state.machineId())==w;}
    public void accept(ChemMasterWindowState s,int sw,int sh){
        if(retiredSessions.contains(s.sessionId()))return;
        Window old=windows.get(s.machineId());
        if(old!=null&&!old.state.sessionId().equals(s.sessionId())){
            // A different live session may replace an old one only with a genuine open update.
            if(!s.open()||dismissedFrames.containsKey(s.sessionId()))return;
            retire(old);old=null;
        }
        if(old!=null&&(!old.state.bodyId().equals(s.bodyId())||old.state.bodyEpoch()!=s.bodyEpoch()))return;
        Long dismissed=dismissedFrames.get(s.sessionId());if(dismissed!=null&&s.frame()<=dismissed)return;
        if(old!=null&&s.frame()<=old.state.frame())return;
        if(!s.open()){if(old!=null)retire(old);else retiredSessions.add(s.sessionId());dismissedFrames.put(s.sessionId(),s.frame());return;}
        if(old==null){if(windows.size()>=MAX_WINDOWS)return;int ww=Math.min(ChemMasterLayout.WIDTH/2,Math.max(1,sw));int hh=Math.min(ChemMasterLayout.HEIGHT/2,Math.max(1,sh));
            old=new Window(s,Math.max(0,(sw-ww)/2),Math.max(0,(sh-hh)/2),com.juicyslew.moonstation14.ms14.hands.client.PopupSiblingOrder.front());windows.put(s.machineId(),old);
            old.overlay=new ChemMasterOverlay(this,old);PointerOverlayRegistry.register(old.overlay);
        }else old.state=s;
        clamp(old,sw,sh);
    }
    private void retire(Window w){windows.remove(w.state.machineId());if(w.overlay!=null)PointerOverlayRegistry.unregister(w.overlay);retiredSessions.add(w.state.sessionId());dismissedFrames.put(w.state.sessionId(),w.state.frame());
        if(windows.isEmpty())com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.analyzerWindowClosed();}
    public void close(Window w){if(!owns(w))return;send(w,ChemMasterControlRequest.Operation.CLOSE,null,0,w.state.mode());retire(w);}
    public void closeLocal(Window w){if(owns(w))retire(w);}
    public void beginDrag(Window w,int px,int py){w.raise();w.dragging=true;w.grabX=px-w.x;w.grabY=py-w.y;}
    public void drag(int px,int py,int sw,int sh){for(Window w:windows.values())if(w.dragging){w.x=px-w.grabX;w.y=py-w.grabY;clamp(w,sw,sh);}}
    public void endDrag(){windows.values().forEach(w->w.dragging=false);}
    public void send(Window w,ChemMasterControlRequest.Operation op,ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent,long amount){send(w,op,reagent,amount,w.state.mode());}
    public void mode(Window w,ChemMasterControlRequest.Mode mode){send(w,ChemMasterControlRequest.Operation.SET_MODE,null,0,mode);}
    public void nextSort(Window w){if(!owns(w))return;var s=w.state;var sorts=ChemMasterControlRequest.Sort.values();var next=sorts[(s.sort().ordinal()+1)%sorts.length];
        long id=++requestSequence;sender.accept(new ChemMasterControlRequest(s.sessionId(),s.machineId(),s.dimension(),new BlockPos(s.x(),s.y(),s.z()),id,s.stateRevision(),s.slotRevision(),ChemMasterControlRequest.Operation.SET_SORT,s.mode(),next,Optional.empty(),0));w.lastRequest=id;}
    private void send(Window w,ChemMasterControlRequest.Operation op,ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent,long amount,ChemMasterControlRequest.Mode mode){
        if(!owns(w))return;var s=w.state;long id=++requestSequence;
        var request=new ChemMasterControlRequest(s.sessionId(),s.machineId(),s.dimension(),new BlockPos(s.x(),s.y(),s.z()),id,s.stateRevision(),s.slotRevision(),op,mode,s.sort(),Optional.ofNullable(reagent),amount);
        w.lastRequest=id;sender.accept(request);
    }
    public void contextChanged(UUID body,long epoch,ResourceLocation dimension){for(Window w:List.copyOf(windows.values()))if(!w.state.bodyId().equals(body)||w.state.bodyEpoch()!=epoch||!w.state.dimension().equals(dimension))retire(w);}
    public void clear(){for(Window w:windows.values())if(w.overlay!=null)PointerOverlayRegistry.unregister(w.overlay);windows.clear();retiredSessions.clear();dismissedFrames.clear();requestSequence=0;}
    private static void clamp(Window w,int sw,int sh){int ww=Math.min(ChemMasterLayout.WIDTH/2,Math.max(1,sw)),hh=Math.min(ChemMasterLayout.HEIGHT/2,Math.max(1,sh));w.x=Math.max(0,Math.min(w.x,sw-ww));w.y=Math.max(0,Math.min(w.y,sh-hh));}
}
