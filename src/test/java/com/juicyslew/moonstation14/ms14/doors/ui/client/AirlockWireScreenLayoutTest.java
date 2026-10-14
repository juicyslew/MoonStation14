package com.juicyslew.moonstation14.ms14.doors.ui.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("PointerOverlayRegistry")
class AirlockWireScreenLayoutTest {
    @AfterEach void resetPointerWindows(){com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.resetOwnedContext();}

    @Test void composedHostCaptureKeepsWireAndChemGesturesOwnedAcrossEachOtherAndOutsideRelease() {
        var wire=new AirlockWireScreen(11,java.util.UUID.randomUUID(),request->{},()->{},()->{},
                ()->new AirlockWireScreen.MovementKeys(false,false,false,false,false,false,false,false,false),
                (w,h)->{},()->true);
        wire.configureInputViewport(320,240);
        var chem=new CaptureWindow(0,0,40,30);
        com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.register(chem);

        // Real wire geometry begins a title drag. The production host adapter must keep
        // routing to it even after the pointer crosses a registered Chem window.
        com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.bringWireToFront();
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.routeWorkspaceClick(
                wire.windowX()+45,wire.windowY()+10,0,false,true,
                ()->wire.workspaceMouseClicked(wire.windowX()+45,wire.windowY()+10,0)));
        assertTrue(wire.workspaceOwnsPointerCapture());
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.capturedDrag(10,10,
                wire::workspaceOwnsPointerCapture,()->wire.workspaceMouseDragged(10,10,0,-100,-100)));
        assertTrue(wire.windowX()<55);assertEquals(0,chem.drags);
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.capturedRelease(0,
                wire::workspaceOwnsPointerCapture,()->wire.workspaceMouseReleased(400,300,0)));
        assertFalse(wire.workspaceOwnsPointerCapture());
        assertFalse(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.capturedDrag(400,300,
                wire::workspaceOwnsPointerCapture,()->wire.workspaceMouseDragged(400,300,0,0,0)));
        assertEquals(0,chem.drags); // released wire gesture cannot fall through to another family

        // Reverse direction uses the same live registry owner: Chem begins outside wire,
        // crosses it, and retains capture through release outside both windows.
        wire.configureInputViewport(320,240);
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.routeWorkspaceClick(
                10,10,0,false,false,()->false));
        assertFalse(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.wireFront());
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerWorkspaceLayers.wireLayer(false,3,1,
                com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.wireFront())
                <com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerWorkspaceLayers.browserLayer(0,true,3));
        int wireX=wire.windowX();
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.capturedDrag(
                wire.windowX()+70,wire.windowY()+45,wire::workspaceOwnsPointerCapture,
                ()->wire.workspaceMouseDragged(wire.windowX()+70,wire.windowY()+45,0,0,0)));
        assertEquals(1,chem.drags);assertEquals(0,chem.releases);assertEquals(wireX,wire.windowX());
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.capturedRelease(0,
                wire::workspaceOwnsPointerCapture,()->wire.workspaceMouseReleased(400,300,0)));
        assertEquals(1,chem.releases);assertFalse(chem.captured);
        assertFalse(wire.workspaceOwnsPointerCapture());
        assertFalse(com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.capturedDrag(
                10,10,wire::workspaceOwnsPointerCapture,()->wire.workspaceMouseDragged(10,10,0,0,0)));
        assertEquals(1,chem.drags);assertEquals(1,chem.releases);
    }

    private static final class CaptureWindow implements com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow {
        final int x,y,w,h;boolean captured;int drags,releases;
        CaptureWindow(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
        public boolean visible(){return true;}public boolean contains(double px,double py){return px>=x&&px<x+w&&py>=y&&py<y+h;}
        public void renderIntoGui(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float p,int sw,int sh){}
        public boolean mouseClicked(double px,double py,int button){captured=true;return true;}
        public boolean ownsPointerCapture(){return captured;}public boolean mouseDragged(double px,double py){drags++;return captured;}
        public boolean mouseReleased(int button){releases++;boolean was=captured;captured=false;return was;}
        public boolean mouseScrolled(double px,double py,double delta){return false;}public boolean keyPressed(int key,int scan,int mods){return false;}
        public boolean charTyped(char value,int mods){return false;}public boolean isSearchFocused(){return false;}
        public void clearTextFocus(){}public void resetOwnedContext(){captured=false;}
    }
    @Test void embeddedLifecycleInitializesEvenWithExistingDimensionsWhenFontIsUnbound() {
        assertTrue(WireEmbeddedScreenInitPolicy.needsInit(false, false, 800, 600, 800, 600));
        assertTrue(WireEmbeddedScreenInitPolicy.needsInit(true, false, 800, 600, 800, 600));
        assertTrue(WireEmbeddedScreenInitPolicy.needsInit(true, true, 800, 600, 1024, 768));
        assertFalse(WireEmbeddedScreenInitPolicy.needsInit(true, true, 800, 600, 800, 600));
    }

    @Test void productionRenderRunsEmbeddedScreenInitializationSeamBeforeSharedPainter() throws Exception {
        var calls = new java.util.ArrayList<String>();
        var lifecycleCalls = new java.util.ArrayList<String>();
        String resource = "/com/juicyslew/moonstation14/ms14/doors/ui/client/AirlockWireScreen.class";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    if (!name.equals("render")&&!name.equals("workspaceSize")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (owner.endsWith("AirlockWireScreen") && name.equals("workspaceSize")) calls.add("init");
                            if (owner.endsWith("AirlockWirePaint") && name.equals("paint")) calls.add("paint");
                            if(owner.equals("java/util/function/BiConsumer")&&name.equals("accept"))lifecycleCalls.add("embedded-init");
                            if(owner.endsWith("AirlockWireScreen")&&name.equals("configureInputViewport"))lifecycleCalls.add("input-init");
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(java.util.List.of("init","paint"),calls);
        assertEquals(java.util.List.of("embedded-init","input-init"),lifecycleCalls);
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
