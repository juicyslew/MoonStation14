package com.juicyslew.moonstation14.ms14.medical.client;

import com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerWindowState;
import java.util.*;

/** Client-only persistent presentation state; it grants no scanner or treatment authority. */
public final class HealthAnalyzerWorkspace {
    public static final int WIDTH = HealthAnalyzerLayout.WIDTH, HEIGHT = HealthAnalyzerLayout.MIN_HEIGHT, MAX_HEIGHT = HealthAnalyzerLayout.MAX_HEIGHT;
    private static long nextLocalZ;
    public static final HealthAnalyzerWorkspace INSTANCE = new HealthAnalyzerWorkspace();
    private final Map<UUID, Window> windows = new LinkedHashMap<>();
    private long nextZ;
    private final Map<UUID, Dismissed> dismissed = new HashMap<>();
    private record Dismissed(long epoch, UUID actor) { }
    private java.util.function.Consumer<com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerCloseRequest> closeSender = ignored -> { };
    public void closeSender(java.util.function.Consumer<com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerCloseRequest> sender) { closeSender=Objects.requireNonNull(sender); }

    public static final class Window {
        private final UUID scanner;
        private long epoch, frame, z;
        private int scroll;
        private UUID actor;
        private boolean visible = true, dragging;
     private int x, y, grabX, grabY, width=WIDTH, height=HEIGHT;
        private HealthAnalyzerWindowState state;
        private Window(HealthAnalyzerWindowState s, int x, int y, long z) {
            scanner=s.scannerId(); epoch=s.epoch(); actor=s.actorId(); frame=s.frame(); state=s; this.x=x; this.y=y; this.z=z;
        }
        public UUID scanner() { return scanner; } public long epoch() { return epoch; }
        public UUID actor() { return actor; } public HealthAnalyzerWindowState state() { return state; }
        public boolean visible() { return visible; } public int x() { return x; } public int y() { return y; }
        public long z() { return z; }
        public int scroll() { return scroll; }
        public int width() { return width; } public int height() { return height; }
    }
    public Collection<Window> windows() { return List.copyOf(windows.values()); }
    public Window get(UUID id) { return windows.get(id); }
    public void accept(HealthAnalyzerWindowState s, int screenWidth, int screenHeight) {
        Window w=windows.get(s.scannerId());
        Dismissed hidden=dismissed.get(s.scannerId());
        if(hidden!=null) {
            if(s.epoch()<hidden.epoch() || (s.epoch()==hidden.epoch() && !s.actorId().equals(hidden.actor()))) return;
            if(s.epoch()>hidden.epoch()) dismissed.remove(s.scannerId());
            else return;
        }
        if (w != null && !w.actor.equals(s.actorId())) return;
        if (w != null && (s.epoch()<w.epoch || (s.epoch()==w.epoch && s.frame()<=w.frame))) return;
        if (!s.open()) {
            boolean removed=windows.remove(s.scannerId())!=null;
            dismissed.put(s.scannerId(),new Dismissed(s.epoch(),s.actorId()));
            if(removed && this == INSTANCE) com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.analyzerWindowClosed();
            return;
        }
        if (w == null || w.epoch != s.epoch()) {
            w=new Window(s, Math.max(0,(screenWidth-WIDTH)/2),Math.max(0,(screenHeight-HEIGHT)/2),com.juicyslew.moonstation14.ms14.hands.client.PopupSiblingOrder.front());
            windows.put(s.scannerId(),w);
        } else { w.frame=s.frame(); w.state=s; }
         w.height=contentHeight(s,screenWidth,screenHeight);
        clamp(w,screenWidth,screenHeight);
    }
    private static int contentHeight(HealthAnalyzerWindowState state,int screenWidth,int screenHeight) {
        int width = Math.min(WIDTH, screenWidth);
        return HealthAnalyzerLayout.desiredHeight(state.readings().orElse(null), width, screenWidth, screenHeight);
    }
    public void close(UUID id) { windows.remove(id); }
    public void explicitClose(UUID id) { Window w=windows.remove(id); if(w!=null)dismissed.put(id,new Dismissed(w.epoch,w.actor)); }
    public void clear() { windows.clear(); dismissed.clear(); nextZ=0; }
    public void resize(int sw,int sh){for(Window w:windows.values())clamp(w,sw,sh);}
    public void scroll(double x,double y,double amount){Window w=topmost(x,y);if(w==null)return;w.scroll=Math.max(0,w.scroll-(int)Math.signum(amount)*24);}
    public void maxScroll(Window w,int max){w.scroll=Math.max(0,Math.min(max,w.scroll));}
    public void contentWidth(Window w,int requested,int sw){int next=Math.max(1,Math.min(requested,sw));if(next!=w.width){w.width=next;clamp(w,sw,Integer.MAX_VALUE);}}
    public Window topmost(double x,double y) { return windows.values().stream().filter(w->w.visible && x>=w.x&&x<w.x+w.width&&y>=w.y&&y<w.y+w.height).max(Comparator.comparingLong(Window::z)).orElse(null); }
    public boolean click(double x,double y,int button,int sw,int sh) {
        Window w=topmost(x,y); if(w==null)return false;
        w.z=com.juicyslew.moonstation14.ms14.hands.client.PopupSiblingOrder.front();
        var closeBounds = com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.closeBounds(w.x, w.y, w.width * 2, .5f);
        if(button==0 && x>=closeBounds.left() && x<closeBounds.right() && y>=closeBounds.top() && y<closeBounds.bottom()){
            closeSender.accept(new com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerCloseRequest(w.scanner,w.epoch,w.actor));
            explicitClose(w.scanner); return true;
        }
        if(button==0 && y<w.y+HealthAnalyzerLayout.HEADER){w.dragging=true;w.grabX=(int)x-w.x;w.grabY=(int)y-w.y;}
        return true;
    }
    public boolean drag(double x,double y,int sw,int sh) { Window w=windows.values().stream().filter(a->a.dragging).findFirst().orElse(null);if(w==null)return false;w.x=(int)x-w.grabX;w.y=(int)y-w.grabY;clamp(w,sw,sh);return true; }
    public void endDrag() { windows.values().forEach(w->w.dragging=false); }
     private static void clamp(Window w,int sw,int sh){w.width=Math.max(1,Math.min(w.width,sw));w.height=Math.max(1,Math.min(w.height,sh));w.x=Math.max(0,Math.min(w.x,Math.max(0,sw-w.width)));w.y=Math.max(0,Math.min(w.y,Math.max(0,sh-w.height)));}
}
