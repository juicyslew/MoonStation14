package com.juicyslew.moonstation14.ms14.construction.client;

import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData;
import com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Non-exclusive, client-owned construction catalogue window. It never installs a Minecraft Screen. */
public final class ConstructionBrowserWindow implements com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow {
    private static final ConstructionBrowserWindow INSTANCE = new ConstructionBrowserWindow();
    private static final int PAGE = 4;
    private final FilteredListModel listModel = new FilteredListModel(512, PAGE);
    private final MachineSearchList list = new MachineSearchList(0, 0, 120, PAGE,
            Component.literal("Construction recipes"), listModel, this::activate, value -> { query=value; refresh(); });
    private WindowGeometryController geometry;
    private boolean visible;
    private boolean inFamily;
    private String family;
    private ResourceLocation selected;
    private int quantity = 1, detailScroll;
    private boolean searchFocused;
    private boolean geometryCaptured;
    private long revision;
    private List<FilteredListModel.Entry> snapshot = List.of();
    private String queuedRecipe="";
    private int queuedRemaining;
    private String craftStatus="";
    private String query="";
    private net.minecraft.client.gui.components.Button closeButton;

    private ConstructionBrowserWindow() { refresh(); ConstructionCraftClient.bindWindow(this::acceptCraftState); }
    public static ConstructionBrowserWindow instance() { return INSTANCE; }
    public static void open(String remembered) {
        com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.register(INSTANCE);
        com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.overlayFront(true);
        INSTANCE.refresh(); INSTANCE.visible = true;
        ResourceLocation id = ResourceLocation.tryParse(remembered == null ? "" : remembered);
        if (id != null) {
            var catalog=PrototypeRuntime.clientConstructionCatalog();
            var recipe=catalog.recipe(id);
            if(recipe!=null) {
                INSTANCE.selected=id;
                long familyCount=catalog.recipeIds().stream().map(catalog::recipe)
                        .filter(r->r!=null&&Objects.equals(r.family(),recipe.family())).count();
                if(familyCount>1) { INSTANCE.inFamily=true; INSTANCE.family=recipe.family(); }
                INSTANCE.refresh();
                if(!INSTANCE.listModel.select(id.toString())) {
                    INSTANCE.list.searchBox().setValue("");
                    INSTANCE.listModel.select(id.toString());
                }
            }
        }
        com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.openConstructionPointerWorkspace();
    }
    public static void toggle(String remembered) { if (INSTANCE.visible) INSTANCE.close(); else open(remembered); }
    @Override public boolean visible() { return visible; }
    @Override public boolean contains(double x, double y) { return visible && geometry != null && x >= geometry.x() && y >= geometry.y() && x < geometry.x()+geometry.width() && y < geometry.y()+geometry.height(); }
    @Override public boolean isSearchFocused() { return visible && searchFocused && list.searchBox().isFocused(); }
    public void clearSearchFocus() { searchFocused=false; list.searchBox().setFocused(false); }
    @Override public void clearTextFocus() { clearSearchFocus(); }
    public void close() { visible = false; clearSearchFocus(); com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.pointerOverlayClosed(); }
    public void clearOwnedQueueState() { queuedRecipe="";queuedRemaining=0;craftStatus=""; }
    @Override public void resetOwnedContext() { visible=false; clearSearchFocus(); geometryCaptured=false; selected=null; quantity=1; detailScroll=0; queuedRecipe="";queuedRemaining=0;craftStatus=""; inFamily=false;family=null; }

