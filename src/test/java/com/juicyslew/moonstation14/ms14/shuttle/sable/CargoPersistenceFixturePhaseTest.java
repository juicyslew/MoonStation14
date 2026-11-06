package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** Pins fixture phase separation and the real flight-only diagnostic registration callsite. */
class CargoPersistenceFixturePhaseTest {
    private static final String OWNER = "com/juicyslew/moonstation14/ms14/shuttle/sable/SableCargoChunkPersistenceGameTests";
    private static final String RESTORED = "com/juicyslew/moonstation14/ms14/shuttle/sable/FrameAnchorState";

    @Test void normalLifecycleBranchesRequireCanonicalSixSubstepsWithoutChangingOverrides() {
        var normalWitness = new SableCargoChunkPersistenceGameTests.OfflineBodyWitness() {
            public void awaitOffline(Runnable ready) { }
            public net.minecraft.world.entity.Entity oldBody() { return null; }
            public String diagnostic() { return "normal"; }
            public void reconnectAndVerify(SableFixturePlot reloaded, Runnable passed) { }
        };
        assertEquals(6, SableCargoChunkPersistenceGameTests.NORMAL_SABLE_SUBSTEPS);
        assertEquals(6, normalWitness.expectedSubsteps());
        assertEquals(6, SableCargoChunkPersistenceGameTests.expectedSubsteps(null));
        assertEquals(6, SableCargoChunkPersistenceGameTests.expectedSubsteps(normalWitness));
        var mismatch = assertThrows(net.minecraft.gametest.framework.GameTestAssertException.class,
                () -> SableCargoChunkPersistenceGameTests.requireExpectedSubsteps(2,
                        SableCargoChunkPersistenceGameTests.expectedSubsteps(null)));
        assertTrue(mismatch.getMessage().contains("expected=6 actual=2"));
    }

    @Test void preUnloadReadinessDoesNotDemandReconstructionButReloadDoes() throws Exception {
        ClassNode node = fixtureClass();
        MethodNode ready = method(node, "awaitReady");
        assertFalse(calls(ready, RESTORED, "restoredFromFrame"), "fresh pre-unload cargo cannot prove deserialization");
        assertTrue(calls(method(node, "waitReload"), RESTORED, "restoredFromFrame"));
        assertTrue(calls(method(node, "verifyReload"), RESTORED, "restoredFromFrame"));
    }

    @Test void reloadObservationKeepsMissingUuidSlotsInAnImmutableDefensiveSnapshot() throws Exception {
        var source = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        source.add(null);
        var snapshot = SableCargoChunkPersistenceGameTests.reloadObservationSnapshot(source);
        source.clear();
        assertEquals(java.util.Collections.singletonList(null), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));

