package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.juicyslew.moonstation14.ms14.fixture.FixtureSiteContext;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;

/** Permission-2 administrative commands for the finite ventilation demonstration. */
public final class VentilationFixtureCommands {
    private static final Map<MinecraftServer, FixtureSiteContext> SITES=Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean stopListenerRegistered;
    private VentilationFixtureCommands() { }
    public static void registerCommands(RegisterCommandsEvent event) {
        if(!stopListenerRegistered){NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{var sites=SITES.remove(e.getServer());if(sites!=null)sites.clear();});stopListenerRegistered=true;}
        event.getDispatcher().register(Commands.literal("ms14").requires(source -> source.hasPermission(2))
                 .then(Commands.literal("firelock_fixture")
                 .then(Commands.literal("build").executes(ctx -> firelock(ctx,0)))
                 .then(Commands.literal("initialize").executes(ctx -> firelock(ctx,1)))
                 .then(Commands.literal("reset").executes(ctx -> firelock(ctx,2)))
                   .then(Commands.literal("scenarios").executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.literal("Firelock scenarios (uniform alarm: warning does not close; danger closes; differential: local lock): " + String.join("; ", java.util.Arrays.stream(VentilationFixturePlan.FirelockScenario.values()).map(x -> x.name().toLowerCase(java.util.Locale.ROOT)+"="+switch(x){case NORMAL->"normal/reset gas";case PRESSURE_WARNING->"uniform pressure warning";case PRESSURE_DANGER->"uniform pressure danger";case TEMPERATURE_WARNING->"uniform temperature warning";case TEMPERATURE_DANGER->"uniform temperature danger";case PRESSURE_DIFFERENTIAL->"pressure differential";case TEMPERATURE_DIFFERENTIAL->"temperature differential";case COMBINED_DIFFERENTIAL->"both differentials";}).toList())+". Differential requires fully CLOSED door; first apply pressure_danger and wait CLOSED. No forced door phase."), false); return 1; }))
                  .then(Commands.literal("scenario").then(Commands.argument("scenario", com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((ctx,builder)->{for(var s:VentilationFixturePlan.FirelockScenario.values())builder.suggest(s.name().toLowerCase(java.util.Locale.ROOT));return builder.buildFuture();}).executes(ctx -> firelock(ctx,3))))
                 .then(Commands.literal("status").executes(ctx -> firelock(ctx,4)))
                 .then(Commands.literal("enter").executes(ctx -> firelock(ctx,5)))
                 .then(Commands.literal("power").then(Commands.literal("on").executes(ctx -> firelock(ctx,6))).then(Commands.literal("off").executes(ctx -> firelock(ctx,7)))))
                .then(Commands.literal("ventilation_fixture")
                        .then(Commands.literal("build").then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> run(ctx, 0))))
                        .then(Commands.literal("initialize").then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> run(ctx, 1))))
                        .then(Commands.literal("status").then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> run(ctx, 2))))));
    }
    private static int firelock(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,int action) {
        var s=ctx.getSource(); var level=s.getLevel(); UUID operator=s.getEntity()==null?new UUID(0,0):s.getEntity().getUUID();
        var sites=SITES.computeIfAbsent(s.getServer(),ignored->new FixtureSiteContext()); BlockPos origin;
        if(action==0){var authority=s.getPlayer()==null?null:HandActorAuthority.resolve(s.getPlayer()).orElse(null);
            if(authority==null){s.sendFailure(Component.literal("A player with an authorized controlled body is required to build without coordinates."));return 0;}
            var body=authority.body(); if(body.level()!=level||body.isRemoved()||!body.isAlive()||!body.isAddedToLevel()||level.getEntity(body.getUUID())!=body){s.sendFailure(Component.literal("Authorized controlled body is unavailable here."));return 0;}
             origin=fixtureOriginNear(body.blockPosition());
             String result=VentilationFixtureService.buildFirelock(level,origin);
             boolean succeeded=result.startsWith("Built finite ventilation fixture");
             if(succeeded) sites.buildResult(operator,level.dimension(),origin,true);
             String message=result+" (origin "+origin.toShortString()+")";
             if(!succeeded){s.sendFailure(Component.literal(message));return 0;}
             s.sendSuccess(()->Component.literal(message),false);return 1;
        }
        origin=sites.find(operator,level.dimension()); if(origin==null){s.sendFailure(Component.literal("No firelock fixture site remembered for you in this dimension; build one first."));return 0;}
        String result=switch(action){case 1->VentilationFixtureService.initializeFirelock(level,origin);case 2->VentilationFixtureService.resetFirelock(level,origin);case 3->VentilationFixtureService.firelockScenario(level,origin,com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"scenario"));case 4->VentilationFixtureService.status(level,origin);case 5->enterAt(ctx,origin);case 6->VentilationFixtureService.setFirelockPower(level,origin,true);case 7->VentilationFixtureService.setFirelockPower(level,origin,false);default->throw new IllegalStateException();};
          if(!successfulResult(action,result)){s.sendFailure(Component.literal(result));return 0;}
         s.sendSuccess(()->Component.literal(result),false);return 1;
    }
    private static String enterAt(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,BlockPos origin){
        var source=ctx.getSource();var level=source.getLevel();
        var entry=VentilationFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElse(null);
        var plan=VentilationFixturePlan.firelockPlan();var target=origin.offset(plan.sourceCells().getFirst());
        if(entry!=null&&entry.firelockVariant()&&entry.initialized()&&entry.firelockUUID()!=null
                &&level.getBlockEntity(origin.offset(plan.firelock())) instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity door
                &&entry.firelockUUID().equals(door.identity())&&level.hasChunkAt(target)&&level.getBlockState(target).isAir()&&level.getBlockState(target.above()).isAir()){
             var player=source.getPlayer();
             var authority=player==null?null:HandActorAuthority.resolve(player).orElse(null);
            if(authority!=null){var body=authority.body();var box=body.getBoundingBox().move(target.getX()+.5-body.getX(),target.getY()-body.getY(),target.getZ()+.5-body.getZ());
                if(body.level()==level&&!body.isRemoved()&&body.isAlive()&&body.isAddedToLevel()&&level.getEntity(body.getUUID())==body
                        &&com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(body).isEmpty()&&level.noCollision(body,box)){
                    body.teleportTo(target.getX()+.5,target.getY(),target.getZ()+.5);body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);return "Moved existing controlled body into safe fixture interior "+target+".";
                }
            }
        }
        return "Enter refused: exact initialized owned firelock fixture and clear interior required.";
    }
    private static int run(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, int action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = ctx.getSource();
        var level = source.getLevel();
        var pos = BlockPosArgument.getBlockPos(ctx, "origin");
        String result = switch (action) {
            case 0 -> VentilationFixtureService.build(level, pos);
            case 1 -> VentilationFixtureService.initialize(level, pos);
            default -> VentilationFixtureService.status(level, pos);
        };
        if(!legacySuccessfulResultForTest(action,result)){source.sendFailure(Component.literal(result));return 0;}
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    /** Places the positive-X footprint beside, rather than through, the controlled body. */
    static BlockPos fixtureOriginNear(BlockPos bodyPosition) {
        return bodyPosition.offset(3, 0, -VentilationFixturePlan.DEPTH / 2);
    }

    static boolean successfulResult(int action, String result) {
        if (result == null || result.isBlank()) return false;
        return switch (action) {
            case 0 -> result.startsWith("Built finite ventilation fixture");
            case 1 -> result.startsWith("Initialized connected firelock");
            case 2 -> result.startsWith("Reset using trusted operator");
            case 3 -> result.startsWith("Applied ");
            case 4 -> result.startsWith("Fixture ") && !result.startsWith("No saved fixture marker");
            case 5 -> result.startsWith("Moved existing controlled body");
            case 6, 7 -> result.startsWith("Fixture APC turned ") || result.startsWith("Fixture APC already ");
            default -> false;
        };
    }

    // Legacy coordinate-addressed commands retain their original action/result semantics.
    static boolean legacySuccessfulResultForTest(int action, String result) {
        if (result == null || result.isBlank()) return false;
        if (result.startsWith("Refused") || result.startsWith("Build refused")
                || result.startsWith("Initialization refused") || result.startsWith("No saved fixture")
                || result.startsWith("Fixture already") || result.startsWith("Partial")
                || result.startsWith("Structure placed but") || result.startsWith("Gas initialized but")
                || result.startsWith("Initialization deferred") || result.startsWith("Initialization guard")) return false;
        return action != 2 || !result.startsWith("No fixture");
    }
}