    private void refresh() {
        var catalog = PrototypeRuntime.clientConstructionCatalog();
        List<FilteredListModel.Entry> entries=ConstructionBrowserModel.catalogEntries(catalog.recipeIds(),
                id->id.toString(),id->{var recipe=catalog.recipe(id);return recipe==null?"":recipe.family();},
                id->{var recipe=catalog.recipe(id);return recipe==null?"":recipe.name();},family,inFamily,
                !query.isBlank(),ConstructionBrowserWindow::familyTitle);
        if (!entries.equals(snapshot)) {
            snapshot=List.copyOf(entries);
            var blocks=PrototypeRuntime.clientConstructionEntities();var disposal=PrototypeRuntime.clientDisposals();
            list.snapshotWithIcons(++revision,snapshot,id->rowIcon(id,catalog,blocks,disposal),this::renderRowIcon,16);
        }
    }
    private ConstructionRecipeIconResolver.Icon rowIcon(String id,
            com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionCatalog catalog,
            com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData> blocks,
            com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData> disposal) {
        if(id.startsWith("@family:"))
            return ConstructionRecipeIconResolver.familyIcon(id.substring(8),catalog,blocks,disposal);
        ResourceLocation recipe=ResourceLocation.tryParse(id);
        return recipe==null?null:ConstructionRecipeIconResolver.recipeIcon(recipe,catalog,blocks,disposal);
    }
    private void renderRowIcon(GuiGraphics graphics,Object value,int x,int y) {
        if(!(value instanceof ConstructionRecipeIconResolver.Icon icon))return;
        if(icon.item()!=null) {
            graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(.75f,.75f,1f);
            com.juicyslew.moonstation14.ms14.hands.client.ItemUiRenderer.render(graphics,icon.item().toString(),1,null,0,0);
            graphics.pose().popPose();return;
        }
        var data=icon.disposal();
        if(data==null||data.tube()==null||data.geometry()==null)return;
        graphics.flush();
        graphics.pose().pushPose();graphics.pose().translate(x,y+ConstructionRecipeIconResolver.THUMBNAIL_LOGICAL_SIZE,0);
        graphics.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        graphics.pose().scale(12,12,12);
        var mesh=ConstructionRecipeIconResolver.thumbnailMesh(icon);
        var texture=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/block/disposal/pipe_material.png");
        var out=graphics.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityCutout(texture));
        com.juicyslew.moonstation14.ms14.disposal.client.DisposalTubeVisualRenderer.renderMesh(out,graphics.pose(),mesh,0,0,0,255,255,255,255,0x00f000f0);
        graphics.pose().popPose();graphics.flush();
    }
    private static String familyTitle(String value) {
        StringJoiner result=new StringJoiner(" ");
        for(String word:value.replace('_',' ').trim().split("\\s+"))
            result.add(word.isEmpty()?word:Character.toUpperCase(word.charAt(0))+word.substring(1));
        return result.toString();
    }
    private void activate(String id) {
        if (id.equals("@back")) { inFamily=false; family=null; refresh(); return; }
        if (id.startsWith("@family:")) { family=id.substring(8); inFamily=true; refresh(); return; }
        selected=ResourceLocation.tryParse(id); detailScroll=0;
    }
    private void acceptCraftState(com.juicyslew.moonstation14.ms14.construction.network.ConstructionCraftState state) {
        if(!com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.constructionStateMatches(state.epoch(),state.body()))return;
        queuedRecipe=state.remaining()>0?state.recipe():"";queuedRemaining=state.remaining();craftStatus=state.message();
    }

    @Override public void renderIntoGui(GuiGraphics g, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
        if (!visible) return;
        refresh(); // snapshot compares values, so unchanged catalogs preserve search, focus, and scroll state
        if (geometry == null || screenWidth != cachedWidth || screenHeight != cachedHeight) {
            var bounds=ConstructionBrowserLayout.preferredBounds(screenWidth,screenHeight);
            geometry = new WindowGeometryController(screenWidth,screenHeight,
                bounds.x(),bounds.y(),bounds.width(),bounds.height(),bounds.minWidth(),bounds.minHeight(),false);
            geometryCaptured=false;
        }
        cachedWidth=screenWidth; cachedHeight=screenHeight;
        int x=geometry.x(), y=geometry.y(), w=geometry.width(), h=geometry.height();
        var recipe=selected==null?null:PrototypeRuntime.clientConstructionCatalog().recipe(selected);
        var layout=ConstructionBrowserLayout.calculate(x,y,w,h,recipe!=null,recipe!=null&&recipe.outputKind()==ConstructionRecipeData.OutputKind.ITEM,!queuedRecipe.isEmpty(),!craftStatus.isEmpty());
        MachineWindowRenderer.compactWindow(g,Minecraft.getInstance().font,x,y,w,h,Component.literal("Construction"));
        int listHeight=Math.max(ConstructionBrowserLayout.ROW_HEIGHT,Math.min(600,layout.listHeight()*2));
        g.pose().pushPose(); g.pose().translate(layout.search().x(),layout.search().y(),0); g.pose().scale(.5f,.5f,1);
        int logicalListWidth=layout.list().width()*2;list.setX(0); list.setY(24); list.resizeViewport(logicalListWidth,listHeight);
        list.searchBox().setX(0); list.searchBox().setY(0); list.searchBox().setWidth(layout.search().width()*2); list.searchBox().setHeight(19);
        list.searchBox().render(g,(int)((mouseX-layout.search().x())*2),(int)((mouseY-layout.search().y())*2),partialTick);
        var clip=layout.list();
        list.render(g,(int)((mouseX-layout.search().x())*2),(int)((mouseY-layout.search().y())*2),partialTick,
                clip.x(),clip.y(),clip.x()+clip.width(),clip.y()+clip.height());
        int total=listModel.matchCount(),page=listModel.pageSize(),trackTop=24,trackBottom=24+listHeight;
        if(total>page){int trackX=logicalListWidth-6,trackHeight=trackBottom-trackTop,thumb=Math.max(8,trackHeight*page/total);g.fill(trackX,trackTop,trackX+3,trackBottom,0xff11191d);g.fill(trackX,trackTop+(trackHeight-thumb)*listModel.first()/Math.max(1,total-page),trackX+3,trackTop+(trackHeight-thumb)*listModel.first()/Math.max(1,total-page)+thumb,0xff82959b);}
        g.pose().popPose();
        var guidePane=layout.instructions();int right=guidePane.x(), top=guidePane.y();
         if(recipe!=null) {
            int paneRight=right+guidePane.width(), clipTop=top, clipBottom=top+guidePane.height();
            List<String> wrapped=new ArrayList<>();
            int fontLineHeight=Minecraft.getInstance().font.lineHeight;
            int count=ConstructionBrowserLayout.guideVisibleRows(layout,fontLineHeight);
            g.enableScissor(right,clipTop,paneRight,clipBottom);
             g.pose().pushPose();g.pose().translate(right,top+ConstructionBrowserLayout.GUIDE_TOP_MARGIN,0);g.pose().scale(.5f,.5f,1);
            try {
            int maxWidth=Math.max(24,guidePane.width()*2-8);
            g.drawString(Minecraft.getInstance().font,Minecraft.getInstance().font.plainSubstrByWidth(recipe.name(),maxWidth),0,0,MachineWindowRenderer.TEXT,true);
            var graph=PrototypeRuntime.clientConstructionCatalog().graph(ResourceLocation.tryParse(recipe.graph().contains(":")?recipe.graph():"moonstation14:"+recipe.graph()));
            var guide=ConstructionRecipeGuide.build(graph,recipe,graph==null?1:graph.nodes().get(recipe.targetNode()).outputStackCount());
             for(int i=0;i<guide.size();i++) {
                 String remaining=guide.get(i).text();
                while(!remaining.isEmpty()) { String part=Minecraft.getInstance().font.plainSubstrByWidth(remaining,maxWidth); if(part.isEmpty())part=remaining.substring(0,1); if(part.length()<remaining.length()){int split=part.lastIndexOf(' ');if(split>0)part=part.substring(0,split);} wrapped.add(part);remaining=remaining.substring(part.length()).stripLeading(); }
            }
            detailScroll=Math.min(detailScroll,ConstructionBrowserLayout.maxScroll(wrapped.size(),count));
            for(int i=detailScroll;i<Math.min(wrapped.size(),detailScroll+count);i++)g.drawString(Minecraft.getInstance().font,wrapped.get(i),0,15+(i-detailScroll)*10,MachineWindowRenderer.MUTED_TEXT,true);
            } finally { g.pose().popPose(); g.disableScissor(); }
            int detailTrackTop=top,detailTrackBottom=clipBottom,detailTrackHeight=Math.max(0,detailTrackBottom-detailTrackTop);
             if(count>0&&wrapped.size()>count&&detailTrackHeight>0) {
                int thumb=Math.max(5,detailTrackHeight*count/wrapped.size());
                int thumbY=detailTrackTop+(detailTrackHeight-thumb)*detailScroll/ConstructionBrowserLayout.maxScroll(wrapped.size(),count);
                g.fill(x+w-7,detailTrackTop,x+w-5,detailTrackBottom,0xff11191d);
                g.fill(x+w-7,thumbY,x+w-5,thumbY+thumb,0xff82959b);
            }
            boolean item=recipe.outputKind()==ConstructionRecipeData.OutputKind.ITEM;
             if(item) { var a=ConstructionBrowserLayout.itemActions(layout);compactAction(g,a.minus(),"−",mouseX,mouseY);compactAction(g,a.plus(),"+",mouseX,mouseY);compactAction(g,a.craft(),"Craft",mouseX,mouseY);compactCenteredText(g,"×"+quantity,a.quantity()); }
            else compactAction(g,layout.actions(),"Select plan",mouseX,mouseY);
        }
        if(!queuedRecipe.isEmpty()) {
            var queuedId=ResourceLocation.tryParse(queuedRecipe);
            var queued=queuedId==null?null:PrototypeRuntime.clientConstructionCatalog().recipe(queuedId);
            String queueLabel="Queued "+(queued==null?"construction":queued.name())+" · remaining "+queuedRemaining;
            compactText(g,queueLabel,layout.queue().x(),layout.queue().y());
              compactAction(g,new ConstructionBrowserLayout.Rect(x+w-19,layout.queue().y(),16,8),"Cancel",mouseX,mouseY);
        }
         if(!craftStatus.isEmpty()) {
             var status=layout.status();g.enableScissor(status.x(),status.y(),status.x()+status.width(),status.y()+status.height());
             try { compactText(g,craftStatus,status.x(),status.y()); } finally { g.disableScissor(); }
         }
        if(closeButton==null){closeButton=WindowChromeButtons.close(0,0,18,this::close);closeButton.setX(0);closeButton.setY(0);}
        var close=layout.close();g.pose().pushPose();g.pose().translate(close.x(),close.y(),0);g.pose().scale(.5f,.5f,1);
        closeButton.render(g,(int)((mouseX-close.x())*2),(int)((mouseY-close.y())*2),partialTick);g.pose().popPose();
    }
    private int cachedWidth=-1,cachedHeight=-1;
    private void action(GuiGraphics g,int x,int y,int w,int h,String label,int mx,int my,int kind) {
        MachineActionButtonRenderer.render(g,label,x,y,w,h,mx>=x&&mx<x+w&&my>=y&&my<y+h);
    }
    private void compactText(GuiGraphics g,String text,int x,int y){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.5f,.5f,1);g.drawString(Minecraft.getInstance().font,text,0,0,MachineWindowRenderer.MUTED_TEXT,true);g.pose().popPose();}
    private void compactCenteredText(GuiGraphics g,String text,ConstructionBrowserLayout.Rect bounds){
        var font=Minecraft.getInstance().font;var origin=ConstructionQuantityPlacement.centered(font.width(text),font.lineHeight,bounds);
        g.pose().pushPose();g.pose().translate(origin.x(),origin.y(),0);g.pose().scale(.5f,.5f,1);
        g.drawString(font,text,0,0,MachineWindowRenderer.MUTED_TEXT,true);g.pose().popPose();
    }
    private void compactAction(GuiGraphics g,ConstructionBrowserLayout.Rect r,String label,int mx,int my){g.pose().pushPose();g.pose().translate(r.x(),r.y(),0);g.pose().scale(.5f,.5f,1);MachineActionButtonRenderer.render(g,label,0,0,r.width()*2,r.height()*2,mx>=r.x()&&mx<r.x()+r.width()&&my>=r.y()&&my<r.y()+r.height());g.pose().popPose();}
    public boolean mouseClicked(double mx,double my,int button) {
        if(!contains(mx,my)) return false;
        int x=geometry.x(),y=geometry.y(),w=geometry.width(),h=geometry.height();
        var selectedRecipe=selected==null?null:PrototypeRuntime.clientConstructionCatalog().recipe(selected);
        var layout=ConstructionBrowserLayout.calculate(x,y,w,h,selectedRecipe!=null,selectedRecipe!=null&&selectedRecipe.outputKind()==ConstructionRecipeData.OutputKind.ITEM,!queuedRecipe.isEmpty(),!craftStatus.isEmpty());
        if(button==0&&layout.close().contains(mx,my)){if(closeButton!=null)closeButton.mouseClicked((mx-layout.close().x())*2,(my-layout.close().y())*2,button);else close();return true;}
        double sx=(mx-layout.search().x())*2,sy=(my-layout.search().y())*2;
        if(list.pointerSearchClick(sx,sy,button)){searchFocused=true;return true;}
        if(geometry.beginPointer(mx,my,button,ConstructionBrowserLayout.HEADER_HEIGHT,14)){geometryCaptured=true;clearSearchFocus();return true;}
        if(button==0) {
            var r=selectedRecipe;
            if(r!=null&&r.outputKind()==ConstructionRecipeData.OutputKind.ITEM) {
                var a=ConstructionBrowserLayout.itemActions(layout);
                if(a.minus().contains(mx,my)){quantity=Math.max(1,quantity-1);return true;}
                if(a.plus().contains(mx,my)){quantity=Math.min(64,quantity+1);return true;}
                if(a.craft().contains(mx,my)){ConstructionCraftClient.request(selected.toString(),quantity,false);return true;}
            } else if(r!=null&&layout.actions().contains(mx,my)){
                DisposalConstructionClient.selectRecipeFromBrowser(selected);
                visible=false; clearSearchFocus();
                com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.dismissPopupPointerMode();
                return true;
            }
         if(!queuedRecipe.isEmpty()&&hit(mx,my,x+w-19,layout.queue().y(),16,8)){ConstructionCraftClient.request(queuedRecipe,0,true);return true;}
        }
        double lx=(mx-layout.search().x())*2,ly=(my-layout.search().y())*2;
        if(list.mouseClicked(lx,ly,button)){var id=listModel.selectedId();if(id!=null)activate(id);return true;}
        clearSearchFocus();return true;
    }
    private static boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override public boolean mouseDragged(double x,double y) { return geometryCaptured&&geometry.dragPointer(x,y); }
    @Override public boolean mouseReleased(int button) { if(!geometryCaptured)return false;geometryCaptured=false;return geometry.releasePointer(button); }
    @Override public boolean mouseScrolled(double x,double y,double delta) { if(!contains(x,y))return false; var r=PrototypeRuntime.clientConstructionCatalog().recipe(selected);var l=ConstructionBrowserLayout.calculate(geometry.x(),geometry.y(),geometry.width(),geometry.height(),r!=null,r!=null&&r.outputKind()==ConstructionRecipeData.OutputKind.ITEM,!queuedRecipe.isEmpty(),!craftStatus.isEmpty());if(l.list().contains(x,y))return list.mouseScrolled((x-l.search().x())*2,(y-l.search().y())*2,0,delta);if(!l.instructions().contains(x,y)||ConstructionBrowserLayout.guideVisibleRows(l,Minecraft.getInstance().font.lineHeight)==0)return false;detailScroll=Math.max(0,detailScroll+(delta<0?1:-1));return true; }
    public static boolean handleConfiguredClose(int key,int scan,int mods) {
        return DisposalConstructionClient.handleBrowserClose(key,scan,mods,INSTANCE.visible,INSTANCE.isSearchFocused(),INSTANCE::close);
    }
    @Override public boolean keyPressed(int key,int scan,int mods) { if(handleConfiguredClose(key,scan,mods))return true; if(!visible||!isSearchFocused())return false; if(key==GLFW.GLFW_KEY_ESCAPE){clearSearchFocus();return true;} return list.searchBox().keyPressed(key,scan,mods); }
    @Override public boolean charTyped(char c,int mods) { return isSearchFocused()&&list.searchBox().charTyped(c,mods); }
}
