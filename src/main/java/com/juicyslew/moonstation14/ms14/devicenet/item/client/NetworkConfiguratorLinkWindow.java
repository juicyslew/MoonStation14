package com.juicyslew.moonstation14.ms14.devicenet.item.client;

import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorRequest;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorSnapshot;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import java.util.Objects;
import java.util.function.Consumer;

/** Retained client presentation for the server-authoritative Link configurator. */
public final class NetworkConfiguratorLinkWindow {
    public static final int WIDTH=300, HEIGHT=250;
    private NetworkConfiguratorSnapshot snapshot;
    public record Command(NetworkConfiguratorRequest.Operation operation,String sourcePort,String sinkPort) { }
    private Consumer<Command> sender=ignored->{ };
    private WindowGeometryController geometry;
    private int screenWidth,screenHeight;
    private double scale=1;
    private String selectedSource,selectedSink;
    private int portScroll;
    private boolean visible;

    public void sender(Consumer<Command> callback){sender=Objects.requireNonNull(callback);}
    /** Accept only a newer snapshot for the current session; caller supplies a request adapter. */
    public void accept(NetworkConfiguratorSnapshot next,int sw,int sh,Consumer<Command> callback){
        Objects.requireNonNull(next);
        if(snapshot!=null && snapshot.session().equals(next.session()) && next.requestId()<=snapshot.requestId())return;
        sender(callback);
        if(snapshot==null||!snapshot.session().equals(next.session())){selectedSource=null;selectedSink=null;}
        snapshot=next; visible=next.open();
         resize(sw,sh);
    }
    public NetworkConfiguratorSnapshot snapshot(){return snapshot;} public boolean visible(){return visible;}
    public int x(){return geometry==null?0:geometry.x();} public int y(){return geometry==null?0:geometry.y();}
    public int width(){return geometry==null?WIDTH:(int)Math.floor(WIDTH*scale);} public int height(){return geometry==null?HEIGHT:(int)Math.floor(HEIGHT*scale);}
    public double displayScale(){return scale;}
    public void resize(int sw,int sh){sw=Math.max(1,sw);sh=Math.max(1,sh);var fit=ConfiguratorPaneLayout.fit(sw,sh,WIDTH,HEIGHT);boolean changed=sw!=screenWidth||sh!=screenHeight;if(geometry==null)geometry=new WindowGeometryController(sw,sh,fit.x,fit.y,fit.width,fit.height,1,1,false);else if(changed){geometry.setScreenSize(sw,sh);geometry.setBounds(geometry.x(),geometry.y(),fit.width,fit.height);}scale=fit.scale;screenWidth=sw;screenHeight=sh;}
    public void setBounds(int nx,int ny,int sw,int sh){resize(sw,sh);geometry.setBounds(nx,ny,width(),height());}
    public boolean undersized(){return false;}
    public boolean beginDrag(double px,double py,int button){return visible&&geometry!=null&&geometry.beginPointer(px,py,button,Math.max(1,(int)Math.round(34*scale)),0);}
    public boolean drag(double px,double py){return geometry!=null&&geometry.dragPointer(px,py);}
    public void endDrag(){if(geometry!=null)geometry.releasePointer(0);}
    public void close(){reset();}
      public void reset(){visible=false;snapshot=null;selectedSource=null;selectedSink=null;portScroll=0;sender=ignored->{ };}
    public boolean click(double px,double py){
         if(!visible||snapshot==null||px<x()||px>=x()+width()||py<y()||py>=y()+height())return false;
         double lx=(px-x())/scale+1e-7,ly=(py-y())/scale+1e-7;
          if(ConfiguratorPaneLayout.LINK_CLEAR.contains(lx,ly)){clearPendingSelection();send(NetworkConfiguratorRequest.Operation.CLEAR,null,null);return true;}
          if(ConfiguratorPaneLayout.LINK_DEFAULTS.contains(lx,ly)){clearPendingSelection();send(NetworkConfiguratorRequest.Operation.LINK_DEFAULTS,null,null);return true;}
         if(ConfiguratorPaneLayout.LINK_DONE.contains(lx,ly)){send(NetworkConfiguratorRequest.Operation.DONE,null,null);reset();return true;}
         if(ly>=220)return true;
         if(ly>=58&&ly<210){int row=portIndexAtY((int)Math.floor(ly),portScroll);
             if(lx<145&&row<snapshot.sources().size()) {String id=snapshot.sources().get(row).id();selectedSource=Objects.equals(selectedSource,id)?null:selectedSource==null?id:selectedSource;}
             else if(lx>=155&&row<snapshot.sinks().size()) {String id=snapshot.sinks().get(row).id();selectedSink=Objects.equals(selectedSink,id)?null:selectedSink==null?id:selectedSink;}
             if(selectedSource!=null&&selectedSink!=null){send(NetworkConfiguratorRequest.Operation.TOGGLE_PAIR,selectedSource,selectedSink);selectedSource=null;selectedSink=null;}return true;}
        return true;
    }
    private void send(NetworkConfiguratorRequest.Operation op,String source,String sink){sender.accept(new Command(op,source,sink));}
    private void clearPendingSelection(){selectedSource=null;selectedSink=null;}
    public String selectedSource(){return selectedSource;} public String selectedSink(){return selectedSink;}
    public int portScroll(){return portScroll;}
    static int portIndexAtY(int logicalY,int scroll){return (logicalY-58)/19+scroll;}
    static int portRowTop(int visibleRow){return 58+visibleRow*19;}
    public void scrollPorts(double delta){if(snapshot==null)return;int count=Math.max(snapshot.sources().size(),snapshot.sinks().size());portScroll=Math.max(0,Math.min(Math.max(0,count-8),portScroll+(delta<0?1:-1)));}
}

