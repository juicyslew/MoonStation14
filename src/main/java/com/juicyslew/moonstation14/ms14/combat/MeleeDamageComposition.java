package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import java.util.LinkedHashMap;
import java.util.Map;

/** Composes ordinary melee damage with listener bonuses after scaling the weapon base once. */
public final class MeleeDamageComposition {
    private MeleeDamageComposition() { }

    public static Map<String, Float> compose(Map<String, Float> base, float clickModifier,
                                              Map<String, Float> hitBonus, Map<String, Float> attackedBonus) {
        var result = new LinkedHashMap<String, Float>(DamageSystem.scaleDelta(base, clickModifier));
        hitBonus.forEach((type, amount) -> result.merge(type, amount, Float::sum));
        attackedBonus.forEach((type, amount) -> result.merge(type, amount, Float::sum));
        return result;
    }
}
