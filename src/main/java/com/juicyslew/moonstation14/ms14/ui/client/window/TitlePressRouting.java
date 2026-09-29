package com.juicyslew.moonstation14.ms14.ui.client.window;

/** Hit test for a visible artwork title, before vanilla container handling consumes empty space. */
public final class TitlePressRouting {
    private TitlePressRouting() {}

    public static boolean isTitlePress(double mouseX, double mouseY, int button,
                                       int artworkX, int artworkY, int artworkWidth, int titleHeight,
                                       int viewportWidth, int viewportHeight, boolean interactiveWidgetHit) {
        return button == 0 && !interactiveWidgetHit
                && Double.isFinite(mouseX) && Double.isFinite(mouseY)
                && mouseX >= 0 && mouseX < viewportWidth && mouseY >= 0 && mouseY < viewportHeight
                && mouseX >= artworkX && mouseX < (long) artworkX + artworkWidth
                && mouseY >= artworkY && mouseY < (long) artworkY + titleHeight;
    }
}
