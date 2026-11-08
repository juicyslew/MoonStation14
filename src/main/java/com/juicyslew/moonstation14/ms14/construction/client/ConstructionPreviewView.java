package com.juicyslew.moonstation14.ms14.construction.client;

/** Canonical controlled-body view gate. Camera entity identity is deliberately not authoritative. */
public final class ConstructionPreviewView {
    private ConstructionPreviewView() { }
    public static boolean allowed(boolean levelPresent,boolean hidden,boolean bodyPresent,boolean bodyInLevel,long committedEpoch) {
        return levelPresent&&!hidden&&bodyPresent&&bodyInLevel&&committedEpoch>0;
    }
}

