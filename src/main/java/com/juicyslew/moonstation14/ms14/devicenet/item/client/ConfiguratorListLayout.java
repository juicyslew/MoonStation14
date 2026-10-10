package com.juicyslew.moonstation14.ms14.devicenet.item.client;

/** Shared local-coordinate geometry for the retained configurator list pane. */
public final class ConfiguratorListLayout {
    public static final int ROW_TOP=55, ROW_HEIGHT=20, ROW_BOTTOM=225, ROW_REMOVE_LEFT=300;
    public static final int LIST_BOTTOM=228, CONFIGURE_BOTTOM=174, MAX_ROWS=(LIST_BOTTOM-ROW_TOP)/ROW_HEIGHT;
    public record Rect(int x,int y,int width,int height) { public boolean contains(int px,int py){return px>=x&&px<x+width&&py>=y&&py<y+height;} }
    public static final Rect REMOVE=new Rect(8,262,67,18), CLEAR=new Rect(80,262,67,18), COPY=new Rect(153,262,57,18), SHOW=new Rect(215,262,57,18), DONE=new Rect(277,262,55,18);
    public static final Rect SET=new Rect(8,235,98,18), ADD=new Rect(111,235,98,18), CONFIG_CLEAR=new Rect(214,235,118,18);
    private ConfiguratorListLayout(){}
    public static Rect row(int index){return new Rect(12,ROW_TOP+index*ROW_HEIGHT-2,316,17);}
}
