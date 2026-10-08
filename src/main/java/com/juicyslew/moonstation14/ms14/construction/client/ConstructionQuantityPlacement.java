package com.juicyslew.moonstation14.ms14.construction.client;

/** Pixel-space origin for text painted at the browser's half-scale content transform. */
final class ConstructionQuantityPlacement {
    record Origin(float x,float y) { }
    private ConstructionQuantityPlacement() { }
    static Origin centered(int fontWidth,int fontHeight,ConstructionBrowserLayout.Rect bounds) {
        float scale=.5f;
        return new Origin(bounds.x()+(bounds.width()-fontWidth*scale)/2f,
                bounds.y()+(bounds.height()-fontHeight*scale)/2f);
    }
}