        MethodNode reload = method(fixtureClass(), "waitReload");
        boolean snapshotCall = false;
        for (var insn = reload.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(OWNER)
                    && call.name.equals("reloadObservationSnapshot")) snapshotCall = true;
            if (insn instanceof MethodInsnNode call && call.name.equals("onReloadObservation"))
                assertTrue(snapshotCall, "the actual observer must receive the null-tolerant snapshot");
        }
    }

    @Test void naturalObserverIsRegisteredOnlyForTheFlightFixtureAtItsActualCallsite() throws Exception {
        assertFalse(SableCargoChunkPersistenceGameTests.shouldObservePersistence(false, false));
        assertFalse(SableCargoChunkPersistenceGameTests.shouldObservePersistence(false, true));
        assertFalse(SableCargoChunkPersistenceGameTests.shouldObservePersistence(true, false));
        assertTrue(SableCargoChunkPersistenceGameTests.shouldObservePersistence(true, true));
        MethodNode create = method(fixtureClass(), "createCargo");
        boolean predicateCall = false, guardedRegistration = false;
        for (var insn = create.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(OWNER)) continue;
            if (!call.name.equals("shouldObservePersistence")) continue;
            predicateCall = true;
            for (var next = insn.getNext(); next != null; next = next.getNext()) {
                if (next instanceof MethodInsnNode watch && watch.name.equals("watchPersistence")) {
                    for (var between = insn.getNext(); between != next; between = between.getNext())
                        if (between instanceof JumpInsnNode jump && (jump.getOpcode() == Opcodes.IFEQ
                                || jump.getOpcode() == Opcodes.IFNE)
                                && create.instructions.indexOf(jump.label) > create.instructions.indexOf(next))
                            guardedRegistration = true;
                    break;
                }
            }
        }
        assertTrue(predicateCall, "actual cargo fixture registration must use the tested predicate");
        assertTrue(guardedRegistration, "observer call must be skipped when the tested predicate is false");
    }

    @Test void naturalSaveReadWitnessRequiresCompoundTagRatherThanMissingOrWrongType() {
        String key = FrameAnchor.KEY;
        int compound = net.minecraft.nbt.CompoundTag.TAG_COMPOUND;
        assertTrue(SableCargoChunkPersistenceGameTests.hasNaturalAnchorField("Pos[9]=...;" + key + "[" + compound + "]={Frame:...};"));
        assertFalse(SableCargoChunkPersistenceGameTests.hasNaturalAnchorField(key + "=MISSING;"));
        assertFalse(SableCargoChunkPersistenceGameTests.hasNaturalAnchorField(key + "[8]=wrong;"));
    }

    @Test void worldInitializationReturnsBeforeFrameServiceSetup() throws Exception {
        String stateName = "com/juicyslew/moonstation14/ms14/shuttle/sable/FrameAnchorState";
        ClassNode node = new ClassNode(Opcodes.ASM9);
        try (var stream = getClass().getClassLoader().getResourceAsStream(stateName + ".class")) {
            assertNotNull(stream); new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        MethodNode initialize = node.methods.stream().filter(m -> m.name.equals("initialize")
                && m.desc.contains("ServerLevel;Lnet/minecraft/world/entity/Entity;Z)Z")).findFirst().orElseThrow();
        int install = -1, worldCheck = -1;
        int index = 0;
        for (var insn = initialize.instructions.getFirst(); insn != null; insn = insn.getNext(), index++) {
            if (insn instanceof MethodInsnNode call && call.owner.endsWith("FrameAnchorCaptureService") && call.name.equals("install")) install = index;
            if (insn instanceof org.objectweb.asm.tree.FieldInsnNode field && field.name.equals("WORLD")) worldCheck = index;
        }
        assertTrue(install >= 0 && worldCheck >= 0 && worldCheck < install,
                "WORLD state short-circuits before frame service installation");
        org.objectweb.asm.tree.JumpInsnNode worldBranch = null;
        for (var insn = initialize.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (initialize.instructions.indexOf(insn) <= worldCheck) continue;
            if (insn instanceof org.objectweb.asm.tree.JumpInsnNode jump) { worldBranch = jump; break; }
        }
        assertNotNull(worldBranch);
        assertEquals(Opcodes.IF_ACMPNE, worldBranch.getOpcode(), "non-WORLD status proceeds to service setup");
        var worldReturn = worldBranch.getNext();
        while (worldReturn != null && worldReturn.getOpcode() < 0) worldReturn = worldReturn.getNext();
        assertNotNull(worldReturn);
        assertEquals(Opcodes.ICONST_1, worldReturn.getOpcode());
        assertEquals(Opcodes.IRETURN, worldReturn.getNext().getOpcode());
        assertTrue(initialize.instructions.indexOf(worldBranch.label) < install,
                "pending FRAME state continues to service-backed hydration");
    }

    @Test void serverLevelLoadInstallsUnloadObserverOutsideEntityInitialization() throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        String owner = "com/juicyslew/moonstation14/MoonStation14";
        try (var stream = getClass().getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(stream); new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        MethodNode load = node.methods.stream().filter(m -> m.name.equals("onLevelLoad")).findFirst().orElseThrow();
        assertTrue(calls(load, "com/juicyslew/moonstation14/ms14/shuttle/sable/FrameAnchorCaptureService", "install"),
                "server level lifecycle must install observer before entity initialization/unload");
        MethodNode unload = node.methods.stream().filter(m -> m.name.equals("onLevelUnload")).findFirst().orElseThrow();
        assertTrue(calls(unload, "com/juicyslew/moonstation14/ms14/shuttle/sable/FrameAnchorCaptureService", "close"));
    }

    private static ClassNode fixtureClass() throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        try (var stream = CargoPersistenceFixturePhaseTest.class.getClassLoader().getResourceAsStream(OWNER + ".class")) {
            assertNotNull(stream); new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }
    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static boolean calls(MethodNode method, String owner, String name) {
        for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }
}
