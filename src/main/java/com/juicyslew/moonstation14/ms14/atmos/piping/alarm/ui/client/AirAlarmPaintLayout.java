package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

/** GUI-pixel geometry for the compact Air Alarm workspace. */
public record AirAlarmPaintLayout(int width, int height, boolean clipped) {
    public static final int WIDTH = 240, HEIGHT = 224;
    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(int px,int py){return px>=x&&py>=y&&px<x+width&&py<y+height;}
        public boolean overlaps(Bounds r){return x<r.x+r.width&&x+width>r.x&&y<r.y+r.height&&y+height>r.y;}
    }
    public static AirAlarmPaintLayout forViewport(int w,int h){int ww=Math.min(WIDTH,Math.max(1,w)),hh=Math.min(HEIGHT,Math.max(1,h));return new AirAlarmPaintLayout(ww,hh,ww<WIDTH||hh<HEIGHT);}
    public static AirAlarmPaintLayout forContent(int w,int h,int tab,int deviceCount){
        int desired=tab==1?170:tab==2?180:Math.min(HEIGHT,150+Math.min(4,Math.max(0,deviceCount-1))*18);
        int ww=Math.min(WIDTH,Math.max(1,w)),hh=Math.min(desired,Math.max(1,h));
        return new AirAlarmPaintLayout(ww,hh,ww<WIDTH||hh<desired);
    }
    public Bounds close(){return new Bounds(width-22,3,18,18);}
    public Bounds tab(int i){int gap=4, available=Math.max(3,width-16-2*gap), each=available/3;return new Bounds(8+i*(each+gap),28,each,18);}
    public Bounds device(int i){return new Bounds(8,90+i*20,width-16,18);}
    public Bounds enabled(){return new Bounds(8,76,width-16,20);}
    public Bounds mode(){return new Bounds(8,100,width-16,20);}
    public Bounds numeric(){return new Bounds(8,124,width-16,20);}
    public Bounds gas(int i){int rows=5, available=Math.max(rows,height-52-30), step=Math.min(22,available/rows), cell=Math.max(1,Math.min(19,step-2));return new Bounds(8+(i%2)*(width-16)/2,52+(i/2)*step,(width-20)/2,cell);}
    public Bounds search(){return new Bounds(8,68,width-16,18);}
    public Bounds refresh(){return new Bounds(8,height-28,72,18);}
    public Bounds configure(){return new Bounds(width-88,height-28,80,18);}
    public Bounds footer(){return new Bounds(8,height-13,width-16,10);}
}
