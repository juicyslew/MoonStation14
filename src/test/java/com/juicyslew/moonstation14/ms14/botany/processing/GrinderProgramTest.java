package com.juicyslew.moonstation14.ms14.botany.processing;

import com.juicyslew.moonstation14.ms14.botany.MachineProfile;
import org.junit.jupiter.api.Test;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;

class GrinderProgramTest {
    private static final MachineProfile GRINDER = new MachineProfile(0, 750, 6, 70, true, false, 0, 0);

    @Test void profileClockIsSingleCompletionAndLocksWhileActive() {
        GrinderProgram program = idleProgram();
        assertFalse(program.start(10, GRINDER, false, 1, true, true));
        assertFalse(program.start(10, GRINDER, true, 0, true, true));
        assertTrue(program.start(10, GRINDER, true, 6, true, true));
        assertEquals(10, program.startTick()); assertEquals(80, program.endTick());
        assertFalse(program.due(79)); assertFalse(program.finishIfDue(79));
        assertTrue(program.locked()); assertFalse(program.canEject());
        assertTrue(program.due(80)); assertTrue(program.finishIfDue(80));
        assertFalse(program.active()); assertFalse(program.finishIfDue(80));
    }

    @Test void powerLossAfterStartDoesNotAlterDeadline() {
        GrinderProgram program = idleProgram();
        assertTrue(program.start(0, GRINDER, true, 1, true, true));
        // Power is intentionally not an input to due/finish: upstream only updates UI on power changes.
        assertFalse(program.due(69)); assertTrue(program.finishIfDue(70));
    }

    private static GrinderProgram idleProgram() {
        GrinderProgram program = GrinderProgram.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"start_tick\":0,\"end_tick\":1}")).getOrThrow();
        program.finishIfDue(1);
        return program;
    }
}
