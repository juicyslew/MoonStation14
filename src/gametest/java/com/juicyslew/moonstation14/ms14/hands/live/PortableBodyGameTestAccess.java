package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Package-local hand-state bridge for fixtures which must commit a real portable custody key. */
public final class PortableBodyGameTestAccess {
    private PortableBodyGameTestAccess() { }

    public static LiveHands holdPortableKey(LivingEntity carrier, String hand, String token, ItemStack key) {
        LiveHands before = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(carrier)) throw new IllegalStateException("carrier hands unavailable");
        var staged = before.putWhole(before.revision(), hand, new ItemToken(token), key);
        if (!staged.succeeded() || !LiveHandsCommitGateway.commitTrusted(carrier, before, staged.state()))
            throw new IllegalStateException("portable key hand commit failed");
        return staged.state();
    }

    public static String lastDisposalAdmissionDiagnostic() {
        return DisposalUnitHandAdapter.lastPortableAdmissionDiagnostic();
    }

    public static AutoCloseable observeEscapeDiagnostics(java.util.function.Consumer<PortableBodyEscapeService.Diagnostic> observer) {
        return PortableBodyEscapeService.observeDiagnostics(observer);
    }

}
