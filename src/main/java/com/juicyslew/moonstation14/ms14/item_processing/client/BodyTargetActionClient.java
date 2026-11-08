package com.juicyslew.moonstation14.ms14.item_processing.client;

import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionSnapshot;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

/**
 * Client presentation for server-authoritative actions on one aimed loose entity. Minecraft has no SS14
 * cursor entity-picker here: the controlled body's single look-ray nomination is the deliberate adaptation.
 */
public final class BodyTargetActionClient {
    private static final TargetActionWindow WINDOW = new TargetActionWindow();
    private static long nextSequence;
    private static TargetActionClientPolicy.Request pending;
    private static UUID retainedTarget;
    private static long actionSequence;
    private static long requiredRevision;
    private static boolean actionInFlight,refreshAfterAction;
    public record HeldRow(BodyHandActionRequest.Action action,Component label,boolean enabled,Component disabledReason) {
        public HeldRow { java.util.Objects.requireNonNull(action);java.util.Objects.requireNonNull(label); }
    }
    public record HeldContext(UUID body,long epoch,net.minecraft.resources.ResourceLocation dimension,String hand,
                              long revision,String token,Component title,java.util.List<HeldRow> rows) {
        public HeldContext { java.util.Objects.requireNonNull(body);java.util.Objects.requireNonNull(dimension);
            java.util.Objects.requireNonNull(hand);java.util.Objects.requireNonNull(title);rows=java.util.List.copyOf(rows); }
    }
    private BodyTargetActionClient() { }

    /** Invoked by the authenticated snapshot receiver; exact correlation prevents stale menus reopening. */
    public static void receive(BodyTargetActionSnapshot snapshot) {
        var request=pending;
        boolean proof=request!=null&&BodyHandClient.targetActionProofMatches(request.epoch(),request.revision(),request.hand(),request.token());
        var mc=Minecraft.getInstance();
        var dimension=mc.level==null?null:mc.level.dimension().location();
        var aimed=retainedTarget!=null?retainedTarget:BodyHandClient.currentAimedTarget();
        boolean context=request!=null&&BodyHandClient.targetActionContextMatches(request.dimension(),request.target());
        if(!context||!TargetActionClientPolicy.accepts(request,snapshot,BodyHandClient.clientClock(),proof,dimension,aimed))return;
        pending=null;
        if(snapshot.actions().isEmpty()){retainedTarget=null;WINDOW.closeLocal();return;}
        retainedTarget=snapshot.targetId();actionInFlight=false;refreshAfterAction=false;
        WINDOW.show(snapshot);
    }

