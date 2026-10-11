package com.juicyslew.moonstation14.ms14.ui.client.paint;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Minimal ordered operations shared by live machine UI paint and offline backends. */
public interface OrderedPaint {
    void fill(int left, int top, int right, int bottom, int argb);
    void text(Component text, int x, int y, int argb, boolean shadow);
    int width(Component text);
    int width(String text);
    int lineHeight();
    String plainSubstrByWidth(String text, int width);
    void push();
    void pop();
    void translate(float x, float y, float z);
    void scale(float x, float y, float z);
    void clip(int left, int top, int right, int bottom);
    void unclip();
    void sprite(ResourceLocation texture, int x, int y, int width, int height,
                int u, int v, int sourceWidth, int sourceHeight, int textureWidth, int textureHeight,
                int tint);
}
