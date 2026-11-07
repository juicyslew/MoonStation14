package com.juicyslew.moonstation14.ms14.atmos.machine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosMachineAmbientClientTest {
    @Test void manualCueFalloffUsesLinearGainAndClampsAtRange() {
        assertEquals(0.8f, AtmosMachineAmbientClient.attenuatedGain(0.8f, 20, 0), 0.00001f);
        assertEquals(0.4f, AtmosMachineAmbientClient.attenuatedGain(0.8f, 20, 10), 0.00001f);
        assertEquals(0f, AtmosMachineAmbientClient.attenuatedGain(0.8f, 20, 20), 0.00001f);
        assertEquals(0f, AtmosMachineAmbientClient.attenuatedGain(0.8f, 20, 25), 0.00001f);
    }
}
