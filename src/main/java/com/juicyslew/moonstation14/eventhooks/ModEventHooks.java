package com.juicyslew.moonstation14.eventhooks;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.UnaryOperator;

public class ModEventHooks {

    public static void register(IEventBus eventBus){
        NeoForge.EVENT_BUS.addListener(DamageHooks::onLivingEntityHurt);
        NeoForge.EVENT_BUS.addListener(TickHooks::onLivingEntityTick);
    }
}
