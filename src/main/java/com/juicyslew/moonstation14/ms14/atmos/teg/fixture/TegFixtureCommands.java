package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Permission-2, explicit disposable TEG fixture commands; production devices are never auto-placed. */
@EventBusSubscriber(modid="moonstation14")
public final class TegFixtureCommands {
    private TegFixtureCommands() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }
    static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ms14").requires(source -> source.hasPermission(2))
                .then(Commands.literal("teg_fixture")
                        .then(operation("build",TegContinuousFixtureService::build,true))
                        .then(automaticStart())
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource()))
                                .then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> status(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "origin")))))
                        .then(Commands.literal("finite").then(operation("build",TegFueledFixtureService::build,true))
                                .then(Commands.literal("status").executes(ctx->statusFinite(ctx.getSource()))
                                        .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->statusFinite(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"))))))));
    }

    @FunctionalInterface private interface FixtureOperation { String apply(ServerLevel level,BlockPos origin); }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> automaticStart() {
        return Commands.literal("start").executes(ctx->startAutomatic(ctx.getSource(),null))
                .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->startAutomatic(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"))));
    }
    private static int startAutomatic(net.minecraft.commands.CommandSourceStack source,BlockPos origin) {
        Entity body=activeBody(source);
        if(origin==null) {
            if(body==null)return failure(source,"Refused: active controlled character body unavailable; supply fixture coordinates.");
            var data=TegFixtureSavedData.existing(source.getLevel()).orElse(null);
            origin=data==null?null:nearestContinuousOrigin(data.entries(),body.blockPosition());
            if(origin==null)return failure(source,"No saved continuous TEG fixture nearby in this dimension.");
        }
        var player=source.getEntity() instanceof ServerPlayer p?p:null;
        var initiator=player==null?new java.util.UUID(0,0):player.getUUID();
        String result=TegContinuousFixtureService.startAutomatic(source.getLevel(),origin,initiator);
        if(result.startsWith("Automatic startup armed")){source.sendSuccess(()->Component.literal("TEG startup started."),false);return 1;}
        return failure(source,result);
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> operation(String name,FixtureOperation operation,boolean building) {
        return Commands.literal(name).executes(ctx->operate(ctx.getSource(),null,operation,building))
                .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->operate(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"),operation,building)));
    }
    private static int operate(net.minecraft.commands.CommandSourceStack source,BlockPos origin,FixtureOperation operation,boolean building) {
        ServerLevel level=source.getLevel();
        if(building) {
            var placement=placement(source,origin);if(placement==null)return 0;origin=placement.origin();level=placement.level();
        } else if(origin==null) {
            Entity body=activeBody(source);if(body==null)return failure(source,"Refused: active controlled character body unavailable; supply fixture coordinates.");
            var data=TegFixtureSavedData.existing(level).orElse(null);if(data==null)return failure(source,"No saved continuous TEG fixture nearby.");
            origin=nearestContinuousOrigin(data.entries(),body.blockPosition());if(origin==null)return failure(source,"No saved continuous TEG fixture nearby in this dimension.");
        }
        String result=operation.apply(level,origin);
        if(result.startsWith("Built fueled TEG hardware"))return buildFiniteComplete(source,new Placement(level,origin),result);
        if(result.startsWith("Built continuous TEG hardware")||result.startsWith("Operating startup admitted")||result.startsWith("Ignition admitted")
                ||result.startsWith("Operating startup waiting for power topology")
                ||result.startsWith("RUNNING controls selected")||result.startsWith("Startup heater disabled")) {
            String message=result;source.sendSuccess(()->Component.literal(message),false);return 1;
        }
        return failure(source,result);
    }

    private static int buildFiniteComplete(net.minecraft.commands.CommandSourceStack source,Placement placement,String result) {
        String initialized=TegFueledFixtureService.initialize(placement.level(),placement.origin());
        if(!initialized.startsWith("Initialized fueled finite fixture once:"))return failure(source,result+" Initialization failed after construction; "+initialized+" The partial fixture is not rolled back; use manual recovery and do not retry.");
        var plan=TegFueledFixturePlan.layout(placement.origin().offset(22,2,18));var heater=plan.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).findFirst().orElseThrow();
        source.sendSuccess(()->Component.literal("Built finite-charge fueled TEG demonstration at "+placement.origin()+"; heater "+heater.position()+". This is not a continuous engine."),false);return 1;
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        Entity body = activeBody(source);
        if (body == null) return failure(source, "Refused: active controlled character body unavailable; supply explicit fixture coordinates.");
        var data = TegFixtureSavedData.existing(source.getLevel()).orElse(null);
        if (data == null) return failure(source, "No saved TEG fixture nearby.");
        BlockPos origin = nearestContinuousOrigin(data.entries(), body.blockPosition());
        if (origin == null) return failure(source, "No saved TEG fixture nearby in this dimension.");
        return status(source, origin);
    }

    static BlockPos nearestFueledOrigin(Iterable<TegFixtureSavedData.Entry> entries, BlockPos bodyPosition) {
        return java.util.stream.StreamSupport.stream(entries.spliterator(), false).filter(e -> e.mode() == TegFixtureSavedData.Mode.FUELED)
                .map(TegFixtureSavedData.Entry::origin)
                .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(bodyPosition))).orElse(null);
    }

    static BlockPos nearestFixtureOrigin(Iterable<TegFixtureSavedData.Entry> entries,BlockPos bodyPosition) {
        return java.util.stream.StreamSupport.stream(entries.spliterator(),false).map(TegFixtureSavedData.Entry::origin)
                .min(java.util.Comparator.comparingDouble(pos->pos.distSqr(bodyPosition))).orElse(null);
    }
    static BlockPos nearestContinuousOrigin(Iterable<TegFixtureSavedData.Entry> entries,BlockPos bodyPosition) {
        return java.util.stream.StreamSupport.stream(entries.spliterator(),false).filter(e->e.mode()==TegFixtureSavedData.Mode.CONTINUOUS)
                .map(TegFixtureSavedData.Entry::origin).min(java.util.Comparator.comparingDouble(pos->pos.distSqr(bodyPosition))).orElse(null);
    }

    private static int status(net.minecraft.commands.CommandSourceStack source, BlockPos origin) {
        var entry=TegFixtureSavedData.existing(source.getLevel()).flatMap(d->d.entry(origin)).orElse(null);
        String result=entry==null?"NOT READY: no saved fixture at "+origin+".":switch(entry.mode()) {
            case CONTINUOUS -> TegContinuousFixtureService.operatingStatus(source.getLevel(),origin);
            case FUELED -> TegFueledFixtureService.status(source.getLevel(),origin);
            case FINITE_CHARGE -> TegFixtureService.status(source.getLevel(),origin);
        };
        if (result.startsWith("NOT READY")) return failure(source, result);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int statusFinite(net.minecraft.commands.CommandSourceStack source){return statusMode(source,null,TegFixtureSavedData.Mode.FUELED);}
    private static int statusFinite(net.minecraft.commands.CommandSourceStack source,BlockPos origin){return statusMode(source,origin,TegFixtureSavedData.Mode.FUELED);}
    private static int statusMode(net.minecraft.commands.CommandSourceStack source,BlockPos origin,TegFixtureSavedData.Mode mode) {
        if(origin==null){Entity body=activeBody(source);if(body==null)return failure(source,"Refused: active controlled character body unavailable; supply explicit fixture coordinates.");var data=TegFixtureSavedData.existing(source.getLevel()).orElse(null);if(data==null)return failure(source,"No saved finite demonstration nearby.");origin=data.entries().stream().filter(e->e.mode()==mode).map(TegFixtureSavedData.Entry::origin).min(java.util.Comparator.comparingDouble(p->p.distSqr(body.blockPosition()))).orElse(null);}
        if(origin==null)return failure(source,"No saved finite demonstration nearby in this dimension.");
        String result=TegFueledFixtureService.status(source.getLevel(),origin);if(result.startsWith("NOT READY"))return failure(source,result);source.sendSuccess(()->Component.literal(result),false);return 1;
    }

    private static Placement placement(net.minecraft.commands.CommandSourceStack source, BlockPos origin) {
        if (origin != null) return new Placement(source.getLevel(), origin);
        Entity body = activeBody(source);
        if (body == null) { failure(source, "Refused: active controlled character body unavailable; console must supply origin coordinates."); return null; }
        // origin is the machine-feet plane: core is origin + (22,2,18),
        // therefore the structural floor is origin + 1 and the operator stands at origin + 2.
        return new Placement(source.getLevel(), body.blockPosition().offset(4, -2, 4));
    }

    private static Entity activeBody(net.minecraft.commands.CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player && com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.isCarrier(player))
            entity = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.resolve(player)
                    .map(com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.Snapshot::body).orElse(null);
        return entity;
    }

    private static int failure(net.minecraft.commands.CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private record Placement(ServerLevel level, BlockPos origin) { }
}
