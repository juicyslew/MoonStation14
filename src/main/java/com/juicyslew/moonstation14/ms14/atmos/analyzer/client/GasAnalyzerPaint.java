package com.juicyslew.moonstation14.ms14.atmos.analyzer.client;

import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerSample;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility;
import com.juicyslew.moonstation14.ms14.examine.ExamineNumbers;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import net.minecraft.network.chat.Component;

/** Production painter shared by gameplay renderer and CPU preview. */
public final class GasAnalyzerPaint {
    public record View(GasAnalyzerSnapshot snapshot,int x,int y,int viewportWidth,int viewportHeight,
                       int mouseX,int mouseY,int distance,int maxRange,int scroll,
                       boolean waitingForCurrentTarget) { }
    public record GasRowText(String name,String quantity,int nameX,int quantityX) { }
    private GasAnalyzerPaint() { }
    public static void paint(OrderedPaint p,View v) {
        var l=GasAnalyzerLayout.calculate(v.viewportWidth(),v.viewportHeight(),v.snapshot()!=null&&v.snapshot().source()!=null);
        p.clip(v.x(),v.y(),v.x()+l.width(),v.y()+l.height());p.push();
        try {
            MachineWindowRenderer.compactWorkspaceFrame(p,v.x(),v.y(),l.width(),l.height());
            p.translate(v.x(),v.y(),0);
            small(p,Component.translatable("gui.moonstation14.gas_analyzer.title"),6,3,MachineWindowRenderer.TEXT,true);
            WindowChromeButtons.paintClose(p,WindowChromeButtons.closeBounds(l.close().x(),l.close().y(),l.close().width(),l.close().height()),v.mouseX(),v.mouseY());
            MachineWindowRenderer.divider(p,6,16,l.width()-12);
            if(v.snapshot()==null) { status(p,"gui.moonstation14.gas_analyzer.waiting",MachineWindowRenderer.MUTED_TEXT,23); return; }
            if(v.waitingForCurrentTarget()) { status(p,"gui.moonstation14.gas_analyzer.waiting",MachineWindowRenderer.MUTED_TEXT,23); return; }
            if(v.snapshot().status()==GasAnalyzerSnapshot.Status.BLOCKED) { status(p,"gui.moonstation14.gas_analyzer.blocked",0xffffc46b,23); return; }
            if(v.snapshot().status()==GasAnalyzerSnapshot.Status.UNAVAILABLE) { status(p,"gui.moonstation14.gas_analyzer.unavailable",MachineWindowRenderer.MUTED_TEXT,23); return; }
            if(v.snapshot().source()!=null)drawNodes(p,l,v.snapshot(),v);
            else drawSample(p,l,v.snapshot().target(),"gui.moonstation14.gas_analyzer.unavailable",v);
        } finally {p.pop();p.unclip();}
    }
    private static void drawSample(OrderedPaint p,GasAnalyzerLayout.Layout l,GasAnalyzerSample s,String absent,View v) {
        if(s==null){status(p,absent,MachineWindowRenderer.MUTED_TEXT,23);return;}
        String pressure=String.format(java.util.Locale.ROOT,"%.2f kPa",s.pressureKpa());
        String kelvin=String.format(java.util.Locale.ROOT,"%.2f K / %.2f °C",s.temperatureKelvin(),s.temperatureKelvin()-273.15);
        small(p,Component.translatable("gui.moonstation14.gas_analyzer.pressure",pressure),l.metrics().x(),l.metrics().y(),MachineWindowRenderer.TEXT,false);
        small(p,Component.translatable("gui.moonstation14.gas_analyzer.temperature",kelvin),l.metrics().x(),l.metrics().y()+10,MachineWindowRenderer.TEXT,false);
        var list=l.gasList();var projected=GasAnalyzerRows.project(v.snapshot());
        int scroll=Math.max(0,Math.min(v.scroll(),Math.max(0,projected.size()-GasAnalyzerLayout.visibleGasRows(l))));
        MachineWindowRenderer.insetPanel(p,list.x(),list.y(),list.width(),list.height());
        if(s.gases().isEmpty()){small(p,Component.translatable("gui.moonstation14.gas_analyzer.no_gases"),list.x()+3,list.y()+3,MachineWindowRenderer.MUTED_TEXT,false);return;}
        var rows=projected;int visible=GasAnalyzerLayout.visibleGasRows(l);
        for(int n=0;n<visible&&n+scroll<rows.size();n++)paintRow(p,list.x(),list.y()+n*GasAnalyzerLayout.ROW,list.width(),rows.get(n+scroll));
        int max=Math.max(0,rows.size()-visible);
        if(max>0){int track=list.height(),thumb=Math.max(8,track*visible/rows.size());int y=list.y()+(track-thumb)*scroll/max;p.fill(list.x()+list.width()-2,y,list.x()+list.width(),y+thumb,MachineWindowRenderer.HIGHLIGHT);}
    }
    private static void drawNodes(OrderedPaint p,GasAnalyzerLayout.Layout l,GasAnalyzerSnapshot snapshot,View v){
        var list=l.gasList();var rows=GasAnalyzerRows.project(snapshot);int visible=GasAnalyzerLayout.visibleGasRows(l);
        MachineWindowRenderer.insetPanel(p,list.x(),list.y(),list.width(),list.height());
        int scroll=Math.max(0,Math.min(v.scroll(),Math.max(0,rows.size()-visible)));
        for(int i=0;i<visible&&i+scroll<rows.size();i++)paintRow(p,list.x(),list.y()+i*GasAnalyzerLayout.ROW,list.width(),rows.get(i+scroll));
        int max=Math.max(0,rows.size()-visible);
        if(max>0){int track=list.height(),thumb=Math.max(8,track*visible/rows.size());int y=list.y()+(track-thumb)*scroll/max;p.fill(list.x()+list.width()-2,y,list.x()+list.width(),y+thumb,MachineWindowRenderer.HIGHLIGHT);}
    }
    private static void paintRow(OrderedPaint p,int x,int y,int width,GasAnalyzerRows.Row row){
        if(row.kind()==GasAnalyzerRows.Kind.HEADER){
            p.fill(x+1,y+1,x+width-3,y+GasAnalyzerLayout.ROW,0xff26363d);
            String volume=String.format(java.util.Locale.ROOT,"%.0f L",row.sample().volumeLitres());
            small(p,row.label(),x+2,y,MachineWindowRenderer.TEXT,false);
            small(p,Component.literal(volume),x+width-3-(int)Math.ceil(p.width(volume)*.7),y,MachineWindowRenderer.MUTED_TEXT,false);
        } else if(row.kind()==GasAnalyzerRows.Kind.METRIC){
            var s=row.sample();String metric=String.format(java.util.Locale.ROOT,"%.2f kPa  %.2f K",s.pressureKpa(),s.temperatureKelvin());
            small(p,Component.literal(metric),x+2,y,MachineWindowRenderer.MUTED_TEXT,false);
        } else if(row.kind()==GasAnalyzerRows.Kind.GAS) {
            var gas=row.gas();int color=color(gas.gas());
            p.fill(x+1,y+2,x+width-3,y+GasAnalyzerLayout.ROW,0x553c555c);
            int barMax=Math.max(1,width-4),bar=Math.min(barMax,(int)Math.round(barMax*gas.percentage()/100));
            if(bar>0)p.fill(x+1,y+2,x+1+bar,y+GasAnalyzerLayout.ROW,color&0x66ffffff);
            String name=Component.translatable("gui.moonstation14.gas."+gas.gas().id()).getString();
            var text=measureGasRow(p,name,gas,x,width);
            small(p,Component.literal(text.name()),text.nameX(),y,MachineWindowRenderer.TEXT,false);
            small(p,Component.literal(text.quantity()),text.quantityX(),y,MachineWindowRenderer.TEXT,false);
        }
    }
    /** Measures full species name and quantity together, choosing plain or compact scientific moles. */
    public static GasRowText measureGasRow(OrderedPaint p,String fullName,GasAnalyzerSample.GasAmount gas,int x,int width){
        int nameWidth=p.width(fullName);
        String quantity=ExamineNumbers.format(gas.moles())+" mol "+String.format(java.util.Locale.ROOT,"%.2f%%",gas.percentage());
        int quantityWidth=p.width(quantity);double maxFontWidth=(width-8)/.7;
        if(nameWidth+quantityWidth+4>maxFontWidth){
            quantity=scientificQuantity(gas);
            quantityWidth=p.width(quantity);
        }
        int nameX=x+2;
        int quantityX=x+width-4-(int)Math.ceil(quantityWidth*.7);
        if(nameWidth+quantityWidth+4>maxFontWidth||nameX+(int)Math.ceil(nameWidth*.7)+2>quantityX)
            throw new IllegalArgumentException("registered gas row cannot fit full name and quantity in compact analyzer width: "+fullName+" / "+quantity);
        return new GasRowText(fullName,quantity,nameX,quantityX);
    }
    private static String scientificQuantity(GasAnalyzerSample.GasAmount gas){
        String amount=String.format(java.util.Locale.ROOT,"%.2e",gas.moles())
                .replace("e+","e").replaceAll("e(-?)0+(\\d+)$","e$1$2");
        return amount+" mol "+String.format(java.util.Locale.ROOT,"%.2f%%",gas.percentage());
    }
    public static int color(GasType gas) { var tint=GasVisibility.displayTint(gas);return 0xff000000|(tint.red()<<16)|(tint.green()<<8)|tint.blue(); }
    private static void status(OrderedPaint p,String key,int color,int y){small(p,Component.translatable(key),6,y,color,false);}
    private static void small(OrderedPaint p,Component text,int x,int y,int color,boolean shadow){p.push();p.translate(x,y,0);p.scale(.7f,.7f,1);p.text(text,0,0,color,shadow);p.pop();}
}
