package com.juicyslew.moonstation14.ms14.construction.client;

/** Deterministic G tap/hold gesture; caller decides whether the active plan has a palette family. */
public final class ConstructionKeyGesture {
    public static final int HOLD_TICKS=10;
    public record Result(boolean openBrowser,boolean showPalette,boolean cancelScroll) { }
    private boolean down, palette;
    private boolean consumed;
    private int consumedKey, consumedScan;
    public boolean closeBrowserOnPress(boolean visible, boolean typingFocused, boolean bindingMatched,
            int key, int scan, Runnable close) {
        if (!visible || typingFocused || !bindingMatched || consumed) return false;
        consumed=true; consumedKey=key; consumedScan=scan; reset(); close.run(); return true;
    }
    public boolean consumeRelease(int key, int scan) {
        if (!consumed || key != consumedKey || scan != consumedScan) return false;
        consumed=false; reset(); return true;
    }
    public boolean pressConsumed() { return consumed; }
    private int ticks;
    public Result update(boolean isDown,boolean hasFamily){
        if(consumed)return new Result(false,false,false);
        boolean opened=false;
        if(isDown&&!down){ticks=0;palette=false;}
        if(isDown){ticks=Math.min(HOLD_TICKS,ticks+1);if(ticks>=HOLD_TICKS&&hasFamily)palette=true;}
        if(!isDown&&down){opened=!palette;palette=false;ticks=0;}
        down=isDown;
        return new Result(opened,palette,palette);
    }
    public void reset(){down=false;palette=false;ticks=0;}
    public void resetContext(){ reset(); consumed=false; consumedKey=0; consumedScan=0; }
}

