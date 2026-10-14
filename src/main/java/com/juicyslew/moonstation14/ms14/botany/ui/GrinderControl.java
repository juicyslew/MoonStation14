package com.juicyslew.moonstation14.ms14.botany.ui;

import java.util.UUID;

/** Client intent only; slot/ledger transfer proofs are always derived from live server state. */
public record GrinderControl(UUID sessionId, UUID placement, long expectedStateRevision,
                             Operation operation, int slotIndex) {
    public enum Operation { GRIND, EJECT_ONE, EJECT_ALL, EJECT_BEAKER, CLOSE }
}
