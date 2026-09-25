package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharacterControlSystemTest {
    @Test
    void stunActionBlockBehaviorHasStableDataDrivenName() {
        assertEquals("stun_action_block", StatusEffectBehavior.STUN_ACTION_BLOCK.serializedName());
        assertEquals("stun_action_block", StatusEffectBehavior.STUN_ACTION_BLOCK.serializedName());
    }
}
