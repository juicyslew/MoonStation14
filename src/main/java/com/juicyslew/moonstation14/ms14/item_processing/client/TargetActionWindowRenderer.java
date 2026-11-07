package com.juicyslew.moonstation14.ms14.item_processing.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionSnapshot;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Single production painter shared by the live pointer window and CPU preview. */
public final class TargetActionWindowRenderer {
    /** A presentation-only row shared by target and held-item action lists. */
    public record Row(Component label, boolean enabled, Component disabledReason,
                      java.util.Optional<ResourceLocation> icon) {
        public Row {
            java.util.Objects.requireNonNull(label, "label");
            icon = icon == null ? java.util.Optional.empty() : icon;
            if (enabled && disabledReason != null)
                throw new IllegalArgumentException("enabled action cannot have a disabled reason");
        }
        public Row(Component label, boolean enabled, Component disabledReason, ResourceLocation icon) {
            this(label, enabled, disabledReason, java.util.Optional.ofNullable(icon));
        }
    }
    public static final ResourceLocation CUTLERY=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/target_actions/cutlery.png");
    private TargetActionWindowRenderer() { }
    /** Shared label measurement used by both the live Font adapter and CPU OrderedPaint previews. */
    public static int measuredWidth(java.util.List<Row> rows,java.util.function.ToIntFunction<Component> measure){
        return measuredWidth(null,rows,measure);
    }
    public static int measuredWidth(Component title,java.util.List<Row> rows,java.util.function.ToIntFunction<Component> measure){
        int widest=title==null?0:measure.applyAsInt(title)+44;for(var row:rows)widest=Math.max(widest,measure.applyAsInt(row.label())+(row.icon().isPresent()?36:14));
        return Math.min(TargetActionClientPolicy.Layout.WIDTH,Math.max(TargetActionClientPolicy.Layout.MIN_USABLE_WIDTH,widest));
    }
    /** Clip a title with an explicit ellipsis; all measuring uses the same OrderedPaint backend as painting. */
    public static String fitTitle(OrderedPaint p,String source,int maxWidth){
        if(p.width(source)<=maxWidth)return source;
        String ellipsis="…";int available=Math.max(0,maxWidth-p.width(ellipsis));
        return p.plainSubstrByWidth(source,available).stripTrailing()+ellipsis;
    }
    /** Wrap at whitespace, splitting only a single token wider than the available line. */
    public static java.util.List<String> wrapReason(OrderedPaint p,String source,int maxWidth,int maxLines){
        if(source==null||source.isBlank()||maxLines<=0||maxWidth<=0)return java.util.List.of();
        var lines=new java.util.ArrayList<String>();String current="";boolean overflow=false;
        String[] words=source.strip().split("\\s+");
        outer:for(String word:words){
            if(p.width(word)>maxWidth){
                if(!current.isEmpty()){lines.add(current);current="";if(lines.size()==maxLines){overflow=true;break;}}
                String remaining=word;
                while(p.width(remaining)>maxWidth){
                    String chunk=p.plainSubstrByWidth(remaining,maxWidth);if(chunk.isEmpty())break outer;
                    lines.add(chunk);remaining=remaining.substring(chunk.length());
                    if(lines.size()==maxLines){overflow=!remaining.isEmpty();break outer;}
                }
                current=remaining;continue;
            }
            String combined=current.isEmpty()?word:current+" "+word;
            if(p.width(combined)<=maxWidth)current=combined;
            else {lines.add(current);current=word;if(lines.size()==maxLines){overflow=true;break;}}
        }
        if(!current.isEmpty()&&lines.size()<maxLines)lines.add(current);
        if(overflow&&!lines.isEmpty()){
            int last=lines.size()-1;String ellipsis="…";int available=Math.max(0,maxWidth-p.width(ellipsis));
            lines.set(last,p.plainSubstrByWidth(lines.get(last),available).stripTrailing()+ellipsis);
        }
        return java.util.List.copyOf(lines);
    }
    public static void paint(OrderedPaint p,BodyTargetActionSnapshot state,TargetActionClientPolicy.Layout layout,int mx,int my){
        var rows=state.actions().stream().map(entry -> new Row(entry.label(),entry.enabled(),entry.disabledReason(),
                entry.id()==BodyTargetActionSnapshot.ActionId.BUTCHER?CUTLERY:null)).toList();
        paint(p,state.targetName(),rows,layout,mx,my);
    }
    /** Shared list painter; geometry and action count remain owned by the existing target-action layout. */
    public static void paint(OrderedPaint p,Component titleComponent,java.util.List<Row> rows,
                             TargetActionClientPolicy.Layout layout,int mx,int my){
        int x=layout.x(),y=layout.y();var fontWidth=Math.max(0,layout.width()-44);
        // Components are flattened intentionally: OrderedPaint's supported contract is unstyled text; styled source content remains unverified.
        String title=fitTitle(p,titleComponent.getString(),fontWidth);
        MachineWindowRenderer.window(p,x,y,layout.width(),layout.height(),Component.literal(title));
        var close=layout.close();WindowChromeButtons.paintClose(p,new net.minecraft.client.gui.navigation.ScreenRectangle(close.x(),close.y(),close.width(),close.height()),mx,my);
        Row footer=null;
        for(int i=0;i<rows.size();i++){
            var entry=rows.get(i);var action=layout.action(i);
            MachineActionButtonRenderer.render(p,"",action.x(),action.y(),action.width(),action.height(),action.contains(mx,my),entry.enabled(),false,1f);
            int textX=action.x()+(entry.icon().isPresent()?27:5);int textWidth=Math.max(0,action.x()+action.width()-textX-4);
            p.push();p.clip(textX,action.y(),textX+textWidth,action.y()+action.height());
            String label=fitTitle(p,entry.label().getString(),textWidth);
            entry.icon().ifPresent(icon -> p.sprite(icon,action.x()+5,action.y()+1,16,16,0,0,64,64,64,64,0xffffffff));
            p.text(Component.literal(label),textX,action.y()+Math.max(1,(action.height()-p.lineHeight())/2),MachineWindowRenderer.TEXT,true);p.unclip();p.pop();
            if(footer==null&&!entry.enabled())footer=entry;
        }
        if(footer!=null&&footer.disabledReason()!=null){
            var bounds=layout.reason();int yy=bounds.y();
            if(bounds.height()<=0)return;
            for(String line:wrapReason(p,footer.disabledReason().getString(),bounds.width(),bounds.height()/p.lineHeight())){
                p.text(Component.literal(line),bounds.x(),yy,MachineWindowRenderer.MUTED_TEXT,false);yy+=p.lineHeight();
            }
        }
    }
}
