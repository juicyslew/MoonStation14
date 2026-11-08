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
        int desired=tab==3?224:tab==2?180:tab==1?170:Math.min(HEIGHT,150+Math.min(4,Math.max(0,deviceCount-1))*18);
        int ww=Math.min(WIDTH,Math.max(1,w)),hh=Math.min(desired,Math.max(1,h));
        return new AirAlarmPaintLayout(ww,hh,ww<WIDTH||hh<desired);
    }
    public Bounds close(){return new Bounds(width-22,3,18,18);}
    public Bounds tab(int i){int count=4,gap=3,available=Math.max(count,width-16-(count-1)*gap),each=available/count;return new Bounds(8+i*(each+gap),28,each,18);}
    public Bounds device(int i){return new Bounds(8,90+i*20,width-16,18);}
    public Bounds alarmMode(int index){int count=com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode.values().length;int cell=(width-16)/count;return new Bounds(8+index*cell,52,cell-2,18);}
    public Bounds autoMode(){return new Bounds(8,73,width-16,18);}
    public int deviceSummaryY(){return 52;}
    public int numericLabelY(){return 116;}
    public Bounds enabled(){return new Bounds(8,68,width-16,20);}
    public Bounds mode(){return new Bounds(8,92,width-16,20);}
    public Bounds numericLabel(){return new Bounds(8,numericLabelY(),width-16,10);}
    public Bounds numeric(){return new Bounds(8,132,width-16,20);}
    public int alarmSeverityY(){return 96;}
    public Bounds alarmSummary(){return new Bounds(8,alarmSeverityY(),width-16,10);}
    public Bounds alarmStats(){return new Bounds(8,106,width-16,20);}
    public int alarmStatsValuesY(){return 116;}
    public Bounds gasReading(int i){int step=9,cell=9;return new Bounds(8,128+i*step,width-16,cell);}
    public Bounds gas(int i){int rows=5, available=Math.max(rows,height-52-30), step=Math.min(22,available/rows), cell=Math.max(1,Math.min(19,step-2));return new Bounds(8+(i%2)*(width-16)/2,52+(i/2)*step,(width-20)/2,cell);}
    public Bounds search(){return new Bounds(8,68,width-16,18);}
    public Bounds refresh(){return new Bounds(8,height-28,72,18);}
    public Bounds configure(){return new Bounds(width-88,height-28,80,18);}
    public Bounds footer(){return new Bounds(8,height-13,width-16,10);}
}
