package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Permission-2 commands: build inactive|running|inactive_wide [origin], or status [origin]. */
public final class SingularityFixtureCommands {
    private SingularityFixtureCommands() { }
    public static void registerCommands(RegisterCommandsEvent event) {
        var root=Commands.literal("singularity_fixture");
        root.then(action("build",true)); root.then(action("status",false));
        root.then(Commands.literal("gravity")
                .then(Commands.argument("stage",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,6))
                        .executes(ctx->gravity(ctx.getSource(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"stage")))));
        root.then(Commands.literal("gravity_status").executes(ctx->gravityStatus(ctx.getSource())));
        root.then(Commands.literal("gravity_clear").executes(ctx->gravityClear(ctx.getSource())));
        root.then(Commands.literal("breach").executes(ctx->breachNearest(ctx.getSource())).then(Commands.argument("corner",BlockPosArgument.blockPos())
                .executes(ctx->breach(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"corner")))));
        event.getDispatcher().register(Commands.literal("ms14").requires(s->s.hasPermission(2)).then(root));
    }
    private static int gravity(CommandSourceStack source,int stage) {
        if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) { source.sendFailure(Component.literal("Gravity fixture requires an in-world player.")); return 0; }
        String result=SingularityFixtureService.gravityStage(source.getLevel(),player,stage);
        source.sendSuccess(()->Component.literal(result),false); return result.startsWith("Refused")?0:1;
    }
    private static int gravityStatus(CommandSourceStack source) {
        if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) { source.sendFailure(Component.literal("Gravity status requires an in-world player.")); return 0; }
        source.sendSuccess(()->Component.literal(SingularityFixtureService.gravityStatus(source.getLevel(),player)),false); return 1;
    }
    private static int gravityClear(CommandSourceStack source) {
        if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) { source.sendFailure(Component.literal("Gravity clear requires an in-world player.")); return 0; }
        source.sendSuccess(()->Component.literal(SingularityFixtureService.clearGravity(source.getLevel(),player)),false); return 1;
    }
    private static int breachNearest(CommandSourceStack source) {
        BlockPos corner=SingularityFixtureService.nearestRecommendedBreachCorner(source.getLevel(),BlockPos.containing(source.getPosition()));
        if(corner==null) { source.sendFailure(Component.literal("No registered singuloosing fixture nearby; use /ms14 singularity_fixture status.")); return 0; }
        return breach(source,corner);
    }
    private static int breach(CommandSourceStack source,BlockPos corner) {
        String result=SingularityFixtureService.breachOwnedCorner(source.getLevel(),corner);
        source.sendSuccess(()->Component.literal(result),false);
        return result.startsWith("Refused")?0:1;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> action(String name,boolean build) {
        var command=Commands.literal(name).executes(ctx->run(ctx.getSource(),null,build,"inactive"));
        if(build) command.then(Commands.literal("inactive").executes(ctx->run(ctx.getSource(),null,true,"inactive"))
                .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"),true,"inactive"))));
        command.then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"),build,"inactive")));
        if(build) command.then(Commands.literal("running").executes(ctx->run(ctx.getSource(),null,true,"running"))
                .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"),true,"running"))));
        if(build) command.then(Commands.literal("inactive_wide").executes(ctx->run(ctx.getSource(),null,true,"inactive_wide"))
                .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"),true,"inactive_wide"))));
        if(build) command.then(Commands.literal("singuloosing").executes(ctx->run(ctx.getSource(),null,true,"singuloosing"))
                .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"),true,"singuloosing"))));
        return command;
    }
    private static int run(CommandSourceStack source,BlockPos origin,boolean build,String mode) {
        if(origin==null) {
            if(source.getEntity()==null) { source.sendFailure(Component.literal("Console must supply origin coordinates: /ms14 singularity_fixture "+(build?"build":"status")+" <x y z>.")); return 0; }
            origin=source.getEntity().blockPosition().offset(4,0,4);
            if(!build) { BlockPos nearest=SingularityFixtureService.nearestOrigin(source.getLevel(),origin); if(nearest!=null) origin=nearest; }
        }
        String message;
        if(build) {
            var result=mode.equals("singuloosing")?SingularityFixtureService.buildSinguloosing(source.getLevel(),origin)
                    :mode.equals("inactive_wide")?SingularityFixtureService.buildWideInactive(source.getLevel(),origin)
                    :SingularityFixtureService.build(source.getLevel(),origin);
            message=result.error(); if(result.successful()&&mode.equals("running")) message=SingularityFixtureService.startRunning(source.getLevel(),origin);
        }
        else message=SingularityFixtureService.status(source.getLevel(),origin);
        String response=message;
        source.sendSuccess(()->Component.literal(response),false);
        return message.startsWith("Refused")||message.startsWith("NOT READY")||message.startsWith("PARTIAL")?0:1;
    }
}
