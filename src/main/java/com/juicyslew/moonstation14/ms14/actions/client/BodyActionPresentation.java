package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;
import net.minecraft.resources.ResourceLocation;

/** Shared source-style presentation rule: alternate icon carries toggle state when supplied. */
public final class BodyActionPresentation {
    private BodyActionPresentation() { }

    public static ResourceLocation icon(BodyActionSnapshot.Entry entry) {
        return entry.active() ? entry.iconOn().orElse(entry.icon()) : entry.icon();
    }

    public static boolean activePressedStyle(BodyActionSnapshot.Entry entry) {
        return entry.active() && entry.iconOn().isEmpty();
    }
}
