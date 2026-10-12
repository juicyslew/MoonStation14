package com.juicyslew.moonstation14.ms14.ghost_roles.client;

import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Request-only role picker hosted in the existing pointer workspace. */
@EventBusSubscriber(modid=com.juicyslew.moonstation14.MoonStation14.MOD_ID,value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class GhostRoleMenuWindow implements PointerOverlayWindow {
    private static final GhostRoleMenuWindow INSTANCE=new GhostRoleMenuWindow();
    private final GhostRoleMenuState state=new GhostRoleMenuState();
    private final Set<String> expanded=new HashSet<>();
    private String selectedId;
    private WindowGeometryController geometry;
    private boolean visible,captured;
    private int scroll,cachedW=-1,cachedH=-1,contentHeight;
    private static long lastRefreshTick;
    private GhostRoleMenuWindow() { }
    public static GhostRoleMenuWindow instance(){return INSTANCE;}
    public static void open(GhostRolePayloads.Context context){if(context==null)return;INSTANCE.state.open(context,gameTick());INSTANCE.visible=true;INSTANCE.expanded.clear();INSTANCE.selectedId=null;INSTANCE.scroll=0;PointerOverlayRegistry.register(INSTANCE);PointerOverlayRegistry.overlayFront(true);GhostRoleNetworking.requestSnapshot();com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.openSharedPointerWorkspace();}
    public static void deliver(GhostRolePayloads.Snapshot snapshot){INSTANCE.state.snapshot(snapshot);}
    public static boolean deliver(GhostRolePayloads.Result result){boolean accepted=INSTANCE.state.result(result);if(accepted&&INSTANCE.visible){INSTANCE.state.refresh(gameTick());GhostRoleNetworking.requestSnapshot();}return accepted;}
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event){var m=INSTANCE;if(!m.visible())return;long t=gameTick();m.state.tick(t);if(t-lastRefreshTick>=20){lastRefreshTick=t;GhostRoleNetworking.requestSnapshot();}}
    private static long gameTick(){return Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();}
    private List<String> groups(){return GhostRoleMenuPresentation.groups(state.entries());}
    private List<GhostRolePayloads.Entry> members(String group){return GhostRoleMenuPresentation.members(state.entries(),group);}
    private GhostRolePayloads.Entry selected(){return GhostRoleMenuPresentation.selected(state.entries(),selectedId);}
    private int rows(){return GhostRoleMenuPresentation.rowCount(state.entries(),expanded);}
    private String statusText(){return state.pending()!=null?"Pending":state.refreshing()?"Loading":state.status();}
    private int desiredHeight(int width){return GhostRoleMenuPresentation.desiredHeight(state.entries(),expanded,selected()!=null,!statusText().isEmpty(),width);}
    private void send(GhostRolePayloads.Entry e,GhostRolePayloads.Action a){if(e==null||state.pending()!=null)return;var c=state.beginRequest();if(c!=null)GhostRoleNetworking.send(new GhostRolePayloads.Request(state.context(),c,e.target(),a));}
    private void close(){visible=false;if(geometry!=null)geometry.cancelPointer();captured=false;state.close();}
    @Override public void resetOwnedContext(){close();geometry=null;captured=false;}
    @Override public boolean visible(){if(visible&&!state.activeFor(com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedGhostContext()))close();return visible;}
    @Override public boolean contains(double x,double y){return visible()&&geometry!=null&&x>=geometry.x()&&y>=geometry.y()&&x<geometry.x()+geometry.width()&&y<geometry.y()+geometry.height();}
    @Override public boolean isSearchFocused(){return false;}
    @Override public void clearTextFocus(){}
    @Override public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int sw,int sh){
        if(!visible())return;
        int w=GhostRoleMenuPresentation.width(sw);int h=Math.min(sh,Math.max(32,desiredHeight(w)));
          if(geometry==null){geometry=new WindowGeometryController(sw,sh,5,5,w,h,Math.min(64,sw),Math.min(32,sh),false);cachedW=sw;cachedH=sh;}else{boolean viewportChanged=sw!=cachedW||sh!=cachedH;GhostRoleMenuPresentation.relayout(geometry,sw,sh,w,h,viewportChanged);cachedW=sw;cachedH=sh;}
        int x=geometry.x(),y=geometry.y();w=geometry.width();h=geometry.height();boolean hasStatus=!statusText().isEmpty(),hasSelection=selected()!=null;
        int actionsHeight=hasSelection?GhostRoleMenuPresentation.actionHeight(w):0;
         contentHeight=Math.max(0,h-15-actionsHeight-(hasStatus?GhostRoleMenuPresentation.ROW_HEIGHT:0)-4);
        int capacity=contentHeight/GhostRoleMenuPresentation.ROW_HEIGHT;
        scroll=Math.max(0,Math.min(scroll,GhostRoleMenuPresentation.maxScroll(state.entries(),expanded,capacity)));
        MachineWindowRenderer.compactWindow(g,Minecraft.getInstance().font,x,y,w,h,Component.literal("Ghost roles"));
         // Chrome geometry is defined in unscaled widget pixels; convert the window's screen-pixel
         // bounds to that space before applying the half-scale, just as compactWindow does.
         var close=GhostRoleMenuPresentation.closeBounds(x,y,w);g.pose().pushPose();g.pose().translate(close.left(),close.top(),0);g.pose().scale(.5f,.5f,1);MachineActionButtonRenderer.render(g,"×",0,0,WindowChromeButtons.SIZE,WindowChromeButtons.SIZE,mx>=close.left()&&mx<close.right()&&my>=close.top()&&my<close.bottom());g.pose().popPose();
        int top=y+15,shown=0;g.enableScissor(x+3,top,x+w-3,top+contentHeight);
        var rows=GhostRoleMenuPresentation.visibleRows(state.entries(),expanded);
           for(int i=scroll;i<Math.min(rows.size(),scroll+capacity);i++){var row=rows.get(i);var bounds=GhostRoleMenuPresentation.rowBounds(x,top,w,contentHeight,shown++);int ry=bounds.top();if(row.header()){g.fill(bounds.left(),bounds.top(),bounds.right(),bounds.bottom(),0xff303b3e);text(g,(expanded.contains(row.roleId())?"▾ ":"▸ ")+roleDisplayName(row.roleId()),x+5,ry+2,0xffe0e7e8);}else{var e=row.entry();boolean sel=e.target().toString().equals(selectedId);if(sel)g.fill(bounds.left(),bounds.top(),bounds.right(),bounds.bottom(),0xff52666b);String label=roleDisplayName(e.roleId());String shortId=e.target().toString().substring(0,4);text(g,label+" #"+shortId,x+9,ry+2,0xffd2dbdd);}}
        g.disableScissor();
        if(hasStatus)text(g,statusText(),x+4,y+h-actionsHeight-10,0xffb0bec2);
        if(hasSelection){var e=selected();if(w<92){int fy=y+h-26;action(g,x+3,fy,w-6,11,"Teleport",mx,my,e.teleport());action(g,x+3,fy+13,w-6,11,"Request",mx,my,e.takeover());}else{int fy=y+h-15;action(g,x+3,fy,(w-8)/2,12,"Teleport",mx,my,e.teleport());action(g,x+w/2,fy,w/2-4,12,"Request",mx,my,e.takeover());}}
    }
    private static String roleDisplayName(String roleId){
        try{var id=net.minecraft.resources.ResourceLocation.parse(roleId);var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id);if(type.isPresent())return type.get().getDescription().getString();}catch(IllegalArgumentException ignored){}
        int colon=roleId.indexOf(':');String path=(colon>=0?roleId.substring(colon+1):roleId).replace('_',' ');return path.isEmpty()?"Entity":path.substring(0,1).toUpperCase(Locale.ROOT)+path.substring(1);
    }
    private static void text(GuiGraphics g,String s,int x,int y,int color){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.5f,.5f,1);g.drawString(Minecraft.getInstance().font,s,0,0,color,true);g.pose().popPose();}
    private void action(GuiGraphics g,int x,int y,int w,int h,String s,int mx,int my,boolean enabled){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.5f,.5f,1);MachineActionButtonRenderer.render(g,s,0,0,w*2,h*2,enabled&&mx>=x&&mx<x+w&&my>=y&&my<y+h);g.pose().popPose();}
     @Override public boolean mouseClicked(double mx,double my,int button){if(!contains(mx,my))return false;int x=geometry.x(),y=geometry.y(),w=geometry.width(),h=geometry.height();var c=GhostRoleMenuPresentation.closeBounds(x,y,w);if(button==0&&mx>=c.left()&&mx<c.right()&&my>=c.top()&&my<c.bottom()){close();return true;}if(geometry.beginPointer(mx,my,button,13,Math.round((WindowChromeButtons.EDGE+WindowChromeButtons.SIZE)*.5f))){captured=true;return true;}if(button==0&&selected()!=null&&my>=y+h-GhostRoleMenuPresentation.actionHeight(w)){var e=selected();boolean teleport=w<92?my<y+h-13:mx<x+w/2;if(teleport)send(e,GhostRolePayloads.Action.TELEPORT);else send(e,GhostRolePayloads.Action.REQUEST_ROLE);return true;}if(button==0&&GhostRoleMenuPresentation.rowAtPoint(state.entries(),expanded,scroll,x,y+15,w,contentHeight,mx,my)!=null){var row=GhostRoleMenuPresentation.rowAtPoint(state.entries(),expanded,scroll,x,y+15,w,contentHeight,mx,my);if(row.header()){if(expanded.contains(row.roleId()))expanded.remove(row.roleId());else expanded.add(row.roleId());}else selectedId=row.entry().target().toString();return true;}return true;}
    @Override public boolean mouseDragged(double x,double y){return captured&&geometry!=null&&geometry.dragPointer(x,y);}
    @Override public boolean mouseReleased(int button){if(!captured)return false;captured=false;return geometry!=null&&geometry.releasePointer(button);}
    @Override public boolean mouseScrolled(double x,double y,double d){if(!contains(x,y))return false;int capacity=contentHeight/GhostRoleMenuPresentation.ROW_HEIGHT;scroll=Math.max(0,Math.min(GhostRoleMenuPresentation.maxScroll(state.entries(),expanded,capacity),scroll-(int)Math.signum(d)));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){close();return true;}return false;}
    @Override public boolean charTyped(char value,int mods){return false;}
}
