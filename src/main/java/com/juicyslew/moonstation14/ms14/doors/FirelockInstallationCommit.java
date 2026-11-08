package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Server-thread commit order for an already prepared attachment and admitted held-item mutation. */
public final class FirelockInstallationCommit {
    private FirelockInstallationCommit() { }
    public static BodyHeldItemMutation.Result commit(BooleanSupplier preparationCurrent,
            Supplier<BodyHeldItemMutation.Result> heldMutation,Runnable publishPrepared) {
        Objects.requireNonNull(preparationCurrent);Objects.requireNonNull(heldMutation);Objects.requireNonNull(publishPrepared);
        if(!preparationCurrent.getAsBoolean())return BodyHeldItemMutation.Result.DENIED;
        BodyHeldItemMutation.Result result=heldMutation.get();
        if(result==BodyHeldItemMutation.Result.SUCCESS)publishPrepared.run();
        return result;
    }
}
