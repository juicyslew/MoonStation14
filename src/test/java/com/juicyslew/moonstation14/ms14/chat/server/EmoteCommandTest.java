package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.ms14.effect.EmoteEffect;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EmoteCommandTest {
    @Test void cooldownCommitsOnlyAfterAValidPlanAndRejectsRapidRepeat() {
        UUID account = UUID.randomUUID();
        assertFalse(EmoteCommand.commitCooldown(account, null, 100));
        var validPlan = new EmoteEffect.Plan("mouse_squeak", .125f, Component.literal("Mouse squeaks."));
        assertTrue(EmoteCommand.commitCooldown(account, validPlan, 100));
        assertFalse(EmoteCommand.commitCooldown(account, validPlan, 100 + 1_000_000_000L));
        assertTrue(EmoteCommand.commitCooldown(account, validPlan, 100 + 2_000_000_000L));
    }
}
