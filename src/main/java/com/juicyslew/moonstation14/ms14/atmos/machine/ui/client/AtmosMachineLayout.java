package com.juicyslew.moonstation14.ms14.atmos.machine.ui.client;

/** Stable GUI-pixel geometry for the compact atmos controls window. */
public record AtmosMachineLayout(int width,int height,boolean tooSmall,java.util.List<String> rows,boolean editor,boolean footer,String expanded) {
    public AtmosMachineLayout(int width,int height,boolean tooSmall){this(width,height,tooSmall,java.util.List.of("enabled","pressure","rate","ratio","filterGas","thermostat"),false,false,null);}
    public static final int WIDTH=216, HEIGHT=224;
    public record Bounds(int x,int y,int width,int height){public boolean contains(int px,int py){return px>=x&&py>=y&&px<x+width&&py<y+height;}}
    public static AtmosMachineLayout forViewport(int w,int h){int ww=Math.min(WIDTH,Math.max(1,w)),hh=Math.min(HEIGHT,Math.max(1,h));return new AtmosMachineLayout(ww,hh,ww<WIDTH||hh<HEIGHT,java.util.List.of(),false,false,null);}
    public static AtmosMachineLayout forControls(int w,int h,com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls c,boolean editor,boolean footer){return forControls(w,h,c,editor,footer,null);}
    public static AtmosMachineLayout forControls(int w,int h,com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls c,boolean editor,boolean footer,String expanded){
        return forControls(w,h,c,editor,footer,expanded,5,true);
    }
    public static AtmosMachineLayout forControls(int w,int h,com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls c,boolean editor,boolean footer,String expanded,int visibleGasRowCount){
        return forControls(w,h,c,editor,footer,expanded,visibleGasRowCount,false);
    }
    private static AtmosMachineLayout forControls(int w,int h,com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls c,boolean editor,boolean footer,String expanded,int visibleGasRowCount,boolean legacyMaximum){
        var rows=new java.util.ArrayList<String>();if(c!=null){if(c.enabled())rows.add("enabled");if(c.pressure())rows.add("pressure");if(c.rate())rows.add("rate");if(c.mixerRatio())rows.add("ratio");if(c.filterGas())rows.add("filterGas");if(c.thermostat())rows.add("thermostat");}
        if("filterGas".equals(expanded)){rows.removeIf(id->!id.equals("enabled")&&!id.equals("filterGas"));}
        int gasCount=Math.max(0,Math.min(5,visibleGasRowCount));
        int desired=expanded!=null&&expanded.equals("filterGas")?30+rows.size()*22+22+(gasCount==0?18:(gasCount-1)*20+18)+(legacyMaximum?6:4):30+rows.size()*22+(expanded==null?(editor?24:0):24)+(footer?12:0);
        int ww=Math.min(WIDTH,Math.max(1,w)),hh=Math.min(HEIGHT,Math.min(Math.max(1,h),Math.max(30,desired)));
        return new AtmosMachineLayout(ww,hh,ww<WIDTH||hh<desired,java.util.List.copyOf(rows),editor,footer,expanded);
    }
    public Bounds row(String id){int i=rows.indexOf(id);return i<0?new Bounds(0,0,0,0):new Bounds(10,30+i*22+(expanded!=null&&rows.indexOf(expanded)>=0&&i>rows.indexOf(expanded)?24:0),width-20,20);}
    public Bounds close(){return new Bounds(width-20,3,18,18);}
    public Bounds row(int i){return new Bounds(10,30+i*22,width-20,20);}
    public Bounds gasRow(int i){return i<0?new Bounds(0,0,0,0):new Bounds(10,edit().y()+22+i*20,width-20,18);}
    public int gasRowBottom(int i){return gasRow(i).y()+gasRow(i).height();}
    public Bounds edit(){return expanded==null?new Bounds(10,30+rows.size()*22,width-20,22):editor(expanded);}
    public Bounds editor(String row){var r=row(row);return new Bounds(10,r.y()+22,width-20,22);}
    public Bounds submit(){return edit();}
    public Bounds numericLabel(int i){return new Bounds(10,82+i*42,width-20,12);} public Bounds numericEdit(int i){return new Bounds(10,96+i*42,width-20,22);}
    public Bounds telemetry(){return new Bounds(10,174,width-20,12);} public Bounds message(){return new Bounds(10,190,width-20,12);}
    public Bounds enabled(){return row("enabled");} public Bounds pressure(){return row("pressure");} public Bounds rate(){return row("rate");} public Bounds ratio(){return row("ratio");} public Bounds filter(){return row("filterGas");} public Bounds thermostat(){return row("thermostat");}
    public Bounds valve(){return new Bounds(width-82,34,70,20);}
}
