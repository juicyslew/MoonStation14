package com.juicyslew.moonstation14.ms14.doors.ui.client;

import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.List;

/** Production content painter. Its immutable view is also the offline backend seam. */
@OnlyIn(Dist.CLIENT)
public final class AirlockWirePaint {
    public record View(int x, int y, AirlockWireSnapshot state, int animationTick, String message, int mouseX, int mouseY, net.minecraft.network.chat.Component title) { }
    private static final String[] LABELS={"PWR","BOLT","TIMR","AIA","LOG","BLIT","SAFE"};
    private static final ResourceLocation WIRE=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/airlock/wire_1.png");
    private static final ResourceLocation CUT=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/airlock/wire_1_cut.png");
    private static final ResourceLocation ALT=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/airlock/wire_2.png");
    private static final ResourceLocation ALT_CUT=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/airlock/wire_2_cut.png");
    private AirlockWirePaint() { }
    public static void paint(OrderedPaint p, View view) {
        int x=view.x(),y=view.y();
        MachineWindowRenderer.window(p,x,y,AirlockWireScreenLayout.WIDTH,AirlockWireScreenLayout.HEIGHT,view.title());
        com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.paintClose(p,
                com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.closeBounds(x,y,AirlockWireScreenLayout.WIDTH,1f),view.mouseX(),view.mouseY());
        MachineWindowRenderer.insetPanel(p,x+6,y+31,AirlockWireScreenLayout.WIDTH-12,110);
        p.fill(x+14,y+80,x+196,y+82,0xff52664b); p.fill(x+14,y+135,x+196,y+137,0xff52664b);
        var state=view.state();
        if(state!=null) for(int i=0;i<state.rows().size();i++) {
            var row=state.rows().get(i); var b=AirlockWireScreenLayout.wire(x,y,i); boolean alt=(i&1)!=0;
            p.sprite(row.cut()?(alt?ALT_CUT:CUT):(alt?ALT:WIRE),b.x(),b.y(),b.width(),b.height(),0,0,16,50,16,50,row.argb());
            p.text(net.minecraft.network.chat.Component.literal(row.letter()),b.x()+5,b.y()+18,0xffffffff,true);
        }
        List<AirlockWireIndicators.Light> lights=state==null?java.util.Collections.nCopies(7,AirlockWireIndicators.Light.OFF):List.of(state.powerIndicator(),state.boltIndicator(),state.timingIndicator(),state.aiInteractIndicator(),state.logIndicator(),state.boltLightsIndicator(),state.safetyIndicator());
        for(int i=0;i<LABELS.length;i++){var b=AirlockWireScreenLayout.indicator(x,y,i);boolean on=AirlockWireLightStyle.isOn(lights.get(i),view.animationTick());int d=AirlockWireLightStyle.diameter(on);int l=b.x()+(10-d)/2,t=b.y()+(10-d)/2;p.fill(l,t,l+d,t+d,on?AirlockWireLightStyle.ON:AirlockWireLightStyle.OFF);p.text(net.minecraft.network.chat.Component.literal(LABELS[i]),b.x()+12,b.y()+2,MachineWindowRenderer.TEXT,false);}
        var footer=AirlockWireScreenLayout.footer(x,y);String text=view.message()!=null?view.message():state!=null&&!state.supplyPowered()?"NO POWER":"";int color=view.message()!=null?0xffffcc55:state!=null&&!state.supplyPowered()?0xffff7770:0xffaab6ba;
        p.text(net.minecraft.network.chat.Component.literal(p.plainSubstrByWidth(text,footer.width())),footer.x(),footer.y(),color,false);
    }
}
