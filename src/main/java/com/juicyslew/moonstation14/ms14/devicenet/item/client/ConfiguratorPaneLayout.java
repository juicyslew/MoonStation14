package com.juicyslew.moonstation14.ms14.devicenet.item.client;

/** Shared logical-to-GUI mapping for the retained configurator panes. */
final class ConfiguratorPaneLayout {
    record ButtonRect(int x,int y,int width,int height) {
        boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    static final ButtonRect LINK_CLEAR=new ButtonRect(8,222,70,18);
    static final ButtonRect LINK_DEFAULTS=new ButtonRect(83,222,80,18);
    static final ButtonRect LINK_DONE=new ButtonRect(242,222,50,18);
    static float textGlyphScale(double frameScale,float effectiveScale,float maxLogicalScale){
        return (float)Math.min(maxLogicalScale,effectiveScale/frameScale);
    }
    static int textWidthBudget(int logicalWidth,float glyphScale){return Math.max(1,(int)Math.floor(logicalWidth/glyphScale));}
    final int x,y,width,height;
    final double scale;
    private ConfiguratorPaneLayout(int x,int y,int width,int height,double scale){this.x=x;this.y=y;this.width=width;this.height=height;this.scale=scale;}
    static ConfiguratorPaneLayout fit(int sw,int sh,int logicalWidth,int logicalHeight){
        sw=Math.max(1,sw);sh=Math.max(1,sh);
        double scale=Math.min(1.0,Math.min(Math.sqrt((double)sw*sh/(6.0*logicalWidth*logicalHeight)),Math.min((double)sw/logicalWidth,(double)sh/logicalHeight)));
        int w=Math.max(1,(int)Math.floor(logicalWidth*scale)),h=Math.max(1,(int)Math.floor(logicalHeight*scale));
        return new ConfiguratorPaneLayout((sw-w)/2,(sh-h)/2,w,h,scale);
    }
    double logicalX(double guiX){return (guiX-x)/scale;}
    double logicalY(double guiY){return (guiY-y)/scale;}
}
