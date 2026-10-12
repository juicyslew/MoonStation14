package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.fixture;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Permission-2 command surface for an open-air, actual-power ChemMaster demo. */
public final class ChemMasterFixtureCommands {
    private ChemMasterFixtureCommands() { }
    public static void registerCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("chemmaster_fixture");
        root.then(action("build", true));
        root.then(action("status", false));
        event.getDispatcher().register(Commands.literal("ms14").requires(s -> s.hasPermission(2)).then(root));
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> action(String name, boolean build) {
        var command = Commands.literal(name);
        command.executes(ctx -> run(ctx.getSource(), null, build));
        command.then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> run(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "origin"), build)));
        return command;
    }
    private static int run(net.minecraft.commands.CommandSourceStack source, BlockPos origin, boolean build) {
        if (origin == null) {
            if (source.getEntity() == null) { source.sendFailure(Component.literal("Console must supply explicit platform-lowest-corner coordinates: /ms14 chemmaster_fixture " + (build ? "build" : "status") + " <x y z>.")); return 0; }
            origin = source.getEntity().blockPosition().offset(4, 0, 0);
        }
        String result = build ? ChemMasterFixtureService.build(source.getLevel(), origin) : ChemMasterFixtureService.status(source.getLevel(), origin);
        source.sendSuccess(() -> Component.literal(result), false);
        return result.startsWith("Refused") || result.startsWith("NOT READY") ? 0 : 1;
    }
}
