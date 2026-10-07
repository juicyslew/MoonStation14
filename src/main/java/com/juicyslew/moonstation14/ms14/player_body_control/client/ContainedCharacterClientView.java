package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.containment.ContainedBodyView;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.UUID;

/** Latest authoritative contained presentation state for the locally committed character body. */
public final class ContainedCharacterClientView {
    private static final ContainedCameraPolicy CAMERA_POLICY = new ContainedCameraPolicy();
    private static UUID body;
    private static long epoch;
    private static ContainedBodyView view;

    private ContainedCharacterClientView() { }

    static void update(UUID bodyId, long committedEpoch, ContainedBodyView next) {
        body = bodyId;
        epoch = committedEpoch;
        view = next;
    }

    static void clear() { body = null; epoch = 0; view = null; }

    public static Optional<ContainedBodyView> current() {
        GhostControlClient.OwnedCharacterBinding binding = GhostControlClient.ownedCharacterBinding();
        return binding != null && binding.body().equals(body) && binding.epoch() == epoch
                ? Optional.ofNullable(view) : Optional.empty();
    }
    public static boolean isContained() { return current().isPresent(); }
    public static Optional<BlockPos> worldTarget() { return current().flatMap(ContainedBodyView::worldTarget); }

    static void reconcileCamera(net.minecraft.client.Minecraft minecraft) {
        OwnedBinding binding = ownedBinding();
        UUID bodyId = binding == null ? null : binding.body;
        long bindingEpoch = binding == null ? 0 : binding.epoch;
        boolean contained = binding != null && current().isPresent();
        var cameraType = CAMERA_POLICY.reconcile(bodyId, bindingEpoch, contained, minecraft.options.getCameraType());
        if (minecraft.options.getCameraType() != cameraType) minecraft.options.setCameraType(cameraType);
    }

    public static boolean perspectiveLocked() { return current().isPresent() && CAMERA_POLICY.active(); }

    public static boolean shouldUnclip(net.minecraft.world.entity.Entity cameraEntity) {
        OwnedBinding binding = ownedBinding();
        return binding != null && cameraEntity != null && CAMERA_POLICY.matches(cameraEntity.getUUID(), binding.body,
                binding.epoch, current().filter(view -> !view.cameraCollision()).isPresent());
    }

    private static OwnedBinding ownedBinding() {
        GhostControlClient.OwnedCharacterBinding binding = GhostControlClient.ownedCharacterBinding();
        return binding == null ? null : new OwnedBinding(binding.body(), binding.epoch());
    }
    private record OwnedBinding(UUID body, long epoch) { }
}
