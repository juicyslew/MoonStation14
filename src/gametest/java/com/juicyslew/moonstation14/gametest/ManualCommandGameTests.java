package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.mojang.brigadier.ParseResults;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManualCommandGameTests {
    private ManualCommandGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bottleGiveCommandsParseCompletely(GameTestHelper helper) {
        String[] commands = {
                "give @s moonstation14:bottle[moonstation14:reagent={\"moonstation14:water\":30.0f}]",
                "give @s moonstation14:bottle[moonstation14:reagent={\"moonstation14:polytrinicacid\":30.0f}]",
                "give @s moonstation14:jug[moonstation14:reagent={\"moonstation14:water\":20.0f}]"
        };

        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = helper.getLevel().getServer().createCommandSourceStack().withPermission(4);
        for (String command : commands) {
            ParseResults<?> parsed = dispatcher.parse(command, source);
            if (!parsed.getExceptions().isEmpty()) {
                throw new GameTestAssertException("Command failed to parse: " + command + " ("
                        + parsed.getExceptions() + ")");
            }
            if (parsed.getReader().canRead()) {
                throw new GameTestAssertException("Command parser left unconsumed input at "
                        + parsed.getReader().getCursor() + ": " + command);
            }
        }

        helper.succeed();
    }
}
