package com.juicyslew.moonstation14.ms14.atmos.machine.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import com.juicyslew.moonstation14.ms14.ui.client.paint.MachineTextFieldPaint;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Stateless production painter. All geometry is in GUI pixels. */
public final class AtmosMachinePaint {
    public record Hit(String id, AtmosMachineLayout.Bounds bounds) { }
    public record NumericPresentation(MachineTextFieldPaint.Snapshot field, String submitLabel, boolean pending) { }
    public record View(AtmosMachineControlSnapshot snapshot, int x, int y, int viewportWidth, int viewportHeight,
                      int mouseX, int mouseY, int scroll, boolean pending, String message, NumericPresentation numeric, String expanded, String gasQuery, List<String> gases, Boolean enabledIntent) {
        public View(AtmosMachineControlSnapshot s,int x,int y,int w,int h,int mx,int my,int scroll,boolean pending,String message,NumericPresentation numeric){this(s,x,y,w,h,mx,my,scroll,pending,message,numeric,null,"",List.of(),null);}
        public View(AtmosMachineControlSnapshot snapshot,int x,int y,int viewportWidth,int viewportHeight,int mouseX,int mouseY,int scroll,boolean pending,String message){this(snapshot,x,y,viewportWidth,viewportHeight,mouseX,mouseY,scroll,pending,message,null,null,"",List.of(),null);}
    }
    private AtmosMachinePaint() { }

    public static List<Hit> hitRegions(AtmosMachineLayout l, AtmosMachineControlSnapshot s) {
        if(s==null)return List.of(); var c=s.controls(); var hits=new ArrayList<Hit>();
        if(c.enabled())hits.add(new Hit("enabled",l.enabled()));
        if(c.pressure())hits.add(new Hit("pressure",l.pressure()));
        if(c.rate())hits.add(new Hit("rate",l.rate()));
        if(c.mixerRatio())hits.add(new Hit("ratio",l.ratio()));
        if(c.filterGas())hits.add(new Hit("filterGas",l.filter()));
        if(l.expanded()!=null&&l.expanded().equals("filterGas"))for(int i=0;i<Math.min(5,GasType.values().length);i++)if(l.gasRowBottom(i)<=l.height()-4)hits.add(new Hit("gas:"+i,l.gasRow(i)));
        if(c.thermostat())hits.add(new Hit("thermostat",l.thermostat()));
        return List.copyOf(hits);
    }

    public static void paint(OrderedPaint p, View v) {
        var l=AtmosMachineLayout.forControls(v.viewportWidth(),v.viewportHeight(),v.snapshot()==null?null:v.snapshot().controls(),v.numeric()!=null,v.message()!=null&&!v.message().isBlank(),v.expanded(),v.gases().size()); int ox=v.x(),oy=v.y();
        p.clip(ox,oy,ox+v.viewportWidth(),oy+v.viewportHeight()); p.push();
        try {
            MachineWindowRenderer.compactWorkspaceFrame(p,ox,oy,l.width(),l.height());
            p.translate(ox,oy,0);
            p.text(Component.translatable("block.moonstation14."+(v.snapshot()==null?"machine":v.snapshot().controls().profileId())),12,8,MachineWindowRenderer.TEXT,true);
            WindowChromeButtons.paintClose(p,WindowChromeButtons.closeBounds(l.close().x(),l.close().y(),l.close().width(),l.close().height()),v.mouseX(),v.mouseY());
            if(v.snapshot()==null){p.text(Component.literal("Waiting for controls"),12,38,MachineWindowRenderer.MUTED_TEXT,true);return;}
            var s=v.snapshot();var c=s.controls();var settings=s.settings();
            if(c.enabled()) button(p,"Power: "+(v.enabledIntent()!=null?(v.enabledIntent()?"On":"Off"):(settings.enabled().orElse(false)?"On":"Off")),l.enabled(),v,"enabled",!v.pending());
            int y=58;
            if(c.pressure())row(p,"Pressure",AtmosMachineClientModel.format(AtmosMachineClientModel.Numeric.PRESSURE_KPA,settings.targetPressureKpa().orElse(0d))+" kPa",l.pressure(),v);
            if(c.rate())row(p,"Rate",AtmosMachineClientModel.format(AtmosMachineClientModel.Numeric.RATE_LPS,settings.transferRateM3PerSecond().orElse(0d))+" L/s",l.rate(),v);
            if(c.mixerRatio())row(p,"Ratio A",AtmosMachineClientModel.format(AtmosMachineClientModel.Numeric.MIXER_PERCENT,settings.mixerFractionA().orElse(0d))+"%",l.ratio(),v);
            if(c.filterGas())row(p,"Filter gas",AtmosMachineClientModel.gasLabel(settings.filterGas()),l.filter(),v);
            if(c.thermostat())row(p,"Thermostat",AtmosMachineClientModel.format(AtmosMachineClientModel.Numeric.THERMOSTAT_K,settings.thermostatSetpointK().orElse(0d))+" K",l.thermostat(),v);
            if(v.numeric()!=null){var edit=l.edit();MachineTextFieldPaint.paint(p,new MachineTextFieldPaint.Rect(edit.x(),edit.y(),edit.width(),edit.height()),v.numeric().field(),ox,oy);}
             if("filterGas".equals(v.expanded())){var edit=l.edit();p.text(Component.literal("Search: "+v.gasQuery()),edit.x(),edit.y()+3,MachineWindowRenderer.TEXT,true);int n=0;for(String gas:v.gases()){button(p,gas,l.gasRow(n),v,"gas:"+n,true);n++;}if(v.gases().isEmpty())p.text(Component.literal("No matches"),edit.x(),edit.y()+25,MachineWindowRenderer.MUTED_TEXT,true);}
            if(v.message()!=null&&!v.message().isBlank())p.text(Component.literal(v.message()),12,l.height()-14,0xffff7777,true);
        } finally {p.pop();p.unclip();}
    }
    private static void row(OrderedPaint p,String label,String value,AtmosMachineLayout.Bounds r,View v){
        button(p,label+": "+value,r,v,label.equals("Filter gas")?"filterGas":label.equals("Thermostat")?"thermostat":label.equals("Ratio A")?"ratio":label.toLowerCase(Locale.ROOT),!v.pending());
    }
    private static void button(OrderedPaint p,String text,AtmosMachineLayout.Bounds r,View v,String id,boolean enabled){
        MachineActionButtonRenderer.render(p,fit(p,text,r.width()-8),r.x(),r.y(),r.width(),r.height(),r.contains(v.mouseX(),v.mouseY()),enabled,false,1f);
    }
    public static String fit(OrderedPaint p,String text,int width){return width<=0?"":p.width(text)<=width?text:p.plainSubstrByWidth(text,Math.max(0,width-6))+"…";}
}
