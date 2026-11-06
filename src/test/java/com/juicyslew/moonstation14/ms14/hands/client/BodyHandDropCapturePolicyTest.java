package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BodyHandDropCapturePolicyTest {
    private static BodyHandRaySnapshot.Snapshot ray(LoadedFrameRayQuery.Result result) {
        return new BodyHandRaySnapshot.Snapshot(Vec3.ZERO, new Vec3(0, -1, 0), new Vec3(0, -3.5, 0), 1, result);
    }

    @Test void onlyDeterminateMissOrNonUpHitCanUsePlainDrop() {
        assertFalse(BodyHandClient.dropAimDecision(null, false).usable());
        assertFalse(BodyHandClient.dropAimDecision(ray(new LoadedFrameRayQuery.Result(
                LoadedFrameRayQuery.State.UNKNOWN, null, null)), false).usable());
        assertTrue(BodyHandClient.dropAimDecision(ray(new LoadedFrameRayQuery.Result(
                LoadedFrameRayQuery.State.MISS, null, null)), false).usable());
        var side = new BlockHitResult(new Vec3(0, -1, 0), Direction.NORTH, new BlockPos(0, -1, 0), false);
        var nonFloor = BodyHandClient.dropAimDecision(ray(new LoadedFrameRayQuery.Result(
                LoadedFrameRayQuery.State.HIT, side, null)), false);
        assertTrue(nonFloor.usable()); assertNull(nonFloor.target());
    }

    @Test void committedContainedDropBypassesCameraCaptureAndRemainsPlain() {
        var contained = BodyHandClient.dropAimDecision(null, true);
        assertTrue(contained.usable()); assertNull(contained.target());
        assertFalse(BodyHandClient.dropAimDecision(null, false).usable());
    }

    @Test void upHitMustProduceTargetAndProductionWiresDecisionAndServerTarget() throws Exception {
        var top = new BlockHitResult(new Vec3(0, -1, 0), Direction.UP, new BlockPos(0, -1, 0), false);
        var decision = BodyHandClient.dropAimDecision(ray(new LoadedFrameRayQuery.Result(
                LoadedFrameRayQuery.State.HIT, top, null)), false);
        assertTrue(decision.usable()); assertNotNull(decision.target());

        String client = read("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClient.java");
        int start = client.indexOf("if (action == BodyHandActionRequest.Action.DROP)");
        int end = client.indexOf("UUID target = null;", start);
        String dropBranch = client.substring(start, end);
        assertTrue(dropBranch.contains("ContainedCharacterClientView.isContained()"));
        assertTrue(dropBranch.contains("if (contained)"));
        assertTrue(dropBranch.contains("dropAimDecision(null, true)"));
        assertTrue(dropBranch.contains("if (cameraBody == null || mc.level == null || mc.getCameraEntity() != cameraBody) return false"));
        assertTrue(dropBranch.contains("BodyHandRaySnapshot.capture("));
        assertTrue(dropBranch.contains("dropAimDecision(ray, false)"));
        assertTrue(dropBranch.contains("if (!decision.usable()) return false"));
        assertFalse(dropBranch.contains("LoadedFrameRayQuery.clip("));
        assertTrue(client.contains("decision.target()"));

        String service = read("src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandRequestService.java");
        assertTrue(service.contains("request.expectedRevision(), request.throwTarget(), spawned::set"));
        String transfer = read("src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyHandItemTransfer.java");
        assertTrue(transfer.contains("target == null\n                        ? BodyItemTargetProbe.resolveDrop"));
        assertTrue(transfer.contains("BodyItemTargetProbe.resolveDrop(level, body, excludedCarrier, profile.physicalWidth(), profile.physicalHeight(), target)"));
    }

    private static String read(String relative) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve(relative))) root = root.getParent();
        assertNotNull(root, "source file: " + relative);
        return Files.readString(root.resolve(relative));
    }
}
