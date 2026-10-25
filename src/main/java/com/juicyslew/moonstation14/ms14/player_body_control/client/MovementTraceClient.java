package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.diagnostics.MovementTraceRecorder;
import com.juicyslew.moonstation14.ms14.player_body_control.diagnostics.MovementTraceSession;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Path;
import java.util.Map;

/** Client command and lifecycle owner for opt-in owned-body movement observations. */
public final class MovementTraceClient {
    private static final com.juicyslew.moonstation14.ms14.player_body_control.diagnostics.MovementTraceFrameSampler FRAMES =
            new com.juicyslew.moonstation14.ms14.player_body_control.diagnostics.MovementTraceFrameSampler();
    private MovementTraceClient() { }
    public static void register(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        var root = com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("ms14movementtrace");
        root.then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("start")
                .executes(ctx -> start(ctx.getSource(),30))
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.commands.CommandSourceStack,Integer>argument("seconds",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,60))
                        .executes(ctx -> start(ctx.getSource(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"seconds")))));
        root.then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("stop")
                .executes(ctx -> finish("stopped",ctx.getSource())));
        event.getDispatcher().register(root);
    }
    public static void sessionChanged() { finish("sessionChanged",null); }
    public static boolean safeRecord(MovementTraceRecorder.Context context,String kind,long now,long tick,long sequence,Map<String,?> values) {
        if(context==null || MovementTraceSession.activeContext()==null)return false;
        try { if(!context.equals(MovementTraceSession.activeContext()))return false; return MovementTraceSession.record(context,kind,now,tick,sequence,values); }
        catch(RuntimeException e) { finish("observerFailure",null); return false; }
    }
    private static int start(net.minecraft.commands.CommandSourceStack source,int seconds) {
        var context=GhostControlClient.movementTraceContext();
        if(context==null) { source.sendFailure(Component.literal("Movement trace requires a committed owned body session.")); return 0; }
        if(!MovementTraceSession.start(context,System.nanoTime(),seconds)) { source.sendFailure(Component.literal("A movement trace is already active.")); return 0; }
        FRAMES.reset();
        source.sendSuccess(()->Component.literal("Movement trace started for "+seconds+"s; client observations active, server stream "+(Minecraft.getInstance().getSingleplayerServer()!=null?"integrated-process available":"unavailable on remote client")+"."),false);
        return 1;
    }
    public static void cameraSetup(net.minecraft.client.Camera camera,float partialTick) {
        var context=MovementTraceSession.activeContext();
        if(context==null)return;
        try {
            var current=GhostControlClient.movementTraceContext();
            if(!context.equals(current)) { finish("sessionChanged",null); return; }
            var mc=Minecraft.getInstance();
            var body=mc.level.getEntity(context.bodyEntityId());
            if(body==null || camera.getEntity()!=body)return;
            var frame=FRAMES.current();
            if(!FRAMES.canSampleOwnedBody())return;
            var p=camera.getPosition(); var b=body.position(); long now=System.nanoTime();
            boolean recorded=safeRecord(context,"CAMERA_SETUP",now,-1,-1,Map.ofEntries(
                    Map.entry("frameId",frame.id()),Map.entry("frameIntervalNanos",frame.intervalNanos()),Map.entry("frameIntervalKnown",frame.intervalKnown()),
                    Map.entry("cameraSetupOffsetNanos",Math.max(0,now-frame.startNanos())),Map.entry("partialTick",partialTick),Map.entry("cameraX",p.x),Map.entry("cameraY",p.y),Map.entry("cameraZ",p.z),
                    Map.entry("bodyX",b.x),Map.entry("bodyY",b.y),Map.entry("bodyZ",b.z),
                    Map.entry("xo",body.xo),Map.entry("yo",body.yo),Map.entry("zo",body.zo)));
            FRAMES.sampleRecorded(recorded);
            if(MovementTraceSession.expired(now)) finish("expired",null);
        } catch(RuntimeException e) { finish("observerFailure",null); }
    }
    @net.neoforged.bus.api.SubscribeEvent public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        if(MovementTraceSession.activeContext()==null)return;
        var frame=FRAMES.onFrame(System.nanoTime());
        safeRecord(MovementTraceSession.activeContext(),"FRAME",frame.startNanos(),-1,-1,Map.of("frameId",frame.id(),
                "frameIntervalNanos",frame.intervalNanos(),"frameIntervalKnown",frame.intervalKnown()));
    }
    @net.neoforged.bus.api.SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var c=MovementTraceSession.activeContext(); if(c==null)return;
        if(!c.equals(GhostControlClient.movementTraceContext())) { finish("sessionChanged",null); return; }
        if(MovementTraceSession.expired(System.nanoTime())) finish("expired",null);
    }
    private static int finish(String reason,net.minecraft.commands.CommandSourceStack source) {
        var mc= Minecraft.getInstance();
        var trace=MovementTraceSession.stop(reason,mc.getSingleplayerServer()!=null?"INTEGRATED_SAME_PROCESS":"UNAVAILABLE_REMOTE");
        if(trace==null)return 0;
        net.minecraft.Util.ioPool().execute(()->{ Path path=null; Exception failure=null;
            try { path=MovementTraceRecorder.export(trace,mc.gameDirectory.toPath()); } catch(Exception e){failure=e;}
            Path result=path; Exception error=failure;
            mc.execute(()->{
                Component msg=Component.literal(error==null?"Movement trace saved: "+result.toAbsolutePath():"Movement trace export failed: "+error.getMessage());
                if(source!=null)source.sendSuccess(()->msg,false); else if(mc.player!=null)mc.player.displayClientMessage(msg,false);
            });
        });
        return 1;
    }
}
