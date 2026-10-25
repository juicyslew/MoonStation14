package com.juicyslew.moonstation14.ms14.devicenet.item.client;

import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorSnapshot;
import java.util.function.Consumer;

/** Client-owned entry point used by the payload receiver and retained workspace hook. */
public final class NetworkConfiguratorLinkWindowState {
    public static final NetworkConfiguratorLinkWindowState INSTANCE=new NetworkConfiguratorLinkWindowState();
    private final NetworkConfiguratorLinkWindow window=new NetworkConfiguratorLinkWindow();
    private NetworkConfiguratorLinkWindowState() { }
    public NetworkConfiguratorLinkWindow window(){return window;}
    public void clear(){window.reset();}
    public void accept(NetworkConfiguratorSnapshot snapshot,int width,int height,Consumer<NetworkConfiguratorLinkWindow.Command> sender){
        window.accept(snapshot,width,height,sender);
    }
    public void draw(net.minecraft.client.gui.GuiGraphics graphics,int width,int height,int mouseX,int mouseY){NetworkConfiguratorLinkRenderer.draw(graphics,window,width,height,mouseX,mouseY);}
    public boolean click(double x,double y,int button){return button==0&&window.click(x,y);}
    public boolean beginDrag(double x,double y,int button){return window.beginDrag(x,y,button);}
    public boolean drag(double x,double y){return window.drag(x,y);}
    public void endDrag(){window.endDrag();}
}

