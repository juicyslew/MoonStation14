package com.juicyslew.moonstation14.ms14.devicenet.item.client;

import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListSnapshot;
import java.util.function.Consumer;
import java.util.List;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListSnapshot.MemberPosition;

/** Storage popup integration entry points; intentionally parallels LinkWindowState. */
public final class NetworkConfiguratorListWindowState {
    public static final NetworkConfiguratorListWindowState INSTANCE=new NetworkConfiguratorListWindowState();
    static { NetworkConfiguratorMembershipOverlay.initialize(); }
    private final NetworkConfiguratorListWindow window=new NetworkConfiguratorListWindow();
    private UUID overlaySession;
    private long overlayEpoch;
    private boolean overlayShown;
    private List<MemberPosition> positions=List.of();
    private NetworkConfiguratorListWindowState() { }
    public NetworkConfiguratorListWindow window(){return window;}
    public void clear(){window.reset();clearOverlay();}
    public void accept(NetworkConfiguratorListSnapshot snapshot,int width,int height,Consumer<NetworkConfiguratorListWindow.Command> sender){
        if(!window.accept(snapshot,width,height,sender))return;
        if(!snapshot.open()||!snapshot.show()) { clearOverlay(); }
        else if(!snapshot.session().equals(overlaySession)||snapshot.epoch()!=overlayEpoch) {
            overlaySession=snapshot.session(); overlayEpoch=snapshot.epoch(); overlayShown=true; positions=snapshot.positions();
        } else { overlayShown=true; positions=snapshot.positions(); }
    }
    public List<MemberPosition> overlayPositions(){return overlayShown&&window.visible()?positions:List.of();}
    public boolean overlayShown(){return overlayShown&&window.visible();}
    public void clearOverlay(){overlaySession=null;overlayEpoch=0;overlayShown=false;positions=List.of();}
    public void draw(net.minecraft.client.gui.GuiGraphics graphics,int width,int height){NetworkConfiguratorListRenderer.draw(graphics,window,width,height);}
    public boolean click(double x,double y,int button){boolean handled=button==0&&window.click(x,y);if(!window.visible())clearOverlay();return handled;}
    public boolean beginDrag(double x,double y,int button){return window.beginDrag(x,y,button);}
    public boolean drag(double x,double y){return window.drag(x,y);}
    public void endDrag(){window.endDrag();}
}
