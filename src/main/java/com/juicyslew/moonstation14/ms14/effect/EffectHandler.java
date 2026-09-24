package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EffectData;

@FunctionalInterface
public interface EffectHandler<T extends EffectData> {
    EffectResult apply(T effect, EffectContext context);
}
