package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.chat.radio.ModRadioChannels;
import com.juicyslew.moonstation14.ms14.chat.radio.RadioChannelData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;

/** Common access point for the independent server and client prototype runtimes. */
public final class PrototypeRuntime {
    private static final PrototypeManager SERVER_MANAGER = createManager();
    private static final PrototypeManager CLIENT_MANAGER = createManager();

    private PrototypeRuntime() {
    }

    public static PrototypeManager serverManager() {
        return SERVER_MANAGER;
    }

    public static PrototypeManager clientManager() {
        return CLIENT_MANAGER;
    }

    public static PrototypeCatalog<ReagentData> serverReagents() {
        return SERVER_MANAGER.snapshot(ModReagents.REAGENT_TYPE);
    }

    public static PrototypeCatalog<ReagentData> clientReagents() {
        return CLIENT_MANAGER.snapshot(ModReagents.REAGENT_TYPE);
    }

    public static PrototypeCatalog<StatusEffectData> serverStatusEffects() {
        return SERVER_MANAGER.snapshot(ModStatusEffects.STATUS_EFFECT_TYPE);
    }

    public static PrototypeCatalog<StatusEffectData> clientStatusEffects() {
        return CLIENT_MANAGER.snapshot(ModStatusEffects.STATUS_EFFECT_TYPE);
    }

    public static PrototypeCatalog<AlertData> serverAlerts() {
        return SERVER_MANAGER.snapshot(ModAlerts.ALERT_TYPE);
    }

    public static PrototypeCatalog<AlertData> clientAlerts() {
        return CLIENT_MANAGER.snapshot(ModAlerts.ALERT_TYPE);
    }

    public static PrototypeCatalog<CharacterData> serverCharacters() {
        return SERVER_MANAGER.snapshot(ModCharacters.CHARACTER_TYPE);
    }

    public static PrototypeCatalog<CharacterData> clientCharacters() {
        return CLIENT_MANAGER.snapshot(ModCharacters.CHARACTER_TYPE);
    }

    public static PrototypeCatalog<RadioChannelData> serverRadioChannels() {
        return SERVER_MANAGER.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE);
    }

    public static PrototypeCatalog<RadioChannelData> clientRadioChannels() {
        return CLIENT_MANAGER.snapshot(ModRadioChannels.RADIO_CHANNEL_TYPE);
    }

    private static PrototypeManager createManager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);
        manager.register(ModStatusEffects.STATUS_EFFECT_TYPE);
        manager.register(ModAlerts.ALERT_TYPE);
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.register(ModRadioChannels.RADIO_CHANNEL_TYPE);
        return manager;
    }
}
