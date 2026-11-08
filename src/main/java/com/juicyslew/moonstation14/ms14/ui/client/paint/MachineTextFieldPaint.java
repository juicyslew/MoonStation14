package com.juicyslew.moonstation14.ms14.ui.client.paint;

import net.minecraft.network.chat.Component;

/** Shared GUI-pixel text-field painter for native EditBox snapshots. */
public final class MachineTextFieldPaint {
    public record Rect(int x,int y,int width,int height) { }
    public record Viewport(int x,int y,int width,int height) { }
    public record Snapshot(String value,int cursor,int anchor,int displayStart,boolean focused,
                           boolean editable,boolean visible,int caretTicks) {
        public Snapshot { value=value==null?"":value; }
    }
    private MachineTextFieldPaint() { }
    public static Viewport viewport(Rect r,int lineHeight) {
        int left=r.x()+3;
        if((left&1)!=0)left++;
        int top=r.y()+Math.max(1,(r.height()-lineHeight)/2);
        return new Viewport(left,top,Math.max(0,r.x()+r.width()-3-left),lineHeight);
    }
    public static void paint(OrderedPaint p,Rect r,Snapshot s) { paint(p,r,s,0,0); }
    public static void paint(OrderedPaint p,Rect r,Snapshot s,int originX,int originY) {
        int x=r.x(),y=r.y(),w=r.width(),h=r.height(); if(w<2||h<2)return;
        p.fill(x,y,x+w,y+h,s.focused()?0xffd2e2e7:0xff11191d);
        p.fill(x+1,y+1,x+w-1,y+h-1,s.editable()?0xff182329:0xff253238);
        var v=viewport(r,p.lineHeight());int left=v.x(),top=v.y(),right=left+v.width();
        int start=Math.max(0,Math.min(s.displayStart(),s.value().length()));
        String visible=p.plainSubstrByWidth(s.value().substring(start),Math.max(0,right-left));
        int clipLeft=originX+left,clipTop=originY+top;
        int clipRight=originX+right,clipBottom=originY+top+v.height();
        p.push();p.clip(clipLeft,clipTop,clipRight,clipBottom);
        try {
            int a=Math.max(start,Math.min(Math.min(s.cursor(),s.anchor()),start+visible.length()));
            int b=Math.max(a,Math.min(Math.max(s.cursor(),s.anchor()),start+visible.length()));
            if(a!=b)p.fill(left+p.width(s.value().substring(start,a)),top,left+p.width(s.value().substring(start,b)),top+p.lineHeight(),0xff526f9c);
            p.text(Component.literal(visible),left,top,s.editable()?0xffe5ecee:0xffa5b0b4,false);
            if(s.focused()&&s.visible()){int cursor=Math.max(start,Math.min(s.cursor(),start+visible.length()));int cx=left+p.width(s.value().substring(start,cursor));p.fill(cx,top,cx+1,top+p.lineHeight(),0xfff0f4f5);}
        } finally {p.unclip();p.pop();}
    }
}