    public static boolean request(UUID target) {
        var proof=BodyHandClient.targetActionProof();
        if(target==null || proof==null || !BodyHandClient.targetActionAdmissionAllowed()) return false;
        var mc=Minecraft.getInstance();
        long seq=++nextSequence; if(seq<=0) { nextSequence=1; seq=1; }
        if(mc.level==null)return false;
        // Retire any prior held/world presentation before installing this request.
        // cancel() clears pending, so it must precede assignment and transmission.
        cancel();
        retainedTarget=target;
        pending=new TargetActionClientPolicy.Request(proof.epoch(),seq,proof.revision(),proof.hand(),proof.token(),target,
                mc.level.dimension().location(),BodyHandClient.clientClock()+TargetActionClientPolicy.QUERY_TTL_TICKS);
        PacketDistributor.sendToServer(new BodyTargetActionQuery(proof.epoch(),seq,proof.revision(),proof.hand(),proof.token(),target));
        return true;
    }
    public static void cancel(){ pending=null;retainedTarget=null;actionInFlight=false;refreshAfterAction=false;WINDOW.closeLocal(); }
    public static void actionQueued(long sequence,UUID target){if(target!=null){actionSequence=sequence;retainedTarget=target;actionInFlight=true;}}
    public static void actionResult(long sequence,boolean correlated,boolean success,long authoritativeRevision){
        if(!actionInFlight||sequence!=actionSequence)return;
        actionInFlight=false;
        if(!correlated||!success||authoritativeRevision<0){cancel();return;}
        requiredRevision=authoritativeRevision;refreshAfterAction=true;
    }
    /** Presents held-item actions in the same pointer overlay and geometry as the world-target menu. */
    public static boolean showHeldGunMenu(HeldContext context) {
        if(context==null||!heldContextCurrent(context))return false;
        pending=null;
        WINDOW.showHeld(context);return WINDOW.held==context;
    }
    private static boolean heldContextCurrent(HeldContext c) {
        var mc=Minecraft.getInstance();var s=BodyHandClient.confirmedOwnedHandSnapshot();
        return mc.level!=null&&s!=null&&s.bodyId().equals(c.body())&&s.epoch()==c.epoch()
                &&s.revision()==c.revision()&&s.activeHand().equals(c.hand())
                &&mc.level.dimension().location().equals(c.dimension())
                &&BodyHandClient.targetActionProofMatches(c.epoch(),c.revision(),c.hand(),c.token());
    }
    public static void dismissOnOutside(double x,double y){
        if((pending!=null||WINDOW.visible())&&!WINDOW.contains(x,y))cancel();
    }
    public static void tick(){
        var p=pending;
        if(p!=null&&(BodyHandClient.clientClock()>p.expiresAt()||!BodyHandClient.targetActionProofMatches(p.epoch(),p.revision(),p.hand(),p.token())
                ||!BodyHandClient.targetActionContextMatches(p.dimension(),p.target())))pending=null;
        var mc=Minecraft.getInstance();
        if(refreshAfterAction&&retainedTarget!=null){
            if(mc.level==null||!BodyHandClient.targetActionContextMatches(mc.level.dimension().location(),retainedTarget))cancel();
            else {var proof=BodyHandClient.targetActionProof();var old=WINDOW.state;
                if(proof!=null&&old!=null&&proof.epoch()!=old.bodyEpoch())cancel();
                else if(proof!=null&&old!=null&&TargetActionClientPolicy.revisionReady(requiredRevision,proof.revision())) {
                    UUID target=retainedTarget;refreshAfterAction=false;request(target);
                }
            }
        }
        if(WINDOW.visible()&&(!WINDOW.layout().usable()||(WINDOW.held!=null?!heldContextCurrent(WINDOW.held):
                mc.level==null||(!actionInFlight&&!refreshAfterAction&&!BodyHandClient.targetActionProofMatches(WINDOW.state.bodyEpoch(),WINDOW.state.expectedRevision(),WINDOW.state.handId(),WINDOW.state.expectedToken()))
                ||!BodyHandClient.targetActionContextMatches(WINDOW.dimension,WINDOW.state.targetId()))))WINDOW.closeLocal();
    }
    private static void dispatch(BodyTargetActionSnapshot state, BodyTargetActionSnapshot.ActionId kind) {
        var selected=TargetActionClientPolicy.dispatchAction(state,kind,true);
        if(selected.isEmpty() || !BodyHandClient.targetActionProofMatches(state.bodyEpoch(),state.expectedRevision(),state.handId(),state.expectedToken())
                || Minecraft.getInstance().level==null||!BodyHandClient.targetActionContextMatches(WINDOW.dimension,state.targetId())) { cancel(); return; }
         BodyHandClient.dispatchTargetAction(state,kind);
         if(kind==BodyTargetActionSnapshot.ActionId.BUTCHER)cancel();
    }

