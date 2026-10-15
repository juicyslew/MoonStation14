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
        event.getDispatcher().register(Commands.literal("ms14").requires(s->s.hasPermission(2)).then(root));
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
            var result=mode.equals("inactive_wide")?SingularityFixtureService.buildWideInactive(source.getLevel(),origin)
                    :SingularityFixtureService.build(source.getLevel(),origin);
            message=result.error(); if(result.successful()&&mode.equals("running")) message=SingularityFixtureService.startRunning(source.getLevel(),origin);
        }
        else message=SingularityFixtureService.status(source.getLevel(),origin);
        String response=message;
        source.sendSuccess(()->Component.literal(response),false);
        return message.startsWith("Refused")||message.startsWith("NOT READY")||message.startsWith("PARTIAL")?0:1;
    }
}
