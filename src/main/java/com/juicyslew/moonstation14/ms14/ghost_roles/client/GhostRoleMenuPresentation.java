package com.juicyslew.moonstation14.ms14.ghost_roles.client;

import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import java.util.*;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;

/** Pure grouping and sizing rules consumed by the ghost-role picker. */
public final class GhostRoleMenuPresentation {
    public static final int TITLE_HEIGHT=13, ROW_HEIGHT=12, CONTENT_GAP=4;
    private GhostRoleMenuPresentation() { }
    public record Row(String roleId,GhostRolePayloads.Entry entry) { public boolean header(){return entry==null;} }
    /** Exact painted list-strip bounds (right/bottom exclusive), shared by paint and pointer input. */
    public record RowBounds(int left,int top,int right,int bottom) {
        public boolean contains(double x,double y){return x>=left&&x<right&&y>=top&&y<bottom;}
    }
    public static RowBounds rowBounds(int x,int top,int width,int viewportHeight,int viewportRow) {
        int rowTop=top+viewportRow*ROW_HEIGHT;
        return new RowBounds(x+3,rowTop,x+width-3,Math.min(rowTop+ROW_HEIGHT,top+viewportHeight));
    }
    public static List<String> groups(List<GhostRolePayloads.Entry> entries) {
        return entries.stream().map(GhostRolePayloads.Entry::roleId).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    public static List<GhostRolePayloads.Entry> members(List<GhostRolePayloads.Entry> entries,String group) {
        return entries.stream().filter(e->e.roleId().equals(group)).sorted(Comparator.comparing(e->e.target().toString())).toList();
    }
    public static int rowCount(List<GhostRolePayloads.Entry> entries,Set<String> expanded) {
        return visibleRows(entries,expanded).size();
    }
    public static List<Row> visibleRows(List<GhostRolePayloads.Entry> entries,Set<String> expanded) {
        List<Row> rows=new ArrayList<>();for(String group:groups(entries)){rows.add(new Row(group,null));if(expanded.contains(group))for(var entry:members(entries,group))rows.add(new Row(group,entry));}return List.copyOf(rows);
    }
    public static int maxScroll(List<GhostRolePayloads.Entry> entries,Set<String> expanded,int visibleRowCapacity) {
        return Math.max(0,rowCount(entries,expanded)-Math.max(0,visibleRowCapacity));
    }
    public static Row rowAt(List<GhostRolePayloads.Entry> entries,Set<String> expanded,int scrollOffset,int viewportRow) {
        int index=scrollOffset+viewportRow;List<Row> rows=visibleRows(entries,expanded);
        return index<0||index>=rows.size()?null:rows.get(index);
    }
    /** Pixel hit lookup matching the fully rendered row bounds; partial trailing rows are not selectable. */
    public static Row rowAtPixel(List<GhostRolePayloads.Entry> entries,Set<String> expanded,int scrollOffset,
                                 int pixelOffset,int contentHeight) {
        if(pixelOffset<0||pixelOffset+ROW_HEIGHT>contentHeight)return null;
        return rowAt(entries,expanded,scrollOffset,pixelOffset/ROW_HEIGHT);
    }
    public static Row rowAtPoint(List<GhostRolePayloads.Entry> entries,Set<String> expanded,int scrollOffset,
                                 int x,int top,int width,int viewportHeight,double pointerX,double pointerY) {
        if(pointerY<top||pointerY>=top+viewportHeight)return null;
        int viewportRow=(int)((pointerY-top)/ROW_HEIGHT);
        if(rowBounds(x,top,width,viewportHeight,viewportRow).bottom()-rowBounds(x,top,width,viewportHeight,viewportRow).top()<ROW_HEIGHT)return null;
        if(!rowBounds(x,top,width,viewportHeight,viewportRow).contains(pointerX,pointerY))return null;
        return rowAt(entries,expanded,scrollOffset,viewportRow);
    }
    public static int actionHeight(int width){return width<92?26:15;}
    /** Chrome width is expressed at the renderer's unscaled pixel size; its origin stays in screen pixels. */
    public static net.minecraft.client.gui.navigation.ScreenRectangle closeBounds(int x,int y,int width){
        return WindowChromeButtons.closeBounds(x,y,width*2,.5f);
    }
    public static int desiredHeight(List<GhostRolePayloads.Entry> entries,Set<String> expanded,boolean selected,boolean status,int width) {
        return TITLE_HEIGHT+CONTENT_GAP+rowCount(entries,expanded)*ROW_HEIGHT+(status?ROW_HEIGHT:0)+(selected?actionHeight(width):0)+CONTENT_GAP;
    }
    public static int width(int viewportWidth){return Math.min(viewportWidth,Math.max(64,Math.min(110,viewportWidth/10)));}
    /** Reconcile content/viewport bounds without repeatedly cancelling a live title drag. */
    public static void relayout(WindowGeometryController geometry,int screenWidth,int screenHeight,int width,int height,boolean viewportChanged){
        if(viewportChanged)geometry.setScreenSize(screenWidth,screenHeight);
        if(!geometry.isPointerCaptured()&&(geometry.width()!=width||geometry.height()!=height))geometry.setBounds(geometry.x(),geometry.y(),width,height);
    }
    public static GhostRolePayloads.Entry selected(List<GhostRolePayloads.Entry> entries,String targetId) {
        if(targetId==null)return null;return entries.stream().filter(e->e.target().toString().equals(targetId)).findFirst().orElse(null);
    }
}
