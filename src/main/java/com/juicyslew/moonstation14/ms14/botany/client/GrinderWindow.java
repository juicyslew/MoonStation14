package com.juicyslew.moonstation14.ms14.botany.client;

import com.juicyslew.moonstation14.ms14.botany.ui.*;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.Consumer;

/** Non-modal, snapshot-driven grinder window hosted by the shared pointer workspace. */
public final class GrinderWindow implements PointerOverlayWindow {
    static final int WINDOW_WIDTH = 210;
    static final int WINDOW_HEIGHT = 148;
    public static final GrinderWindow INSTANCE = new GrinderWindow();
    private GrinderSnapshot state;
    private int x, y, grabX, grabY;
    private boolean dragging;
    private GrinderWindow() { }
    public void accept(GrinderSnapshot next) {
        if (state != null && state.sessionId().equals(next.sessionId())
                && (!state.body().equals(next.body()) || state.bodyEpoch() != next.bodyEpoch())) closeLocal();
        if (!next.open()) { if(state==null||state.sessionId().equals(next.sessionId())) closeLocal(); return; }
        if (state != null && !state.sessionId().equals(next.sessionId())) closeLocal();
        boolean opening=state==null; state=next;
         if(opening){var mc=Minecraft.getInstance();x=Math.max(0,(mc.getWindow().getGuiScaledWidth()-WINDOW_WIDTH)/2);y=Math.max(0,(mc.getWindow().getGuiScaledHeight()-WINDOW_HEIGHT)/2);PointerOverlayRegistry.register(this);BodyHandClient.openSharedPointerWorkspace();}
        clamp();
    }
    private void closeLocal(){if(state!=null)PointerOverlayRegistry.unregister(this);state=null;dragging=false;}
    private void close(){if(state!=null)send(GrinderControl.Operation.CLOSE,-1);closeLocal();}
    private void send(GrinderControl.Operation operation,int slot){if(state!=null)PacketDistributor.sendToServer(new GrinderControlPayload(new GrinderControl(state.sessionId(),state.placement(),state.stateRevision(),operation,slot)));}
    private void clamp(){var m=Minecraft.getInstance();x=Math.max(0,Math.min(x,m.getWindow().getGuiScaledWidth()-WINDOW_WIDTH));y=Math.max(0,Math.min(y,m.getWindow().getGuiScaledHeight()-WINDOW_HEIGHT));}
    @Override public boolean visible(){return state!=null;}
    @Override public boolean pointerAvailable(){return !Minecraft.getInstance().mouseHandler.isMouseGrabbed();}
    @Override public boolean ownsPointerCapture(){return dragging;}
    @Override public boolean contains(double px,double py){return visible()&&containsBounds(px,py,x,y);}
    static boolean containsBounds(double px,double py,int x,int y){return px>=x&&px<x+WINDOW_WIDTH&&py>=y&&py<y+WINDOW_HEIGHT;}
    @Override public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int width,int height){
        if(state==null)return; clamp(); var p=new GuiGraphicsPaint(g,Minecraft.getInstance().font); MachineWindowRenderer.window(p,x,y,WINDOW_WIDTH,WINDOW_HEIGHT,Component.literal("Reagent Grinder"));
        var close=WindowChromeButtons.closeBounds(x+190,y+5,14,14); WindowChromeButtons.paintClose(new GuiGraphicsPaint(g,Minecraft.getInstance().font),close,mx,my);
        int bx=x+8,by=y+32; var beaker=state.chamber().stream().filter(s->s.index()==-1).findFirst().orElse(null);
        p.text(Component.literal("Beaker: "+(beaker==null||beaker.item().isEmpty()?"Empty":beaker.item().getHoverName().getString())),bx,by,MachineWindowRenderer.TEXT,true);
        boolean busy=state.active(); String status=state.rejection()!=null?state.rejection():busy?"Grinding "+Math.max(0,state.endTick()-state.startTick())+" ticks (70 tick cycle)":state.powered()?"Ready":"Unpowered";
        p.text(Component.literal(status),bx,by+12,MachineWindowRenderer.MUTED_TEXT,true);
        for(int i=0;i<6;i++){final int index=i;int sx=bx+i*31;var slot=state.chamber().stream().filter(s->s.index()==index).findFirst().orElse(null);MachineWindowRenderer.insetPanel(p,sx,by+29,28,28);
            if(slot!=null&&!slot.item().isEmpty()){g.renderItem(slot.item(),sx+6,by+34);g.renderItemDecorations(Minecraft.getInstance().font,slot.item(),sx+6,by+34);}
            String name=slot==null||slot.item().isEmpty()?"—":Minecraft.getInstance().font.plainSubstrByWidth(slot.item().getHoverName().getString(),28);p.text(Component.literal(name),sx,by+59,MachineWindowRenderer.TEXT,false);
            button(g,"E",sx,by+73,28,14,mx,my,slot!=null&&!slot.item().isEmpty()&&!busy,()->send(GrinderControl.Operation.EJECT_ONE,index));
        }
        button(g,"GRIND",bx,by+91,55,18,mx,my,state.powered()&&!busy&&beaker!=null&&!beaker.item().isEmpty()&&state.chamber().stream().anyMatch(s->s.index()>=0&&!s.item().isEmpty()),()->send(GrinderControl.Operation.GRIND,-1));
        button(g,"Eject All",bx+60,by+91,58,18,mx,my,!busy,()->send(GrinderControl.Operation.EJECT_ALL,-1));
        button(g,"Eject Beaker",bx+123,by+91,70,18,mx,my,beaker!=null&&!beaker.item().isEmpty()&&!busy,()->send(GrinderControl.Operation.EJECT_BEAKER,-1));
    }
    private static void button(GuiGraphics g,String label,int x,int y,int w,int h,int mx,int my,boolean enabled,Runnable action){MachineActionButtonRenderer.render(new GuiGraphicsPaint(g,Minecraft.getInstance().font),label,x,y,w,h,mx>=x&&mx<x+w&&my>=y&&my<y+h,enabled,false,1f);}
     @Override public boolean mouseClicked(double px,double py,int button){if(button!=0||!contains(px,py))return false;int mx=(int)px,my=(int)py;if(mx>=x+190&&my>=y+5&&my<y+19){close();return true;}PointerOverlayRegistry.bringToFront(this);if(my<y+28){dragging=true;grabX=mx-x;grabY=my-y;return true;}int row=y+32;int slot=(mx-(x+8))/31;if(my>=row+73&&my<row+87&&slot>=0&&slot<6){if(!state.active())send(GrinderControl.Operation.EJECT_ONE,slot);return true;}if(my>=row+91)routeActionClick(mx,my,x,y,state.active(),canGrind(),op->send(op,-1));return true;}
     static boolean routeActionClick(int mx,int my,int x,int y,boolean active,boolean canGrind,Consumer<GrinderControl.Operation> dispatch){
         int row=y+32;
         if(my<row+91||my>=row+109)return false;
         if(mx>=x+8&&mx<x+63){if(canGrind)dispatch.accept(GrinderControl.Operation.GRIND);return true;}
         if(mx>=x+68&&mx<x+126){if(!active)dispatch.accept(GrinderControl.Operation.EJECT_ALL);return true;}
         if(mx>=x+131&&mx<x+201){if(!active)dispatch.accept(GrinderControl.Operation.EJECT_BEAKER);return true;}
         return true;
     }
    private boolean canGrind(){return state.powered()&&!state.active()&&state.chamber().stream().anyMatch(s->s.index()==-1&&!s.item().isEmpty())&&state.chamber().stream().anyMatch(s->s.index()>=0&&!s.item().isEmpty());}
    @Override public boolean mouseDragged(double px,double py){if(!dragging)return false;x=(int)px-grabX;y=(int)py-grabY;clamp();return true;}
    @Override public boolean mouseReleased(int button){if(button==0&&dragging){dragging=false;return true;}return false;}
    @Override public boolean mouseScrolled(double x,double y,double d){return contains(x,y);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==256){close();return true;}return false;}
    @Override public boolean charTyped(char c,int mods){return false;}
    @Override public boolean isSearchFocused(){return false;}
    @Override public void clearTextFocus(){}
    @Override public void resetOwnedContext(){if(state!=null)send(GrinderControl.Operation.CLOSE,-1);closeLocal();}
}
