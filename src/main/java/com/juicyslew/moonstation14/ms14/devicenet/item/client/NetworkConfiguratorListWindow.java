package com.juicyslew.moonstation14.ms14.devicenet.item.client;

import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListRequest;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListSnapshot;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Retained LIST/CONFIGURE pane. Membership and target state always come from server snapshots. */
public final class NetworkConfiguratorListWindow {
    public static final int WIDTH=340, HEIGHT=286;
    public record Command(NetworkConfiguratorListRequest.Operation operation,List<String> members,String member) {
        public Command { members=List.copyOf(members); }
    }
    private NetworkConfiguratorListSnapshot snapshot;
    private Consumer<Command> sender=ignored->{ };
    private WindowGeometryController geometry;
    private int screenWidth,screenHeight;
    private double scale=1;
    private int selected=-1;
    private int scroll;
    private boolean visible;
    public void sender(Consumer<Command> callback){sender=Objects.requireNonNull(callback);}
    public boolean accept(NetworkConfiguratorListSnapshot next,int sw,int sh,Consumer<Command> callback){
        Objects.requireNonNull(next);
        if(snapshot!=null&&snapshot.session().equals(next.session())) {
            if(next.handRevision()<snapshot.handRevision())return false;
            if(next.handRevision()==snapshot.handRevision()&&next.requestId()<=snapshot.requestId())return false;
        }
        if(snapshot==null||!snapshot.session().equals(next.session()))selected=-1;
        sender(callback);
        snapshot=next;visible=next.open();
        resize(sw,sh);
        return true;
    }
    public NetworkConfiguratorListSnapshot snapshot(){return snapshot;} public boolean visible(){return visible;}
    public int x(){return geometry==null?0:geometry.x();} public int y(){return geometry==null?0:geometry.y();} public int selected(){return selected;}
    public int width(){return (int)Math.floor(WIDTH*scale);} public int height(){return (int)Math.floor(HEIGHT*scale);} public double displayScale(){return scale;}
    public void resize(int sw,int sh){sw=Math.max(1,sw);sh=Math.max(1,sh);var fit=ConfiguratorPaneLayout.fit(sw,sh,WIDTH,HEIGHT);if(geometry!=null&&(sw!=screenWidth||sh!=screenHeight)){geometry.setScreenSize(sw,sh);geometry.setBounds(geometry.x(),geometry.y(),fit.width,fit.height);}scale=fit.scale;screenWidth=sw;screenHeight=sh;if(geometry==null)geometry=new WindowGeometryController(sw,sh,fit.x,fit.y,fit.width,fit.height,1,1,false);}
    public boolean undersized(){return false;}
    public void setBounds(int nx,int ny,int sw,int sh){resize(sw,sh);geometry.setBounds(nx,ny,width(),height());}
    public boolean beginDrag(double px,double py,int button){return visible&&geometry!=null&&geometry.beginPointer(px,py,button,Math.max(1,(int)Math.round(34*scale)),0);}
    public boolean drag(double px,double py){return geometry!=null&&geometry.dragPointer(px,py);}
    public void endDrag(){if(geometry!=null)geometry.releasePointer(0);}
    public void close(){reset();} public void reset(){visible=false;snapshot=null;selected=-1;scroll=0;sender=ignored->{ };}
    public boolean click(double px,double py){
        if(!visible||snapshot==null||px<x()||px>=x()+width()||py<y()||py>=y()+height())return false;
        int lx=(int)((px-x())/scale),ly=(int)((py-y())/scale);
         boolean configuring=!snapshot.targetAddress().isEmpty();
          if(!configuring){if(ConfiguratorListLayout.REMOVE.contains(lx,ly)){if(selected>=0&&selected<snapshot.staged().size())send(NetworkConfiguratorListRequest.Operation.REMOVE,List.of(),snapshot.staged().get(selected).identity().toString());return true;}
              if(ConfiguratorListLayout.CLEAR.contains(lx,ly))send(NetworkConfiguratorListRequest.Operation.CLEAR,List.of(),null);
             else if(ConfiguratorListLayout.COPY.contains(lx,ly))send(NetworkConfiguratorListRequest.Operation.COPY,List.of(),null);
             else if(ConfiguratorListLayout.SHOW.contains(lx,ly))send(NetworkConfiguratorListRequest.Operation.SHOW,List.of(),null);
              else if(ConfiguratorListLayout.DONE.contains(lx,ly)){send(NetworkConfiguratorListRequest.Operation.DONE,List.of(),null);reset();}if(ly>=ConfiguratorListLayout.ROW_TOP-2&&ly<ConfiguratorListLayout.LIST_BOTTOM){int row=(ly-(ConfiguratorListLayout.ROW_TOP-2))/20+scroll;if(row<snapshot.staged().size()){selected=row;if(lx>=ConfiguratorListLayout.ROW_REMOVE_LEFT)send(NetworkConfiguratorListRequest.Operation.REMOVE,List.of(),snapshot.staged().get(row).identity().toString());}}return true;}
         if(ConfiguratorListLayout.SET.contains(lx,ly))send(NetworkConfiguratorListRequest.Operation.SET,snapshot.staged().stream().map(v->v.address()).toList(),null);
         else if(ConfiguratorListLayout.ADD.contains(lx,ly))send(NetworkConfiguratorListRequest.Operation.ADD,snapshot.staged().stream().map(v->v.address()).toList(),null);
         else if(ConfiguratorListLayout.CONFIG_CLEAR.contains(lx,ly))send(NetworkConfiguratorListRequest.Operation.CLEAR,List.of(),null);
         if(configuring&&(ly>=ConfiguratorListLayout.ROW_TOP-2&&ly<ConfiguratorListLayout.CONFIGURE_BOTTOM)) {int row=(ly-(ConfiguratorListLayout.ROW_TOP-2))/20+scroll;if(row<snapshot.members().size()){selected=row;if(lx>=ConfiguratorListLayout.ROW_REMOVE_LEFT)send(NetworkConfiguratorListRequest.Operation.REMOVE,List.of(),snapshot.members().get(row));}return true;}
         return true;
    }
    public void scroll(double delta){if(snapshot==null)return;int count=snapshot.targetAddress().isEmpty()?snapshot.staged().size():snapshot.members().size();scroll=Math.max(0,Math.min(Math.max(0,count-ConfiguratorListLayout.MAX_ROWS),scroll+(delta<0?1:-1)));}
    public int scroll(){return scroll;}
    private void send(NetworkConfiguratorListRequest.Operation op,List<String> members,String member){sender.accept(new Command(op,members,member));}
}

