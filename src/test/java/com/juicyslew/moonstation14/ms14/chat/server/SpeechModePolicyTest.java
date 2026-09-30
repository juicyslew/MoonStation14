package com.juicyslew.moonstation14.ms14.chat.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpeechModePolicyTest {
    @Test void ordinarySpeechPreservesAuthenticatedRawBody() {
        String raw = "  Hello:world §a <literal>!  ";
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.SAY, raw, null),
                SpeechModePolicy.parse(raw));
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.SAY, ": hello", null),
                SpeechModePolicy.parse(": hello"));
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.SHOUT, "hello!!", null),
                SpeechModePolicy.parse("hello!!"));
        assertEquals(15.0, SpeechModePolicy.SAY_RANGE);
        assertEquals(SpeechModePolicy.SAY_RANGE, SpeechModePolicy.SHOUT_RANGE);
        assertEquals(3.0, SpeechModePolicy.WHISPER_CLEAR_RANGE);
        assertEquals(6.0, SpeechModePolicy.WHISPER_MUFFLED_RANGE);
    }

    @Test void prefixesHaveExplicitPrecedenceAndRemoveOnlyTheirOwnMarkers() {
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.WHISPER, " quiet!!", null),
                SpeechModePolicy.parse(", quiet!!"));
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.WHISPER, " quiet", null),
                SpeechModePolicy.parse(">, quiet"));
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.SAY, "; broadcast?", null),
                SpeechModePolicy.parse(">; broadcast?"));
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.SAY, ";", null),
                SpeechModePolicy.parse(">;"));
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.SAY, ":h hi", null),
                SpeechModePolicy.parse(">:h hi"));
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.RADIO_ATTEMPT, " hello", null),
                SpeechModePolicy.parse("; hello"));
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.RADIO_ATTEMPT, " hello", 'h'),
                SpeechModePolicy.parse(":h hello"));
        assertEquals(new SpeechModePolicy.Result(SpeechModePolicy.Mode.RADIO_ATTEMPT, " hello", 'z'),
                SpeechModePolicy.parse(":z hello"));
    }

    @Test void malformedAttemptsAndEmptyBodiesFailClosed() {
        for (String raw : new String[] {":unknown hello", ":H hello", ":1 hello", ":hh hello",
                ":h", ";", ",", ">", ">,", "", "   ", ",  ", ";  ", ":h  "}) {
            assertEquals(SpeechModePolicy.Mode.INVALID, SpeechModePolicy.parse(raw).mode(), raw);
        }
        assertEquals(SpeechModePolicy.Mode.INVALID, SpeechModePolicy.parse(null).mode());
    }

    @Test void validRadioAttemptsRouteOnlyAsLocalWhispersOfParsedBody() {
        for (String raw : new String[] {":h PIN 7!", "; PIN 7!"}) {
            SpeechModePolicy.Result parsed = SpeechModePolicy.parse(raw);
            assertEquals(SpeechModePolicy.Mode.RADIO_ATTEMPT, parsed.mode());
            assertEquals(" PIN 7!", parsed.body());
            SpeechModePolicy.Mode local = LocalSpeechServerHooks.localMode(parsed.mode());
            assertEquals(SpeechModePolicy.Mode.WHISPER, local);
            assertEquals(SpeechRecipientPolicy.Delivery.CLEAR,
                    SpeechRecipientPolicy.classify(local, true, true, 0, 0, 0, 100, 0, 0));
            assertEquals(SpeechRecipientPolicy.Delivery.CLEAR,
                    SpeechRecipientPolicy.classify(local, false, true, 0, 0, 0, 3, 0, 0));
            assertEquals(SpeechRecipientPolicy.Delivery.MUFFLED,
                    SpeechRecipientPolicy.classify(local, false, true, 0, 0, 0, 6, 0, 0));
            assertEquals(SpeechRecipientPolicy.Delivery.NONE,
                    SpeechRecipientPolicy.classify(local, false, true, 0, 0, 0, 6.01, 0, 0));
            assertEquals(" ··· ··", SpeechRecipientPolicy.muffle(parsed.body()));
        }
        assertEquals(SpeechModePolicy.Mode.INVALID,
                LocalSpeechServerHooks.localMode(SpeechModePolicy.parse(":H PIN 7!").mode()));
    }

    @Test void limitsApplyToBodyAfterPrefixRemoval() {
        String body = "a".repeat(256);
        for (String prefix : new String[] {"", ",", ">,", ";"}) {
            assertEquals(body, SpeechModePolicy.parse(prefix + body).body(), prefix);
            assertEquals(SpeechModePolicy.Mode.INVALID,
                    SpeechModePolicy.parse(prefix + body + "a").mode(), prefix);
        }
        String radioBody = " " + "a".repeat(255);
        assertEquals(radioBody, SpeechModePolicy.parse(":h" + radioBody).body());
        assertEquals(SpeechModePolicy.Mode.INVALID,
                SpeechModePolicy.parse(":h" + radioBody + "a").mode());
        String forcedBody = ";" + "a".repeat(255);
        assertEquals(forcedBody, SpeechModePolicy.parse(">" + forcedBody).body());
        assertEquals(SpeechModePolicy.Mode.INVALID,
                SpeechModePolicy.parse(">" + forcedBody + "a").mode());
        assertEquals("a".repeat(254) + "!!", SpeechModePolicy.parse("a".repeat(254) + "!!").body());
    }
}
