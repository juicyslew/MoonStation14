package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobThresholdsComponent;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MobStateReducerTest {
    private final MobStateComponent states = new MobStateComponent(MobStateComponent.DEFAULT_ALLOWED_STATES);
    private final MobThresholdsComponent thresholds = new MobThresholdsComponent(Map.of(0d, MobStateComponent.State.ALIVE,
            100d, MobStateComponent.State.CRITICAL, 200d, MobStateComponent.State.DEAD), false);

    @Test void damageHealingAndDeadLockAreDistinct() {
        var alive = new MobStateReducer.State(MobStateComponent.State.ALIVE, 0);
        var critical = MobStateReducer.damageOrHealing(alive, 100, states, thresholds).state();
        assertEquals(MobStateComponent.State.CRITICAL, critical.value());
        assertEquals(MobStateComponent.State.ALIVE, MobStateReducer.damageOrHealing(critical, 0, states, thresholds).state().value());
        var dead = MobStateReducer.damageOrHealing(critical, 200, states, thresholds).state();
        assertSame(dead, MobStateReducer.damageOrHealing(dead, 0, states, thresholds).state());
        assertEquals(MobStateComponent.State.ALIVE, MobStateReducer.explicitRevive(dead, 0, states, thresholds).state().value());
    }

    @Test void lethalCanSkipCriticalAndNoOpDoesNotIncrementRevision() {
        var alive = new MobStateReducer.State(MobStateComponent.State.ALIVE, 4);
        var dead = MobStateReducer.damageOrHealing(alive, 200, states, thresholds);
        assertEquals(MobStateComponent.State.DEAD, dead.state().value());
        assertEquals(5, dead.state().revision());
        assertSame(dead.state(), MobStateReducer.damageOrHealing(dead.state(), 200, states, thresholds).state());
    }
}
