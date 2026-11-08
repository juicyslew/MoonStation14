package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("PointerOverlayRegistry")
class ChemMasterOutputFocusPolicyTest {
    private static final class FocusedWindow implements PointerOverlayWindow {
        boolean focused=true;
        public boolean visible(){return true;} public boolean contains(double x,double y){return false;}
        public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int width,int height){}
        public boolean mouseClicked(double x,double y,int button){return false;} public boolean mouseDragged(double x,double y){return false;}
        public boolean mouseReleased(int button){return false;} public boolean mouseScrolled(double x,double y,double d){return false;}
        public boolean keyPressed(int key,int scan,int mods){return focused;} public boolean charTyped(char c,int mods){return focused;}
        public boolean isSearchFocused(){return focused;} public void clearTextFocus(){focused=false;} public void resetOwnedContext(){focused=false;}
    }
    @Test void foreignPointerClickClearsRegisteredTextFocus(){
        var field=new FocusedWindow();PointerOverlayRegistry.register(field);
        try {
            assertTrue(PointerOverlayRegistry.textFocused());
            assertFalse(PointerOverlayRegistry.routeWorkspaceClick(50,50,0,false,false,()->false));
            assertFalse(PointerOverlayRegistry.textFocused());
            assertFalse(PointerOverlayRegistry.key(87,0,0));
        } finally {PointerOverlayRegistry.unregister(field);}
    }
}