    private static final class TargetActionWindow implements com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow {
        private BodyTargetActionSnapshot state; private HeldContext held; private int x,y;private net.minecraft.resources.ResourceLocation dimension;
        private com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController geometry;
         void show(BodyTargetActionSnapshot next){var m=Minecraft.getInstance();if(m.level==null)return;var guiW=m.getWindow().getGuiScaledWidth();var guiH=m.getWindow().getGuiScaledHeight();int anchorX=(int)(m.mouseHandler.xpos()*guiW/m.getWindow().getScreenWidth()),anchorY=(int)(m.mouseHandler.ypos()*guiH/m.getWindow().getScreenHeight());boolean footer=next.actions().stream().anyMatch(r->!r.enabled()&&r.disabledReason()!=null);var initial=new TargetActionClientPolicy.Layout(anchorX,anchorY,guiW,guiH,next.actions().size(),TargetActionClientPolicy.Layout.WIDTH,footer);if(!initial.usable()){closeLocal();return;}held=null;state=next;dimension=m.level.dimension().location();setGeometry(m,initial);}
         void showHeld(HeldContext next){var m=Minecraft.getInstance();if(m.level==null)return;var guiW=m.getWindow().getGuiScaledWidth();var guiH=m.getWindow().getGuiScaledHeight();int anchorX=(int)(m.mouseHandler.xpos()*guiW/m.getWindow().getScreenWidth()),anchorY=(int)(m.mouseHandler.ypos()*guiH/m.getWindow().getScreenHeight());boolean footer=next.rows().stream().anyMatch(r->!r.enabled()&&r.disabledReason()!=null);var initial=new TargetActionClientPolicy.Layout(anchorX,anchorY,guiW,guiH,next.rows().size(),TargetActionClientPolicy.Layout.WIDTH,footer);if(!initial.usable()){closeLocal();return;}state=null;held=next;dimension=next.dimension();setGeometry(m,initial);}
         private void setGeometry(Minecraft m,TargetActionClientPolicy.Layout initial){x=initial.x();y=initial.y();geometry=new com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController(initial.viewportWidth(),initial.viewportHeight(),x,y,initial.width(),initial.height(),1,1,false);PointerOverlayRegistry.register(this);if(m.screen==null||!PointerOverlayRegistry.allowsHost(m.screen))BodyHandClient.openSharedPointerWorkspace();}
         void closeLocal(){if(visible())PointerOverlayRegistry.unregister(this);if(geometry!=null)geometry.cancelPointer();geometry=null;state=null;held=null;}
         public boolean visible(){return state!=null||held!=null;} public boolean pointerAvailable(){return com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.hostPointerAvailable(Minecraft.getInstance().mouseHandler.isMouseGrabbed());}
          private TargetActionClientPolicy.Layout layout(){var mc=Minecraft.getInstance();int sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight();int count=state!=null?state.actions().size():held==null?0:held.rows().size();var rows=held!=null?held.rows().stream().map(r->new TargetActionWindowRenderer.Row(r.label(),r.enabled(),r.disabledReason(),(net.minecraft.resources.ResourceLocation)null)).toList():state==null?java.util.List.<TargetActionWindowRenderer.Row>of():state.actions().stream().map(r->new TargetActionWindowRenderer.Row(r.label(),r.enabled(),r.disabledReason(),r.id()==BodyTargetActionSnapshot.ActionId.BUTCHER?TargetActionWindowRenderer.CUTLERY:null)).toList();boolean footer=rows.stream().anyMatch(r->!r.enabled()&&r.disabledReason()!=null);var title=held!=null?held.title():state==null?Component.empty():state.targetName();int wanted=TargetActionWindowRenderer.measuredWidth(title,rows,r->mc.font.width(r));var measured=new TargetActionClientPolicy.Layout(x,y,sw,sh,count,wanted,footer);int h=TargetActionClientPolicy.Layout.heightFor(count,footer);if(geometry==null)return measured;if(geometry.width()!=measured.width()||geometry.height()!=Math.min(h,sh)){geometry.setScreenSize(sw,sh);geometry.setBounds(geometry.x(),geometry.y(),measured.width(),Math.min(h,sh));}x=geometry.x();y=geometry.y();return new TargetActionClientPolicy.Layout(x,y,sw,sh,geometry.width(),geometry.height(),count,footer);}
        public boolean contains(double px,double py){return visible()&&layout().usable()&&layout().window().contains(px,py);}
        public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int w,int h){if(!visible())return;var p=new com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint(g,Minecraft.getInstance().font);if(state!=null)TargetActionWindowRenderer.paint(p,state,layout(),mx,my);else TargetActionWindowRenderer.paint(p,held.title(),held.rows().stream().map(r->new TargetActionWindowRenderer.Row(r.label(),r.enabled(),r.disabledReason(),(net.minecraft.resources.ResourceLocation)null)).toList(),layout(),mx,my);}
          public boolean mouseClicked(double px,double py,int button){if(button!=0)return false;var l=layout();var press=TargetActionClientPolicy.press(l,px,py);if(press.kind()==TargetActionClientPolicy.PressKind.OUTSIDE)return false;PointerOverlayRegistry.bringToFront(this);if(press.kind()==TargetActionClientPolicy.PressKind.CLOSE){cancel();return true;}if(press.kind()==TargetActionClientPolicy.PressKind.TITLE){geometry.beginPointer(px,py,button,TargetActionClientPolicy.Layout.TITLE_HEIGHT,18);return true;}if(press.kind()==TargetActionClientPolicy.PressKind.ACTION){if(actionInFlight)return true;int i=press.actionIndex();if(held!=null){var c=held;var row=c.rows().get(i);if(!heldContextCurrent(c)){cancel();return true;}if(row.enabled()&&BodyHandClient.dispatchHeldGunAction(row.action(),c.epoch(),c.revision(),c.hand(),c.token()))cancel();return true;}dispatch(state,state.actions().get(i).id());return true;}return true;}
         public boolean mouseDragged(double px,double py){if(geometry==null||!geometry.dragPointer(px,py))return false;x=geometry.x();y=geometry.y();return true;}public boolean mouseReleased(int b){return geometry!=null&&geometry.releasePointer(b);}public boolean ownsPointerCapture(){return geometry!=null&&geometry.isPointerCaptured();}public boolean mouseScrolled(double px,double py,double d){return contains(px,py);}
        public boolean keyPressed(int key,int scan,int mods){if(key==256){cancel();return true;}return false;}public boolean charTyped(char c,int m){return false;}public boolean isSearchFocused(){return false;}public void clearTextFocus(){}public void resetOwnedContext(){cancel();}
    }
}
