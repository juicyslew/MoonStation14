package com.juicyslew.moonstation14.ms14.singularity.pa.control.client;

import com.juicyslew.moonstation14.ms14.singularity.pa.control.*;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** PA-only session cache and ACTIVATE_WORLD correlation; snapshots are never client authority. */
public final class PaControlWorkspace {
    public static final PaControlWorkspace INSTANCE = new PaControlWorkspace();
    private record Pending(long correlation, UUID body, long epoch, ResourceLocation dimension, BlockPos target,
                           UUID machine, ResourceLocation profile, long deadline) { }
    private final Map<UUID, PaControlSnapshot> snapshots = new HashMap<>();
    private final Set<UUID> retired = new HashSet<>();
    private Pending pending;
    private UUID selected;
    private long actionSequence;
    private long latestAction;
    private UUID actionSession;
    private boolean actionPending;
    private WindowGeometryController geometry;
    private final Overlay overlay = new Overlay();
    PaControlWorkspace() { }
    public synchronized Overlay overlay() { return overlay; }
    public synchronized PaControlSnapshot session() { return selected == null ? null : snapshots.get(selected); }

    public synchronized void expectOpen(long correlation, UUID body, long epoch, ResourceLocation dimension, BlockPos target,
            UUID machine, ResourceLocation profile, long tick) {
        pending = new Pending(correlation, body, epoch, dimension, target.immutable(), machine, profile, tick + 80);
    }
    public synchronized void receive(PaControlSnapshot s) {
        if (s == null || retired.contains(s.sessionId())) return;
        if (s.closed()) {
            receiveClosed(s);
            return;
        }
        PaControlSnapshot previous = snapshots.get(s.sessionId());
        if (!acceptSnapshot(previous, s, retired.contains(s.sessionId()),
                Objects.equals(actionSession, s.sessionId()), latestAction)) return;
        snapshots.put(s.sessionId(), s);
        Pending p = pending;
        var proof = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.currentHandProof();
        boolean authorityCurrent = proof != null && proof.snapshot().bodyId().equals(s.bodyId())
                && proof.snapshot().epoch() == s.authorityEpoch() && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.dimension().location().equals(s.dimension());
        if (p != null && authorityCurrent) activateOpen(s);
        if (Objects.equals(selected, s.sessionId()) && actionPending && s.requestId() == latestAction) actionPending = false;
    }
    /** A close retires only its session; its request id is not in the open-correlation namespace. */
    synchronized void receiveClosed(PaControlSnapshot s) {
        snapshots.remove(s.sessionId()); retired.add(s.sessionId());
        if (Objects.equals(selected, s.sessionId())) { selected=null; actionPending=false; PointerOverlayRegistry.unregister(overlay); }
    }
    synchronized Long pendingCorrelation() { return pending == null ? null : pending.correlation; }
    synchronized boolean activateOpen(PaControlSnapshot s) {
        Pending p=pending;
        if(p==null||!matchesOpen(p.correlation,p.body,p.epoch,p.dimension,p.target,p.machine,p.profile,s))return false;
        snapshots.put(s.sessionId(),s);selected=s.sessionId();pending=null;actionPending=false;
        PointerOverlayRegistry.register(overlay);PointerOverlayRegistry.bringToFront(overlay);
        return true;
    }
    public static boolean acceptSnapshot(PaControlSnapshot previous, PaControlSnapshot incoming, boolean isRetired,
            boolean actionIssuedForSession, long latestActionId) {
        if (incoming == null || isRetired || previous != null && incoming.revision() < previous.revision()) return false;
        if (actionIssuedForSession) return incoming.requestId() >= latestActionId;
        return previous == null || incoming.revision() > previous.revision() || incoming.requestId() == previous.requestId()
                || incoming.revision() == previous.revision() && incoming.requestId() == -1;
    }
    public static int nextStrengthLevel(int current, int maximum) {
        int max = Math.max(0, Math.min(4, maximum));
        return current < 0 || current >= max ? 0 : current + 1;
    }
    public static boolean matchesOpen(long correlation, UUID body, long epoch, ResourceLocation dimension,
            BlockPos target, UUID machine, ResourceLocation profile, PaControlSnapshot s) {
        return s != null && target != null && s.requestId() == correlation && s.accepted()
                && Objects.equals(body, s.bodyId()) && epoch == s.authorityEpoch()
                && Objects.equals(dimension, s.dimension()) && target.getX() == s.x()
                && target.getY() == s.y() && target.getZ() == s.z()
                && Objects.equals(machine, s.machineId()) && Objects.equals(profile, s.profileId());
    }
    public synchronized void tick(UUID body, long epoch, ResourceLocation dimension, long tick) {
        if (pending != null && (tick > pending.deadline || body == null || !pending.body.equals(body)
                || pending.epoch != epoch || !pending.dimension.equals(dimension))) pending = null;
        PaControlSnapshot s = session();
        if (s != null && (body == null || !s.bodyId().equals(body) || s.authorityEpoch() != epoch || !s.dimension().equals(dimension))) closeLocal(false);
    }
    public synchronized boolean enabled() { return session() != null && !actionPending; }
    public synchronized boolean send(PaControlRequest.Operation op, boolean enabled, int strength) {
        PaControlSnapshot s = session();
        if (s == null || actionPending || op == null) return false;
        if (op == PaControlRequest.Operation.ENABLE && (!s.assembled() || !s.apcAvailable())) return false;
        if (op == PaControlRequest.Operation.STRENGTH && (!s.assembled() || !s.enabled() || !s.apcAvailable()
                || strength < 0 || strength > s.maximumStrength())) return false;
        if (op == PaControlRequest.Operation.RESCAN && !s.apcAvailable()) return false;
        actionSequence = actionSequence == Long.MAX_VALUE ? 1 : actionSequence + 1;
        latestAction = actionSequence; actionSession = s.sessionId(); actionPending = true;
        PaControlNetworking.requestAction(new PaControlRequest(s.sessionId(), latestAction, s.revision(), op, enabled, strength));
        return true;
    }
    public synchronized void closeLocal(boolean send) {
        PaControlSnapshot s = session(); if (s != null) { retired.add(s.sessionId()); snapshots.remove(s.sessionId()); if (send) sendCloseOnce(s); }
        clearLocal(true);
    }
    private void sendCloseOnce(PaControlSnapshot s) {
        actionSequence = actionSequence == Long.MAX_VALUE ? 1 : actionSequence + 1;
        PaControlNetworking.requestAction(new PaControlRequest(s.sessionId(), actionSequence, s.revision(), PaControlRequest.Operation.CLOSE, false, s.strength()));
    }
    private void clearLocal(boolean unregister) { selected = null; actionPending = false; pending = null; if (unregister) PointerOverlayRegistry.unregister(overlay); }
    public synchronized void clear() { snapshots.clear(); retired.clear(); clearLocal(true); }

