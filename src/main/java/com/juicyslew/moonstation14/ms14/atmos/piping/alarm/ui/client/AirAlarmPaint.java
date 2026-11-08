package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import com.juicyslew.moonstation14.ms14.ui.client.paint.MachineTextFieldPaint;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Stateless production painter. Coordinates and hits use GUI pixels. */
public final class AirAlarmPaint {
    public record Hit(String id, AirAlarmPaintLayout.Bounds bounds) { }
    public record View(AirAlarmControlSnapshot snapshot,int x,int y,int viewportWidth,int viewportHeight,
                       int mouseX,int mouseY,int selectedIndex,int scroll,int tab,
                       String search,String draft,String message,boolean pending,boolean searching,
                        MachineTextFieldPaint.Snapshot searchEditor,MachineTextFieldPaint.Snapshot numericEditor, Boolean intendedEnabled) {
        public View(AirAlarmControlSnapshot snapshot,int x,int y,int viewportWidth,int viewportHeight,int mouseX,int mouseY,int selectedIndex,int scroll,int tab,String search,String draft,String message,boolean pending,boolean searching){this(snapshot,x,y,viewportWidth,viewportHeight,mouseX,mouseY,selectedIndex,scroll,tab,search,draft,message,pending,searching,null,null,null);}
        public View(AirAlarmControlSnapshot snapshot,int x,int y,int viewportWidth,int viewportHeight,int mouseX,int mouseY,int selectedIndex,int scroll,int tab,String search,String draft,String message,boolean pending,boolean searching,MachineTextFieldPaint.Snapshot searchEditor,MachineTextFieldPaint.Snapshot numericEditor){this(snapshot,x,y,viewportWidth,viewportHeight,mouseX,mouseY,selectedIndex,scroll,tab,search,draft,message,pending,searching,searchEditor,numericEditor,null);}
    }
    private AirAlarmPaint() { }
    public static List<Hit> hitRegions(AirAlarmPaintLayout l,View v){
        var out=new ArrayList<Hit>();out.add(new Hit("close",l.close()));
        out.add(new Hit("tab_devices",l.tab(0)));out.add(new Hit("tab_controls",l.tab(1)));out.add(new Hit("tab_filters",l.tab(2)));out.add(new Hit("tab_alarm",l.tab(3)));
        if(v.tab()==0){out.add(new Hit("device_search",l.search()));out.add(new Hit("refresh",l.refresh()));out.add(new Hit("configure",l.configure()));
            var rows=filtered(v);for(int i=Math.max(0,v.scroll());i<Math.min(rows.size(),v.scroll()+5);i++)out.add(new Hit("device:"+rows.get(i).id(),l.device(i-v.scroll())));
        }else if(v.tab()==3){
            if(v.snapshot()!=null&&v.snapshot().powered()){for(int i=0;i<AirAlarmMode.values().length;i++)out.add(new Hit("alarm_mode:"+i,l.alarmMode(i)));out.add(new Hit("auto_mode",l.autoMode()));}
        }else if(v.tab()==1&&v.snapshot()!=null&&!v.snapshot().devices().isEmpty()){
            var d=selected(v);if(d!=null){out.add(new Hit("enabled",l.enabled()));if(d.kind()==VentilationDeviceConfig.Kind.SCRUBBER)out.add(new Hit("mode",l.mode()));out.add(new Hit("numeric",l.numeric()));}
        }else if(v.tab()==2&&v.snapshot()!=null&&!v.snapshot().devices().isEmpty()){
            var d=selected(v);if(d!=null&&d.kind()==VentilationDeviceConfig.Kind.SCRUBBER)for(int i=0;i<9;i++)out.add(new Hit("gas:"+i,l.gas(i)));
        }
        return List.copyOf(out);
    }
    public static void paint(OrderedPaint p,View v){
        var l=layout(v);int x=v.x(),y=v.y();
        p.clip(x,y,x+l.width(),y+l.height());p.push();try{MachineWindowRenderer.compactWorkspaceFrame(p,x,y,l.width(),l.height());p.translate(x,y,0);
            MachineWindowRenderer.insetPanel(p,4,22,l.width()-8,l.height()-28);
            p.text(Component.literal("Air Alarm"),10,7,MachineWindowRenderer.TEXT,true);
            WindowChromeButtons.paintClose(p,WindowChromeButtons.closeBounds(l.close().x(),l.close().y(),l.close().width(),l.close().height()),v.mouseX(),v.mouseY());
            String[] tabs={"Devices","Controls","Filters","Alarm"};for(int i=0;i<4;i++)button(p,tabs[i],l.tab(i),v,"tab_"+tabs[i].toLowerCase(Locale.ROOT),v.tab()==i,true);
            var s=v.snapshot();var d=selected(v);
            if(v.tab()==0){p.text(Component.literal(s==null?"Environment  ·  Awaiting alarm":Component.translatable("ui.moonstation14.air_alarm.summary",Component.translatable("ui.moonstation14.air_alarm.severity."+s.aggregateSeverity().name().toLowerCase(Locale.ROOT)),Component.translatable("ui.moonstation14.air_alarm.mode."+s.currentMode().name().toLowerCase(Locale.ROOT)),Component.translatable(s.autoMode()?"ui.moonstation14.air_alarm.auto_short":"ui.moonstation14.air_alarm.manual_short"),Component.translatable("ui.moonstation14.air_alarm.summary_current")).getString()),10,51,s!=null&&s.powered()?0xff80d890:0xffff8877,true);
                var searchEditor=v.searchEditor()==null?new MachineTextFieldPaint.Snapshot(v.search()==null?"":v.search(),0,0,0,false,true,false,0):v.searchEditor();MachineTextFieldPaint.paint(p,new MachineTextFieldPaint.Rect(l.search().x(),l.search().y(),l.search().width(),l.search().height()),searchEditor,x,y);
                if(s!=null){var rows=filtered(v);for(int i=0;i<5;i++){int n=v.scroll()+i;if(n>=rows.size())break;var item=rows.get(n);boolean selected=item==d;String label=(item.linked()?"LINK":"PAIR")+"  "+kindLabel(item.kind())+"  "+item.pos().toShortString()+(item.online()?"":" OFFLINE");button(p,label,l.device(i),v,"device:"+item.id(),selected,true);}}
                button(p,"Refresh",l.refresh(),v,"refresh",false,!v.pending());button(p,"Configure",l.configure(),v,"configure",false,!v.pending()&&d!=null);
            }else if(v.tab()==3){paintAlarm(p,v,l,s);
            }else if(d==null)p.text(Component.literal("Select a device to configure"),10,58,MachineWindowRenderer.MUTED_TEXT,true);
            else {if(v.tab()==1)p.text(Component.literal(kindLabel(d.kind())+"  ·  "+d.pos().toShortString()+(d.linked()?"":"  NOT PAIRED")+(d.online()?"":"  OFFLINE")),10,l.deviceSummaryY(),MachineWindowRenderer.MUTED_TEXT,true);
                if(v.tab()==1){boolean enabled=v.intendedEnabled()==null?d.enabled():v.intendedEnabled();button(p,"Enabled: "+(enabled?"On":"Off"),l.enabled(),v,"enabled",false,available(s,d,v));if(d.kind()==VentilationDeviceConfig.Kind.SCRUBBER)button(p,Component.translatable("gui.moonstation14.scrubber_mode."+VentilationDeviceConfig.ScrubberMode.values()[d.mode()].name().toLowerCase(Locale.ROOT)).getString(),l.mode(),v,"mode",false,available(s,d,v));p.text(Component.literal(d.kind()==VentilationDeviceConfig.Kind.VENT?"Pressure bound (kPa)":"Volume rate (m³/s)"),10,l.numericLabelY(),MachineWindowRenderer.MUTED_TEXT,false);var numeric=v.numericEditor()==null?new MachineTextFieldPaint.Snapshot(v.draft()==null||v.draft().isBlank()?value(d):v.draft(),0,0,0,false,true,false,0):v.numericEditor();MachineTextFieldPaint.paint(p,new MachineTextFieldPaint.Rect(l.numeric().x(),l.numeric().y(),l.numeric().width(),l.numeric().height()),numeric,x,y);}
                else {for(int i=0;i<9;i++){var gas=com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()[i];String label=Component.translatable("gui.moonstation14.gas."+gas.id()).getString();button(p,label+(((d.filterBits()&(1<<i))!=0)?"  ON":"  OFF"),l.gas(i),v,"gas:"+i,false,available(s,d,v));}}
            }
            String status=v.message()!=null?v.message():s==null?"Waiting for alarm":s.powered()?"System online":"Read-only · no power";p.text(Component.literal(fit(p,status,l.width()-16)),10,l.height()-13,v.message()!=null?0xffffcc55:MachineWindowRenderer.MUTED_TEXT,false);
        }finally{p.pop();p.unclip();}
    }
    public static AirAlarmPaintLayout layout(View v){return AirAlarmPaintLayout.forContent(v.viewportWidth(),v.viewportHeight(),v.tab(),v.snapshot()==null?0:v.snapshot().devices().size());}
    private static void paintAlarm(OrderedPaint p,View v,AirAlarmPaintLayout l,AirAlarmControlSnapshot s){
        if(s==null){clippedText(p,l.alarmSummary(),v.x(),v.y(),Component.translatable("ui.moonstation14.air_alarm.waiting"),10,l.alarmSeverityY(),MachineWindowRenderer.MUTED_TEXT,true);return;}
        for(int i=0;i<AirAlarmMode.values().length;i++){var mode=AirAlarmMode.values()[i];button(p,Component.translatable("ui.moonstation14.air_alarm.mode."+mode.name().toLowerCase(Locale.ROOT)).getString(),l.alarmMode(i),v,"alarm_mode:"+i,s.currentMode()==mode,s.powered());}
        button(p,Component.translatable(s.autoMode()?"ui.moonstation14.air_alarm.auto_on":"ui.moonstation14.air_alarm.auto_off").getString(),l.autoMode(),v,"auto_mode",false,s.powered());
        var reading=selectedReading(s,selected(v));
        String status=s.readings().isEmpty()?"ui.moonstation14.air_alarm.summary_no_sources":s.readings().stream().anyMatch(r->!r.valid())?"ui.moonstation14.air_alarm.summary_unavailable":s.readings().stream().anyMatch(AirAlarmControlSnapshot.MonitorReading::stale)?"ui.moonstation14.air_alarm.summary_cached":"ui.moonstation14.air_alarm.summary_current";
        Component summary=Component.translatable("ui.moonstation14.air_alarm.summary",Component.translatable("ui.moonstation14.air_alarm.severity."+s.aggregateSeverity().name().toLowerCase(Locale.ROOT)),Component.translatable("ui.moonstation14.air_alarm.mode."+s.currentMode().name().toLowerCase(Locale.ROOT)),Component.translatable(s.autoMode()?"ui.moonstation14.air_alarm.auto_short":"ui.moonstation14.air_alarm.manual_short"),Component.translatable(status));
        clippedText(p,l.alarmSummary(),v.x(),v.y(),summary,10,l.alarmSeverityY(),MachineWindowRenderer.TEXT,true);
        if(reading==null||!reading.valid()){clippedText(p,l.alarmStats(),v.x(),v.y(),Component.translatable("ui.moonstation14.air_alarm.reading_unavailable"),10,l.alarmStats().y(),MachineWindowRenderer.MUTED_TEXT,true);return;}
        if(reading.stale())clippedText(p,l.alarmStats(),v.x(),v.y(),Component.translatable("ui.moonstation14.air_alarm.cached_reading"),10,l.alarmStats().y(),MachineWindowRenderer.MUTED_TEXT,true);
        Component stats=Component.translatable("ui.moonstation14.air_alarm.reading_stats",format(reading.pressureKpa()),format(reading.temperatureKelvin()),format(reading.totalMoles()));
        clippedText(p,l.alarmStats(),v.x(),v.y(),stats,10,reading.stale()?l.alarmStatsValuesY():l.alarmStats().y(),MachineWindowRenderer.TEXT,true);
        for(int i=0;i<GasType.values().length;i++){var gas=GasType.values()[i];double moles=reading.gasMoles().get(i),fraction=reading.totalMoles()>0?moles/reading.totalMoles():0;Component line=Component.translatable("ui.moonstation14.air_alarm.gas_reading",Component.translatable("gui.moonstation14.gas."+gas.id()),format(moles),format(fraction*100));var b=l.gasReading(i);clippedText(p,b,v.x(),v.y(),line,b.x(),b.y(),MachineWindowRenderer.TEXT,false);}
    }
    private static void clippedText(OrderedPaint p,AirAlarmPaintLayout.Bounds b,int originX,int originY,Component text,int x,int y,int color,boolean shadow){p.push();p.clip(originX+b.x(),originY+b.y(),originX+b.x()+b.width(),originY+b.y()+b.height());try{p.text(text,x,y,color,shadow);}finally{p.unclip();p.pop();}}
    private static AirAlarmControlSnapshot.MonitorReading selectedReading(AirAlarmControlSnapshot s,AirAlarmControlSnapshot.Device d){return d==null?null:s.readings().stream().filter(r->r.sourceId().equals(d.id())).findFirst().orElse(null);}
    private static String format(double value){return String.format(Locale.ROOT,"%.2f",value);}
    private static AirAlarmControlSnapshot.Device selected(View v){var s=v.snapshot();return s==null||v.selectedIndex()<0||v.selectedIndex()>=s.devices().size()?null:s.devices().get(v.selectedIndex());}
    private static List<AirAlarmControlSnapshot.Device> filtered(View v){if(v.snapshot()==null)return List.of();String q=v.search()==null?"":v.search().toLowerCase(Locale.ROOT);return v.snapshot().devices().stream().filter(d->(kindLabel(d.kind())+" "+d.pos().toShortString()+" "+(d.linked()?"linked":"unpaired")+" "+(d.online()?"online":"offline")).toLowerCase(Locale.ROOT).contains(q)).toList();}
    static String searchLabel(AirAlarmControlSnapshot.Device d){return kindLabel(d.kind())+" "+d.pos().toShortString()+" "+(d.linked()?"linked":"unpaired")+" "+(d.online()?"online":"offline");}
    private static String kindLabel(VentilationDeviceConfig.Kind kind){return Component.translatable("gui.moonstation14.air_alarm.device."+kind.name().toLowerCase(Locale.ROOT)).getString();}
    private static boolean available(AirAlarmControlSnapshot s,AirAlarmControlSnapshot.Device d,View v){return !v.pending()&&s!=null&&s.powered()&&d.linked()&&d.online();}
    private static String value(AirAlarmControlSnapshot.Device d){return String.format(Locale.ROOT,"%.3f",d.kind()==VentilationDeviceConfig.Kind.VENT?d.externalPressureBoundKPa():d.volumeRateM3PerSecond());}
    private static void button(OrderedPaint p,String text,AirAlarmPaintLayout.Bounds b,View v,String id,boolean selected,boolean enabled){MachineActionButtonRenderer.render(p,fit(p,text,b.width()-8),b.x(),b.y(),b.width(),b.height(),b.contains(v.mouseX(),v.mouseY()),enabled,selected,1f);}
    public static String fit(OrderedPaint p,String text,int width){if(width<=0)return "";if(p.width(text)<=width)return text;String suffix="…";int available=width-p.width(suffix);if(available<0)return "";String prefix=p.plainSubstrByWidth(text,available);while(!prefix.isEmpty()&&p.width(prefix+suffix)>width)prefix=p.plainSubstrByWidth(prefix,Math.max(0,p.width(prefix)-1));return p.width(prefix+suffix)<=width?prefix+suffix:"";}
}
