package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisposalBuildCommitPolicyTest {
    @Test
    void recordRollbackRequiresObservedUnchangedMaterials() {
        for (BodyHeldItemMutation.Result result : BodyHeldItemMutation.Result.values()) {
            assertEquals(DisposalBuildCommitPolicy.Decision.ROLLBACK_TUBE,
                    DisposalBuildCommitPolicy.decide(result, DisposalBuildCommitPolicy.Materials.UNCHANGED));
        }
    }

    @Test
    void confirmedConsumptionKeepsTubeForEveryMutationResult() {
        for (BodyHeldItemMutation.Result result : BodyHeldItemMutation.Result.values()) {
            assertEquals(DisposalBuildCommitPolicy.Decision.KEEP_TUBE,
                    DisposalBuildCommitPolicy.decide(result, DisposalBuildCommitPolicy.Materials.CONSUMED));
        }
    }

    @Test
    void uncertainPublicationKeepsTubeAndRequiresRecoveryInsteadOfRiskingMaterialLoss() {
        assertEquals(DisposalBuildCommitPolicy.Decision.KEEP_TUBE_RECOVERY,
                DisposalBuildCommitPolicy.decide(BodyHeldItemMutation.Result.RECOVERY_REQUIRED,
                        DisposalBuildCommitPolicy.Materials.UNCERTAIN));
        assertEquals(DisposalBuildCommitPolicy.Decision.KEEP_TUBE_RECOVERY,
                DisposalBuildCommitPolicy.decide(BodyHeldItemMutation.Result.SUCCESS,
                        DisposalBuildCommitPolicy.Materials.UNCERTAIN));
    }
}
