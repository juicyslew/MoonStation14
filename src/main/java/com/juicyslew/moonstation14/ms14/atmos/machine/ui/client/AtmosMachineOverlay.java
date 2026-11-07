package com.juicyslew.moonstation14.ms14.atmos.machine.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.*;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint;
import com.juicyslew.moonstation14.ms14.ui.client.paint.MachineTextFieldPaint;
import com.juicyslew.moonstation14.mixin.client.EditBoxDisplayAccessor;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import com.juicyslew.moonstation14.ms14.ui.client.window.TitlePressRouting;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.model.DraftFieldModel;
import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.*;

/** Draggable compact, GUI-pixel production atmospheric-machine window. */
final class AtmosMachineOverlay implements PointerOverlayWindow {
    private final AtmosMachineWorkspace owner; private final UUID session;
    private WindowGeometryController geometry; private int screenWidth,screenHeight; private AtmosMachineControlSnapshot state;
    private AtmosMachineClientModel clientModel;
     private long nextRequest,pending=-1,lastTick=-1,pendingSince,draftIntentId=-1; private String rejectedDraft=""; private boolean dragging; private String message=""; private Boolean enabledIntent;
      private AtmosMachineClientModel.Numeric editing; private EditBox draft,gasSearch; private DraftFieldModel draftModel; private final FilteredListModel gasModel=new FilteredListModel(32,5); private String gasQuery=""; private boolean gasSearchFocused;
    private final BooleanSupplier pointer; private final Consumer<AtmosMachineControlRequest> sender; private final Consumer<UUID> closer;
    private final Font font;
    private final Function<AtmosMachineLayout.Bounds,EditBox> editorFactory;
    AtmosMachineOverlay(AtmosMachineWorkspace owner,UUID session,int sw,int sh){this(owner,session,sw,sh,()->!Minecraft.getInstance().mouseHandler.isMouseGrabbed(),PacketDistributor::sendToServer,id->owner.closed(id),Minecraft.getInstance().font);}
    AtmosMachineOverlay(AtmosMachineWorkspace owner,UUID session,int sw,int sh,BooleanSupplier pointer,Consumer<AtmosMachineControlRequest> sender,Consumer<UUID> closer){this(owner,session,sw,sh,pointer,sender,closer,Minecraft.getInstance().font);}
    AtmosMachineOverlay(AtmosMachineWorkspace owner,UUID session,int sw,int sh,BooleanSupplier pointer,Consumer<AtmosMachineControlRequest> sender,Consumer<UUID> closer,Font font){this(owner,session,sw,sh,pointer,sender,closer,font,b->new EditBox(font,b.x(),b.y(),b.width(),20,Component.literal("Value")));}
     AtmosMachineOverlay(AtmosMachineWorkspace owner,UUID session,int sw,int sh,BooleanSupplier pointer,Consumer<AtmosMachineControlRequest> sender,Consumer<UUID> closer,Font font,Function<AtmosMachineLayout.Bounds,EditBox> editorFactory){this.owner=owner;this.session=session;this.pointer=pointer;this.sender=sender;this.closer=closer;this.font=font;this.editorFactory=editorFactory;screenWidth=sw;screenHeight=sh;var initial=AtmosMachineLayout.forViewport(sw,sh);geometry=new WindowGeometryController(Math.max(1,sw),Math.max(1,sh),Math.max(0,(sw-initial.width())/2),Math.max(0,(sh-initial.height())/2),initial.width(),initial.height(),100,32,false);}
    void snapshot(AtmosMachineControlSnapshot s){if(!session.equals(s.session()))return;
        if(clientModel==null)clientModel=new AtmosMachineClientModel(s.containerId(),session,s.controls().profileId());
        if(s.requestId()>=0&&pending==s.requestId()){}
         if(!clientModel.accept(s))return;
         boolean matched=s.requestId()>=0&&s.requestId()==pending;
          state=s;resizeWindow();
          if(editing!=null&&draftModel!=null){String authority=AtmosMachineClientModel.format(editing,authority(editing));draftModel.snapshot(s.settings().revision(),authority);}
          var gasEntries=new ArrayList<FilteredListModel.Entry>();gasEntries.add(new FilteredListModel.Entry("none","None"));Arrays.stream(com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()).map(g->new FilteredListModel.Entry(Integer.toString(g.ordinal()),AtmosMachineClientModel.gasLabel(Optional.of(g)))).forEach(gasEntries::add);gasModel.snapshot(Math.max(gasModel.revision()+1,s.settings().revision()+1),gasEntries);gasModel.filter(gasQuery);
            if(matched){if(editing!=null&&draftModel!=null&&draftIntentId>=0){rejectedDraft=draftModel.draft();draftModel.resolve(draftIntentId,s.settings().revision(),AtmosMachineClientModel.format(editing,authority(editing)),s.accepted());if(!s.accepted())draftModel.edit(rejectedDraft);draft.setValue(draftModel.draft());draftIntentId=-1;}pending=-1;pendingSince=0;enabledIntent=null;if(!s.accepted())message="Request denied";else message="";}
          if(matched&&s.accepted()&&editing!=null){editing=null;draft=null;draftModel=null;}}
      void advanceTick(){if(pending>=0&&++pendingSince>=100){if(draftModel!=null&&draftModel.pending()!=null){rejectedDraft=draftModel.draft();draftModel.cancelPending(draftIntentId);draftModel.edit(rejectedDraft);draft.setValue(rejectedDraft);}draftIntentId=-1;pending=-1;pendingSince=0;enabledIntent=null;if(clientModel!=null)clientModel.pending(-1);message="Request timed out";}}
    @Override public boolean visible(){return state!=null||!session.equals(new UUID(0,0));}
    private void resize(int sw,int sh){if(screenWidth!=sw||screenHeight!=sh){geometry.setScreenSize(Math.max(1,sw),Math.max(1,sh));screenWidth=sw;screenHeight=sh;}resizeWindow();}
     private String expanded(){return editing!=null?rowId(editing):gasSearchFocused?"filterGas":null;}
     private static String rowId(AtmosMachineClientModel.Numeric n){return switch(n){case PRESSURE_KPA->"pressure";case RATE_LPS->"rate";case MIXER_PERCENT->"ratio";case THERMOSTAT_K->"thermostat";};}
      private int visibleGasCount(){return gasModel.visibleCount();}
      private AtmosMachineLayout layout(int w,int h){return AtmosMachineLayout.forControls(w,h,state==null?null:state.controls(),draft!=null,!message.isBlank(),expanded(),visibleGasCount());}
      private void resizeWindow(){var l=layout(screenWidth,screenHeight);geometry.setBounds(geometry.x(),geometry.y(),Math.min(screenWidth,l.width()),Math.min(screenHeight,l.height()));var active=layout(geometry.width(),geometry.height());if(draft!=null){var b=active.edit();draft.setX(b.x());draft.setY(b.y());draft.setWidth(b.width());}if(gasSearch!=null&&gasSearchFocused){var b=active.edit();gasSearch.setX(b.x());gasSearch.setY(b.y());gasSearch.setWidth(b.width());}}
    @Override public boolean contains(double x,double y){return x>=geometry.x()&&y>=geometry.y()&&x<geometry.x()+geometry.width()&&y<geometry.y()+geometry.height();}
     @Override public boolean pointerAvailable(){return pointer.getAsBoolean();}
    @Override public boolean ownsPointerCapture(){return dragging||geometry.isPointerCaptured();}
      String messageForTest(){return message;}
      AtmosMachineLayout.Bounds pressureBoundsForTest(){var b=layout(geometry.width(),geometry.height()).pressure();return new AtmosMachineLayout.Bounds(geometry.x()+b.x(),geometry.y()+b.y(),b.width(),b.height());}
      Boolean enabledIntentForTest(){return enabledIntent;}
     AtmosMachineLayout.Bounds editorBoundsForTest(){return draft==null?null:new AtmosMachineLayout.Bounds(draft.getX(),draft.getY(),draft.getWidth(),draft.getHeight());}
     List<String> visibleGasLabelsForTest(){return java.util.stream.IntStream.range(0,gasModel.visibleCount()).mapToObj(i->gasModel.visibleAt(i).label()).toList();}
     void openGasPickerForTest(){gasSearchFocused=true;resizeWindow();gasSearch=editorFactory.apply(AtmosMachineLayout.forControls(geometry.width(),geometry.height(),state.controls(),false,false,"filterGas").edit());gasSearch.setFocused(true);}
        @Override public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int sw,int sh){resize(sw,sh);long tick=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();if(tick!=lastTick){advanceTick();lastTick=tick;resizeWindow();}int x=geometry.x(),y=geometry.y();var l=layout(geometry.width(),geometry.height());
         int lx=mx-x,ly=my-y;AtmosMachinePaint.NumericPresentation numeric=null;
         if(draft!=null){var a=(EditBoxDisplayAccessor)draft;int caretTick=(int)(Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime());var field=new MachineTextFieldPaint.Snapshot(draft.getValue(),draft.getCursorPosition(),a.ms14$highlightPos(),a.ms14$displayPos(),draft.isFocused(),true,(caretTick/6)%2==0,caretTick);numeric=new AtmosMachinePaint.NumericPresentation(field,"",pending>=0);}
        var gasLabels=gasModel.visibleCount()==0?List.<String>of():java.util.stream.IntStream.range(0,gasModel.visibleCount()).mapToObj(i->gasModel.visibleAt(i).label()).toList();AtmosMachinePaint.paint(new GuiGraphicsPaint(g,Minecraft.getInstance().font),new AtmosMachinePaint.View(state,x,y,geometry.width(),geometry.height(),lx,ly,0,pending>=0,message,numeric,expanded(),gasQuery,gasLabels,enabledIntent));}
      @Override public boolean mouseClicked(double x,double y,int button){if(!pointerAvailable()||!contains(x,y)||button!=0)return false;PointerOverlayRegistry.bringToFront(this);int lx=(int)(x-geometry.x()),ly=(int)(y-geometry.y());var l=layout(geometry.width(),geometry.height());
         var cb=WindowChromeButtons.closeBounds(l.close().x(),l.close().y(),l.close().width(),l.close().height());if(WindowChromeButtons.contains(cb,lx,ly)){closer.accept(session);return true;}
        if(TitlePressRouting.isTitlePress(lx,ly,button,0,0,l.width(),24,l.width(),l.height(),false)){dragging=geometry.beginPointer(x,y,button,12,28);clearTextFocus();return true;}
         if(draft!=null&&l.edit().contains(lx,ly)){draft.mouseClicked(lx,ly,button);draft.setFocused(true);return true;}
         if(gasSearchFocused&&l.edit().contains(lx,ly)){gasSearch.mouseClicked(lx,ly,button);gasSearch.setFocused(true);return true;}
         if(state==null)return true;clearTextFocus();
            for(var h:AtmosMachinePaint.hitRegions(l,state))if(h.bounds().contains(lx,ly)){String id=h.id();if(id.equals("enabled")){send(AtmosMachineSettings.Operation.SET_ENABLED,!state.settings().enabled().orElse(false),0,0);return true;}if(id.equals("filterGas")){editing=null;draft=null;draftModel=null;gasSearchFocused=true;gasQuery="";gasModel.filter("");resizeWindow();var pickerLayout=AtmosMachineLayout.forControls(geometry.width(),geometry.height(),state.controls(),false,false,"filterGas");gasSearch=editorFactory.apply(pickerLayout.edit());gasSearch.setValue("");gasSearch.setFocused(true);return true;}if(id.startsWith("gas:")){int visible=Integer.parseInt(id.substring(4));if(visible<gasModel.visibleCount()){String gas=gasModel.visibleAt(visible).id();if(gas.equals("none"))send(AtmosMachineSettings.Operation.CLEAR_FILTER_GAS,false,0,0);else sendGas(Integer.parseInt(gas));gasSearchFocused=false;gasSearch.setFocused(false);}return true;}editing=switch(id){case "pressure"->AtmosMachineClientModel.Numeric.PRESSURE_KPA;case "rate"->AtmosMachineClientModel.Numeric.RATE_LPS;case "ratio"->AtmosMachineClientModel.Numeric.MIXER_PERCENT;case "thermostat"->AtmosMachineClientModel.Numeric.THERMOSTAT_K;default->null;};if(editing!=null){gasSearchFocused=false;gasSearch=null;String v=AtmosMachineClientModel.format(editing,authority(editing));draftModel=DraftFieldModel.number(24,new BigDecimal("-1000000"),new BigDecimal("1000000"));draftModel.snapshot(state.settings().revision(),v);var expandedLayout=AtmosMachineLayout.forControls(geometry.width(),geometry.height(),state.controls(),true,false,rowId(editing));draft=editorFactory.apply(expandedLayout.edit());draft.setValue(v);draft.setFocused(true);resizeWindow();}return true;}return true;}
    private double authority(AtmosMachineClientModel.Numeric n){var s=state.settings();return switch(n){case PRESSURE_KPA->s.targetPressureKpa().orElse(0d);case RATE_LPS->s.transferRateM3PerSecond().orElse(0d);case MIXER_PERCENT->s.mixerFractionA().orElse(0d);case THERMOSTAT_K->s.thermostatSetpointK().orElse(0d);};}
    private void sendGas(int i){send(AtmosMachineSettings.Operation.SET_FILTER_GAS,false,0,i);}
       private void send(AtmosMachineSettings.Operation op,boolean desired,double n,int gas){if(state==null||pending>=0)return;pending=nextRequest++;pendingSince=0;enabledIntent=op==AtmosMachineSettings.Operation.SET_ENABLED?desired:null;clientModel.pending(pending);sender.accept(new AtmosMachineControlRequest(state.containerId(),session,pending,state.settings().revision(),op,desired,n,gas));}
      private void submit(){if(state==null||editing==null||draft==null||pending>=0||!draft.isFocused()||!pointerAvailable())return;try{double shown=Double.parseDouble(draft.getValue());if(!AtmosMachineClientModel.validNumeric(editing,shown,state.controls())){message="Value outside supported limits";return;}draftModel.edit(draft.getValue());var intent=draftModel.submit();if(intent==null){message="Enter a valid number";return;}draftIntentId=intent.requestId();var op=switch(editing){case PRESSURE_KPA->AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA;case RATE_LPS->AtmosMachineSettings.Operation.SET_TRANSFER_RATE_M3_PER_SECOND;case MIXER_PERCENT->AtmosMachineSettings.Operation.SET_MIXER_FRACTION_A;case THERMOSTAT_K->AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K;};send(op,false,AtmosMachineClientModel.canonicalValue(editing,shown),0);}catch(NumberFormatException ex){message="Enter a valid number";}}
    @Override public boolean mouseDragged(double x,double y){return geometry.dragPointer(x,y);}
    @Override public boolean mouseReleased(int b){dragging=false;return geometry.releasePointer(b);}
     @Override public boolean mouseScrolled(double x,double y,double d){if(!gasSearchFocused||!contains(x,y)||d==0)return false;gasModel.scroll(d>0?-1:1);return true;}
      @Override public boolean keyPressed(int k,int s,int m){if(!pointerAvailable())return false;if(gasSearchFocused&&gasSearch!=null&&gasSearch.isFocused()){if(k==256){clearTextFocus();return true;}return gasSearch.keyPressed(k,s,m);}if(draft==null||!draft.isFocused())return false;if(k==256){clearTextFocus();return true;}if(k==257||k==335){submit();return true;}boolean handled=draft.keyPressed(k,s,m);if(handled)draftModel.edit(draft.getValue());return handled;}
      @Override public boolean charTyped(char c,int m){if(!pointerAvailable())return false;if(gasSearchFocused&&gasSearch!=null&&gasSearch.isFocused()){boolean handled=gasSearch.charTyped(c,m);if(handled){gasQuery=gasSearch.getValue();MachineSearchList.applyQuery(gasModel,gasQuery,v->{});}return handled;}if(draft==null||!draft.isFocused())return false;boolean handled=draft.charTyped(c,m);if(handled)draftModel.edit(draft.getValue());return handled;}
     @Override public boolean isSearchFocused(){return (draft!=null&&draft.isFocused())||(gasSearchFocused&&gasSearch!=null&&gasSearch.isFocused());}
     @Override public void clearTextFocus(){if(draft!=null)draft.setFocused(false);if(gasSearch!=null)gasSearch.setFocused(false);}
     @Override public void resetOwnedContext(){clearTextFocus();geometry.cancelPointer();}
}
