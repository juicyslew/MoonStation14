package com.juicyslew.moonstation14.ms14.ghost_roles.client;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GhostRoleMenuInitializationTest {
    private String source() throws IOException {
        Path path=Path.of(System.getProperty("moonstation14.projectDir","."),"src/main/java/com/juicyslew/moonstation14/ms14/ghost_roles/client/GhostRoleMenuWindow.java");
        return Files.readString(path);
    }
    @Test void singletonClassloadDoesNotCreateFontDependentWidgetsOrSearchUi() throws IOException {
        String code=source();
        assertFalse(code.contains("MachineSearchList"));assertFalse(code.contains("FilteredListModel"));
        assertFalse(code.contains("searchBox"));assertFalse(code.contains("searchFocused"));
        assertFalse(code.matches("(?s).*private\\s+final\\s+.*Widget\\s+\\w+\\s*=.*"));
        assertTrue(code.contains("private GhostRoleMenuWindow() { }"));
        assertTrue(code.contains("private static final GhostRoleMenuWindow INSTANCE=new GhostRoleMenuWindow()"));
    }
    @Test void windowOpensAtTopLeftAndUsesSharedScaledCloseChromeForDrawingAndHitTesting() throws IOException {
        String code=source();
        assertTrue(code.contains("GhostRoleMenuPresentation.width(sw)"));
        assertTrue(code.contains("new WindowGeometryController(sw,sh,5,5,w,h"));
        assertTrue(code.contains("GhostRoleMenuPresentation.closeBounds(x,y,w)"));
        assertTrue(code.contains("mx>=c.left()&&mx<c.right()&&my>=c.top()&&my<c.bottom()"));
        assertTrue(code.contains("g.pose().scale(.5f,.5f,1)"));
        assertTrue(code.contains("geometry.beginPointer(mx,my,button,13,"));
        assertTrue(code.contains("captured&&geometry!=null&&geometry.dragPointer(x,y)"));
        assertTrue(code.contains("geometry.releasePointer(button)"));
        assertTrue(code.contains("if(key==GLFW.GLFW_KEY_ESCAPE){close();return true;}"));
        assertTrue(code.contains("GhostRoleMenuPresentation.groups(state.entries())"));
        assertTrue(code.contains("GhostRoleMenuPresentation.desiredHeight(state.entries(),expanded,selected()!=null,!statusText().isEmpty(),width)"));
        assertTrue(code.contains("GhostRoleMenuPresentation.visibleRows(state.entries(),expanded)"));
        assertTrue(code.contains("GhostRoleMenuPresentation.rowAtPoint(state.entries(),expanded,scroll,x,y+15,w,contentHeight,mx,my)"));
        assertTrue(code.contains("GhostRoleMenuPresentation.rowBounds(x,top,w,contentHeight,shown++)"));
    }
}
