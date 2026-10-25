package com.juicyslew.moonstation14.ms14.construction.client;

/** Single source of geometry for the compact browser painting, viewport and pointer hits. */
public final class ConstructionBrowserLayout {
    public static final float CONTENT_SCALE=.5f;
    public static final int HEADER_HEIGHT=13, ROW_HEIGHT=7;
    public static final int GUIDE_TOP_MARGIN=3;
    public static final int GUIDE_TITLE_LOGICAL_HEIGHT=15, GUIDE_ROW_LOGICAL_HEIGHT=10;
    public static final int GUIDE_SHADOW_GUARD_LOGICAL=1;
    public record WindowBounds(int x,int y,int width,int height,int minWidth,int minHeight) { }
    public record Rect(int x,int y,int width,int height) { public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;} }
    public record Layout(int x,int y,int width,int height,int leftWidth,int contentTop,int listHeight,
                         Rect search,Rect list,Rect instructions,Rect actions,Rect queue,Rect status,Rect close) { }
    public record ItemActions(Rect minus,Rect quantity,Rect plus,Rect craft) { }
    private ConstructionBrowserLayout() { }
    /** Shared preferred bounds and controller minimums; constraints are applied before centering. */
    public static WindowBounds preferredBounds(int screenWidth,int screenHeight) {
        if(screenWidth<1||screenHeight<1)throw new IllegalArgumentException("screen dimensions must be positive");
        int width=Math.min(240,Math.min(screenWidth,Math.max(Math.min(144,screenWidth),(int)(screenWidth*.34))));
        int preferredHeight=Math.min(140,Math.max(1,(int)(screenHeight*.36)));
        int areaHeight=Math.max(1,(int)(((long)screenWidth*screenHeight)/8/width));
        int height=Math.min(preferredHeight,areaHeight);
        int minWidth=Math.min(144,screenWidth),minHeight=Math.min(92,height);
        width=Math.max(minWidth,width);height=Math.max(minHeight,height);
        return new WindowBounds((screenWidth-width)/2,(screenHeight-height)/2,width,height,minWidth,minHeight);
    }
    public static Layout calculate(int x,int y,int width,int height,boolean selected,boolean item,boolean queued,boolean status) {
        int left=Math.max(75,Math.min(85,(int)(width*.34))), top=y+HEADER_HEIGHT+GUIDE_TOP_MARGIN;
        int leftX=x+5, leftWidth=left-7, rightX=x+left+5, rightWidth=Math.max(1,width-left-12);
        int listTop=top+12, listHeight=Math.max(0,height-(listTop-y)-5);
        int cursor=y+height-5;
        Rect s=new Rect(rightX,cursor,rightWidth,0),q=new Rect(rightX,cursor,rightWidth,0),act=new Rect(rightX,cursor,rightWidth,0);
        if(selected){int ay=cursor-9;act=new Rect(rightX,ay,item?rightWidth:Math.min(rightWidth,40),9);cursor=ay-2;}
        if(status){int sy=cursor-7;s=new Rect(rightX,sy,rightWidth,7);cursor=sy-1;}
        if(queued){int qy=cursor-8;q=new Rect(rightX,qy,rightWidth,8);cursor=qy-2;}
        int guideBottom=Math.max(top,cursor);
        return new Layout(x,y,width,height,left,leftX, listHeight,
                new Rect(leftX,top,leftWidth,10),new Rect(leftX,listTop,leftWidth,listHeight),
                new Rect(rightX,top,rightWidth,Math.max(0,guideBottom-top)),act,q,s,
                new Rect(x+width-14,y+2,9,9));
    }
    public static ItemActions itemActions(Layout l) {
        Rect r=l.actions(); int small=8,gap=1;
        return new ItemActions(new Rect(r.x(),r.y(),small,r.height()),new Rect(r.x()+small+gap,r.y(),12,r.height()),
                new Rect(r.x()+small+gap+12+gap,r.y(),small,r.height()),
                new Rect(r.x()+small+gap+12+gap+small+gap,r.y(),24,r.height()));
    }
    public static int visibleRows(Layout l){return Math.max(1,Math.min(24,l.listHeight()/ROW_HEIGHT));}
    /** Guide rows start 15 logical pixels below the title and advance by 10 logical pixels. */
    public static int guideVisibleRows(Layout l,int fontLineHeight){
        int logicalHeight=Math.max(0,(l.instructions().height()-GUIDE_TOP_MARGIN)*2);
        int firstBaseline=GUIDE_TITLE_LOGICAL_HEIGHT;
        int finalGlyphExtent=fontLineHeight+GUIDE_SHADOW_GUARD_LOGICAL;
        return Math.max(0,logicalHeight<firstBaseline+finalGlyphExtent?0:
                1+(logicalHeight-firstBaseline-finalGlyphExtent)/GUIDE_ROW_LOGICAL_HEIGHT);
    }
    public static int maxScroll(int totalLines,int visibleLines){return Math.max(0,totalLines-Math.max(0,visibleLines));}
}

