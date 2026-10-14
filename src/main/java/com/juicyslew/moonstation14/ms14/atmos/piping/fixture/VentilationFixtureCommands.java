package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Permission-2 administrative commands for the finite ventilation demonstration. */
public final class VentilationFixtureCommands {
    private VentilationFixtureCommands() { }
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14").requires(source -> source.hasPermission(2))
                .then(Commands.literal("ventilation_fixture")
                        .then(Commands.literal("build").then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> run(ctx, 0))))
                        .then(Commands.literal("initialize").then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> run(ctx, 1))))
                        .then(Commands.literal("status").then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> run(ctx, 2))))));
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
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}

