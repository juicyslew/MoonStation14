package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.*;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.*;
import com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint;
import com.juicyslew.moonstation14.ms14.ui.client.window.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import com.juicyslew.moonstation14.ms14.ui.model.DraftFieldModel;
import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList;
import com.juicyslew.moonstation14.ms14.ui.client.paint.MachineTextFieldPaint;
import com.juicyslew.moonstation14.mixin.client.EditBoxDisplayAccessor;
import java.math.BigDecimal;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.*;

/** GUI-scale Air Alarm painter and pointer owner; server snapshots remain authoritative. */
final class AirAlarmOverlay implements PointerOverlayWindow {
    private final AirAlarmWorkspace owner; private final UUID session;
    private final WindowGeometryController geometry;
    private int sw,sh,selected=-1,tab,scroll; private AirAlarmControlSnapshot state;
    private EditBox draft,searchBox; private final FilteredListModel searchModel=new FilteredListModel(256,5); private String search=""; private UUID editDevice; private long editRevision; private DraftFieldModel numericDraft; private DraftFieldModel.Intent numericIntent; private long numericWireRequest=-1; private boolean dragging,awaitingCommandAck; private long sequence,pending=-1,requestTick,lastClock=Long.MIN_VALUE,appliedRequest=-1; private final AirAlarmPendingIntentOwner intent=new AirAlarmPendingIntentOwner();
    private final LongSupplier clock; private String message="";
    private final Font editorFont;
    private final BooleanSupplier pointer; private final Consumer<AirAlarmControlRequest> sender; private final Consumer<UUID> closer;
    AirAlarmOverlay(AirAlarmWorkspace owner,UUID session,int sw,int sh){this(owner,session,sw,sh,()->!Minecraft.getInstance().mouseHandler.isMouseGrabbed(),PacketDistributor::sendToServer,owner::close,()->Minecraft.getInstance().level==null?0L:Minecraft.getInstance().level.getGameTime(),Minecraft.getInstance().font);}
    AirAlarmOverlay(AirAlarmWorkspace owner,UUID session,int sw,int sh,BooleanSupplier pointer,Consumer<AirAlarmControlRequest> sender,Consumer<UUID> closer){this(owner,session,sw,sh,pointer,sender,closer,()->0L,null);}
    AirAlarmOverlay(AirAlarmWorkspace owner,UUID session,int sw,int sh,BooleanSupplier pointer,Consumer<AirAlarmControlRequest> sender,Consumer<UUID> closer,LongSupplier clock){this(owner,session,sw,sh,pointer,sender,closer,clock,null);}
     AirAlarmOverlay(AirAlarmWorkspace owner,UUID session,int sw,int sh,BooleanSupplier pointer,Consumer<AirAlarmControlRequest> sender,Consumer<UUID> closer,LongSupplier clock,Font editorFont){this.owner=owner;this.session=session;this.sw=sw;this.sh=sh;this.pointer=pointer;this.sender=sender;this.closer=closer;this.clock=clock;this.editorFont=editorFont;var initial=AirAlarmPaintLayout.forContent(sw,sh,0,0);geometry=new WindowGeometryController(Math.max(1,sw),Math.max(1,sh),Math.max(0,(sw-initial.width())/2),Math.max(0,(sh-initial.height())/2),initial.width(),initial.height(),1,1,false);}
    void snapshot(AirAlarmControlSnapshot s){if(!session.equals(s.session()))return;
        if(pending>=0&&s.requestId()==pending){boolean command=awaitingCommandAck;pending=-1;awaitingCommandAck=false;if(!s.accepted()){if(command){intent.acknowledge(false);resolveNumeric(state,false);}message="Request rejected or stale";}else if(command){intent.acknowledge(true);message="";}}
        boolean newest=s.requestId()>=appliedRequest;UUID selectedId=selectedDevice()==null?null:selectedDevice().id();
        var devices=new ArrayList<AirAlarmControlSnapshot.Device>();
        if(newest){for(var incoming:s.devices()){var prior=find(incoming.id());devices.add(prior!=null&&prior.revision()>incoming.revision()?prior:incoming);}}
        else {if(state==null)return;for(var prior:state.devices()){var incoming=s.devices().stream().filter(d->d.id().equals(prior.id())).findFirst().orElse(null);devices.add(incoming!=null&&incoming.revision()>prior.revision()?incoming:prior);}}
        if(newest){appliedRequest=s.requestId();state=new AirAlarmControlSnapshot(s.containerId(),s.session(),s.requestId(),s.accepted(),s.powered(),devices);var entries=new ArrayList<FilteredListModel.Entry>();for(var d:state.devices())entries.add(new FilteredListModel.Entry(d.id().toString(),AirAlarmPaint.searchLabel(d)));searchModel.snapshot(s.requestId(),entries);searchModel.filter(search);if(selectedId!=null)searchModel.select(selectedId.toString());}
        else state=new AirAlarmControlSnapshot(state.containerId(),state.session(),state.requestId(),state.accepted(),state.powered(),devices);
         if(intent.pending()){var observed=new AirAlarmControlSnapshot(state.containerId(),state.session(),s.requestId(),s.accepted(),state.powered(),state.devices());var outcome=intent.observe(observed,now());if(outcome==AirAlarmPendingIntentOwner.Outcome.SUCCEEDED){resolveNumeric(state,true);finish("");}else if(outcome==AirAlarmPendingIntentOwner.Outcome.CONFLICT){resolveNumeric(state,false);finish("Device changed; refresh and retry");}else if(outcome==AirAlarmPendingIntentOwner.Outcome.REJECTED){resolveNumeric(state,false);finish("Request rejected or stale");}}
        if(selectedId!=null&&indexOf(selectedId)>=0)selected=indexOf(selectedId);else if(state!=null&&!state.devices().isEmpty())selected=0;updateContentBounds();
    }
    private void finish(String text){pending=-1;message=text;}
    private AirAlarmControlSnapshot.Device find(UUID id){return state==null?null:state.devices().stream().filter(d->d.id().equals(id)).findFirst().orElse(null);}
     private void resize(int w,int h){if(sw!=w||sh!=h){sw=w;sh=h;geometry.setScreenSize(Math.max(1,w),Math.max(1,h));updateContentBounds();}}
     private void updateContentBounds(){var l=AirAlarmPaintLayout.forContent(sw,sh,tab,state==null?0:state.devices().size());geometry.setBounds(geometry.x(),geometry.y(),l.width(),l.height());}
    @Override public boolean visible(){return true;}
    @Override public boolean pointerAvailable(){return pointer.getAsBoolean();}
    @Override public boolean contains(double x,double y){return x>=geometry.x()&&y>=geometry.y()&&x<geometry.x()+geometry.width()&&y<geometry.y()+geometry.height();}
    @Override public boolean ownsPointerCapture(){return dragging||geometry.isPointerCaptured();}
    private long now(){long value=clock.getAsLong();if(value>lastClock)lastClock=value;return lastClock==Long.MIN_VALUE?0:lastClock;}
    void tick(){long tick=now();if(intent.pending()){var result=intent.observe(null,tick);if(result==AirAlarmPendingIntentOwner.Outcome.TIMED_OUT){if(numericDraft!=null&&numericIntent!=null){numericDraft.cancelPending(numericIntent.requestId());numericDraft.edit(numericIntent.value());numericIntent=null;numericWireRequest=-1;}finish("Request timed out");}else if(intent.shouldPoll(tick,20)&&pending<0)sendQuery();}if(pending>=0&&tick-requestTick>=100)finish("Request timed out");}
    @Override public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int w,int h){resize(w,h);tick();int x=geometry.x(),y=geometry.y();var l=AirAlarmPaintLayout.forContent(geometry.width(),geometry.height(),tab,state==null?0:state.devices().size());int lx=mx-x,ly=my-y;
          var view=new AirAlarmPaint.View(state,x,y,geometry.width(),geometry.height(),lx,ly,selected,scroll,tab,search,draft==null?null:draft.getValue(),message,pending>=0,searchBox!=null&&searchBox.isFocused(),editorSnapshot(searchBox),editorSnapshot(draft),intendedEnabled());
        AirAlarmPaint.paint(new GuiGraphicsPaint(g,Minecraft.getInstance().font),view);
         }
    private static MachineTextFieldPaint.Snapshot editorSnapshot(EditBox box){if(box==null)return null;var access=(EditBoxDisplayAccessor)box;int tick=(int)(Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime());return new MachineTextFieldPaint.Snapshot(box.getValue(),box.getCursorPosition(),access.ms14$highlightPos(),access.ms14$displayPos(),box.isFocused(),true,(tick/6)%2==0,tick);}
    @Override public boolean mouseClicked(double x,double y,int b){if(!pointerAvailable()||!contains(x,y)||b!=0)return false;PointerOverlayRegistry.bringToFront(this);int lx=(int)(x-geometry.x()),ly=(int)(y-geometry.y());var l=AirAlarmPaintLayout.forContent(geometry.width(),geometry.height(),tab,state==null?0:state.devices().size());
         var close=WindowChromeButtons.closeBounds(l.close().x(),l.close().y(),l.close().width(),l.close().height());if(WindowChromeButtons.contains(close,lx,ly)){closer.accept(session);return true;}
        if(TitlePressRouting.isTitlePress(lx,ly,b,0,0,l.width(),24,l.width(),l.height(),false)){dragging=geometry.beginPointer(x,y,b,12,28);clearTextFocus();return true;}
          for(var hit:AirAlarmPaint.hitRegions(l,new AirAlarmPaint.View(state,geometry.x(),geometry.y(),geometry.width(),geometry.height(),lx,ly,selected,scroll,tab,search,draft==null?null:draft.getValue(),message,pending>=0,searchBox!=null&&searchBox.isFocused()))){if(!hit.bounds().contains(lx,ly))continue;String id=hit.id();
            if(id.equals("tab_devices")){tab=0;updateContentBounds();return true;}if(id.equals("tab_controls")){tab=1;updateContentBounds();return true;}if(id.equals("tab_filters")){tab=2;updateContentBounds();return true;}
             if(id.equals("device_search")){clearTextFocus();if(searchBox==null){searchBox=new EditBox(editorFont,l.search().x(),l.search().y(),l.search().width(),18,Component.literal("Search"));searchBox.setMaxLength(128);searchBox.setResponder(this::editSearch);searchBox.setValue(search);}searchBox.setFocused(true);searchBox.mouseClicked(lx,ly,b);return true;}
            if(id.startsWith("device:")){UUID idv=UUID.fromString(id.substring(7));selected=indexOf(idv);if(draft!=null)clearTextFocus();return true;}
            if(id.equals("refresh")){send(AirAlarmControlRequest.Operation.QUERY,null,0,false,0,0);return true;}
             if(id.equals("configure")){tab=1;updateContentBounds();return true;}
            var d=selectedDevice();if(d==null)return true;
            if(id.equals("enabled")){send(AirAlarmControlRequest.Operation.SET_ENABLED,d.id(),d.revision(),!d.enabled(),0,0);return true;}
            if(id.equals("mode")){int n=(d.mode()+1)%VentilationDeviceConfig.ScrubberMode.values().length;send(AirAlarmControlRequest.Operation.SET_SCRUBBER_MODE,d.id(),d.revision(),false,n,0);return true;}
            if(id.startsWith("gas:")){int n=Integer.parseInt(id.substring(4));send(AirAlarmControlRequest.Operation.SET_FILTER_GAS,d.id(),d.revision(),(d.filterBits()&(1<<n))==0,n,0);return true;}
               if(id.equals("numeric")){clearTextFocus();beginNumeric(d);draft=new EditBox(editorFont,l.numeric().x(),l.numeric().y(),l.numeric().width(),20,Component.literal("Value"));draft.setValue(numericDraft.draft());draft.setFocused(true);return true;}
        }return true;}
    private int indexOf(UUID id){for(int i=0;i<state.devices().size();i++)if(state.devices().get(i).id().equals(id))return i;return -1;}
    private void beginNumeric(AirAlarmControlSnapshot.Device d){editDevice=d.id();editRevision=d.revision();String value=numericText(d);numericDraft=DraftFieldModel.number(24,BigDecimal.ZERO,d.kind()==VentilationDeviceConfig.Kind.VENT?new BigDecimal("4500"):new BigDecimal("0.2"));numericDraft.snapshot(d.revision(),value);numericIntent=null;numericWireRequest=-1;}
    private void resolveNumeric(AirAlarmControlSnapshot snapshot,boolean accepted){if(numericDraft==null||numericIntent==null||snapshot==null||intent.pending()||numericWireRequest<0)return;var target=snapshot.devices().stream().filter(d->d.id().equals(editDevice)).findFirst().orElse(null);if(target==null)return;String prior=numericIntent.value();boolean resolved=numericDraft.resolve(numericIntent.requestId(),target.revision(),numericText(target),accepted);if(resolved&&!accepted)numericDraft.edit(prior);if(resolved){numericIntent=null;numericWireRequest=-1;}}
    void editSearch(String value){search=value;MachineSearchList.applyQuery(searchModel,value,v->{});scroll=searchModel.first();}
     private Boolean intendedEnabled(){var d=selectedDevice();return d==null?null:intent.intendedEnabled(d.id());}
     AirAlarmPaint.View testView(){return new AirAlarmPaint.View(state,geometry.x(),geometry.y(),geometry.width(),geometry.height(),0,0,selected,scroll,tab,search,numericDraft==null?null:numericDraft.draft(),message,pending>=0,false,null,null,intendedEnabled());}
     Boolean testIntendedEnabled(){return intendedEnabled();}
    void testBeginNumeric(UUID id){var d=find(id);if(d==null)throw new IllegalArgumentException("unknown device");beginNumeric(d);}
    void testEditNumeric(String text){if(numericDraft==null)throw new IllegalStateException("no numeric target");numericDraft.edit(text);}
    boolean testSubmitNumeric(){return submitNumericDraft();}
    private boolean submitNumericDraft(){var d=find(editDevice);if(d==null||d.revision()!=editRevision||pending>=0||!intent.admitsSetting())return false;var submitted=numericDraft.submit();if(submitted==null)return false;numericIntent=submitted;numericWireRequest=sequence;double value=Double.parseDouble(submitted.value());if(!AirAlarmNumericBounds.valid(d.kind(),value)){numericDraft.cancelPending(submitted.requestId());numericIntent=null;numericWireRequest=-1;message="Value outside supported limits";return false;}var op=d.kind()==VentilationDeviceConfig.Kind.VENT?AirAlarmControlRequest.Operation.SET_EXTERNAL_PRESSURE_BOUND:AirAlarmControlRequest.Operation.SET_VOLUME_RATE;send(op,d.id(),editRevision,false,0,value);return true;}
    String testNumericState(){return numericDraft==null?"none":numericDraft.state().name();}
    String testMessage(){return message;}
    UUID testEditDevice(){return editDevice;}
    private AirAlarmControlSnapshot.Device selectedDevice(){return state==null||selected<0||selected>=state.devices().size()?null:state.devices().get(selected);}
    private static String numericText(AirAlarmControlSnapshot.Device d){return String.format(Locale.ROOT,"%.3f",d.kind()==VentilationDeviceConfig.Kind.VENT?d.externalPressureBoundKPa():d.volumeRateM3PerSecond());}
    private void sendQuery(){send(AirAlarmControlRequest.Operation.QUERY,null,0,false,0,0);}
    private void send(AirAlarmControlRequest.Operation op,UUID device,long revision,boolean want,int index,double value){if(pending>=0||!intent.admitsSetting()&&op!=AirAlarmControlRequest.Operation.QUERY)return;long id=sequence++;pending=id;requestTick=now();awaitingCommandAck=op!=AirAlarmControlRequest.Operation.QUERY;var request=new AirAlarmControlRequest(0,session,id,device==null?new UUID(0,0):device,revision,op,want,index,value);if(awaitingCommandAck)intent.begin(request,requestTick,100);sender.accept(request);}
    @Override public boolean mouseDragged(double x,double y){if(!dragging)return false;geometry.dragPointer(x,y);return true;}
    @Override public boolean mouseReleased(int b){if(b!=0)return false;dragging=false;return geometry.releasePointer(b);}
    @Override public boolean mouseScrolled(double x,double y,double d){if(!contains(x,y)||state==null)return false;searchModel.scroll(-(int)Math.signum(d));scroll=searchModel.first();return true;}
      @Override public boolean keyPressed(int k,int s,int m){if(searchBox!=null&&searchBox.isFocused()){if(k==256){clearTextFocus();return true;}return searchBox.keyPressed(k,s,m);}if(draft==null||!draft.isFocused())return false;if(k==256){clearTextFocus();if(numericDraft!=null)numericDraft.discard();return true;}if(k==257||k==335){if(find(editDevice)==null||find(editDevice).revision()!=editRevision){clearTextFocus();message="Device changed; refresh and retry";return true;}numericDraft.edit(draft.getValue());try{if(!submitNumericDraft())message="Enter a valid changed number";}catch(NumberFormatException e){message="Enter a valid number";}return true;}boolean handled=draft.keyPressed(k,s,m);if(handled&&numericDraft!=null)numericDraft.edit(draft.getValue());return handled;}
     @Override public boolean charTyped(char c,int m){if(searchBox!=null&&searchBox.isFocused())return searchBox.charTyped(c,m);if(draft!=null&&draft.isFocused()){boolean ok=draft.charTyped(c,m);if(ok&&numericDraft!=null)numericDraft.edit(draft.getValue());return ok;}return false;}
     @Override public boolean isSearchFocused(){if(!pointerAvailable()){clearTextFocus();return false;}return searchBox!=null&&searchBox.isFocused()||draft!=null&&draft.isFocused();}
     @Override public void clearTextFocus(){if(draft!=null)draft.setFocused(false);if(searchBox!=null)searchBox.setFocused(false);}
    @Override public void resetOwnedContext(){clearTextFocus();geometry.cancelPointer();}
}
