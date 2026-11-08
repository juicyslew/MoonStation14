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
    public record Layout(int x,int y,int viewportWidth,int viewportHeight,int width,int height,int rowCount){
        public static final int WIDTH=184,HEIGHT=92;
        public static final int MIN_USABLE_WIDTH=112,MIN_USABLE_HEIGHT=89;
        public Layout(int x,int y){this(x,y,1);}
        public Layout(int x,int y,int rowCount){this(x,y,WIDTH,HEIGHT,rowCount);}
        public Layout(int anchorX,int anchorY,int viewportWidth,int viewportHeight){this(anchorX,anchorY,viewportWidth,viewportHeight,1);}
        public Layout(int anchorX,int anchorY,int viewportWidth,int viewportHeight,int rowCount){
            this(clamp(anchorX,Math.max(0,viewportWidth)-Math.min(WIDTH,Math.max(0,viewportWidth))),
                    clamp(anchorY,Math.max(0,viewportHeight)-Math.min(heightFor(rowCount),Math.max(0,viewportHeight))),
                    Math.max(0,viewportWidth),Math.max(0,viewportHeight),Math.min(WIDTH,Math.max(0,viewportWidth)),
                    Math.min(heightFor(rowCount),Math.max(0,viewportHeight)),Math.max(0,rowCount));
        }
        private static int heightFor(int rows){return rows<=0?HEIGHT:HEIGHT+26*(rows-1);}
        private static int clamp(int value,int max){return Math.max(0,Math.min(value,max));}
        public boolean usable(){return rowCount>0&&width>=MIN_USABLE_WIDTH&&height>=MIN_USABLE_HEIGHT+26*(rowCount-1);}
        public Rect window(){return new Rect(x,y,width,height);}
        public Rect action(){return action(0);}
        public Rect action(int row){return new Rect(x+Math.min(8,width),y+Math.min(32+26*row,height),Math.max(0,width-16),Math.min(22,Math.max(0,height-(32+26*row))));}
        public Rect reason(){int top=58+26*Math.max(0,rowCount-1);return new Rect(x+Math.min(8,width),y+Math.min(top,height),Math.max(0,width-16),Math.max(0,height-(top+4)));}
        public Rect close(){return new Rect(x+Math.max(0,width-18),y+Math.min(5,height),Math.min(12,width),Math.min(14,Math.max(0,height-5)));}
        public boolean rectWithinViewport(Rect r){return r.x()>=0&&r.y()>=0&&r.x()+r.width()<=viewportWidth&&r.y()+r.height()<=viewportHeight;}
    }
}
