package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.character.components.RespiratorPolicy;
import com.juicyslew.moonstation14.ms14.organ.OrganData;
import java.util.Map;

/** Ephemeral reducer input composed from the current mob and attached organ prototypes. Not catalog data. */
record LungRuntimePolicy(RespiratorPolicy respirator, OrganData.Lung lung) {
    double breathIntervalSeconds() { return respirator.breathIntervalSeconds(); }
    double breathVolumeLiters() { return respirator.breathVolumeLiters(); }
    double maxSaturation() { return respirator.maxSaturation(); }
    double initialSaturation() { return respirator.initialSaturation(); }
    double minSaturation() { return respirator.minSaturation(); }
    double saturationLossPerUpdate() { return respirator.saturationLossPerUpdate(); }
    double suffocationThreshold() { return respirator.suffocationThreshold(); }
    double suffocationDamagePerUpdate() { return respirator.suffocationDamagePerUpdate(); }
    double suffocationRecoveryPerUpdate() { return respirator.suffocationRecoveryPerUpdate(); }
    boolean suffocationIgnoreResistances() { return respirator.suffocationIgnoreResistances(); }
    double maxLungMoles() { return lung.maxLungMoles(); }
    double breathMolesToSaturationMultiplier() { return lung.breathMolesToSaturationMultiplier(); }
    Map<String, Map<String, Double>> toxicGasDamagePerMole() { return lung.toxicGasDamagePerMole(); }
    double toxicGasDamageCapPerInhale() { return lung.toxicGasDamageCapPerInhale(); }
}
