package com.juicyslew.moonstation14;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientConfigTest {
    @Test void comfortCameraIsOffByDefaultAndCanBeOptedIn() {
        assertEquals(false, ClientConfig.ANGULAR_RECOVERY_COMFORT_CAMERA.getDefault());
    }
}
