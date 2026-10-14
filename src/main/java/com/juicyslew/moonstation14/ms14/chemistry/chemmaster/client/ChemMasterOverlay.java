package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Half-scale view of the authoritative, nonmodal ChemMaster workspace. */
final class ChemMasterOverlay implements PointerOverlayWindow {
    private static final int MARGIN = ChemMasterLayout.MARGIN;
    private final ChemMasterWorkspace workspace;
    private final ChemMasterWorkspace.Window window;
    private int screenWidth,screenHeight;
    private boolean dragging,outputTab;
    ChemMasterOverlay(ChemMasterWorkspace workspace,ChemMasterWorkspace.Window window){this.workspace=workspace;this.window=window;}
    static Component localizedReagentName(ResourceLocation id){
        String key="reagent-name-"+id.getPath();
        Component localized=Component.translatable(key);
        String resolved=localized.getString();
        // Component#getString returns the translation key when the key is absent.
        if(resolved.equals(key))return Component.literal(id.getPath().replace('_',' '));
        return localized;
    }
    private static int logical(double screen,int origin){return ChemMasterLayout.toLocal(screen,origin);}
    static String containerCaption(ChemMasterWindowState state){
        String loaded=state.loadedItemName().map(Component::getString).orElse("No container");
        if(state.loadedItemName().isEmpty())return loaded;
        return loaded+"  "+com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(state.inputVolumeCents())+" / "+
                com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(state.inputCapacityCents())+"u";
    }
    private ChemMasterLayout.Geometry geometry(){var f=Minecraft.getInstance().font;return ChemMasterLayout.geometry(screenWidth,screenHeight,f.width("Input"),f.width("Output"));}
    @Override public boolean visible(){return workspace.owns(window);}
    @Override public boolean contains(double x,double y){return visible()&&x>=window.x()&&y>=window.y()&&x<window.x()+Math.min(ChemMasterLayout.WIDTH,screenWidth*2)/2d&&y<window.y()+Math.min(ChemMasterLayout.HEIGHT,screenHeight*2)/2d;}
    @Override public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int sw,int sh){
        workspace.resize(sw,sh);screenWidth=sw;screenHeight=sh;
        var state=window.state();var font=Minecraft.getInstance().font;
        var geo=ChemMasterLayout.geometry(sw,sh,font.width("Input"),font.width("Output"));int ox=window.x(),oy=window.y(),w=geo.width(),h=geo.height();
        boolean pointerAvailable=!Minecraft.getInstance().mouseHandler.isMouseGrabbed();
        int mouseX=pointerAvailable?logical(mx,ox):Integer.MIN_VALUE,mouseY=pointerAvailable?logical(my,oy):Integer.MIN_VALUE;
        // Scissors are absolute GUI coordinates; logical window-space rectangles are converted explicitly.
        var outer=ChemMasterLayout.toScreen(new ChemMasterLayout.Rect(0,0,w,h),ox,oy);
        g.enableScissor(outer.left(),outer.top(),outer.right(),outer.bottom());
        g.pose().pushPose();try{
            MachineWindowRenderer.compactWorkspaceFrame(g,ox,oy,w/2,h/2);
            g.pose().translate(ox,oy,0);g.pose().scale(.5f,.5f,1);
            MachineWindowRenderer.tabbedPane(g,font,MARGIN-4,ChemMasterLayout.HEADER,w-2*(MARGIN-4),h-4,
                    geo.inputTab().x(),geo.inputTab().width(),geo.outputTab().x(),geo.outputTab().width(),outputTab);
            g.drawString(font,"ChemMaster 4000",MARGIN,8,MachineWindowRenderer.TEXT,true);
            button(g,"×",geo.close(),mouseX,mouseY,false);
            tab(g,"Input",geo.inputTab(),!outputTab);
            tab(g,"Output",geo.outputTab(),outputTab);
            if(outputTab){MachineWindowRenderer.insetPanel(g,geo.outputPlaceholder().x(),geo.outputPlaceholder().y(),geo.outputPlaceholder().width(),Math.max(24,h-geo.outputPlaceholder().y()-8));
                g.drawString(font,"Pill and bottle production is not implemented.",geo.outputPlaceholder().x()+5,geo.outputPlaceholder().y()+7,MachineWindowRenderer.MUTED_TEXT,true);return;}
             g.drawString(font,truncate(font,containerCaption(state),geo.caption().width()),geo.caption().x(),geo.caption().y()+2,MachineWindowRenderer.TEXT,true);
            button(g,"Eject",geo.eject(),mouseX,mouseY,false);
            header(g,Component.translatable("gui.moonstation14.chemmaster.container").getString(),geo.inputHeading());
            var inputRows=workspace.presentationRows(window,true);var bufferRows=workspace.presentationRows(window,false);
            drawRows(g,inputRows,geo.inputViewport(),window.inputScroll(),mouseX,mouseY,window.pending(),ox,oy);
            scrollbar(g,geo.inputScrollbar(),inputRows.size(),window.inputScroll());
            header(g,"Buffer",new ChemMasterLayout.Rect(geo.bufferHeading().x(),geo.bufferHeading().y(),Math.max(0,geo.sort().x()-geo.bufferHeading().x()-2),geo.bufferHeading().height()));
            button(g,"Sort: "+sortName(state.sort()),geo.sort(),mouseX,mouseY,false,1.0f);
            button(g,"Transfer",geo.transferMode(),mouseX,mouseY,state.mode()==ChemMasterControlRequest.Mode.TRANSFER);
            button(g,"Discard",geo.discardMode(),mouseX,mouseY,state.mode()==ChemMasterControlRequest.Mode.DISCARD);
            drawRows(g,bufferRows,geo.bufferViewport(),window.bufferScroll(),mouseX,mouseY,window.pending(),ox,oy);
            scrollbar(g,geo.bufferScrollbar(),bufferRows.size(),window.bufferScroll());
            if(inputRows.isEmpty())empty(g,geo.inputViewport(),"No reagents in container");
            if(bufferRows.isEmpty())empty(g,geo.bufferViewport(),"Buffer is empty");
            if(window.pending())g.drawString(font,"Pending…",MARGIN,h-13,0xffffd35a,true);
            state.rejectionReason().ifPresent(r->g.drawString(font,Component.literal(r),MARGIN,h-13,0xffff7777,true));
        }finally{g.pose().popPose();g.disableScissor();}
    }
    private static void empty(GuiGraphics g,ChemMasterLayout.Rect r,String text){var f=Minecraft.getInstance().font;g.drawString(f,text,r.x()+5,r.y()+5,MachineWindowRenderer.MUTED_TEXT,true);}
    private static void header(GuiGraphics g,String text,ChemMasterLayout.Rect r){MachineWindowRenderer.divider(g,r.x(),r.y()+r.height()-2,r.width());g.drawString(Minecraft.getInstance().font,text,r.x()+2,r.y()+1,MachineWindowRenderer.MUTED_TEXT,true);}
    private static String sortName(ChemMasterControlRequest.Sort s){return switch(s){case OLDEST_FIRST->"Oldest";case ALPHABETICAL->"Alphabetical";case QUANTITY->"Quantity";case RECENT_FIRST->"Recent";};}
    private static String truncate(net.minecraft.client.gui.Font f,String text,int width){return width<=0?"":f.width(text)<=width?text:f.plainSubstrByWidth(text,Math.max(0,width-6))+"…";}
    private static void button(GuiGraphics g,String text,ChemMasterLayout.Rect r,int mx,int my,boolean selected){button(g,text,r,mx,my,selected,0.65f);}
    private static void button(GuiGraphics g,String text,ChemMasterLayout.Rect r,int mx,int my,boolean selected,float labelScale){if(r.width()<3||r.height()<3)return;
        MachineActionButtonRenderer.render(g,text,r.x(),r.y(),r.width(),r.height(),r.contains(mx,my),true,selected,labelScale);
    }
    private static void tab(GuiGraphics g,String text,ChemMasterLayout.Rect r,boolean selected){
        MachineWindowRenderer.tabLabel(g,Minecraft.getInstance().font,text,r.x(),r.y(),r.width(),r.height(),selected);
    }
    private static void scrollbar(GuiGraphics g,ChemMasterLayout.Rect track,int count,int scroll){
        if(count*ChemMasterLayout.ROW_HEIGHT<=track.height()||track.height()<=0)return;
        g.fill(track.x()+2,track.y(),track.x()+track.width()-2,track.y()+track.height(),0xff182329);
        var thumb=ChemMasterLayout.scrollbarThumb(track,count,ChemMasterLayout.ROW_HEIGHT,scroll);
        g.fill(thumb.x()+2,thumb.y(),thumb.x()+thumb.width()-2,thumb.y()+thumb.height(),0xff7c969e);
    }
    private static void drawRows(GuiGraphics g,java.util.List<ChemMasterWindowState.ReagentRow> rows,ChemMasterLayout.Rect box,int scroll,int mx,int my,boolean pending,int ox,int oy){
        MachineWindowRenderer.insetPanel(g,box.x()-2,box.y()-2,box.width()+4,box.height()+4);
        // GuiGraphics scissor coordinates are absolute GUI pixels, independent of the pose.
        enableRowScissor(box,ox,oy,r->g.enableScissor(r.left(),r.top(),r.right(),r.bottom()));try{
            var font=Minecraft.getInstance().font;int first=scroll/ChemMasterLayout.ROW_HEIGHT;
            int end=Math.min(rows.size(),(scroll+box.height()+ChemMasterLayout.ROW_HEIGHT-1)/ChemMasterLayout.ROW_HEIGHT);
            for(int i=first;i<end;i++){var row=rows.get(i);var rr=ChemMasterLayout.reagentRow(box,scroll,i);int yy=rr.y();
                Component name=localizedReagentName(row.reagent().location());int color=0xff82959b;
                try{var mc=Minecraft.getInstance();if(mc.level!=null)color=0xff000000|(com.juicyslew.moonstation14.ms14.reagent.ModReagents.require(mc.level,row.reagent().location()).color()&0xffffff);}catch(IllegalArgumentException ignored){}
                g.fill(rr.x()+2,yy+3,rr.x()+5,yy+12,color);
                g.drawString(font,truncate(font,name.getString(),64),rr.x()+8,yy+3,MachineWindowRenderer.TEXT,true);
                   String quantity=com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(row.amountCents())+"u";
                   int quantityWidth=font.width(quantity);var quantityCell=ChemMasterLayout.quantityCell(rr,quantityWidth);
                  g.drawString(font,quantity,ChemMasterLayout.quantityTextX(quantityCell,font.width(quantity)),yy+3,MachineWindowRenderer.TEXT,true);
                 var buttons=ChemMasterLayout.amountButtons(rr,quantityWidth);for(int b=0;b<buttons.size();b++){var br=buttons.get(b);String text=ChemMasterLayout.AMOUNTS[b]<0?"All":Integer.toString(ChemMasterLayout.AMOUNTS[b]);
                     MachineActionButtonRenderer.render(g,text,br.x(),br.y(),br.width(),br.height(),br.contains(mx,my),!pending,false,0.75f);}
                if(mx>=rr.x()+8&&mx<rr.x()+rr.width()-38&&my>=yy&&my<yy+rr.height())g.renderTooltip(font,name,mx,my);
            }
        }finally{g.disableScissor();}
    }
    static void enableRowScissor(ChemMasterLayout.Rect box,int ox,int oy,java.util.function.Consumer<ChemMasterLayout.ScreenRect> installer){
        installer.accept(ChemMasterLayout.toScreenClip(box,ox,oy));
    }
    @Override public boolean mouseClicked(double px,double py,int button){if(button!=0||!contains(px,py)||Minecraft.getInstance().mouseHandler.isMouseGrabbed())return false;
        int x=logical(px,window.x()),y=logical(py,window.y());var geo=geometry();
        if(geo.close().contains(x,y)){workspace.close(window);return true;}window.raise();com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.bringToFront(this);
        if(y<ChemMasterLayout.HEADER){dragging=true;workspace.beginDrag(window,(int)px,(int)py);return true;}
        if(geo.outputTab().contains(x,y)){outputTab=true;return true;}if(geo.inputTab().contains(x,y)){outputTab=false;return true;}
        if(geo.eject().contains(x,y)){workspace.send(window,ChemMasterControlRequest.Operation.EJECT,null,0);return true;}
        if(geo.sort().contains(x,y)){workspace.nextSort(window);return true;}
        if(geo.transferMode().contains(x,y)){workspace.mode(window,ChemMasterControlRequest.Mode.TRANSFER);return true;}
        if(geo.discardMode().contains(x,y)){workspace.mode(window,ChemMasterControlRequest.Mode.DISCARD);return true;}
        boolean input=geo.inputViewport().contains(x,y),buffer=geo.bufferViewport().contains(x,y);
        if(input||buffer){var rows=workspace.presentationRows(window,input);var view=input?geo.inputViewport():geo.bufferViewport();
            var font=Minecraft.getInstance().font;
            var hit=ChemMasterLayout.doseHit(view,input?window.inputScroll():window.bufferScroll(),rows.size(),x,y,index->font.width(com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(rows.get(index).amountCents())+"u"));if(hit!=null&&!window.pending()){long n=ChemMasterLayout.AMOUNTS[hit.amountIndex()]<0?com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.MAX_CENTS:(long)ChemMasterLayout.AMOUNTS[hit.amountIndex()]*100;
                workspace.send(window,input?ChemMasterControlRequest.Operation.INPUT_REAGENT:ChemMasterControlRequest.Operation.BUFFER_REAGENT,rows.get(hit.rowIndex()).reagent(),n);}return true;}
        return true;
    }
    @Override public boolean mouseDragged(double x,double y){if(!dragging)return false;workspace.drag((int)x,(int)y,screenWidth,screenHeight);return true;}
    @Override public boolean mouseReleased(int button){if(button==0&&dragging){dragging=false;workspace.endDrag();return true;}return false;}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(Minecraft.getInstance().mouseHandler.isMouseGrabbed()||!contains(x,y))return false;int lx=logical(x,window.x()),ly=logical(y,window.y());var geo=geometry();int n=(int)-Math.signum(delta)*ChemMasterLayout.ROW_HEIGHT*3;
        if(geo.inputViewport().contains(lx,ly)){window.scrollInput(n,ChemMasterLayout.maxScroll(workspace.presentationRows(window,true).size(),ChemMasterLayout.ROW_HEIGHT,geo.inputViewport().height()));return true;}
        else if(geo.bufferViewport().contains(lx,ly)){window.scrollBuffer(n,ChemMasterLayout.maxScroll(workspace.presentationRows(window,false).size(),ChemMasterLayout.ROW_HEIGHT,geo.bufferViewport().height()));return true;}return false;}
    @Override public boolean keyPressed(int key,int scan,int mods){return false;}@Override public boolean charTyped(char c,int mods){return false;}
    @Override public boolean isSearchFocused(){return false;}@Override public void clearTextFocus(){}@Override public void resetOwnedContext(){workspace.closeLocal(window);}
}

