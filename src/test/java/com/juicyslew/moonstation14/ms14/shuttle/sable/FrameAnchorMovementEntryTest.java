package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FrameAnchorMovementEntryTest {
    @Test void explicitMovementEntryProjectionUsesCapturedPoseTransform() {
        var last = new Pose3d();
        last.position().set(120, 7, -80);
        last.orientation().rotateY(.2);
        var logical = new Pose3d();
        logical.position().set(121, 7, -80);
        logical.orientation().rotateY(.2);
        Vec3 storage = new Vec3(4, 2, 9);

        Vec3 previousIntervalWorld = last.transformPosition(storage);
        Vec3 currentLogicalWorld = logical.transformPosition(storage);

        assertEquals(120 + 4 * Math.cos(.2) + 9 * Math.sin(.2), previousIntervalWorld.x, 1e-10);
        assertEquals(9, previousIntervalWorld.y, 1e-10);
        assertEquals(-80 - 4 * Math.sin(.2) + 9 * Math.cos(.2), previousIntervalWorld.z, 1e-10);
        assertNotEquals(previousIntervalWorld, currentLogicalWorld);
        assertEntryProjectionDoesNotDoubleApplyCarrier(last, logical, storage);

        var rotatedLogical = new Pose3d();
        rotatedLogical.position().set(121, 7, -80);
        rotatedLogical.orientation().rotateY(.5);
        assertEntryProjectionDoesNotDoubleApplyCarrier(last, rotatedLogical, storage);
    }

    @Test void liveHydrationSelectsLastPoseAndUsesTwoEndpointResidencyProjection() throws Exception {
        var node = read(FrameAnchor.class);
        var hydrate = node.methods.stream().filter(m -> m.name.equals("hydrate")
                && m.desc.contains("Z)Z")).findFirst().orElseThrow();
        int lastPose = -1, project = -1;
        int poseLocal = -1;
        for (var instruction : hydrate.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.name.equals("lastPose")) {
                lastPose = hydrate.instructions.indexOf(instruction);
            }
            if (call.name.equals("projectActorPointForOwnedMovement"))
                project = hydrate.instructions.indexOf(instruction);
        }
        assertTrue(lastPose >= 0, "movement-entry restoration must consume the SDK previous interval pose");
        assertTrue(project > lastPose, "captured interval pose must be supplied to frame-point projection");
        for (var instruction : hydrate.instructions) {
            int i = index(hydrate, instruction);
            if (i <= lastPose || i >= project) continue;
            if (instruction instanceof org.objectweb.asm.tree.VarInsnNode store
                    && store.getOpcode() == Opcodes.ASTORE) { poseLocal = store.var; break; }
        }
        assertTrue(loadsLocalBetween(hydrate, poseLocal, lastPose, project),
                "the exact local receiving sub.lastPose flows into movement-entry projection");
        var query = read(com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.class);
        var checkedProjection = query.methods.stream().filter(m -> m.name.equals("projectActorPointForOwnedMovement"))
                .findFirst().orElseThrow();
        assertTrue(calls(checkedProjection, "physicalWorldPointReady") >= 2,
                "both current and movement-entry world endpoints require loaded FULL chunks");
        assertEquals(1, calls(checkedProjection, "projectActorPoint"),
                "the existing current-pose exact-frame/storage resolver supplies endpoint one");
        MethodInsnNode pointTransform = null;
        for (var instruction : checkedProjection.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals("transformPosition")
                    && call.owner.endsWith("/Pose3dc")) { pointTransform = call; break; }
        assertNotNull(pointTransform);
        assertTrue(loadsLocalBefore(checkedProjection, 3, pointTransform),
                "the explicit entryPose parameter is the SDK transform receiver for the stored point");
    }

    @Test void bothStateEntryPointsReuseTheSameInitializationBarrier() throws Exception {
        var node = read(FrameAnchorState.class);
        var ordinary = node.methods.stream().filter(m -> m.name.equals("initialize")
                && m.desc.endsWith("ServerLevel;Lnet/minecraft/world/entity/Entity;)Z")).findFirst().orElseThrow();
        var movement = node.methods.stream().filter(m -> m.name.equals("initializeForOwnedMovement")).findFirst().orElseThrow();
        assertTrue(calls(ordinary, "initialize") > 0);
        assertTrue(calls(movement, "initialize") > 0);
        var common = node.methods.stream().filter(m -> m.name.equals("initialize")
                && m.desc.contains("ServerLevel;Lnet/minecraft/world/entity/Entity;Z)Z")).findFirst().orElseThrow();
        assertTrue(calls(common, "initializeAttempt") > 0, "status, diagnostics, and one-time completion stay centralized");
    }

    @Test void unknownPhysicalEndpointIsRejectedWithoutDereference() throws Exception {
        var ready = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.class
                .getDeclaredMethod("physicalWorldPointReady", net.minecraft.server.level.ServerLevel.class, Vec3.class);
        ready.setAccessible(true);
        assertFalse((boolean) ready.invoke(null, null, null));
        assertFalse((boolean) ready.invoke(null, null, Vec3.ZERO));
    }

    @Test void groundedMovementBudgetAndFailedHydrationBranchesSkipStateCaptureMotorAndNativeAttempt() throws Exception {
        var node = read(com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep.class);
        var wrapper = node.methods.stream().filter(m -> m.name.equals("stepCore")
                && calls(m, "push") > 0).findFirst().orElseThrow();
        var step = node.methods.stream().filter(m -> m.name.equals("stepCoreUnprofiled")).findFirst().orElseThrow();
        var delegate = call(wrapper, "stepCoreUnprofiled");
        assertEquals(step.desc, delegate.desc, "profiling wrapper delegates to the underlying movement implementation");
        var budget = call(step, "moonstation14$physicalStepConsumed");
        var initialize = call(step, "initializeForOwnedMovement");
        var motor = call(step, "tick", "GroundedHarnessMotor");
        MethodInsnNode stateCapture = null;
        for (var instruction : step.instructions) {
            if (instruction instanceof MethodInsnNode constructor && constructor.name.equals("<init>")
                    && constructor.owner.endsWith("/CharacterMovementState")
                    && index(step, instruction) > index(step, initialize)) { stateCapture = constructor; break; }
        }
        assertNotNull(stateCapture, "successful initialization must reach post-hydration state capture");
        var budgetBranch = branchAfter(step, budget);
        assertTrue(canReachReturnBefore(step, nextOpcode(budgetBranch), Set.of("initializeForOwnedMovement", "tick")),
                "consumed-attempt branch returns before initialization and motor");
        assertTrue(canReach(step, budgetBranch.label, "initializeForOwnedMovement"), "unconsumed branch reaches initialization");

        var failureBranch = branchAfter(step, initialize);
        assertTrue(canReachReturnBefore(step, nextOpcode(failureBranch), Set.of("tick", "<init>")),
                "failed hydration returns before post-hydration state capture or motor");
        assertTrue(canReach(step, failureBranch.label, "tick"), "successful hydration reaches motor");
        assertTrue(instructionsBetween(step, initialize, stateCapture), "state capture follows hydration");
        assertTrue(instructionsBetween(step, initialize, motor), "motor follows hydration");

        var collisionCallback = node.methods.stream().filter(m -> m.name.startsWith("lambda$stepCoreUnprofiled$"))
                .filter(m -> calls(m, "move") > 0).findFirst().orElseThrow();
        var mark = call(collisionCallback, "moonstation14$markPhysicalStep");
        var nativeMove = call(collisionCallback, "move", "SableOwnedBodyMotion");
        assertTrue(index(collisionCallback, mark) < index(collisionCallback, nativeMove),
                "the actual native attempt is marked before the callback moves the body");
    }

    @Test void passiveCorpseAdmissionAndFailedHydrationBranchesAreGated() throws Exception {
        var dispatcher = read(com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedLivingPhysics.class)
                .methods.stream().filter(m -> m.name.equals("tick")).findFirst().orElseThrow();
        int ticking = index(dispatcher, call(dispatcher, "isPositionEntityTicking"));
        var pendingCall = call(dispatcher, "sable$frameAnchorInitializationPending");
        int pending = index(dispatcher, pendingCall);
        int corpseEligibility = index(dispatcher, call(dispatcher, "eligible", "OwnedCorpsePhysics"));
        var corpseStepCall = call(dispatcher, "step", "OwnedCorpsePhysics");
        int corpseStep = index(dispatcher, corpseStepCall);
        assertTrue(ticking < pending && pending < corpseEligibility && corpseEligibility < corpseStep,
                "position-ticking admission and corpse/pending predicates precede the corpse step");
        var tickingBranch = branchAfter(dispatcher, call(dispatcher, "isPositionEntityTicking"));
        assertEquals(Opcodes.IFNE, tickingBranch.getOpcode(), "position-ticking bodies take the normal dispatch branch");
        assertTrue(canReach(dispatcher, tickingBranch.label, "step", "OwnedCorpsePhysics"),
                "position-ticking admission allows eligible corpse consumers");
        assertFalse(canReachBefore(dispatcher, nextOpcode(tickingBranch), "step", "OwnedCorpsePhysics", Set.of("next")),
                "non-position-ticking bodies cannot reach corpse initialization before the loop advances");
        assertFalse(canReachBefore(dispatcher, nextOpcode(tickingBranch), "passiveStep", "GroundedHarnessWorldStep", Set.of("next")),
                "non-position-ticking bodies cannot reach the custom native consumer");

        var pendingLocal = storedLocalAfter(dispatcher, pendingCall, corpseStepCall);
        assertTrue(pendingLocal >= 0);
        var pendingGuard = nearestConditionalBefore(dispatcher, corpseStepCall);
        assertEquals(Opcodes.IFEQ, pendingGuard.getOpcode());
        assertEquals(pendingLocal, loadedLocalBefore(dispatcher, pendingGuard),
                "only the captured pending=true value enters the corpse consumer");
        assertTrue(canReach(dispatcher, nextOpcode(pendingGuard), "step", "OwnedCorpsePhysics"),
                "pending eligible corpse can enter the owned corpse consumer");
        assertFalse(canReachBefore(dispatcher, pendingGuard.label, "step", "OwnedCorpsePhysics", Set.of("next")),
                "eligible corpse with pending=false skips the corpse/native movement consumer");

        var corpse = read(com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.class);
        var step = corpse.methods.stream().filter(m -> m.name.equals("step")).findFirst().orElseThrow();
        var consumed = call(step, "moonstation14$physicalStepConsumed");
        var initialize = call(step, "initializeForOwnedMovement");
        var motor = call(step, "tick", "GroundedHarnessMotor");
        var consumedBranch = branchAfter(step, consumed);
        assertTrue(canReachReturnBefore(step, nextOpcode(consumedBranch), Set.of("initializeForOwnedMovement", "tick")),
                "already-consumed corpse attempt returns before initialization and gravity");
        var failureBranch = branchAfter(step, initialize);
        assertTrue(canReachReturnBefore(step, nextOpcode(failureBranch), Set.of("tick", "move")),
                "failed corpse hydration returns before motor and native movement");
        assertTrue(canReach(step, failureBranch.label, "tick", "GroundedHarnessMotor"));
        assertTrue(index(step, initialize) < index(step, motor));
    }

    private static int calls(org.objectweb.asm.tree.MethodNode method, String name) {
        int result = 0;
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) result++;
        return result;
    }

    private static void assertEntryProjectionDoesNotDoubleApplyCarrier(Pose3d entry, Pose3d logical, Vec3 storage) {
        Vec3 entryWorld = entry.transformPosition(storage);
        Vec3 logicalWorld = logical.transformPosition(storage);
        Vec3 nativeIntervalCarrier = logicalWorld.subtract(entryWorld);
        Vec3 correctlyEntered = logical.transformPositionInverse(entryWorld.add(nativeIntervalCarrier));
        Vec3 latestPosePlaced = logical.transformPositionInverse(logicalWorld.add(nativeIntervalCarrier));
        assertEquals(storage.x, correctlyEntered.x, .12);
        assertEquals(storage.y, correctlyEntered.y, .12);
        assertEquals(storage.z, correctlyEntered.z, .12);
        assertTrue(latestPosePlaced.distanceTo(storage) > .12,
                "latest-pose placement followed by native interval carrier double-applies frame motion");
    }

    private static MethodInsnNode call(org.objectweb.asm.tree.MethodNode method, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) return call;
        throw new AssertionError("missing invocation " + name + " in " + method.name);
    }
    private static MethodInsnNode call(org.objectweb.asm.tree.MethodNode method, String name, String ownerSuffix) {
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(name) && call.owner.endsWith(ownerSuffix)) return call;
        throw new AssertionError("missing invocation " + name + " owner=" + ownerSuffix + " in " + method.name);
    }
    private static org.objectweb.asm.tree.JumpInsnNode branchAfter(org.objectweb.asm.tree.MethodNode method,
            MethodInsnNode call) {
        var next = nextOpcode(call);
        assertTrue(next instanceof org.objectweb.asm.tree.JumpInsnNode);
        return (org.objectweb.asm.tree.JumpInsnNode) next;
    }
    private static boolean canReach(org.objectweb.asm.tree.MethodNode method,
            org.objectweb.asm.tree.AbstractInsnNode start, String name) {
        return canReach(method, start, name, null);
    }
    private static boolean canReachBefore(org.objectweb.asm.tree.MethodNode method,
            org.objectweb.asm.tree.AbstractInsnNode start, String name, String owner, Set<String> forbiddenCalls) {
        return walk(method, start, instruction -> instruction instanceof MethodInsnNode call
                && call.name.equals(name) && call.owner.endsWith(owner), forbiddenCalls).found;
    }
    private static boolean canReach(org.objectweb.asm.tree.MethodNode method,
            org.objectweb.asm.tree.AbstractInsnNode start, String name, String owner) {
        return walk(method, start, instruction -> instruction instanceof MethodInsnNode call
                && call.name.equals(name) && (owner == null || call.owner.endsWith(owner)), Set.of()).found;
    }
    private static boolean canReachReturnBefore(org.objectweb.asm.tree.MethodNode method,
            org.objectweb.asm.tree.AbstractInsnNode start, Set<String> forbiddenCalls) {
        return walk(method, start, instruction -> instruction.getOpcode() == Opcodes.RETURN
                || instruction.getOpcode() == Opcodes.ARETURN || instruction.getOpcode() == Opcodes.IRETURN
                || instruction.getOpcode() == Opcodes.LRETURN || instruction.getOpcode() == Opcodes.FRETURN
                || instruction.getOpcode() == Opcodes.DRETURN,
                forbiddenCalls).found;
    }
    private static WalkResult walk(org.objectweb.asm.tree.MethodNode method,
            org.objectweb.asm.tree.AbstractInsnNode start,
            java.util.function.Predicate<org.objectweb.asm.tree.AbstractInsnNode> target,
            Set<String> forbiddenCalls) {
        var pending = new java.util.ArrayDeque<org.objectweb.asm.tree.AbstractInsnNode>();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<org.objectweb.asm.tree.AbstractInsnNode, Boolean>());
        pending.add(start);
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (current == null || !seen.add(current)) continue;
            if (target.test(current)) return new WalkResult(true);
            if (current instanceof MethodInsnNode call && forbiddenCalls.contains(call.name)) continue;
            int opcode = current.getOpcode();
            if (opcode == Opcodes.RETURN || opcode == Opcodes.ARETURN || opcode == Opcodes.IRETURN
                    || opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN || opcode == Opcodes.DRETURN
                    || opcode == Opcodes.ATHROW) continue;
            if (current instanceof org.objectweb.asm.tree.JumpInsnNode jump) {
                pending.add(jump.label);
                if (opcode != Opcodes.GOTO && opcode != Opcodes.JSR) pending.add(current.getNext());
            } else if (current instanceof org.objectweb.asm.tree.TableSwitchInsnNode table) {
                pending.add(table.dflt); pending.addAll(table.labels);
            } else if (current instanceof org.objectweb.asm.tree.LookupSwitchInsnNode lookup) {
                pending.add(lookup.dflt); pending.addAll(lookup.labels);
            } else pending.add(current.getNext());
        }
        return new WalkResult(false);
    }
    private record WalkResult(boolean found) { }
    private static boolean instructionsBetween(org.objectweb.asm.tree.MethodNode method,
            org.objectweb.asm.tree.AbstractInsnNode first, org.objectweb.asm.tree.AbstractInsnNode last) {
        return index(method, first) >= 0 && index(method, last) > index(method, first);
    }
    private static int index(org.objectweb.asm.tree.MethodNode method, org.objectweb.asm.tree.AbstractInsnNode target) {
        int i = 0;
        for (var instruction : method.instructions) { if (instruction == target) return i; i++; }
        return -1;
    }
    private static boolean loadsLocalBetween(org.objectweb.asm.tree.MethodNode method, int local,
            int start, int end) {
        if (local < 0) return false;
        for (var instruction : method.instructions) {
            int i = index(method, instruction);
            if (i <= start || i >= end) continue;
            if (instruction instanceof org.objectweb.asm.tree.VarInsnNode var
                    && var.getOpcode() == Opcodes.ALOAD && var.var == local) return true;
        }
        return false;
    }
    private static boolean loadsLocalBefore(org.objectweb.asm.tree.MethodNode method, int local,
            org.objectweb.asm.tree.AbstractInsnNode end) {
        for (var instruction : method.instructions) {
            if (instruction == end) return false;
            if (instruction instanceof org.objectweb.asm.tree.VarInsnNode var
                    && var.getOpcode() == Opcodes.ALOAD && var.var == local) return true;
        }
        return false;
    }
    private static org.objectweb.asm.tree.AbstractInsnNode nextOpcode(org.objectweb.asm.tree.AbstractInsnNode node) {
        var next = node.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }
    private static org.objectweb.asm.tree.JumpInsnNode nearestConditionalBefore(
            org.objectweb.asm.tree.MethodNode method, org.objectweb.asm.tree.AbstractInsnNode node) {
        for (var previous = node.getPrevious(); previous != null; previous = previous.getPrevious())
            if (previous instanceof org.objectweb.asm.tree.JumpInsnNode jump
                    && jump.getOpcode() != Opcodes.GOTO && jump.getOpcode() != Opcodes.JSR) return jump;
        throw new AssertionError("missing conditional guard before " + node);
    }
    private static int storedLocalAfter(org.objectweb.asm.tree.MethodNode method,
            org.objectweb.asm.tree.AbstractInsnNode start, org.objectweb.asm.tree.AbstractInsnNode end) {
        for (var instruction = start.getNext(); instruction != null && instruction != end; instruction = instruction.getNext())
            if (instruction instanceof org.objectweb.asm.tree.VarInsnNode var && var.getOpcode() == Opcodes.ISTORE)
                return var.var;
        return -1;
    }
    private static int loadedLocalBefore(org.objectweb.asm.tree.MethodNode method,
            org.objectweb.asm.tree.AbstractInsnNode node) {
        var previous = node.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous instanceof org.objectweb.asm.tree.VarInsnNode var && var.getOpcode() == Opcodes.ILOAD
                ? var.var : -1;
    }

    private static ClassNode read(Class<?> type) throws Exception {
        var node = new ClassNode(Opcodes.ASM9);
        try (var in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(in);
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }
}
