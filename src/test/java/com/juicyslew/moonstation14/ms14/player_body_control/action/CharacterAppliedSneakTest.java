package com.juicyslew.moonstation14.ms14.player_body_control.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharacterAppliedSneakTest {
    @Test
    void acceptsCurrentAndOnePriorAppliedTickOnlyWhenSneakWasApplied() {
        assertTrue(CharacterAppliedSneak.freshAppliedSneak(true, 20, 20));
        assertTrue(CharacterAppliedSneak.freshAppliedSneak(true, 20, 21));
        assertFalse(CharacterAppliedSneak.freshAppliedSneak(true, 20, 22));
        assertFalse(CharacterAppliedSneak.freshAppliedSneak(false, 20, 20));
        assertFalse(CharacterAppliedSneak.freshAppliedSneak(true, 21, 20));
        assertFalse(CharacterAppliedSneak.freshAppliedSneak(false, 20, 21));
    }
}
