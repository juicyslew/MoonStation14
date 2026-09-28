package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterAppearance;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleAppearanceCommandsTest {
    @Test void acceptsOnlyExplicitSkinOptions() {
        assertEquals(PlayerCharacterAppearance.DEFAULT, LifecycleAppearanceCommands.parseSkin("default"));
        assertEquals(PlayerCharacterAppearance.ALEX, LifecycleAppearanceCommands.parseSkin("alex"));
        assertNull(LifecycleAppearanceCommands.parseSkin("steve"));
        assertNull(LifecycleAppearanceCommands.parseSkin("Alex"));
        assertNull(LifecycleAppearanceCommands.parseSkin(null));
    }

    @Test void acceptsOnlyExplicitShapeOptions() {
        assertEquals(PlayerCharacterBodyShape.WIDE, LifecycleAppearanceCommands.parseShape("wide"));
        assertEquals(PlayerCharacterBodyShape.SLIM, LifecycleAppearanceCommands.parseShape("slim"));
        assertNull(LifecycleAppearanceCommands.parseShape("normal"));
        assertNull(LifecycleAppearanceCommands.parseShape("SLIM"));
        assertNull(LifecycleAppearanceCommands.parseShape(null));
    }
}
