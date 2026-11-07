package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunWieldInputPolicyTest {
    @Test void onlyUnmodifiedCtrlZWithAdmittedWieldableInputDispatches() {
        assertTrue(GunWieldInputPolicy.mayDispatch(true, false, false, true, false, true));
        assertFalse(GunWieldInputPolicy.mayDispatch(false, false, false, true, false, true)); // normal Z remains held-use route
        assertFalse(GunWieldInputPolicy.mayDispatch(false, true, false, true, false, true)); // Shift+Z medical
        assertFalse(GunWieldInputPolicy.mayDispatch(false, false, true, true, false, true)); // Alt+Z medical
        assertFalse(GunWieldInputPolicy.mayDispatch(true, true, false, true, false, true));
        assertFalse(GunWieldInputPolicy.mayDispatch(true, false, true, true, false, true));
        assertFalse(GunWieldInputPolicy.mayDispatch(true, false, false, false, false, true)); // screen admission
        assertFalse(GunWieldInputPolicy.mayDispatch(true, false, false, true, true, true)); // text focus
        assertFalse(GunWieldInputPolicy.mayDispatch(true, false, false, true, false, false));
    }
}
