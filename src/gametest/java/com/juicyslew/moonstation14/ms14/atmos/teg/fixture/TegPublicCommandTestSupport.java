package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

/** Captures actual permission-2 Brigadier dispatch results and feedback for native fixture assertions. */
final class TegPublicCommandTestSupport {
    record Result(int returnCode, List<String> messages) { }

    private TegPublicCommandTestSupport() { }

    static Result execute(ServerLevel level, String command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var messages = new ArrayList<String>();
        CommandSource capture = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { messages.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPermission(2).withSource(capture);
        int result = level.getServer().getCommands().getDispatcher().execute(command, source);
        return new Result(result, List.copyOf(messages));
    }
}
