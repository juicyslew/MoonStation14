package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LoadedFrameRayQueryCaptureTest {
    private static ClassNode compiled(Class<?> type) throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (var in = type.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            new ClassReader(in).accept(node, 0);
        }
        return node;
    }

    @Test void temporalPoseSelectionUsesExactTrackedFrameInterval() throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        try (var in = LoadedFrameRayQuery.class.getResourceAsStream("LoadedFrameRayQuery.class")) {
            assertNotNull(in);
            new ClassReader(in).accept(node, 0);
        }
        MethodNode pose = node.methods.stream().filter(m -> m.name.equals("pose")).findFirst().orElseThrow();
        var calls = Arrays.stream(pose.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).toList();
        assertTrue(calls.stream().anyMatch(call -> call.owner.endsWith("OwnedFrameSimulationScope")
                && call.name.equals("activeExact")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.endsWith("OwnedFrameSimulationScope")
                && call.name.equals("to") && call.desc.endsWith("SubLevel;)Ldev/ryanhcode/sable/companion/math/Pose3dc;")),
                "selection must resolve the requested sublevel's interval, not the scope anchor pose");
        assertFalse(calls.stream().anyMatch(call -> call.owner.endsWith("OwnedFrameSimulationScope")
                && call.name.equals("to") && call.desc.equals("()Ldev/ryanhcode/sable/companion/math/Pose3dc;")));
    }

    @Test void currentActorPointGuardsMissingSelectedPoseBeforeInverseTransform() throws Exception {
        var node = compiled(LoadedFrameRayQuery.class);
        MethodNode method = node.methods.stream().filter(m -> m.name.equals("currentActorPoint")).findFirst().orElseThrow();
        var insns = method.instructions.toArray();
        int poseCall = -1, inverseCall = -1;
        for (int i = 0; i < insns.length; i++) {
            if (insns[i] instanceof MethodInsnNode call && call.name.equals("pose")) poseCall = i;
            if (insns[i] instanceof MethodInsnNode call && call.name.equals("transformPositionInverse")) inverseCall = i;
        }
        assertTrue(poseCall >= 0 && inverseCall > poseCall);
        JumpInsnNode nullGuard = null;
        for (int i = poseCall + 1; i < inverseCall; i++)
            if (insns[i] instanceof JumpInsnNode jump
                    && (jump.getOpcode() == Opcodes.IFNULL || jump.getOpcode() == Opcodes.IFNONNULL)) nullGuard = jump;
        assertNotNull(nullGuard, "selected pose must be checked for null");
        int target = method.instructions.indexOf(nullGuard.label);
        assertTrue(target > poseCall && target < inverseCall, "null branch must bypass inverse transform");
        // IFNONNULL branches over the null return; IFNULL branches directly to it.
        int nullPath = nullGuard.getOpcode() == Opcodes.IFNULL ? target : method.instructions.indexOf(nullGuard) + 1;
        boolean nullReturn = false;
        for (int i = nullPath; i < inverseCall; i++) {
            if (insns[i].getOpcode() == Opcodes.ACONST_NULL) nullReturn = true;
            if (insns[i].getOpcode() == Opcodes.ARETURN) break;
        }
        assertTrue(nullReturn, "missing selected pose path returns null");
    }

    @Test void characterPredictionTemporalUnavailableHandlerDoesNotCommitOrPublish() throws Exception {
        var node = compiled(com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.class);
        List<MethodNode> candidates = node.methods.stream().filter(m -> m.name.equals("predictCharacter")
                && m.tryCatchBlocks.stream().anyMatch(block -> block.type != null
                && block.type.endsWith("OwnedFrameSimulationScope$TemporalUnavailableException"))
                && Arrays.stream(m.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).anyMatch(call -> call.name.equals("commit"))).toList();
        assertEquals(1, candidates.size(), "select the scoped prediction overload with handler and normal commit");
        MethodNode method = candidates.get(0);
        var insns = method.instructions.toArray();
        var handlers = method.tryCatchBlocks.stream().filter(block -> block.type != null
                && block.type.endsWith("OwnedFrameSimulationScope$TemporalUnavailableException")).toList();
        for (var handler : handlers) {
            int start = method.instructions.indexOf(handler.handler);
            int end = insns.length;
            for (int i = start + 1; i < insns.length; i++) {
                int opcode = insns[i].getOpcode();
                if (opcode == Opcodes.RETURN || opcode == Opcodes.ARETURN || opcode == Opcodes.ATHROW) { end = i + 1; break; }
            }
            boolean reset = false, pause = false, barrier = false;
            for (int i = start; i < end; i++) if (insns[i] instanceof MethodInsnNode call) {
                assertFalse(call.name.equals("commit") || call.name.equals("publish"), "unavailable handler cannot commit/publish");
                assertFalse(call.name.equals("predictCharacterInTemporalScope"), "unavailable handler cannot replay prediction");
                reset |= call.name.equals("reset");
                pause |= call.name.equals("pause");
                barrier |= call.name.equals("onSnapshotAuthorityBarrier");
            }
            assertTrue(reset, "unavailable handler resets owned simulation clock");
            assertTrue(pause, "unavailable handler pauses frame prediction state");
            assertTrue(barrier, "unavailable handler installs snapshot authority barrier");
        }
        assertTrue(Arrays.stream(insns).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.name.equals("commit")), "normal path still commits");
        assertTrue(method.tryCatchBlocks.stream().noneMatch(block -> block.type == null
                || block.type.equals("java/lang/Exception") || block.type.equals("java/lang/RuntimeException")
                || block.type.equals("java/lang/NullPointerException")), "unexpected failures must not be swallowed");
    }

    @Test void capturePreservesCoordinateBasisAndRefusesUnknownOrIncompleteFrameHit() {
        Vec3 endpoint = new Vec3(4, 5, 6);
        assertNull(LoadedFrameRayQuery.capture(new LoadedFrameRayQuery.Result(LoadedFrameRayQuery.State.UNKNOWN,null,null),endpoint));
        var miss=LoadedFrameRayQuery.capture(new LoadedFrameRayQuery.Result(LoadedFrameRayQuery.State.MISS,null,null),endpoint);
        assertEquals(endpoint,miss.point()); assertNull(miss.frameId());
        var worldHit=new BlockHitResult(new Vec3(1,2,3),Direction.UP,BlockPos.ZERO,false);
        var world=LoadedFrameRayQuery.capture(new LoadedFrameRayQuery.Result(LoadedFrameRayQuery.State.HIT,worldHit,null),null);
        assertEquals(worldHit.getLocation(),world.point()); assertNull(world.frameId());

        UUID id=UUID.randomUUID(); BlockPos source=new BlockPos(20_000_000,2,3);
        var legacy=new LoadedFrameRayQuery.Hit(source,Direction.NORTH,Vec3.ZERO,id);
        assertNull(LoadedFrameRayQuery.capture(new LoadedFrameRayQuery.Result(LoadedFrameRayQuery.State.HIT,null,legacy),null));
        Vec3 precise=new Vec3(20_000_000.125,2.75,3.5);
        var hit=new LoadedFrameRayQuery.Hit(source,Direction.NORTH,Vec3.ZERO,id,precise);
        source=new BlockPos(0,0,0);
        var captured=LoadedFrameRayQuery.capture(new LoadedFrameRayQuery.Result(LoadedFrameRayQuery.State.HIT,null,hit),null);
        assertEquals(precise,captured.point()); assertEquals(id,captured.frameId());
        assertEquals(new BlockPos(20_000_000,2,3),captured.frameCell());
        assertNull(LoadedFrameRayQuery.capture(new LoadedFrameRayQuery.Result(LoadedFrameRayQuery.State.MISS,null,null),
                new Vec3(Double.NaN,0,0)));
    }
}
