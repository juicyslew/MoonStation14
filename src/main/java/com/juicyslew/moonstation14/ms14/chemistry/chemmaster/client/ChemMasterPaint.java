package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Single production ChemMaster content paint path, parameterized for offline backends. */
public final class ChemMasterPaint {
    public record DisplayRow(ChemMasterWindowState.ReagentRow row, Component name, int color) { }
    public record View(ChemMasterWindowState state, String caption, List<DisplayRow> inputRows, List<DisplayRow> bufferRows,
                       ChemMasterLayout.Geometry geometry, int x, int y, int viewportWidth, int viewportHeight,
                       int mouseX, int mouseY, int inputScroll, int bufferScroll, boolean pending, boolean outputTab,
                       Component containerHeading, String rejectionReason) {
        public View { inputRows=List.copyOf(inputRows); bufferRows=List.copyOf(bufferRows); }
    }
    private static final int MARGIN=ChemMasterLayout.MARGIN;
    private ChemMasterPaint() { }
    public static void paint(OrderedPaint p, View v) {
        var geo=v.geometry(); int w=geo.width(),h=geo.height(),ox=v.x(),oy=v.y();
        var clip=ChemMasterLayout.toScreen(new ChemMasterLayout.Rect(0,0,w,h),ox,oy);
        p.clip(clip.left(),clip.top(),clip.right(),clip.bottom()); p.push();
        try {
            MachineWindowRenderer.compactWorkspaceFrame(p,ox,oy,w/2,h/2);
            p.translate(ox,oy,0);p.scale(.5f,.5f,1);
            MachineWindowRenderer.tabbedPane(p,MARGIN-4,ChemMasterLayout.HEADER,w-2*(MARGIN-4),h-4,
                    geo.inputTab().x(),geo.inputTab().width(),geo.outputTab().x(),geo.outputTab().width(),v.outputTab());
            p.text(Component.literal("ChemMaster 4000"),MARGIN,8,MachineWindowRenderer.TEXT,true);
            WindowChromeButtons.paintClose(p,WindowChromeButtons.closeBounds(geo.close().x(),geo.close().y(),geo.close().width(),geo.close().height()),v.mouseX(),v.mouseY());
            tab(p,"Input",geo.inputTab(),!v.outputTab());tab(p,"Output",geo.outputTab(),v.outputTab());
            if(v.outputTab()) { MachineWindowRenderer.insetPanel(p,geo.outputPlaceholder().x(),geo.outputPlaceholder().y(),geo.outputPlaceholder().width(),Math.max(24,h-geo.outputPlaceholder().y()-8));p.text(Component.literal("Pill and bottle production is not implemented."),geo.outputPlaceholder().x()+5,geo.outputPlaceholder().y()+7,MachineWindowRenderer.MUTED_TEXT,true);return; }
            p.text(Component.literal(fitText(p,v.caption(),geo.caption().width())),geo.caption().x(),geo.caption().y()+2,MachineWindowRenderer.TEXT,true);
            button(p,"Eject",geo.eject(),v.mouseX(),v.mouseY(),false);
            header(p,v.containerHeading().getString(),geo.inputHeading());
            rows(p,v.inputRows(),geo.inputViewport(),v.inputScroll(),v.mouseX(),v.mouseY(),v.pending(),ox,oy);
            scrollbar(p,geo.inputScrollbar(),v.inputRows().size(),v.inputScroll());
            header(p,"Buffer",new ChemMasterLayout.Rect(geo.bufferHeading().x(),geo.bufferHeading().y(),Math.max(0,geo.sort().x()-geo.bufferHeading().x()-2),geo.bufferHeading().height()));
            button(p,"Sort: "+sortName(v.state().sort()),geo.sort(),v.mouseX(),v.mouseY(),false,1f);
            button(p,"Transfer",geo.transferMode(),v.mouseX(),v.mouseY(),v.state().mode()==ChemMasterControlRequest.Mode.TRANSFER);
            button(p,"Discard",geo.discardMode(),v.mouseX(),v.mouseY(),v.state().mode()==ChemMasterControlRequest.Mode.DISCARD);
            rows(p,v.bufferRows(),geo.bufferViewport(),v.bufferScroll(),v.mouseX(),v.mouseY(),v.pending(),ox,oy);
            scrollbar(p,geo.bufferScrollbar(),v.bufferRows().size(),v.bufferScroll());
            if(v.inputRows().isEmpty())empty(p,geo.inputViewport(),"No reagents in container");
            if(v.bufferRows().isEmpty())empty(p,geo.bufferViewport(),"Buffer is empty");
            if(v.rejectionReason()!=null)p.text(Component.literal(v.rejectionReason()),MARGIN,h-13,0xffff7777,true);
        } finally {p.pop();p.unclip();}
    }
    private static void empty(OrderedPaint p,ChemMasterLayout.Rect r,String s){p.text(Component.literal(s),r.x()+5,r.y()+5,MachineWindowRenderer.MUTED_TEXT,true);}
    private static void header(OrderedPaint p,String s,ChemMasterLayout.Rect r){MachineWindowRenderer.divider(p,r.x(),r.y()+r.height()-2,r.width());p.text(Component.literal(s),r.x()+2,r.y()+1,MachineWindowRenderer.MUTED_TEXT,true);}
    private static String sortName(ChemMasterControlRequest.Sort s){return switch(s){case OLDEST_FIRST->"Oldest";case ALPHABETICAL->"Alphabetical";case QUANTITY->"Quantity";case RECENT_FIRST->"Recent";};}
    /** Shared fit policy used by production painting and its offline layout assertions. */
    public static String fitText(OrderedPaint p,String s,int width){return width<=0?"":p.width(s)<=width?s:p.plainSubstrByWidth(s,Math.max(0,width-6))+"…";}
    private static void button(OrderedPaint p,String s,ChemMasterLayout.Rect r,int mx,int my,boolean selected){button(p,s,r,mx,my,selected,.65f);}
    private static void button(OrderedPaint p,String s,ChemMasterLayout.Rect r,int mx,int my,boolean selected,float scale){if(r.width()>=3&&r.height()>=3)MachineActionButtonRenderer.render(p,s,r.x(),r.y(),r.width(),r.height(),r.contains(mx,my),true,selected,scale);}
    private static void tab(OrderedPaint p,String s,ChemMasterLayout.Rect r,boolean selected){MachineWindowRenderer.tabLabel(p,s,r.x(),r.y(),r.width(),r.height(),selected);}
    private static void scrollbar(OrderedPaint p,ChemMasterLayout.Rect track,int count,int scroll){if(count*ChemMasterLayout.ROW_HEIGHT<=track.height()||track.height()<=0)return;p.fill(track.x()+2,track.y(),track.x()+track.width()-2,track.y()+track.height(),0xff182329);var thumb=ChemMasterLayout.scrollbarThumb(track,count,ChemMasterLayout.ROW_HEIGHT,scroll);p.fill(thumb.x()+2,thumb.y(),thumb.x()+thumb.width()-2,thumb.y()+thumb.height(),0xff7c969e);}
    private static void rows(OrderedPaint p,List<DisplayRow> rows,ChemMasterLayout.Rect box,int scroll,int mx,int my,boolean pending,int ox,int oy){
        MachineWindowRenderer.insetPanel(p,box.x()-2,box.y()-2,box.width()+4,box.height()+4);
        var c=ChemMasterLayout.toScreenClip(box,ox,oy);p.clip(c.left(),c.top(),c.right(),c.bottom());try{
            int first=scroll/ChemMasterLayout.ROW_HEIGHT,end=Math.min(rows.size(),(scroll+box.height()+ChemMasterLayout.ROW_HEIGHT-1)/ChemMasterLayout.ROW_HEIGHT);
            for(int i=first;i<end;i++){var d=rows.get(i);var rr=ChemMasterLayout.reagentRow(box,scroll,i);int yy=rr.y();p.fill(rr.x()+2,yy+3,rr.x()+5,yy+12,d.color());String name=fitText(p,d.name().getString(),64);p.text(Component.literal(name),rr.x()+8,yy+3,MachineWindowRenderer.TEXT,true);
                String quantity=com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(d.row().amountCents())+"u";int qw=p.width(quantity);var cell=ChemMasterLayout.quantityCell(rr,qw);p.text(Component.literal(quantity),ChemMasterLayout.quantityTextX(cell,p.width(quantity)),yy+3,MachineWindowRenderer.TEXT,true);
                var buttons=ChemMasterLayout.amountButtons(rr,qw);for(int b=0;b<buttons.size();b++){var br=buttons.get(b);String label=ChemMasterLayout.AMOUNTS[b]<0?"All":Integer.toString(ChemMasterLayout.AMOUNTS[b]);MachineActionButtonRenderer.render(p,label,br.x(),br.y(),br.width(),br.height(),br.contains(mx,my),!pending,false,.75f);}
            }
        }finally{p.unclip();}
    }
}
