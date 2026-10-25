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
    private BodyTargetActionClient() { }

    /** Invoked by the authenticated snapshot receiver; exact correlation prevents stale menus reopening. */
    public static void receive(BodyTargetActionSnapshot snapshot) {
        var request=pending;
        boolean proof=request!=null&&BodyHandClient.targetActionProofMatches(request.epoch(),request.revision(),request.hand(),request.token());
        var mc=Minecraft.getInstance();
        var dimension=mc.level==null?null:mc.level.dimension().location();
        var aimed=BodyHandClient.currentAimedTarget();
        boolean context=request!=null&&BodyHandClient.targetActionContextMatches(request.dimension(),request.target());
        if(!context||!TargetActionClientPolicy.accepts(request,snapshot,BodyHandClient.clientClock(),proof,dimension,aimed))return;
        pending=null;
        if(snapshot.actions().isEmpty())return;
        WINDOW.show(snapshot);
    }

    public static boolean request(UUID target) {
        var proof=BodyHandClient.targetActionProof();
        if(target==null || proof==null || !BodyHandClient.targetActionAdmissionAllowed()) return false;
        var mc=Minecraft.getInstance();
        long seq=++nextSequence; if(seq<=0) { nextSequence=1; seq=1; }
        if(mc.level==null)return false;
        pending=new TargetActionClientPolicy.Request(proof.epoch(),seq,proof.revision(),proof.hand(),proof.token(),target,
                mc.level.dimension().location(),BodyHandClient.clientClock()+TargetActionClientPolicy.QUERY_TTL_TICKS);
        PacketDistributor.sendToServer(new BodyTargetActionQuery(proof.epoch(),seq,proof.revision(),proof.hand(),proof.token(),target));
        return true;
    }
    public static void cancel(){ pending=null; WINDOW.closeLocal(); }
    public static void dismissOnOutside(double x,double y){
        if((pending!=null||WINDOW.visible())&&!WINDOW.contains(x,y))cancel();
    }
    public static void tick(){
        var p=pending;
        if(p!=null&&(BodyHandClient.clientClock()>p.expiresAt()||!BodyHandClient.targetActionProofMatches(p.epoch(),p.revision(),p.hand(),p.token())
                ||!BodyHandClient.targetActionContextMatches(p.dimension(),p.target())))pending=null;
        var mc=Minecraft.getInstance();
        if(WINDOW.state!=null&&(mc.level==null||!WINDOW.layout().usable()||!BodyHandClient.targetActionProofMatches(WINDOW.state.bodyEpoch(),WINDOW.state.expectedRevision(),WINDOW.state.handId(),WINDOW.state.expectedToken())
                ||!BodyHandClient.targetActionContextMatches(WINDOW.dimension,WINDOW.state.targetId())))WINDOW.closeLocal();
    }
    private static void dispatch(BodyTargetActionSnapshot state, BodyTargetActionSnapshot.ActionId kind) {
        var selected=TargetActionClientPolicy.dispatchAction(state,kind,true);
        if(selected.isEmpty() || !BodyHandClient.targetActionProofMatches(state.bodyEpoch(),state.expectedRevision(),state.handId(),state.expectedToken())
                || Minecraft.getInstance().level==null||!BodyHandClient.targetActionContextMatches(WINDOW.dimension,state.targetId())) { cancel(); return; }
        BodyHandClient.dispatchTargetAction(state,kind);
        cancel();
    }

    private static final class TargetActionWindow implements com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow {
        private BodyTargetActionSnapshot state; private int x,y;private net.minecraft.resources.ResourceLocation dimension;
        void show(BodyTargetActionSnapshot next){var m=Minecraft.getInstance();if(m.level==null)return;var guiW=m.getWindow().getGuiScaledWidth();var guiH=m.getWindow().getGuiScaledHeight();int anchorX=(int)(m.mouseHandler.xpos()*guiW/m.getWindow().getScreenWidth()),anchorY=(int)(m.mouseHandler.ypos()*guiH/m.getWindow().getScreenHeight());var initial=new TargetActionClientPolicy.Layout(anchorX,anchorY,guiW,guiH,next.actions().size());if(!initial.usable()){closeLocal();return;}state=next;dimension=m.level.dimension().location();x=initial.x();y=initial.y();PointerOverlayRegistry.register(this);BodyHandClient.openSharedPointerWorkspace();}
        void closeLocal(){if(state!=null)PointerOverlayRegistry.unregister(this);state=null;}
        public boolean visible(){return state!=null;} public boolean pointerAvailable(){return !Minecraft.getInstance().mouseHandler.isMouseGrabbed();}
        private TargetActionClientPolicy.Layout layout(){var mc=Minecraft.getInstance();return new TargetActionClientPolicy.Layout(x,y,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),state==null?0:state.actions().size());}
        public boolean contains(double px,double py){return visible()&&layout().usable()&&layout().window().contains(px,py);}
        public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int w,int h){if(state==null)return;var p=new com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint(g,Minecraft.getInstance().font);TargetActionWindowRenderer.paint(p,state,layout(),mx,my);}
        public boolean mouseClicked(double px,double py,int button){if(button!=0||!contains(px,py))return false;if(layout().close().contains(px,py)){cancel();return true;}for(int i=0;i<state.actions().size();i++)if(layout().action(i).contains(px,py)){dispatch(state,state.actions().get(i).id());return true;}return true;}
        public boolean mouseDragged(double px,double py){return false;}public boolean mouseReleased(int b){return false;}public boolean mouseScrolled(double px,double py,double d){return contains(px,py);}
        public boolean keyPressed(int key,int scan,int mods){if(key==256){cancel();return true;}return false;}public boolean charTyped(char c,int m){return false;}public boolean isSearchFocused(){return false;}public void clearTextFocus(){}public void resetOwnedContext(){cancel();}
    }
}
