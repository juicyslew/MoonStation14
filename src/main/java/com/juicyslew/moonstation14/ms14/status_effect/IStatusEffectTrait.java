package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;

public interface IStatusEffectTrait {
    default TraitHandler<IStatusEffectTrait> toHandle(Object holder) {
        return new TraitHandler<>(holder, this);
    }

    default TraitHandler<IStatusEffectTrait> toHandleSelf() {
        return new TraitHandler<>(this, this);
    }
}
