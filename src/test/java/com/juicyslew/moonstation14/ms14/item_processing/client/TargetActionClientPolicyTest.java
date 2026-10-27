package com.juicyslew.moonstation14.ms14.item_processing.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TargetActionClientPolicyTest {
    @Test void replyMustMatchLiveRequestAndContextAndRemainUnexpired(){
        var target=UUID.randomUUID();var dim=ResourceLocation.withDefaultNamespace("overworld");
        var request=new TargetActionClientPolicy.Request(4,9,12,"right","token",target,dim,100);
        var good=new BodyTargetActionSnapshot(4,9,12,"right","token",target,Component.literal("mouse"),true,false,Component.literal("reason"));
        assertTrue(TargetActionClientPolicy.accepts(request,good,100,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,good,101,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,good,10,false,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,good,10,true,ResourceLocation.withDefaultNamespace("nether"),target));
        assertFalse(TargetActionClientPolicy.accepts(request,good,10,true,dim,UUID.randomUUID()));
        assertFalse(TargetActionClientPolicy.accepts(null,good,10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,8,12,"right","token",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(3,9,12,"right","token",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,9,11,"right","token",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,9,12,"left","token",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,9,12,"right","different",target,Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
        assertFalse(TargetActionClientPolicy.accepts(request,new BodyTargetActionSnapshot(4,9,12,"right","token",UUID.randomUUID(),Component.literal("mouse"),true,false,Component.literal("reason")),10,true,dim,target));
    }

    @Test void sharedLayoutUsesHalfOpenBoundsForWindowAndAction(){
        var layout=new TargetActionClientPolicy.Layout(10,20);
        assertTrue(layout.window().contains(10,20));assertFalse(layout.window().contains(194,20));
        assertTrue(layout.action().contains(18,52));assertFalse(layout.action().contains(186,52));
        assertTrue(layout.reason().contains(18,78));assertFalse(layout.reason().contains(186,78));
        assertTrue(layout.window().contains(layout.action().x(),layout.action().y()));
        assertTrue(layout.window().contains(layout.reason().x(),layout.reason().y()+layout.reason().height()-1));
        assertTrue(layout.window().contains(layout.close().x(),layout.close().y()));
        assertTrue(TargetActionClientPolicy.dispatchesButcher(true,true,layout.action().contains(layout.action().x(),layout.action().y())));
        assertFalse(TargetActionClientPolicy.dispatchesButcher(true,false,layout.action().contains(layout.action().x(),layout.action().y())));
        assertFalse(TargetActionClientPolicy.dispatchesButcher(true,true,layout.action().contains(layout.close().x(),layout.close().y())));
        assertFalse(TargetActionClientPolicy.dispatchesButcher(false,true,true));
    }
    @Test void selectedEnabledGunRowsDispatchIndependentOfLegacyButcherView(){
        var target=UUID.randomUUID();
        var spin=new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.SPIN_REVOLVER,Component.literal("spin"),true,null);
        var disabled=new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.EMPTY_REVOLVER,Component.literal("empty"),false,Component.literal("no"));
        var gunOnly=new BodyTargetActionSnapshot(1,2,3,"right",null,target,Component.literal("gun"),java.util.List.of(spin,disabled));
        assertFalse(gunOnly.enabled());
        var layout=new TargetActionClientPolicy.Layout(20,20,400,300,gunOnly.actions().size());
        boolean hitSpin=layout.action(0).contains(layout.action(0).x(),layout.action(0).y());
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_SPIN,
                TargetActionClientPolicy.dispatchAction(gunOnly,spin.id(),hitSpin).orElseThrow());
        assertTrue(TargetActionClientPolicy.dispatchAction(gunOnly,disabled.id(),true).isEmpty());
        assertTrue(TargetActionClientPolicy.dispatchAction(gunOnly,spin.id(),false).isEmpty());
        var mixed=new BodyTargetActionSnapshot(1,2,3,"right",null,target,Component.literal("mixed"),java.util.List.of(
                new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.BUTCHER,Component.literal("butcher"),false,Component.literal("no")),spin));
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_SPIN,
                TargetActionClientPolicy.dispatchAction(mixed,spin.id(),true).orElseThrow());
    }
    @Test void viewportFittingClampsAnchorsAndResizesEverySemanticRectangle(){
        var standard=new TargetActionClientPolicy.Layout(310,170,320,180);
        assertTrue(standard.usable());assertEquals(184,standard.width());assertEquals(92,standard.height());
        assertEquals(136,standard.x());assertEquals(88,standard.y());
        var narrow=new TargetActionClientPolicy.Layout(310,170,160,90);
        assertTrue(narrow.usable());assertEquals(160,narrow.width());assertEquals(90,narrow.height());assertEquals(0,narrow.x());assertEquals(0,narrow.y());
        var rects=java.util.List.of(narrow.window(),narrow.action(),narrow.reason(),narrow.close());
        for(var rect:rects)assertTrue(narrow.rectWithinViewport(rect),"semantic rectangle must fit viewport: "+rect);
        assertTrue(narrow.action().within(narrow.window()));assertTrue(narrow.reason().within(narrow.window()));assertTrue(narrow.close().within(narrow.window()));
        assertEquals(144,narrow.action().width());assertEquals(144,narrow.reason().width());assertEquals(28,narrow.reason().height());
        var tiny=new TargetActionClientPolicy.Layout(400,-3,100,50);assertFalse(tiny.usable());
        for(var rect:java.util.List.of(tiny.window(),tiny.action(),tiny.reason(),tiny.close()))assertTrue(tiny.rectWithinViewport(rect));
        var resized=new TargetActionClientPolicy.Layout(310,170,640,360);
        assertEquals(184,resized.width());assertEquals(92,resized.height());assertEquals(310,resized.x());assertEquals(170,resized.y());
    }
}
