package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;

/** Bounded post-publication classification for steel consumption after tube construction. */
final class DisposalBuildCommitPolicy {
    enum Materials { UNCHANGED, CONSUMED, UNCERTAIN }
    enum Decision { ROLLBACK_TUBE, KEEP_TUBE, KEEP_TUBE_RECOVERY }

    private DisposalBuildCommitPolicy() { }

    static Decision decide(BodyHeldItemMutation.Result result, Materials observed) {
        if (result == null || observed == null) return Decision.KEEP_TUBE_RECOVERY;
        if (observed == Materials.CONSUMED) return Decision.KEEP_TUBE;
        if (observed == Materials.UNCHANGED) return Decision.ROLLBACK_TUBE;
        return Decision.KEEP_TUBE_RECOVERY;
    }
}
