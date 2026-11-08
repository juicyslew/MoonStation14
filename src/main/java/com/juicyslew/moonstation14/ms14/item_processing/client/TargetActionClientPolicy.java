package com.juicyslew.moonstation14.ms14.item_processing.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionSnapshot;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

/** Pure pending-query correlation and semantic bounds shared by production input/paint. */
public final class TargetActionClientPolicy {
    public static final long QUERY_TTL_TICKS=100;
    private TargetActionClientPolicy() { }
    public record Request(long epoch,long sequence,long revision,String hand,String token,UUID target,
                          ResourceLocation dimension,long expiresAt) { }
    public static boolean accepts(Request r,BodyTargetActionSnapshot s,long now,boolean proofCurrent,
                                  ResourceLocation currentDimension,UUID currentlyAimedTarget){
        return r!=null&&s!=null&&now<=r.expiresAt()&&proofCurrent&&Objects.equals(r.dimension(),currentDimension)
                &&Objects.equals(r.target(),currentlyAimedTarget)
                &&s.bodyEpoch()==r.epoch()&&s.sequence()==r.sequence()&&s.expectedRevision()==r.revision()
                &&s.handId().equals(r.hand())&&Objects.equals(s.expectedToken(),r.token())&&s.targetId().equals(r.target());
    }
    public static boolean dispatchesButcher(boolean hasButcher,boolean enabled,boolean actionHit){
        return hasButcher&&enabled&&actionHit;
    }
    /** Mutation refresh barrier: action result revision is authoritative, unchanged world-only actions are valid. */
    public static boolean revisionReady(long required,long confirmed){return required>=0&&confirmed>=required;}
    public static boolean retainedSourceContextMatches(boolean bodyAdmitted,boolean sourcePresent,boolean withinReach){
        return bodyAdmitted&&sourcePresent&&withinReach;
    }
    public static java.util.Optional<com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action>
    dispatchAction(BodyTargetActionSnapshot snapshot, BodyTargetActionSnapshot.ActionId kind, boolean actionHit) {
        if (snapshot == null || kind == null || !actionHit) return java.util.Optional.empty();
        return snapshot.actions().stream().filter(entry -> entry.id() == kind && entry.enabled())
                .map(entry -> kind.requestAction()).findFirst();
    }
    public record Rect(int x,int y,int width,int height){
        public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
        public boolean within(Rect outer){return x>=outer.x&&y>=outer.y&&x+width<=outer.x+outer.width&&y+height<=outer.y+outer.height;}
    }
    public enum PressKind { OUTSIDE, CLOSE, TITLE, ACTION, BODY }
    public record Press(PressKind kind,int actionIndex) { }
    /** Shared hit decision used by the production overlay before invoking its existing geometry/action owners. */
    public static Press press(Layout l,double px,double py){
        if(l==null||!l.window().contains(px,py))return new Press(PressKind.OUTSIDE,-1);
        if(l.close().contains(px,py))return new Press(PressKind.CLOSE,-1);
        var title=new Rect(l.x(),l.y(),l.width(),Math.min(Layout.TITLE_HEIGHT,l.height()));
        if(title.contains(px,py))return new Press(PressKind.TITLE,-1);
        for(int i=0;i<l.rowCount();i++)if(l.action(i).contains(px,py))return new Press(PressKind.ACTION,i);
        return new Press(PressKind.BODY,-1);
    }
    public record Layout(int x,int y,int viewportWidth,int viewportHeight,int width,int height,int rowCount,boolean footer){
        /** Compact context-menu footprint; action rows remain shared 22px buttons. */
        public static final int WIDTH=148,HEIGHT=47,TITLE_HEIGHT=27,ROW_HEIGHT=20,ROW_GAP=1,FOOTER_HEIGHT=20;
        public static final int MIN_USABLE_WIDTH=106,MIN_USABLE_HEIGHT=47;
        public Layout(int x,int y){this(x,y,1);}
        public Layout(int x,int y,int rowCount){this(x,y,WIDTH,HEIGHT,rowCount);}
        public Layout(int anchorX,int anchorY,int viewportWidth,int viewportHeight){this(anchorX,anchorY,viewportWidth,viewportHeight,1);}
        public Layout(int anchorX,int anchorY,int viewportWidth,int viewportHeight,int rowCount){this(anchorX,anchorY,viewportWidth,viewportHeight,rowCount,WIDTH,false);}
        public Layout(int anchorX,int anchorY,int viewportWidth,int viewportHeight,int rowCount,int requestedWidth,boolean footer){
            this(clamp(anchorX,Math.max(0,viewportWidth)-Math.min(Math.max(MIN_USABLE_WIDTH,requestedWidth),Math.max(0,viewportWidth))),
                    clamp(anchorY,Math.max(0,viewportHeight)-Math.min(heightFor(rowCount,footer),Math.max(0,viewportHeight))),
                    Math.max(0,viewportWidth),Math.max(0,viewportHeight),Math.min(Math.max(MIN_USABLE_WIDTH,requestedWidth),Math.max(0,viewportWidth)),
                    Math.min(heightFor(rowCount,footer),Math.max(0,viewportHeight)),Math.max(0,rowCount),footer);
        }
        public Layout(int x,int y,int viewportWidth,int viewportHeight,int width,int height,int rowCount){this(x,y,viewportWidth,viewportHeight,width,height,rowCount,false);}
        public static int heightFor(int rows,boolean footer){return TITLE_HEIGHT+Math.max(0,rows)*ROW_HEIGHT+(footer?FOOTER_HEIGHT:0);}
        private static int clamp(int value,int max){return Math.max(0,Math.min(value,max));}
        public boolean usable(){return rowCount>0&&width>=MIN_USABLE_WIDTH&&height>=heightFor(rowCount,footer);}
        public Rect window(){return new Rect(x,y,width,height);}
        public Rect action(){return action(0);}
        public Rect action(int row){int top=TITLE_HEIGHT+ROW_HEIGHT*row;return new Rect(x+3,y+top,Math.max(0,width-6),Math.min(ROW_HEIGHT-ROW_GAP,Math.max(0,height-top)));}
        public Rect reason(){int top=TITLE_HEIGHT+ROW_HEIGHT*rowCount;return new Rect(x+3,Math.min(y+top,y+height),Math.max(0,width-6),Math.min(FOOTER_HEIGHT,Math.max(0,height-top)));}
        public Rect close(){return new Rect(x+Math.max(0,width-18),y+Math.min(5,height),Math.min(12,width),Math.min(14,Math.max(0,height-5)));}
        public boolean rectWithinViewport(Rect r){return r.x()>=0&&r.y()>=0&&r.x()+r.width()<=viewportWidth&&r.y()+r.height()<=viewportHeight;}
    }
}
