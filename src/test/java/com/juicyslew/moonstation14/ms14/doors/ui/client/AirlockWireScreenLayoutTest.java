package com.juicyslew.moonstation14.ms14.doors.ui.client;

import org.junit.jupiter.api.Test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class AirlockWireScreenLayoutTest {
    @Test void embeddedLifecycleInitializesEvenWithExistingDimensionsWhenFontIsUnbound() {
        assertTrue(WireEmbeddedScreenInitPolicy.needsInit(false, false, 800, 600, 800, 600));
        assertTrue(WireEmbeddedScreenInitPolicy.needsInit(true, false, 800, 600, 800, 600));
        assertTrue(WireEmbeddedScreenInitPolicy.needsInit(true, true, 800, 600, 1024, 768));
        assertFalse(WireEmbeddedScreenInitPolicy.needsInit(true, true, 800, 600, 800, 600));
    }

    @Test void productionRenderInitializesEmbeddedScreenBeforeUsingDrawingMethods() throws Exception {
        var calls = new java.util.ArrayList<String>();
        String resource = "/com/juicyslew/moonstation14/ms14/doors/ui/client/AirlockWireScreen.class";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    if (!name.equals("render")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (owner.endsWith("AirlockWireScreen") && name.equals("workspaceSize")) calls.add("init");
                            if (name.equals("window") || name.equals("drawString")) calls.add("draw");
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals("init", calls.getFirst());
        assertTrue(calls.indexOf("init") < calls.indexOf("draw"));
    }

    @Test void circuitBoundsFitAndOnlyWireColumnsAreInteractive() {
        assertEquals(210, AirlockWireScreenLayout.WIDTH);
        assertEquals(160, AirlockWireScreenLayout.HEIGHT);
        for (int i = 0; i < 7; i++) {
            var r = AirlockWireScreenLayout.indicator(0, 0, i);
            assertTrue(r.x() >= 0 && r.x() + r.width() <= 210);
            assertTrue(r.y() >= 0 && r.y() + r.height() <= 160);
        }
        for (int i = 0; i < 8; i++) {
            var r = AirlockWireScreenLayout.wire(0, 0, i);
            assertTrue(r.x() >= 0 && r.x() + r.width() <= 210);
            assertEquals(i, AirlockWireScreenLayout.hitWire(0, 0, r.x(), r.y(), 8));
            assertEquals(i, AirlockWireScreenLayout.hitWire(0, 0, r.x() + r.width() - 1, r.y() + r.height() - 1, 8));
        }
        var footer = AirlockWireScreenLayout.footer(0, 0);
        assertTrue(footer.y() + footer.height() <= AirlockWireScreenLayout.HEIGHT);
        for (int i = 0; i < 7; i++) assertFalse(intersects(footer, AirlockWireScreenLayout.indicator(0, 0, i)));
        for (int i = 0; i < 8; i++) assertFalse(intersects(footer, AirlockWireScreenLayout.wire(0, 0, i)));
        assertEquals(-1, AirlockWireScreenLayout.hitWire(0, 0, 5, 50, 8));
        assertEquals(-1, AirlockWireScreenLayout.hitWire(0, 0, 20, 20, 8));
        assertEquals(-1, AirlockWireScreenLayout.hitWire(0, 0, 32, 100, 8));
        assertEquals(-1, AirlockWireScreenLayout.hitWire(0, 0, 10, 85, 0));
    }

    private static boolean intersects(AirlockWireScreenLayout.Rect a, AirlockWireScreenLayout.Rect b) {
        return a.x() < b.x() + b.width() && a.x() + a.width() > b.x()
                && a.y() < b.y() + b.height() && a.y() + a.height() > b.y();
    }

    @Test void indicatorsKeepRequestedTwoRowOrderingAndPlaceholders() {
        var pwr = AirlockWireScreenLayout.indicator(0, 0, 0);
        var aia = AirlockWireScreenLayout.indicator(0, 0, 3);
        var log = AirlockWireScreenLayout.indicator(0, 0, 4);
        var safe = AirlockWireScreenLayout.indicator(0, 0, 6);
        assertEquals(pwr.y(), aia.y());
        assertTrue(log.y() > aia.y());
        assertEquals(log.y(), safe.y());
        assertTrue(AirlockWireScreenLayout.indicator(0, 0, 3).contains(160, 40));
        assertTrue(AirlockWireScreenLayout.indicator(0, 0, 4).contains(10, 56));
    }
}
