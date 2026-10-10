package com.juicyslew.moonstation14.ms14.stripping.client;

import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.stripping.*;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Compact remote inventory viewer. The only input it emits is one timed slot intent. */
public final class StripWindow implements PointerOverlayWindow {
    private final StripClient client;
    private WindowGeometryController geometry;
    private boolean visible;
    private boolean dragging;
    private int geometryViewportWidth=-1, geometryViewportHeight=-1, preferredWidth, preferredHeight;
    private boolean gridFits=true;
    StripWindow(StripClient client) { this.client=client; }
    void open() { visible=true; ensureGeometry(Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight()); }
    @Override public boolean visible() { return visible; }
    @Override public boolean contains(double x,double y) { return visible&&geometry!=null&&x>=geometry.x()&&y>=geometry.y()&&x<geometry.x()+geometry.width()&&y<geometry.y()+geometry.height(); }
    @Override public boolean isSearchFocused(){return false;}
    @Override public void clearTextFocus(){}
    @Override public void resetOwnedContext(){visible=false;dragging=false;if(geometry!=null)geometry.cancelPointer();}
    private void close(){visible=false;dragging=false;client.close();com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.pointerOverlayClosed();}
    void remoteClosed(){visible=false;dragging=false;if(geometry!=null)geometry.cancelPointer();com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.pointerOverlayClosed();}
    private void ensureGeometry(int w,int h) {
        var metrics=metrics(w,h);
        int cell=com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelLayout.cellSizeForViewport(w,h);
        var size=metrics==null?new StripPanelLayout.WindowSize(Math.min(Math.max(1,w),120),Math.min(Math.max(1,h),48),false)
                :StripPanelLayout.windowSize(metrics,w,h);
        int width=size.width(),height=size.height();
        gridFits=metrics!=null&&size.gridFits();
        boolean viewportChanged=w!=geometryViewportWidth||h!=geometryViewportHeight;
        boolean sizeChanged=width!=preferredWidth||height!=preferredHeight;
        if(geometry==null) {
            int safeAboveClothing=cell*4+8;
            geometry=new WindowGeometryController(w,h,4,Math.max(0,h-height-safeAboveClothing),width,height,1,1,false);
        } else {
            if(viewportChanged)geometry.setScreenSize(w,h);
            if(sizeChanged)geometry.setBounds(geometry.x(),Math.max(0,geometry.y()-(height-geometry.height())),width,height);
        }
        geometryViewportWidth=w;geometryViewportHeight=h;preferredWidth=width;preferredHeight=height;
    }
    private StripPanelLayout.Layout metrics(int w,int h) {
        var view=client.snapshot(); if(view==null)return null;
        InventoryTemplateData template=null;
        if(view.templateId()!=null) try { template=com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.require(
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.clientCatalog(),net.minecraft.resources.ResourceLocation.tryParse(view.templateId())); }
        catch(RuntimeException ignored){return null;}
        if(template==null&&view.templateId()!=null)return null;
        var hands=view.slots().stream().filter(s->s.kind()==StripSlotRef.Kind.HAND).map(StripViewSnapshot.Slot::id).toList();
        var resolved=template==null?List.<InventoryTemplateData.Slot>of():template.slots();
        return StripPanelLayout.calculate(0,0,w,h,resolved,hands);
    }
    private StripPanelLayout.Layout layout(int w,int h) {
        var raw=metrics(w,h);if(raw==null||geometry==null)return null;
        int originX=geometry.x()+4,baseline=geometry.y()+22-raw.bounds().y();
        return StripPanelLayout.calculate(originX,baseline,w,h,client.templateSlots(client.snapshot()),
                client.snapshot().slots().stream().filter(s->s.kind()==StripSlotRef.Kind.HAND).map(StripViewSnapshot.Slot::id).toList());
    }
    @Override public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int w,int h) {
        if(!visible)return; ensureGeometry(w,h);
        int x=geometry.x(),y=geometry.y(),ww=geometry.width(),hh=geometry.height();
        g.fill(x,y,x+ww,y+hh,0xc910171b); g.fill(x,y,x+ww,y+18,0xff27363c);
        var snapshot=client.snapshot();
        String title=snapshot==null?"Strip view":client.targetName();
        title=Minecraft.getInstance().font.plainSubstrByWidth(title,Math.max(0,ww-31));
        g.drawString(Minecraft.getInstance().font,title,x+4,y+5,0xffeeeeee,false);
        g.drawString(Minecraft.getInstance().font,"×",x+ww-11,y+4,0xffeeeeee,false);
        if(snapshot==null){g.drawString(Minecraft.getInstance().font,client.status(),x+5,y+25,0xffcccccc,false);return;}
        var plan=layout(w,h); if(plan==null){g.drawString(Minecraft.getInstance().font,"Inventory unavailable",x+5,y+25,0xffff8888,false);return;}
        if(!gridFits){g.drawString(Minecraft.getInstance().font,"Inventory does not fit viewport",x+4,y+22,0xffffcc66,false);return;}
        var projected=new HashMap<String,StripViewSnapshot.Slot>(); for(var slot:snapshot.slots())projected.put((slot.kind()==StripSlotRef.Kind.HAND?"H:":"E:")+slot.id(),slot);
        var template=client.template(snapshot);
        for(var pos:plan.slots()) {
            var r=pos.rect(); var ref=projected.get((pos.kind()==StripPanelLayout.Kind.HAND?"H:":"E:")+pos.id());
            if(ref==null)continue; // never synthesize undeclared/undelivered inventory cells
            var def=pos.kind()==StripPanelLayout.Kind.EQUIPMENT&&template!=null?template.slot(pos.id()).orElse(null):null;
            boolean slotEnabled=pos.kind()==StripPanelLayout.Kind.HAND||ref.occupied()||client.equipmentSlotEnabled(snapshot,pos.id());
            com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelRenderer.drawSlot(g,r,
                    def==null?null:def.icon(),snapshot.canAct()&&slotEnabled,false);
            if(ref.occupied()) {
                if(ref.hidden()) {
                    g.blit(StripClient.HIDDEN_ICON,r.x()+2,r.y()+2,r.width()-4,r.height()-4,0,0,64,64,64,64);
                    if(mx>=r.x()&&my>=r.y()&&mx<r.x()+r.width()&&my<r.y()+r.height())g.renderTooltip(Minecraft.getInstance().font,Component.literal("Hidden item"),mx,my);
                } else if(ref.item()!=null)com.juicyslew.moonstation14.ms14.hands.client.ItemUiRenderer.renderIntoSlot(g,ref.item().stack(),ref.item().stack().getCount(),r.x(),r.y(),r.width(),r.height(),Minecraft.getInstance().font);
            } else if(pos.kind()==StripPanelLayout.Kind.HAND)com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.drawStripHandPlaceholder(g,r,pos.id());
        }
        if(snapshot.status()!=StripViewSnapshot.Status.AVAILABLE)g.drawString(Minecraft.getInstance().font,client.status(),x+4,y+20,0xffffcc66,false);
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(!contains(mx,my))return false;
        int x=geometry.x(),y=geometry.y(),w=geometry.width(),h=geometry.height();
        if(button==0&&my<y+18&&mx>=x+w-18){close();return true;}
        if(button==0){ensureGeometry(Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight());if(geometry.beginPointer(mx,my,button,18,20)){dragging=true;return true;}}
        if(button!=0)return true;
        var snap=client.snapshot(); var plan=layout(Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight());
        if(gridFits&&snap!=null&&plan!=null)for(var pos:plan.slots())if(pos.rect().contains((int)mx,(int)my)) {
            var item=snap.slots().stream().filter(s->s.id().equals(pos.id())&&s.kind()==(pos.kind()==StripPanelLayout.Kind.HAND?StripSlotRef.Kind.HAND:StripSlotRef.Kind.EQUIPMENT)).findFirst().orElse(null);
            if(item!=null&&StripSlotAvailability.actionEnabled(snap,item,client.template(snap),
                    com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.clientCatalog()))client.action(item);return true;
        }
        return true;
    }
    @Override public boolean mouseDragged(double x,double y){return dragging&&geometry!=null&&geometry.dragPointer(x,y);}
    @Override public boolean mouseReleased(int button){boolean was=dragging;dragging=false;return geometry!=null&&geometry.releasePointer(button)||was;}
    @Override public boolean mouseScrolled(double x,double y,double delta){return contains(x,y);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){close();return true;}return false;}
    @Override public boolean charTyped(char value,int modifiers){return false;}
}
