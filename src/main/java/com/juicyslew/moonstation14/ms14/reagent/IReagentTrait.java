package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.ms14.TraitHandler;

public interface IReagentTrait {
    float getCapacity();

    default TraitHandler<IReagentTrait> toHandle(Object holder) {
        return new TraitHandler<>(holder, this);
    }

    default TraitHandler<IReagentTrait> toHandleSelf() {
        return new TraitHandler<>(this, this);
    }
}
