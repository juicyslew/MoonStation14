package com.juicyslew.moonstation14.ms14.tesla.fixture;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid="moonstation14")
public final class TeslaFixtureCommands {
    private TeslaFixtureCommands() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root=Commands.literal("tesla_fixture");
        root.then(Commands.literal("beam_test").executes(c->beamTest(c.getSource(),null))
                .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(c->beamTest(c.getSource(),BlockPosArgument.getBlockPos(c,"origin")))));
        for(String action:new String[]{"build","start","status"}) root.then(Commands.literal(action)
                .executes(c->run(c.getSource(),action,null))
                .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(c->run(c.getSource(),action,BlockPosArgument.getBlockPos(c,"origin")))));
        root.then(Commands.literal("breach").executes(c->breach(c.getSource(),null))
                .then(Commands.argument("corner",BlockPosArgument.blockPos()).executes(c->breach(c.getSource(),BlockPosArgument.getBlockPos(c,"corner")))));
        event.getDispatcher().register(Commands.literal("ms14").requires(s->s.hasPermission(2)).then(root));
    }
    private static int beamTest(net.minecraft.commands.CommandSourceStack source, BlockPos pos) {
        net.minecraft.server.level.ServerLevel level=source.getLevel();
        if(pos==null) { var entity=source.getEntity(); if(entity==null) { source.sendFailure(Component.literal("Console must supply explicit beam test origin coordinates.")); return 0; }
            if(entity instanceof net.minecraft.server.level.ServerPlayer player && com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.isCarrier(player)) {
                var body=com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.resolve(player).map(com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.Snapshot::body).orElse(null);
                if(body==null) { source.sendFailure(Component.literal("Refused: active controlled character body unavailable; supply explicit beam test coordinates.")); return 0; } entity=body;
            }
            if(entity.level() instanceof net.minecraft.server.level.ServerLevel entityLevel)level=entityLevel;
            pos=entity.blockPosition().relative(entity.getDirection(),3);
        }
        String result=TeslaFixtureService.beamTest(level,pos);
        source.sendSuccess(()->Component.literal(result),false); return result.startsWith("Refused")?0:1;
    }
    private static int run(net.minecraft.commands.CommandSourceStack source,String action,BlockPos pos) {
        if(pos==null) {
            var entity=source.getEntity();
            if(entity==null){source.sendFailure(Component.literal("Console must supply origin coordinates."));return 0;}
            if(entity instanceof net.minecraft.server.level.ServerPlayer player && com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.isCarrier(player))
                entity=com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.resolve(player).map(com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.Snapshot::body).orElse(null);
            if(entity==null){source.sendFailure(Component.literal("Refused: active controlled character body unavailable."));return 0;}
            pos=entity.blockPosition().offset(4,0,4);
            if(!action.equals("build")) { var nearest=TeslaFixtureService.nearestOrigin(source.getLevel(),entity.blockPosition()); if(nearest==null){source.sendFailure(Component.literal("No registered Tesla fixture nearby."));return 0;} pos=nearest; }
        }
        if(pos==null){source.sendFailure(Component.literal("Console must supply origin coordinates."));return 0;}
        String result=switch(action){case "build"->TeslaFixtureService.build(source.getLevel(),pos);case "start"->TeslaFixtureService.start(source.getLevel(),pos);default->TeslaFixtureService.status(source.getLevel(),pos);};
        source.sendSuccess(()->Component.literal(result),false);return result.startsWith("Refused")||result.startsWith("NOT READY")?0:1;
    }
    private static int breach(net.minecraft.commands.CommandSourceStack source,BlockPos pos) {
        if(pos==null) {
            var origin=TeslaFixtureService.nearestOrigin(source.getLevel(),BlockPos.containing(source.getPosition()));
            var record=origin==null?null:com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService.registered(source.getLevel(),origin);
            pos=record==null?null:record.placed().fieldGenerators().getFirst();
        }
        if(pos==null){source.sendFailure(Component.literal("No recommended tracked containment corner found."));return 0;}
        String result=TeslaFixtureService.breach(source.getLevel(),pos);
        source.sendSuccess(()->Component.literal(result),false);return result.startsWith("Refused")?0:1;
    }
}
