package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.MoonStation14;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShuttleSimulationResidencyTest {
    private static final String TICKET_NAME = "moonstation14:operational_frame";

    @Test void ticketTypeIsRegisteredEarlyWithStablePersistableCodec() {
        ShuttleSimulationResidency.registerTicketType();
        var name = net.minecraft.resources.ResourceLocation.parse(TICKET_NAME);
        SubLevelLoadingTicketType<?> registered = SubLevelLoadingTicketType.byName(name);
        assertNotNull(registered);
        assertEquals(name, registered.name());
        @SuppressWarnings("unchecked") var codec = (com.mojang.serialization.Codec<String>) registered.codec();
        assertEquals("frame-uuid-key", codec.encodeStart(
                net.minecraft.nbt.NbtOps.INSTANCE, "frame-uuid-key").getOrThrow().getAsString());
        assertEquals("frame-uuid-key", codec.parse(
                net.minecraft.nbt.NbtOps.INSTANCE, net.minecraft.nbt.StringTag.valueOf("frame-uuid-key")).getOrThrow());

        MethodNode constructor = method(read(MoonStation14.class), "<init>");
        List<String> calls = calls(constructor);
        assertTrue(calls.indexOf("registerTicketType") >= 0);
        assertTrue(calls.indexOf("registerTicketType") < calls.indexOf("addListener"),
                "ticket codec registration precedes mod event listener setup");
        MethodNode levelLoad = method(read(MoonStation14.class), "onLevelLoad");
        List<String> levelCalls = calls(levelLoad);
        assertTrue(levelCalls.indexOf("install") < levelCalls.lastIndexOf("install"),
                "capture observation is installed before operational residency observation");
        assertTrue(hasCall(levelLoad, "FrameAnchorCaptureService", "install"));
        assertTrue(hasCall(levelLoad, "ShuttleSimulationResidency", "install"));
    }

    @Test void installAddsObserverBeforeScanningAndUsesOneTicketPerAddedOwner() throws Exception {
        MethodNode install = method(read(ShuttleSimulationResidency.class), "install");
        List<String> calls = calls(install);
        assertTrue(calls.indexOf("addObserver") >= 0);
        assertTrue(calls.indexOf("addObserver") < calls.indexOf("getAllSubLevels"));
        assertTrue(calls.contains("acquire"));

        MethodNode acquire = method(read(ShuttleSimulationResidency.class), "acquire");
        List<String> acquireCalls = calls(acquire);
        assertTrue(acquireCalls.contains("addForceLoadTicket"));
        assertTrue(acquireCalls.contains("getSubLevel"), "admission verifies exact registered owner identity");
        assertTrue(acquireCalls.contains("getUniqueId"), "ticket key derives from frame identity");
        assertTrue(acquireCalls.contains("contains"), "same exact owner is deduplicated");

        MethodNode allocate = method(read(SubLevelContainer.class), "allocateSubLevel");
        List<String> allocationCalls = calls(allocate);
        assertTrue(allocationCalls.indexOf("put") < allocationCalls.indexOf("forEach"),
                "Sable publishes UUID registration before onSubLevelAdded observer dispatch");
    }

    @Test void releaseValidatesExactOwnerBeforeMutatingAndNeverReacquiresReleasedOwner() {
        ClassNode service = read(ShuttleSimulationResidency.class);
        MethodNode release = method(service, "release");
        List<String> releaseCalls = calls(release);
        assertTrue(releaseCalls.indexOf("getSubLevel") < releaseCalls.indexOf("removeForceLoadTicket"));
        assertTrue(releaseCalls.indexOf("removeForceLoadTicket") < releaseCalls.lastIndexOf("remove"),
                "admitted ownership changes only after exact-owner check and Sable removal attempt");

        MethodNode acquire = method(service, "acquire");
        int releasedRead = -1, releasedMembership = -1, addTicket = -1, index = 0;
        for (var instruction : acquire.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("released")
                    && field.getOpcode() == Opcodes.GETFIELD) releasedRead = index;
            if (instruction instanceof MethodInsnNode call && call.name.equals("contains") && releasedRead >= 0)
                releasedMembership = index;
            if (instruction instanceof MethodInsnNode call && call.name.equals("addForceLoadTicket")) addTicket = index;
            index++;
        }
        assertTrue(releasedRead >= 0 && releasedMembership > releasedRead && releasedMembership < addTicket,
                "released exact owner is checked before the stock add-ticket call");
    }

    @Test void closeLeavesSavedTicketMetadataAndLateObserverCallbacksAreInert() {
        MethodNode close = method(read(ShuttleSimulationResidency.class), "close");
        List<String> closeCalls = calls(close);
        assertFalse(closeCalls.contains("removeForceLoadTicket"));
        assertTrue(closeCalls.contains("clear"));
        assertTrue(closeCalls.contains("remove"));

        MethodNode removed = method(read(ShuttleSimulationResidency.class.getName() + "$1"), "onSubLevelRemoved");
        assertTrue(calls(removed).contains("removeForceLoadTicket"),
                "only deliberate non-UNLOADED retirement cleans its owned ticket");
        int activeGuard = -1, firstCall = Integer.MAX_VALUE, unloadedGuard = -1, removeTicket = -1, index = 0;
        for (var instruction : removed.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("active")
                    && field.getOpcode() == Opcodes.GETFIELD) activeGuard = index;
            if (instruction instanceof FieldInsnNode field && field.name.equals("UNLOADED")
                    && field.getOpcode() == Opcodes.GETSTATIC) unloadedGuard = index;
            if (instruction instanceof MethodInsnNode call) {
                firstCall = Math.min(firstCall, index);
                if (call.name.equals("removeForceLoadTicket")) removeTicket = index;
            }
            index++;
        }
        assertTrue(activeGuard >= 0 && activeGuard < firstCall, "closed service returns before callback side effects");
        assertTrue(unloadedGuard >= 0 && unloadedGuard < removeTicket,
                "UNLOADED callback is distinguished before deliberate-retirement ticket cleanup");
    }

    private static List<String> calls(MethodNode method) {
        List<String> result = new ArrayList<>();
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) result.add(call.name);
        return result;
    }

    private static boolean hasCall(MethodNode method, String ownerSuffix, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith(ownerSuffix) && call.name.equals(name)) return true;
        return false;
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode read(Class<?> type) {
        return read(type.getName(), type.getClassLoader());
    }

    private static ClassNode read(String className) {
        return read(className, ShuttleSimulationResidency.class.getClassLoader());
    }

    private static ClassNode read(String className, ClassLoader loader) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        String resource = className.replace('.', '/') + ".class";
        try (InputStream input = loader.getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (Exception failure) {
            throw new AssertionError("failed reading " + resource, failure);
        }
    }
}
