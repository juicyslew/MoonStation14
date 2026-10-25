package com.juicyslew.moonstation14.ms14.player_body_control.diagnostics;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MovementTraceRecorderTest {
    @TempDir Path temp;
    private final MovementTraceRecorder.Context context = new MovementTraceRecorder.Context(7, 12, UUID.randomUUID(), "test:dim", "CHARACTER");

    @Test void boundsDropsMismatchExpiryAndImmutableStop() {
        var r = new MovementTraceRecorder(context, 100, 1, 2);
        assertFalse(r.record(new MovementTraceRecorder.Context(8,12,context.bodyUuid(),"test:dim","CHARACTER"), "SERVER_STEP", 110, 20, 5, Map.of()));
        assertTrue(r.record(context,"SERVER_STEP",90,20,91,Map.of("x",1)));
        assertTrue(r.record(context,"CLIENT_TICK",120,21,92,Map.of()));
        assertFalse(r.record(context,"SNAPSHOT",130,22,93,Map.of()));
        var trace = r.stop("stopped", "INTEGRATED");
        assertEquals(3,trace.attempted()); assertEquals(1,trace.dropped()); assertEquals(2,trace.samples().size());
        assertEquals(0,trace.samples().get(0).relativeNanos());
        assertFalse(r.record(context,"CAMERA",140,23,94,Map.of()));
        assertThrows(UnsupportedOperationException.class,()->trace.samples().clear());
        assertThrows(IllegalArgumentException.class,()->new MovementTraceRecorder(context,0,61,2));
    }

    @Test void expiryAndSafeAtomicExport() throws Exception {
        var r = new MovementTraceRecorder(context, 1_000, 1, 5);
        assertTrue(r.record(context,"CAMERA",1_001,30,4,Map.of("ok",true,"bad",Double.NaN,"value",2.5)));
        assertFalse(r.record(context,"CAMERA",1_000_001_000L,31,5,Map.of()));
        var trace=r.stop("expired",null);
        assertEquals("expired",trace.finishReason()); assertEquals("UNAVAILABLE",trace.serverStream());
        assertEquals(1,trace.invalidValues());
        Path file=MovementTraceRecorder.export(trace,temp);
        assertEquals(temp.resolve("build/reports/nonhostile-animals/movement-traces").resolve(trace.traceId().toString()).resolve("trace.json"),file);
        var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(1,json.get("formatVersion").getAsInt());
        assertFalse(json.toString().contains("NaN"));
    }

    @Test void sessionOwnsSingleTraceAndFreshStartAfterStop() {
        assertTrue(MovementTraceSession.start(context,0,30));
        assertFalse(MovementTraceSession.start(context,1,30));
        assertEquals(context,MovementTraceSession.activeContext());
        var first=MovementTraceSession.stop("stop","UNAVAILABLE_REMOTE");
        assertNotNull(first); assertNull(MovementTraceSession.activeContext());
        assertTrue(MovementTraceSession.start(context,2,1));
        assertNotEquals(first.traceId(),MovementTraceSession.stop("stop",null).traceId());
    }
}
