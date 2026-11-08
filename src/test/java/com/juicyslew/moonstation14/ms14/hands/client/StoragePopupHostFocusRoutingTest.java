package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("PointerOverlayRegistry")
class StoragePopupHostFocusRoutingTest {
    @AfterEach void cleanup(){PointerOverlayRegistry.resetOwnedContext();}

    @Test void actualStorageHostRouteLetsClickedEditorReclaimFocusAndKeyboardAfterDispatch(){
        var host=new StoragePopupScreen(new StoragePopupWorkspace(320,240));
        var editor=new EditableOverlay(0,0);editor.visible=true;editor.focus=true;PointerOverlayRegistry.register(editor);

        assertTrue(host.routeRegisteredWorkspaceClick(5,5,0,false,false,()->false));
        assertTrue(editor.focus,"the field acquires focus after the registry's pre-dispatch clear");
        assertTrue(PointerOverlayRegistry.key(65,0,0));assertTrue(PointerOverlayRegistry.character('a',0));
        assertEquals(1,editor.keys);assertEquals(1,editor.characters);

        editor.focus=true;
        assertFalse(host.routeRegisteredWorkspaceClick(5,5,0,true,false,()->false));
        assertFalse(editor.focus,"a storage-owned/foreign hit keeps the previous overlay focus cleared");

        editor.focus=true;int[] wireCalls={0};PointerOverlayRegistry.bringWireToFront();
        assertTrue(host.routeRegisteredWorkspaceClick(5,5,0,false,true,()->{assertFalse(editor.focus);wireCalls[0]++;return true;}));
        assertEquals(1,wireCalls[0]);assertFalse(editor.focus,"wire routing clears the old editor before wire dispatch");
    }

    private static final class EditableOverlay implements PointerOverlayWindow {
        final int x,y;boolean visible,focus;int keys,characters;
        EditableOverlay(int x,int y){this.x=x;this.y=y;}
        public boolean visible(){return visible;}
        public boolean contains(double px,double py){return visible&&px>=x&&px<x+20&&py>=y&&py<y+20;}
        public boolean pointerAvailable(){return true;}
        public boolean ownsPointerCapture(){return false;}
        public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int width,int height){}
        public boolean mouseClicked(double px,double py,int button){focus=true;return true;}
        public boolean mouseDragged(double x,double y){return false;}
        public boolean mouseReleased(int button){return false;}
        public boolean mouseScrolled(double x,double y,double delta){return false;}
        public boolean keyPressed(int key,int scan,int mods){if(!focus)return false;keys++;return true;}
        public boolean charTyped(char c,int mods){if(!focus)return false;characters++;return true;}
        public boolean isSearchFocused(){return focus;}
        public void clearTextFocus(){focus=false;}
        public void resetOwnedContext(){visible=false;focus=false;}
    }
}
