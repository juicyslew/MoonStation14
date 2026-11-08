package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MeleeTargetAttackedEventTest {
    @Test void scalingUsesSharedTypedDamageKernel() {
        assertEquals(Map.of(DamageKeys.BLUNT, 3f), DamageSystem.scaleDelta(Map.of(DamageKeys.BLUNT, 2f), 1.5f));
    }

}
