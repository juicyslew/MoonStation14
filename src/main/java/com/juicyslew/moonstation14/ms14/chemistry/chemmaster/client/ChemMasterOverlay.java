package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

/** Half-scale view of the authoritative, nonmodal ChemMaster workspace. */
final class ChemMasterOverlay implements PointerOverlayWindow {
    private static final int MARGIN = ChemMasterLayout.MARGIN;
    private final ChemMasterWorkspace workspace;
    private final ChemMasterWorkspace.Window window;
    private final ToIntFunction<String> measure;
    private final BooleanSupplier pointerAvailable;
    private int screenWidth,screenHeight;
    private boolean dragging,outputTab;
    private ChemMasterOutputEditors editors(){if(window.outputEditors==null)window.outputEditors=new ChemMasterOutputEditors(window.outputForm);return window.outputEditors;}
    ChemMasterOverlay(ChemMasterWorkspace workspace,ChemMasterWorkspace.Window window,int sw,int sh){
        this(workspace,window,sw,sh,text->Minecraft.getInstance().font.width(text),
                ()->!Minecraft.getInstance().mouseHandler.isMouseGrabbed());
    }
    ChemMasterOverlay(ChemMasterWorkspace workspace,ChemMasterWorkspace.Window window,int sw,int sh,
                      ToIntFunction<String> measure,BooleanSupplier pointerAvailable){
        this.workspace=workspace;this.window=window;this.screenWidth=sw;this.screenHeight=sh;
        this.measure=java.util.Objects.requireNonNull(measure);this.pointerAvailable=java.util.Objects.requireNonNull(pointerAvailable);
    }
    static Component localizedReagentName(ResourceLocation id){
        String key="reagent-name-"+id.getPath();
        Component localized=Component.translatable(key);
        String resolved=localized.getString();
        // Component#getString returns the translation key when the key is absent.
        if(resolved.equals(key))return Component.literal(id.getPath().replace('_',' '));
        return localized;
    }
    private static int logical(double screen,int origin){return ChemMasterLayout.toLocal(screen,origin);}
    static String containerCaption(ChemMasterWindowState state){
        String loaded=state.loadedItemName().map(Component::getString).orElse("No container");
        if(state.loadedItemName().isEmpty())return loaded;
        return loaded+"  "+com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(state.inputVolumeCents())+" / "+
                com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(state.inputCapacityCents())+"u";
    }
    private ChemMasterLayout.Geometry geometry(){return ChemMasterLayout.geometry(screenWidth,screenHeight,measure.applyAsInt("Input"),measure.applyAsInt("Output"));}
    @Override public boolean visible(){return workspace.owns(window);}
    @Override public boolean pointerAvailable(){boolean available=pointerAvailable.getAsBoolean();if(!available)clearTextFocus();return available;}
    @Override public boolean ownsPointerCapture(){return dragging;}
    boolean outputTabSelected(){return outputTab;}
    boolean dragging(){return dragging;}
    @Override public boolean contains(double x,double y){return visible()&&x>=window.x()&&y>=window.y()&&x<window.x()+Math.min(ChemMasterLayout.WIDTH,screenWidth*2)/2d&&y<window.y()+Math.min(ChemMasterLayout.HEIGHT,screenHeight*2)/2d;}
    void viewportSize(int sw,int sh){screenWidth=sw;screenHeight=sh;workspace.resize(sw,sh);}
    @Override public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int sw,int sh){
        viewportSize(sw,sh);
        var editors=editors();
        var state=window.state();var font=Minecraft.getInstance().font;
        if(window.outputPreviewFrame!=state.frame()){
            window.outputPreviewFrame=state.frame();window.pillTypePreview=null;window.drawSourcePreview=null;
        }
        var geo=ChemMasterLayout.geometry(sw,sh,font.width("Input"),font.width("Output"));int ox=window.x(),oy=window.y();
        boolean pointerAvailable=this.pointerAvailable.getAsBoolean();
        int mouseX=pointerAvailable?logical(mx,ox):Integer.MIN_VALUE,mouseY=pointerAvailable?logical(my,oy):Integer.MIN_VALUE;
        var inputRows=displayRows(workspace.presentationRows(window,true));var bufferRows=displayRows(workspace.presentationRows(window,false));
        String rejection=state.rejectionReason().orElse(null);
        refreshOutputForm(state);
        var outGeo=ChemMasterOutputLayout.geometry(new ChemMasterOutputLayout.Rect(geo.outputPlaceholder().x(),geo.outputPlaceholder().y(),geo.outputPlaceholder().width(),geo.outputPlaceholder().height()),font.lineHeight);
        int currentBottleFree=(int)Math.min(Integer.MAX_VALUE,Math.max(0,state.outputSlot().capacityCents()-state.outputSlot().volumeCents())/100);
        editors.sync(window.outputForm,outGeo,state.outputSlot().freeCells(),state.pillDosageLimit(),currentBottleFree,state.outputSlot().kind(),font.lineHeight);
        java.util.Optional<ChemMasterPaint.OutputPresentation> outputPresentation=java.util.Optional.empty();
        if(outputTab){
            int tick=(int)(System.currentTimeMillis()/100L);
            int sourceUnits=(int)Math.min(Integer.MAX_VALUE,(state.drawSource()==ChemMasterControlRequest.DrawSource.BUFFER?state.bufferReagents():state.inputReagents())
                    .stream().mapToLong(ChemMasterWindowState.ReagentRow::amountCents).sum()/100);
            outputPresentation=java.util.Optional.of(new ChemMasterPaint.OutputPresentation(
                    window.outputForm.state(sourceUnits,state.outputSlot().freeCells(),state.pillDosageLimit(),currentBottleFree),outGeo,
                    editors.snapshot(ChemMasterOutputEditors.Field.LABEL,tick),editors.snapshot(ChemMasterOutputEditors.Field.COUNT,tick),
                    editors.snapshot(ChemMasterOutputEditors.Field.PILL_DOSE,tick),editors.snapshot(ChemMasterOutputEditors.Field.BOTTLE_DOSE,tick),
                    window.pillTypePreview==null?state.pillType():window.pillTypePreview,
                    window.drawSourcePreview==null?state.drawSource():window.drawSourcePreview,displayRows(state.outputSlot().reagents())));
        }
        var view=new ChemMasterPaint.View(state,containerCaption(state),inputRows,bufferRows,geo,ox,oy,sw,sh,mouseX,mouseY,
                window.inputScroll(),window.bufferScroll(),window.pending(),outputTab,
                Component.translatable("gui.moonstation14.chemmaster.container"),rejection,outputPresentation);
        ChemMasterPaint.paint(new GuiGraphicsPaint(g,font),view);
        if(!outputTab&&pointerAvailable)paintHoveredTooltip(g,font,geo,inputRows,bufferRows,mouseX,mouseY,window.inputScroll(),window.bufferScroll());
    }
    void refreshOutputForm(ChemMasterWindowState state){
        var focused=window.outputEditors==null?null:window.outputEditors.focused();
        String inProgress=focused==null?null:window.outputEditors.get(focused).getValue();
        refreshOutputForm(state,focused,inProgress);
    }
    void refreshOutputForm(ChemMasterWindowState state,ChemMasterOutputEditors.Field focused,String inProgress){
        if(window.outputFormFrame==state.frame())return;
        var source=state.drawSource()==ChemMasterControlRequest.DrawSource.BUFFER?state.bufferReagents():state.inputReagents();
        long sourceCents=source.stream().mapToLong(ChemMasterWindowState.ReagentRow::amountCents).sum();
        int sourceUnits=(int)Math.min(Integer.MAX_VALUE,sourceCents/100);
        int bottleFree=(int)Math.min(Integer.MAX_VALUE,Math.max(0,state.outputSlot().capacityCents()-state.outputSlot().volumeCents())/100);
        var signature=new ChemMasterOutputForm.NumericInputSignature(state.drawSource(),sourceCents,state.outputSlot().kind(),
                state.outputSlot().freeCells(),bottleFree,state.pillDosageLimit());
        boolean numericInputsChanged=window.outputForm.authoritativeState(signature,sourceUnits,state.outputSlot().freeCells(),
                state.pillDosageLimit(),bottleFree);
        if(state.updateLabel()){
            String generated=source.stream().min(java.util.Comparator.comparingLong(ChemMasterWindowState.ReagentRow::amountCents))
                    .map(row->localizedReagentName(row.reagent().location()).getString()).orElse("");
            window.outputForm.authoritativeGeneratedLabel(generated,true);
        }
        if(numericInputsChanged&&inProgress!=null&&focused!=null&&ChemMasterOutputEditors.editable(focused,state.outputSlot().kind(),state.pillDosageLimit())
                &&(focused==ChemMasterOutputEditors.Field.COUNT
                ||focused==ChemMasterOutputEditors.Field.PILL_DOSE||focused==ChemMasterOutputEditors.Field.BOTTLE_DOSE)){
            switch(focused){
                case COUNT->window.outputForm.editPillCount(inProgress);
                case PILL_DOSE->window.outputForm.restorePillDoseDraft(inProgress);
                case BOTTLE_DOSE->window.outputForm.editBottleDose(inProgress,Integer.MAX_VALUE);
                case LABEL->throw new IllegalStateException("label is not a numeric field");
            }
        }
        window.outputFormFrame=state.frame();
    }
    private static java.util.List<ChemMasterPaint.DisplayRow> displayRows(java.util.List<ChemMasterWindowState.ReagentRow> rows){
        var result=new java.util.ArrayList<ChemMasterPaint.DisplayRow>(rows.size());
        for(var row:rows){int color=0xff82959b;try{var mc=Minecraft.getInstance();if(mc.level!=null)color=0xff000000|(com.juicyslew.moonstation14.ms14.reagent.ModReagents.require(mc.level,row.reagent().location()).color()&0xffffff);}catch(IllegalArgumentException ignored){}
            result.add(new ChemMasterPaint.DisplayRow(row,localizedReagentName(row.reagent().location()),color));}
        return java.util.List.copyOf(result);
    }
    private static void paintHoveredTooltip(GuiGraphics g,net.minecraft.client.gui.Font font,ChemMasterLayout.Geometry geo,
            java.util.List<ChemMasterPaint.DisplayRow> input,java.util.List<ChemMasterPaint.DisplayRow> buffer,int mx,int my,int inputScroll,int bufferScroll){
        for(var part:java.util.List.of(new Object[]{input,geo.inputViewport(),inputScroll},new Object[]{buffer,geo.bufferViewport(),bufferScroll})){
            @SuppressWarnings("unchecked") var rows=(java.util.List<ChemMasterPaint.DisplayRow>)part[0];var box=(ChemMasterLayout.Rect)part[1];int scroll=(int)part[2];
            if(!box.contains(mx,my))continue;int first=scroll/ChemMasterLayout.ROW_HEIGHT,end=Math.min(rows.size(),(scroll+box.height()+ChemMasterLayout.ROW_HEIGHT-1)/ChemMasterLayout.ROW_HEIGHT);
            for(int i=first;i<end;i++){var rr=ChemMasterLayout.reagentRow(box,scroll,i);if(mx>=rr.x()+8&&mx<rr.x()+rr.width()-38&&my>=rr.y()&&my<rr.y()+rr.height()){g.renderTooltip(font,rows.get(i).name(),mx,my);return;}}
        }
    }
    static void enableRowScissor(ChemMasterLayout.Rect box,int ox,int oy,java.util.function.Consumer<ChemMasterLayout.ScreenRect> installer){
        installer.accept(ChemMasterLayout.toScreenClip(box,ox,oy));
    }
    @Override public boolean mouseClicked(double px,double py,int button){if(!pointerAvailable.getAsBoolean()){clearTextFocus();return false;}if(button!=0||!contains(px,py)){clearTextFocus();return false;}
        int x=logical(px,window.x()),y=logical(py,window.y());var geo=geometry();
        var close=WindowChromeButtons.closeBounds(geo.close().x(),geo.close().y(),geo.close().width(),geo.close().height());
        if(WindowChromeButtons.contains(close,x,y)){workspace.close(window);return true;}window.raise();com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.bringToFront(this);
        if(geo.outputTab().contains(x,y)){clearTextFocus();outputTab=true;return true;}if(geo.inputTab().contains(x,y)){clearTextFocus();outputTab=false;return true;}
        if(y<ChemMasterLayout.HEADER){clearTextFocus();dragging=true;workspace.beginDrag(window,(int)px,(int)py);return true;}
        if(outputTab){var og=ChemMasterOutputLayout.geometry(new ChemMasterOutputLayout.Rect(geo.outputPlaceholder().x(),geo.outputPlaceholder().y(),geo.outputPlaceholder().width(),geo.outputPlaceholder().height()),Minecraft.getInstance().font.lineHeight);
            var fields=new ChemMasterOutputLayout.Rect[]{og.label(),og.pillCount(),og.pillDose(),og.bottleDose()};
                for(var f:ChemMasterOutputEditors.Field.values())if(fields[f.ordinal()].contains(x,y)){if(editors().editable(f)){editors().focus(f);editors().get(f).mouseClicked(x,y,button);}else clearTextFocus();return true;}}
        if(outputTab){var og=ChemMasterOutputLayout.geometry(new ChemMasterOutputLayout.Rect(geo.outputPlaceholder().x(),geo.outputPlaceholder().y(),geo.outputPlaceholder().width(),geo.outputPlaceholder().height()),Minecraft.getInstance().font.lineHeight);
            var state=window.state();var slot=state.outputSlot();var source=state.drawSource()==ChemMasterControlRequest.DrawSource.BUFFER?state.bufferReagents():state.inputReagents();
            long sourceCents=source.stream().mapToLong(ChemMasterWindowState.ReagentRow::amountCents).sum();int sourceUnits=(int)Math.min(Integer.MAX_VALUE,sourceCents/100);
            int bottleFree=(int)Math.min(Integer.MAX_VALUE,Math.max(0,slot.capacityCents()-slot.volumeCents())/100);
            if(og.sourceSelection().contains(x,y)&&!window.pending()){
                int mid=og.sourceSelection().x()+og.sourceSelection().width()/2;var next=x<mid?ChemMasterControlRequest.DrawSource.BUFFER:ChemMasterControlRequest.DrawSource.INPUT;
                if(workspace.drawSource(window,next)){window.drawSourcePreview=next;window.outputPreviewFrame=state.frame();}clearTextFocus();return true;}
            if(og.pillPalette().contains(x,y)&&!window.pending()&&slot.kind()==ChemMasterWindowState.OutputKind.PILL_CANISTER){
                for(int i=0;i<og.pills().size();i++)if(og.pills().get(i).contains(x,y)){if(workspace.pillType(window,i)){window.pillTypePreview=i;window.outputPreviewFrame=state.frame();}clearTextFocus();return true;}}
            if(og.eject().contains(x,y)){if(slot.kind()!=ChemMasterWindowState.OutputKind.EMPTY&&!window.pending())workspace.ejectOutput(window);clearTextFocus();return true;}
            var fs=window.outputForm.state(sourceUnits,slot.freeCells(),state.pillDosageLimit(),bottleFree);
            if(og.pillCountMinus().contains(x,y)||og.pillCountPlus().contains(x,y)){
                if(slot.kind()==ChemMasterWindowState.OutputKind.PILL_CANISTER&&state.pillDosageLimit()>0&&sourceUnits>0){window.outputForm.adjustCount(og.pillCountPlus().contains(x,y)?1:-1,slot.freeCells());syncEditor(ChemMasterOutputEditors.Field.COUNT,window.outputForm.pillCount());}clearTextFocus();return true;}
            if(og.pillDoseMinus().contains(x,y)||og.pillDosePlus().contains(x,y)){
                int max=Math.min(sourceUnits,state.pillDosageLimit());window.outputForm.adjustPillDose(og.pillDosePlus().contains(x,y)?1:-1,max);syncEditor(ChemMasterOutputEditors.Field.PILL_DOSE,window.outputForm.pillDose());clearTextFocus();return true;}
            if(og.bottleDoseMinus().contains(x,y)||og.bottleDosePlus().contains(x,y)){
                if(slot.kind()==ChemMasterWindowState.OutputKind.BOTTLE){window.outputForm.adjustBottleDose(og.bottleDosePlus().contains(x,y)?1:-1,Math.min(21474836,bottleFree));syncEditor(ChemMasterOutputEditors.Field.BOTTLE_DOSE,window.outputForm.bottleDose());}clearTextFocus();return true;}
            if(og.createPills().contains(x,y)){
                if(!window.pending()&&slot.kind()==ChemMasterWindowState.OutputKind.PILL_CANISTER&&fs.canCreatePills()&&parse(window.outputForm.pillCount())<=64)
                    workspace.createPills(window,parse(window.outputForm.pillCount()),parse(window.outputForm.pillDose()),window.outputForm.label());
                clearTextFocus();return true;}
            if(og.fillBottle().contains(x,y)){
                if(!window.pending()&&slot.kind()==ChemMasterWindowState.OutputKind.BOTTLE&&fs.canFillBottle())workspace.fillBottle(window,parse(window.outputForm.bottleDose()),window.outputForm.label());
                clearTextFocus();return true;}
        }
        clearTextFocus();
        if(geo.eject().contains(x,y)){workspace.send(window,ChemMasterControlRequest.Operation.EJECT,null,0);return true;}
        if(geo.sort().contains(x,y)){workspace.nextSort(window);return true;}
        if(geo.transferMode().contains(x,y)){workspace.mode(window,ChemMasterControlRequest.Mode.TRANSFER);return true;}
        if(geo.discardMode().contains(x,y)){workspace.mode(window,ChemMasterControlRequest.Mode.DISCARD);return true;}
        boolean input=geo.inputViewport().contains(x,y),buffer=geo.bufferViewport().contains(x,y);
        if(input||buffer){var rows=workspace.presentationRows(window,input);var view=input?geo.inputViewport():geo.bufferViewport();
            var hit=ChemMasterLayout.doseHit(view,input?window.inputScroll():window.bufferScroll(),rows.size(),x,y,index->measure.applyAsInt(com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(rows.get(index).amountCents())+"u"));if(hit!=null&&!window.pending()){long n=ChemMasterLayout.AMOUNTS[hit.amountIndex()]<0?com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.MAX_CENTS:(long)ChemMasterLayout.AMOUNTS[hit.amountIndex()]*100;
                workspace.send(window,input?ChemMasterControlRequest.Operation.INPUT_REAGENT:ChemMasterControlRequest.Operation.BUFFER_REAGENT,rows.get(hit.rowIndex()).reagent(),n);}return true;}
        return true;
    }
    private void syncEditor(ChemMasterOutputEditors.Field field,String value){if(window.outputEditors!=null)window.outputEditors.get(field).setValue(value);}
    private static int parse(String value){try{return Integer.parseInt(value);}catch(RuntimeException ignored){return 0;}}
    @Override public boolean mouseDragged(double x,double y){if(!dragging)return false;workspace.drag((int)x,(int)y,screenWidth,screenHeight);return true;}
    @Override public boolean mouseReleased(int button){if(button==0&&dragging){dragging=false;workspace.endDrag();return true;}return false;}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(!pointerAvailable.getAsBoolean()||!contains(x,y))return false;int lx=logical(x,window.x()),ly=logical(y,window.y());var geo=geometry();int n=(int)-Math.signum(delta)*ChemMasterLayout.ROW_HEIGHT*3;
        if(geo.inputViewport().contains(lx,ly)){window.scrollInput(n,ChemMasterLayout.maxScroll(workspace.presentationRows(window,true).size(),ChemMasterLayout.ROW_HEIGHT,geo.inputViewport().height()));return true;}
        else if(geo.bufferViewport().contains(lx,ly)){window.scrollBuffer(n,ChemMasterLayout.maxScroll(workspace.presentationRows(window,false).size(),ChemMasterLayout.ROW_HEIGHT,geo.bufferViewport().height()));return true;}return false;}
    @Override public boolean keyPressed(int key,int scan,int mods){var e=window.outputEditors;if(e!=null&&e.hasFocus()){if(key==256){clearTextFocus();return true;}return e.get(e.focused()).keyPressed(key,scan,mods);}return false;}
    @Override public boolean charTyped(char c,int mods){var e=window.outputEditors;return e!=null&&e.hasFocus()&&e.get(e.focused()).charTyped(c,mods);}
    @Override public boolean isSearchFocused(){if(!pointerAvailable.getAsBoolean()){clearTextFocus();return false;}return window.outputEditors!=null&&window.outputEditors.hasFocus();}
    @Override public void clearTextFocus(){if(window.outputEditors!=null)window.outputEditors.clearFocus();}
    @Override public void resetOwnedContext(){clearTextFocus();workspace.closeLocal(window);}
}
