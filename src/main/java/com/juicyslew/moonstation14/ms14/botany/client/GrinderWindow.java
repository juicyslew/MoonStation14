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
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.Consumer;

/** Non-modal, snapshot-driven grinder window hosted by the shared pointer workspace. */
public final class GrinderWindow implements PointerOverlayWindow {
    static final int WINDOW_WIDTH = 210;
    static final int WINDOW_HEIGHT = 170;
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
         boolean busy=state.active(); String status=state.rejection()!=null?state.rejection():busy?(state.activeOperation()==com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram.Operation.JUICE?"Juicing ":"Grinding ")+Math.max(0,state.endTick()-state.startTick())+" ticks":state.powered()?"Ready":"Unpowered";
        p.text(Component.literal(status),bx,by+12,MachineWindowRenderer.MUTED_TEXT,true);
        for(int i=0;i<6;i++){final int index=i;int sx=bx+i*31;var slot=state.chamber().stream().filter(s->s.index()==index).findFirst().orElse(null);MachineWindowRenderer.insetPanel(p,sx,by+29,28,28);
            if(slot!=null&&!slot.item().isEmpty()){g.renderItem(slot.item(),sx+6,by+34);g.renderItemDecorations(Minecraft.getInstance().font,slot.item(),sx+6,by+34);}
            String name=slot==null||slot.item().isEmpty()?"—":Minecraft.getInstance().font.plainSubstrByWidth(slot.item().getHoverName().getString(),28);p.text(Component.literal(name),sx,by+59,MachineWindowRenderer.TEXT,false);
            button(g,"E",new ScreenRectangle(sx,by+73,28,14),mx,my,slot!=null&&!slot.item().isEmpty()&&!busy);
        }
         button(g,"GRIND",actionBounds(GrinderControl.Operation.GRIND,x,y),mx,my,state.canGrind());
         button(g,"JUICE",actionBounds(GrinderControl.Operation.JUICE,x,y),mx,my,state.canJuice());
         button(g,"Eject All",actionBounds(GrinderControl.Operation.EJECT_ALL,x,y),mx,my,!busy);
         button(g,"Eject Beaker",actionBounds(GrinderControl.Operation.EJECT_BEAKER,x,y),mx,my,beaker!=null&&!beaker.item().isEmpty()&&!busy);
     }
     private static void button(GuiGraphics g,String label,ScreenRectangle bounds,int mx,int my,boolean enabled){MachineActionButtonRenderer.render(new GuiGraphicsPaint(g,Minecraft.getInstance().font),label,bounds.left(),bounds.top(),bounds.width(),bounds.height(),WindowChromeButtons.contains(bounds,mx,my),enabled,false,1f);}
     static ScreenRectangle actionBounds(GrinderControl.Operation operation,int x,int y){
         return switch(operation){
             case GRIND -> new ScreenRectangle(x+8,y+123,55,18);
             case JUICE -> new ScreenRectangle(x+68,y+123,55,18);
             case EJECT_ALL -> new ScreenRectangle(x+8,y+146,90,18);
             case EJECT_BEAKER -> new ScreenRectangle(x+103,y+146,90,18);
             default -> throw new IllegalArgumentException("not a grinder action button: "+operation);
         };
     }
      @Override public boolean mouseClicked(double px,double py,int button){if(button!=0||!contains(px,py))return false;int mx=(int)px,my=(int)py;if(mx>=x+190&&my>=y+5&&my<y+19){close();return true;}PointerOverlayRegistry.bringToFront(this);if(my<y+28){dragging=true;grabX=mx-x;grabY=my-y;return true;}int row=y+32;int slot=(mx-(x+8))/31;if(my>=row+73&&my<row+87&&slot>=0&&slot<6){if(!state.active())send(GrinderControl.Operation.EJECT_ONE,slot);return true;}if(my>=row+91)routeActionClick(mx,my,x,y,state.active(),state.canGrind(),state.canJuice(),op->send(op,-1));return true;}
      static boolean routeActionClick(int mx,int my,int x,int y,boolean active,boolean canGrind,boolean canJuice,Consumer<GrinderControl.Operation> dispatch){
          for(var operation:new GrinderControl.Operation[]{GrinderControl.Operation.GRIND,GrinderControl.Operation.JUICE,
                  GrinderControl.Operation.EJECT_ALL,GrinderControl.Operation.EJECT_BEAKER}){
              if(!WindowChromeButtons.contains(actionBounds(operation,x,y),mx,my))continue;
              if(active)return true;
              if(operation==GrinderControl.Operation.GRIND&&canGrind)dispatch.accept(operation);
              else if(operation==GrinderControl.Operation.JUICE&&canJuice)dispatch.accept(operation);
              else if(operation==GrinderControl.Operation.EJECT_ALL||operation==GrinderControl.Operation.EJECT_BEAKER)dispatch.accept(operation);
              return true;
          }
          return false;
      }
    @Override public boolean mouseDragged(double px,double py){if(!dragging)return false;x=(int)px-grabX;y=(int)py-grabY;clamp();return true;}
    @Override public boolean mouseReleased(int button){if(button==0&&dragging){dragging=false;return true;}return false;}
    @Override public boolean mouseScrolled(double x,double y,double d){return contains(x,y);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==256){close();return true;}return false;}
    @Override public boolean charTyped(char c,int mods){return false;}
    @Override public boolean isSearchFocused(){return false;}
    @Override public void clearTextFocus(){}
    @Override public void resetOwnedContext(){if(state!=null)send(GrinderControl.Operation.CLOSE,-1);closeLocal();}
}
