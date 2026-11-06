package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reconstructed R45 coverage; original source unavailable, not byte-exact restoration. */
class FrameAnchorLifecycleTraceTest {
    @Test void commonInitializerArmsOnlyFromServiceEvidenceAndLivingTypeBeforeHydrationAttempt() throws Exception {
        ClassNode node = readState();
        MethodNode initialize = node.methods.stream().filter(m -> m.name.equals("initialize")
                && m.desc.contains("ServerLevel;Lnet/minecraft/world/entity/Entity;Z)Z")).findFirst().orElseThrow();
        List<MethodInsnNode> calls = calls(initialize);
        MethodInsnNode arm = calls.stream().filter(c -> c.name.equals("armPostRestoreMotionDiagnostics")).findFirst().orElseThrow();
        MethodInsnNode hydrationAttempt = calls.stream().filter(c -> c.name.equals("initializeAttempt")).findFirst().orElseThrow();
        assertTrue(index(initialize, arm) < index(initialize, hydrationAttempt), "frame restoration arms before hydration");
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("lifecycleDiagnosticsEnabled")
                && index(initialize, c) < index(initialize, arm)), "scope arm input comes from enabled capture service");
        boolean livingTypeCheck = false;
        for (AbstractInsnNode insn : initialize.instructions)
            if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals("net/minecraft/world/entity/LivingEntity") && index(initialize, insn) < index(initialize, arm)) livingTypeCheck = true;
        assertTrue(livingTypeCheck, "arming input must be derived from LivingEntity type, not a constant");
    }

    @Test void motionTraceUsesBoundedSampleAndDisablesOnlyInsideDiagnosticFailurePath() throws Exception {
        ClassNode node = readState();
        MethodNode trace = node.methods.stream().filter(m -> m.name.equals("tracePostRestoreMotion")).findFirst().orElseThrow();
        List<MethodInsnNode> calls = calls(trace);
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("nextPostRestoreMotionSample")), "trace must consume bounded sample budget");
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("describe") && c.owner.endsWith("OwnedNativeMoveDiagnostic")),
                "native diagnostic formatter must remain within the trace boundary");
        MethodInsnNode disable = calls.stream().filter(c -> c.name.equals("disablePostRestoreMotionDiagnostics")).findFirst().orElseThrow();
        assertTrue(trace.tryCatchBlocks.stream().anyMatch(block -> "java/lang/RuntimeException".equals(block.type)
                && index(trace, block.handler) < index(trace, disable)), "formatter/output failure must route through bounded catch");
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("println") && index(trace, c) > index(trace, disable)),
                "failure diagnostic is attempted after disabling further trace output");
    }

    @Test void pendingBodyTickPreservesNativeTickBarrierAndTracesNormalSuper() throws Exception {
        ClassNode node = readClass("com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity");
        MethodNode tick = node.methods.stream().filter(m -> m.name.equals("tick") && m.desc.equals("()V")).findFirst().orElseThrow();
        List<MethodInsnNode> calls = calls(tick);
        MethodInsnNode before = calls.stream().filter(c -> c.name.equals("traceLifecycle") && hasConstantBefore(tick, c, "BEFORE_BODY_SUPER")).findFirst().orElseThrow();
        MethodInsnNode pending = calls.stream().filter(c -> c.name.equals("pending") && c.owner.endsWith("FrameAnchorState")).findFirst().orElseThrow();
        MethodInsnNode superTick = calls.stream().filter(c -> c.name.equals("tick") && c.owner.equals(node.superName)).findFirst().orElseThrow();
        MethodInsnNode after = calls.stream().filter(c -> c.name.equals("traceLifecycle") && hasConstantBefore(tick, c, "AFTER_BODY_SUPER")).findFirst().orElseThrow();
        assertTrue(index(tick, before) < index(tick, pending));
        assertFalse(calls.stream().anyMatch(c -> c.name.equals("initialize") && c.owner.endsWith("FrameAnchorState")),
                "body tick must not consume the anchor before its admitted movement entry");
        assertPendingBodyTickReturnsBeforeSuper(tick, pending, superTick);
        assertTrue(index(tick, superTick) < index(tick, after));
        assertEquals(2, calls.stream().filter(c -> c.name.equals("traceLifecycle") && c.owner.endsWith("FrameAnchorState")).count());
    }

    @Test void bodySaveUsesStateWriterWithActualServerLevelAndBody() throws Exception {
        ClassNode node = readClass("com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity");
        MethodNode save = node.methods.stream().filter(m -> m.name.equals("addAdditionalSaveData")).findFirst().orElseThrow();
        List<MethodInsnNode> calls = calls(save);
        MethodInsnNode parent = calls.stream().filter(c -> c.name.equals("addAdditionalSaveData") && c.owner.equals(node.superName)).findFirst().orElseThrow();
        MethodInsnNode write = calls.stream().filter(c -> c.name.equals("write") && c.owner.endsWith("FrameAnchorState")).findFirst().orElseThrow();
        assertTrue(index(save, parent) < index(save, write));
        assertTrue(hasLocalAndThisArguments(save, write), "state writer must receive server-level local and this entity");
        assertFalse(calls.stream().anyMatch(c -> c.name.equals("writeCapture")), "entity save must not bypass the state writer");
    }

    @Test void lifecycleWriterTracesSaveBeforePendingReturnAndCapture() throws Exception {
        ClassNode node = readState();
        MethodNode write = node.methods.stream().filter(m -> m.name.equals("write") && m.desc.contains("ServerLevel")).findFirst().orElseThrow();
        List<MethodInsnNode> calls = calls(write);
        MethodInsnNode trace = calls.stream().filter(c -> c.name.equals("traceLifecycle")).findFirst().orElseThrow();
        MethodInsnNode pending = calls.stream().filter(c -> c.name.equals("pending")).findFirst().orElseThrow();
        MethodInsnNode capture = calls.stream().filter(c -> c.name.equals("capture")).findFirst().orElseThrow();
        assertTrue(index(write, trace) < index(write, pending));
        assertTrue(index(write, pending) < index(write, capture));
        assertPendingWriteReturnsBeforeCapture(write, pending, capture);
        assertTrue(hasFieldRead(write, "physicalComposition"),
                "normal capture must receive this state's owned physical composition");
    }

    @Test void unloadObserverReportsArmedActorsAndRecordsOwnerIdentityWithoutFilteringDiagnostics() throws Exception {
        ClassNode node = readClass(FrameAnchorCaptureService.class.getName());
        MethodNode observer = node.methods.stream().filter(m -> m.name.equals("observeLifecycleRemoval")).findFirst().orElseThrow();
        List<MethodInsnNode> calls = calls(observer);
        MethodInsnNode trace = calls.stream().filter(c -> c.name.equals("traceLifecycle")).findFirst().orElseThrow();
        assertLifecycleObserverGuards(observer, trace);
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("lifecycleDiagnosticsArmed") && index(observer, c) < index(observer, trace)),
                "unarmed actor must be skipped before trace");
        MethodInsnNode armed = calls.stream().filter(c -> c.name.equals("lifecycleDiagnosticsArmed")).findFirst().orElseThrow();
        assertActorUnarmedSkipsTrace(observer, armed, trace);
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("sable$getTrackingSubLevel") && index(observer, c) < index(observer, trace)), "diagnostic records current native owner");
        assertTrue(containsStringConcatMarker(observer, "nativeOwnerIsCallback"), "identity comparison is diagnostic detail, not a filter");
        assertFalse(calls.stream().anyMatch(c -> c.name.contains("getChunk") || c.name.equals("setPos") || c.name.equals("move")), "observer must not force chunks or move actors");

        ClassNode callbackNode = readClass(FrameAnchorCaptureService.class.getName() + "$1");
        MethodNode callback = callbackNode.methods.stream().filter(m -> m.name.equals("onSubLevelRemoved")).findFirst().orElseThrow();
        List<MethodInsnNode> callbackCalls = calls(callback);
        MethodInsnNode diagnostic = callbackCalls.stream().filter(c -> c.name.equals("observeLifecycleRemoval")).findFirst().orElseThrow();
        MethodInsnNode proof = callbackCalls.stream().filter(c -> c.name.equals("recordRemoval")).findFirst().orElseThrow();
        assertTrue(index(callback, diagnostic) < index(callback, proof), "real SDK observer runs diagnostic before owned removal proof");
    }

    @Test void lifecycleTraceFailureAndBudgetRemainDiagnosticOnlyAndNonforcing() throws Exception {
        ClassNode node = readState();
        MethodNode trace = node.methods.stream().filter(m -> m.name.equals("traceLifecycle") && m.desc.contains("DiagnosticPhase") && m.desc.contains("Ljava/lang/String;)V")).findFirst().orElseThrow();
        List<MethodInsnNode> traceCalls = calls(trace);
        MethodInsnNode unsafe = traceCalls.stream().filter(c -> c.name.equals("traceLifecycleUnsafe")).findFirst().orElseThrow();
        TryCatchBlockNode boundary = trace.tryCatchBlocks.stream().filter(b -> "java/lang/RuntimeException".equals(b.type)
                && index(trace, b.start) <= index(trace, unsafe) && index(trace, unsafe) < index(trace, b.end)).findFirst().orElseThrow();
        assertCatchBoundaryIsDiagnosticOnly(trace, boundary);
        MethodNode unsafeTrace = node.methods.stream().filter(m -> m.name.equals("traceLifecycleUnsafe")).findFirst().orElseThrow();
        List<MethodInsnNode> unsafeCalls = calls(unsafeTrace);
        MethodInsnNode budget = unsafeCalls.stream().filter(c -> c.name.equals("diagnosticBudgetAllows")).findFirst().orElseThrow();
        assertBudgetFailureReturnsBeforeWorldSnapshot(unsafeTrace, budget);
        assertTrue(unsafeCalls.stream().anyMatch(c -> c.name.equals("getChunkNow")), "chunk read must remain nonforcing");
        assertTrue(unsafeCalls.stream().anyMatch(c -> c.name.equals("isPositionEntityTicking")), "ticking evidence uses nonforcing API");
        assertFalse(unsafeCalls.stream().anyMatch(c -> c.name.equals("getChunk") || c.name.equals("getChunkForLighting")), "no force-load chunk query");
        assertDiagnosticMutationFree(unsafeTrace);
        assertDiagnosticMutationFree(trace);
    }

    private static ClassNode readState() throws Exception {
        String resource = FrameAnchorState.class.getName().replace('.', '/') + ".class";
        try (InputStream in = FrameAnchorState.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in); ClassNode node = new ClassNode(Opcodes.ASM9); new ClassReader(in).accept(node, 0); return node;
        }
    }
    private static ClassNode readClass(String name) throws Exception {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream in = FrameAnchorLifecycleTraceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource); ClassNode node = new ClassNode(Opcodes.ASM9); new ClassReader(in).accept(node, 0); return node;
        }
    }
    private static boolean hasConstantBefore(MethodNode method, AbstractInsnNode target, String value) {
        int until = index(method, target), i = 0;
        for (AbstractInsnNode insn : method.instructions) { if (i++ >= until) break; if (insn instanceof FieldInsnNode f && f.name.equals(value)) return true; }
        return false;
    }
    private static void assertInitializationFailureSkipsSuper(MethodNode method, MethodInsnNode initialize, MethodInsnNode superTick, MethodInsnNode after) {
        AbstractInsnNode next = initialize.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        assertTrue(next instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNE, "false initialization result branches to normal tick path");
        AbstractInsnNode fallthrough = next.getNext();
        while (fallthrough != null && fallthrough.getOpcode() < 0) fallthrough = fallthrough.getNext();
        assertNotNull(fallthrough);
        assertEquals(Opcodes.RETURN, fallthrough.getOpcode(), "failed initialization returns before super");
        assertTrue(index(method, ((JumpInsnNode) next).label) > index(method, fallthrough));
        assertTrue(index(method, ((JumpInsnNode) next).label) < index(method, superTick));
        assertTrue(index(method, ((JumpInsnNode) next).label) < index(method, after));
    }
    private static void assertPendingBodyTickReturnsBeforeSuper(MethodNode method, MethodInsnNode pending,
                                                                  MethodInsnNode superTick) {
        AbstractInsnNode branch = nextOpcode(pending);
        assertTrue(branch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ,
                "normal bodies branch to vanilla tick; pending bodies remain behind the native-entry barrier");
        AbstractInsnNode pendingPath = nextOpcode(branch);
        assertNotNull(pendingPath);
        assertEquals(Opcodes.RETURN, pendingPath.getOpcode(), "pending reconstruction skips vanilla physics and gravity");
        assertTrue(index(method, ((JumpInsnNode) branch).label) < index(method, superTick));
    }
    private static AbstractInsnNode nextOpcode(AbstractInsnNode node) {
        AbstractInsnNode next = node.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }
    private static void assertLifecycleObserverGuards(MethodNode method, MethodInsnNode trace) {
        FieldInsnNode scope = firstFieldRead(method, "lifecycleDiagnostics");
        assertNotNull(scope);
        AbstractInsnNode scopeBranch = nextOpcode(scope);
        assertTrue(scopeBranch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ, "disabled scope branches to early return");
        AbstractInsnNode scopeFalse = ((JumpInsnNode) scopeBranch).label;
        assertEquals(Opcodes.RETURN, nextOpcode(scopeFalse).getOpcode(), "disabled scope returns before entity scan");
        FieldInsnNode active = firstFieldReadAfter(method, "active", scopeBranch);
        assertNotNull(active);
        AbstractInsnNode activeBranch = nextOpcode(active);
        assertTrue(activeBranch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNE, "active ownership proofs branch to scan continuation");
        AbstractInsnNode activeTrue = ((JumpInsnNode) activeBranch).label;
        AbstractInsnNode activeFalse = nextOpcode(activeBranch);
        assertNotNull(activeFalse); assertEquals(Opcodes.RETURN, activeFalse.getOpcode(), "inactive service returns before entity scan");
        assertSame(nextOpcode(scopeFalse), activeFalse, "both false service guards converge on early return");
        MethodInsnNode scan = calls(method).stream().filter(c -> c.name.equals("getAllEntities")).findFirst().orElseThrow();
        assertTrue(index(method, scopeFalse) < index(method, scan));
        assertTrue(index(method, activeTrue) < index(method, scan));
        assertTrue(index(method, scan) < index(method, trace));
    }
    private static void assertActorUnarmedSkipsTrace(MethodNode method, MethodInsnNode armed, MethodInsnNode trace) {
        AbstractInsnNode branch = nextOpcode(armed);
        assertTrue(branch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNE, "armed body takes trace path");
        LabelNode loopContinue = ((JumpInsnNode) branch).label;
        assertTrue(index(method, loopContinue) > index(method, armed) && index(method, loopContinue) < index(method, trace),
                "armed path reaches trace; unarmed path jumps to next loop iteration before trace");
        AbstractInsnNode unarmed = nextOpcode(branch);
        assertTrue(unarmed instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.GOTO, "unarmed actor takes observer-loop backedge");
        assertTrue(index(method, ((JumpInsnNode) unarmed).label) < index(method, armed));
        MethodInsnNode hasNext = calls(method).stream().filter(c -> c.name.equals("hasNext")).findFirst().orElseThrow();
        assertTrue(index(method, ((JumpInsnNode) unarmed).label) <= index(method, hasNext));
        assertTrue(index(method, hasNext) < index(method, armed), "unarmed backedge returns to iterator check before next actor");
    }
    private static void assertPendingWriteReturnsBeforeCapture(MethodNode method, MethodInsnNode pending, MethodInsnNode capture) {
        AbstractInsnNode branch = nextOpcode(pending);
        assertTrue(branch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ, "nonpending write branches to normal capture path");
        assertTrue(index(method, ((JumpInsnNode) branch).label) > index(method, pending));
        assertTrue(index(method, ((JumpInsnNode) branch).label) < index(method, capture));
        assertPathsTerminateBefore(method, nextOpcode(branch), capture);
    }
    private static void assertPathsTerminateBefore(MethodNode method, AbstractInsnNode start, AbstractInsnNode forbidden) {
        java.util.ArrayDeque<AbstractInsnNode> work = new java.util.ArrayDeque<>();
        java.util.Set<AbstractInsnNode> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        work.add(start);
        while (!work.isEmpty()) {
            AbstractInsnNode insn = work.removeFirst();
            if (!seen.add(insn)) continue;
            assertNotSame(forbidden, insn, "pending-state path cannot reach capture");
            int opcode = insn.getOpcode();
            if (opcode == Opcodes.RETURN || opcode == Opcodes.ATHROW) continue;
            if (insn instanceof JumpInsnNode jump) {
                work.add(jump.label);
                if (opcode != Opcodes.GOTO && insn.getNext() != null) work.add(insn.getNext());
            } else if (insn instanceof TableSwitchInsnNode table) {
                work.add(table.dflt); work.addAll(table.labels);
            } else if (insn instanceof LookupSwitchInsnNode lookup) {
                work.add(lookup.dflt); work.addAll(lookup.labels);
            } else { assertNotNull(insn.getNext()); work.add(insn.getNext()); }
        }
        assertFalse(seen.isEmpty());
    }
    private static void assertCatchBoundaryIsDiagnosticOnly(MethodNode method, TryCatchBlockNode block) {
        int start = index(method, block.handler);
        List<FieldInsnNode> writes = new ArrayList<>();
        MethodInsnNode output = null;
        for (AbstractInsnNode insn : method.instructions) {
            if (index(method, insn) < start) continue;
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) writes.add(field);
            if (insn instanceof MethodInsnNode call && call.name.equals("println")) { output = call; break; }
            if (insn.getOpcode() == Opcodes.RETURN) break;
        }
        assertNotNull(output);
        assertEquals(2, writes.size(), "catch only changes diagnostic flags before error output");
        assertEquals("lifecycleDiagnosticTruncated", writes.get(0).name);
        assertEquals("lifecycleDiagnosticArmed", writes.get(1).name);
        assertEquals(Opcodes.ICONST_1, previousOpcode(writes.get(0)).getOpcode());
        assertEquals(Opcodes.ICONST_0, previousOpcode(writes.get(1)).getOpcode());
        MethodInsnNode failureOutput = output;
        assertTrue(writes.stream().allMatch(write -> index(method, write) < index(method, failureOutput)));
    }
    private static void assertBudgetFailureReturnsBeforeWorldSnapshot(MethodNode method, MethodInsnNode budget) {
        AbstractInsnNode branch = nextOpcode(budget);
        assertTrue(branch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNE, "available budget branches to snapshot");
        int boundary = index(method, ((JumpInsnNode) branch).label);
        MethodInsnNode chunk = calls(method).stream().filter(c -> c.name.equals("getChunkNow")).findFirst().orElseThrow();
        assertTrue(boundary < index(method, chunk), "available-budget continuation reaches the physical snapshot");
        AbstractInsnNode falsePath = nextOpcode(branch);
        boolean truncated = false, returned = false;
        for (AbstractInsnNode insn = falsePath; insn != null && index(method, insn) < boundary; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode field && field.name.equals("lifecycleDiagnosticTruncated") && field.getOpcode() == Opcodes.PUTFIELD
                    && previousOpcode(field).getOpcode() == Opcodes.ICONST_1) truncated = true;
            if (insn.getOpcode() == Opcodes.RETURN) { returned = true; break; }
            assertFalse(insn instanceof MethodInsnNode call && (call.name.equals("getChunkNow") || call.name.equals("isPositionEntityTicking")),
                    "exhausted budget returns before level snapshot");
        }
        assertTrue(truncated && returned, "budget exhaustion marks truncation and returns before snapshots");
    }
    private static FieldInsnNode firstFieldRead(MethodNode method, String name) { return firstFieldReadAfter(method, name, null); }
    private static FieldInsnNode firstFieldReadAfter(MethodNode method, String name, AbstractInsnNode after) {
        int start = after == null ? -1 : index(method, after);
        for (AbstractInsnNode insn : method.instructions) if (index(method, insn) > start && insn instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETFIELD && field.name.equals(name)) return field;
        return null;
    }
    private static AbstractInsnNode previousOpcode(AbstractInsnNode node) {
        AbstractInsnNode previous = node.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }
    private static boolean hasLocalAndThisArguments(MethodNode method, MethodInsnNode call) {
        int target = index(method, call), i = 0; boolean thisSeen = false, localSeen = false;
        for (AbstractInsnNode insn : method.instructions) { if (i++ >= target) break; if (insn instanceof VarInsnNode var && var.getOpcode() == Opcodes.ALOAD) { if (var.var == 0) thisSeen = true; else localSeen = true; } }
        return thisSeen && localSeen;
    }
    private static boolean hasFieldRead(MethodNode method, String name) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof FieldInsnNode field && field.name.equals(name) && field.getOpcode() == Opcodes.GETFIELD) return true;
        return false;
    }
    private static boolean containsStringConcatMarker(MethodNode method, String marker) {
        for (AbstractInsnNode insn : method.instructions) if (insn instanceof InvokeDynamicInsnNode indy
                && indy.bsmArgs != null) for (Object arg : indy.bsmArgs) if (arg instanceof String s && s.contains(marker)) return true;
        return false;
    }
    private static void assertDiagnosticMutationFree(MethodNode method) {
        List<MethodInsnNode> calls = calls(method);
        assertFalse(calls.stream().anyMatch(c -> c.name.contains("hydrate") || c.name.equals("initialize")
                || c.name.equals("observePhysicalMove") || c.name.equals("writeCapture") || c.name.equals("move") || c.name.equals("setPos")
                || c.name.contains("setTrackingSubLevel")), "diagnostic path must not alter physical or ownership state");
    }
    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) if (insn instanceof MethodInsnNode call) result.add(call);
        return result;
    }
    private static int index(MethodNode method, AbstractInsnNode target) {
        int i = 0; for (AbstractInsnNode insn : method.instructions) { if (insn == target) return i; i++; } return -1;
    }
}