    public final class Overlay implements com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow {
        private static final int W=250,H=184,S=2;
        @Override public boolean visible(){return session()!=null;}
        void ensureGeometry(int width,int height){int sw=Math.max(1,width),sh=Math.max(1,height),ww=Math.min(W/S,sw),hh=Math.min(H/S,sh);if(geometry==null)geometry=new WindowGeometryController(sw,sh,(sw-ww)/2,(sh-hh)/2,ww,hh,1,1,false);else if(geometry.width()!=ww||geometry.height()!=hh||sw!=lastWidth||sh!=lastHeight){geometry.setScreenSize(sw,sh);geometry.setBounds(geometry.x(),geometry.y(),ww,hh);}lastWidth=sw;lastHeight=sh;}
        WindowGeometryController geometry(){return geometry;}
        private int lastWidth,lastHeight;
        @Override public boolean contains(double px,double py){return visible()&&geometry!=null&&px>=geometry.x()&&py>=geometry.y()&&px<geometry.x()+geometry.width()&&py<geometry.y()+geometry.height();}
        @Override public void renderIntoGui(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial,int width,int height){
            ensureGeometry(width,height);int x=geometry.x(),y=geometry.y();PaControlSnapshot s=session();if(s==null)return;
            var font=Minecraft.getInstance().font;int lx=(mx-x)*S,ly=(my-y)*S;
            g.pose().pushPose();try{g.pose().translate(x,y,0);g.pose().scale(.5f,.5f,1);
                com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer.window(g,font,0,0,W,H,net.minecraft.network.chat.Component.translatable("gui.moonstation14.pa.title"));
                com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer.insetPanel(g,6,26,W-12,58);
                g.drawString(font,tr(s.assembled()?"gui.moonstation14.pa.operational":"gui.moonstation14.pa.incomplete"),10,29,s.assembled()?0xff70d080:0xffd0a050,true);
                g.drawString(font,tr("gui.moonstation14.pa.draw")+": "+(s.assembled()?watts(s.powerDrawWatts()):tr("gui.moonstation14.pa.not_applicable"))+"   "+tr("gui.moonstation14.pa.received")+": "+watts(s.receivedWatts()),10,44,0xffdddddd,true);
                g.drawString(font,tr("gui.moonstation14.pa.apc")+": "+tr(s.apcAvailable()?"gui.moonstation14.pa.available":"gui.moonstation14.pa.unavailable"),10,59,0xffbbbbbb,true);
                String[] roles={"control","end","fuel","power","port","fore","starboard"};
                drawRole(g,font,"control",0,true);
                for(int i=0;i<s.partRoles().size();i++){String role=s.partRoles().get(i);int slot=Arrays.asList(roles).indexOf(role);if(slot<0)continue;int bx=10+(slot%4)*58,by=88+(slot/4)*22;
                    drawRole(g,font,role,slot,s.present()[i]);}
                boolean assembled=s.assembled(),available=s.apcAvailable(),ready=enabled();
                button(g,tr("gui.moonstation14.pa.on"),10,140,40,20,lx,ly,assembled&&available&&ready&&!s.enabled());
                button(g,tr("gui.moonstation14.pa.off"),54,140,40,20,lx,ly,assembled&&available&&ready&&s.enabled());
                button(g,tr("gui.moonstation14.pa.rescan"),100,140,56,20,lx,ly,ready&&available);
                button(g,tr("gui.moonstation14.pa.strength")+": "+strengthLabel(s.strength()),160,140,82,20,lx,ly,assembled&&s.enabled()&&available&&ready);
                var close=WindowChromeButtons.closeBounds(0,0,W,1f);
                button(g,"×",close.left(),close.top(),close.width(),close.height(),lx,ly,true);
            }finally{g.pose().popPose();}}
        private String watts(double value){return String.format(Locale.ROOT,"%.0f W",value);}
        private String tr(String key){return net.minecraft.network.chat.Component.translatable(key).getString();}
        private String strengthLabel(int level){return tr(switch(level){case 0->"gui.moonstation14.pa.standby";case 1->"gui.moonstation14.pa.level0";case 2->"gui.moonstation14.pa.level1";case 3->"gui.moonstation14.pa.level2";case 4->"gui.moonstation14.pa.level3";default->"gui.moonstation14.pa.unknown";});}
        private void drawRole(net.minecraft.client.gui.GuiGraphics g,net.minecraft.client.gui.Font font,String role,int slot,boolean present){int bx=10+(slot%4)*58,by=88+(slot/4)*22;g.fill(bx,by,bx+52,by+17,present?0xff526755:0xff363636);g.drawString(font,tr("gui.moonstation14.pa.part."+role),bx+2,by+4,present?0xffeeeeee:0xff888888,true);}
        private void button(net.minecraft.client.gui.GuiGraphics g,String label,int bx,int by,int bw,int bh,int mx,int my,boolean allowed){com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer.render(g,label,bx,by,bw,bh,mx>=bx&&mx<bx+bw&&my>=by&&my<by+bh,allowed,false,.65f);}
         @Override public boolean mouseClicked(double px,double py,int button){if(button!=0||!contains(px,py)||Minecraft.getInstance().mouseHandler.isMouseGrabbed())return false;PointerOverlayRegistry.bringToFront(this);int x=geometry.x(),y=geometry.y(),lx=(int)((px-x)*S),ly=(int)((py-y)*S);var s=session();if(s==null)return true;var close=WindowChromeButtons.closeBounds(0,0,W,1f);if(lx>=close.left()&&lx<close.left()+close.width()&&ly>=close.top()&&ly<close.top()+close.height()){closeLocal(true);return true;}if(ly<24){geometry.beginPointer(px,py,button,12,0);return true;}if(ly>=140&&ly<162){if(lx<52&&!s.enabled())send(PaControlRequest.Operation.ENABLE,true,s.strength());else if(lx<98&&s.enabled())send(PaControlRequest.Operation.ENABLE,false,s.strength());else if(lx>=100&&lx<158)send(PaControlRequest.Operation.RESCAN,false,s.strength());else if(lx>=160&&s.enabled())send(PaControlRequest.Operation.STRENGTH,false,nextStrengthLevel(s.strength(),s.maximumStrength()));}return true;}
         @Override public boolean mouseDragged(double px,double py){return geometry!=null&&geometry.dragPointer(px,py);}
         @Override public boolean mouseReleased(int b){return geometry!=null&&geometry.releasePointer(b);}
        @Override public boolean mouseScrolled(double x,double y,double d){return false;}
        @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256){closeLocal(true);return true;}return false;}
        @Override public boolean charTyped(char c,int m){return false;}
        @Override public boolean isSearchFocused(){return false;}
        @Override public void clearTextFocus(){}
        @Override public void resetOwnedContext(){closeLocal(true);}
    }
}
