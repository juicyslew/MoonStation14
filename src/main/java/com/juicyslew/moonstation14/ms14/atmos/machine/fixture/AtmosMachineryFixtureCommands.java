package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Permission-2 bounded atmosphere fixture commands. */
@EventBusSubscriber(modid="moonstation14")
public final class AtmosMachineryFixtureCommands {
    private AtmosMachineryFixtureCommands() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14").requires(source -> source.hasPermission(2))
                .then(atmosFixtureCommand()));
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> atmosFixtureCommand() {
        return Commands.literal("atmos_fixture")
                .then(Commands.literal("build").executes(ctx -> build(ctx.getSource(), null))
                        .then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> build(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "origin")))))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource(), null))
                        .then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> status(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "origin")))))
                .then(startCommand());
    }

    private static int build(net.minecraft.commands.CommandSourceStack source, BlockPos origin) {
        if (origin == null) {
            Entity body = activeBody(source);
            if (body == null) return failure(source, "Refused: active controlled character body unavailable; supply fixture coordinates.");
            origin = body.blockPosition().offset(4, -1, 4);
        }
        String result = AtmosMachineryFixtureService.build(source.getLevel(), origin);
        if (!result.startsWith("Built")) return failure(source, result);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int status(net.minecraft.commands.CommandSourceStack source, BlockPos origin) {
        if (origin == null) {
            Entity body = activeBody(source);
            if (body == null) return failure(source, "Refused: active controlled character body unavailable; supply fixture coordinates.");
            var saved = AtmosMachineryFixtureSavedData.existing(source.getLevel()).orElse(null);
            if (saved == null) return failure(source, "No saved atmosphere machinery fixture nearby.");
            origin = java.util.stream.StreamSupport.stream(saved.entries().spliterator(), false)
                    .map(AtmosMachineryFixtureSavedData.Entry::origin)
                    .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(body.blockPosition()))).orElse(null);
            if (origin == null) return failure(source, "No saved atmosphere machinery fixture nearby in this dimension.");
        }
        Component result = AtmosMachineryFixtureService.statusComponent(source.getLevel(), origin);
        if (result.getString().startsWith("Status unavailable") || result.getString().startsWith("Invalid")) return failure(source, result.getString());
        source.sendSuccess(() -> result, false);
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> startCommand() {
        var root=Commands.literal("start").executes(ctx -> failure(ctx.getSource(),
                "Choose a demonstration: pressure_pump|volume_pump|mixer|filter|valve|heater|freezer|heat_exchanger|manifold|all."));
        for (var kind : AtmosMachineryFixturePlan.Kind.values()) {
            String name=demoLiteral(kind);
            var choice = Commands.literal(name).executes(ctx -> start(ctx.getSource(), null, name))
                    .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->start(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"),name)));
            root.then(choice);
        }
        var all=Commands.literal("all").executes(ctx->start(ctx.getSource(),null,"all"))
                .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->start(ctx.getSource(),BlockPosArgument.getBlockPos(ctx,"origin"),"all")));
        root.then(all);
        return root;
    }

    private static int start(net.minecraft.commands.CommandSourceStack source, BlockPos origin, String demo) {
        if (origin == null) {
            Entity body = activeBody(source);
            if (body == null) return failure(source, "Refused: active controlled character body unavailable; supply fixture coordinates.");
            var saved = AtmosMachineryFixtureSavedData.existing(source.getLevel()).orElse(null);
            if (saved == null) return failure(source, "No saved atmosphere machinery fixture nearby.");
            origin = java.util.stream.StreamSupport.stream(saved.entries().spliterator(), false)
                    .map(AtmosMachineryFixtureSavedData.Entry::origin)
                    .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(body.blockPosition()))).orElse(null);
            if (origin == null) return failure(source, "No saved atmosphere machinery fixture nearby in this dimension.");
        }
        String result;
        if(demo.equals("all")) result=AtmosMachineryFixtureService.startAll(source.getLevel(),origin);
        else {
            result=AtmosMachineryFixtureService.start(source.getLevel(),origin,parseDemo(demo).orElseThrow());
        }
        if (!result.startsWith("Started")) return failure(source, result);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    static String demoLiteral(AtmosMachineryFixturePlan.Kind kind) {
        return kind==AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER?"heat_exchanger":kind.name().toLowerCase(java.util.Locale.ROOT);
    }
    static java.util.Optional<AtmosMachineryFixturePlan.Kind> parseDemo(String literal) {
        return java.util.Arrays.stream(AtmosMachineryFixturePlan.Kind.values()).filter(kind->demoLiteral(kind).equals(literal)).findFirst();
    }

    private static Entity activeBody(net.minecraft.commands.CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player && ActiveCharacterPolicy.isCarrier(player))
            entity = BodyActionAuthority.resolve(player).map(BodyActionAuthority.Snapshot::body).orElse(null);
        return entity;
    }
    private static int failure(net.minecraft.commands.CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message)); return 0;
    }
}
