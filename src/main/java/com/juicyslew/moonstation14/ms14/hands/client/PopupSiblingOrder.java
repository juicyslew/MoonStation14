package com.juicyslew.moonstation14.ms14.hands.client;

/** Shared BaseWindow-like front order for the two sibling popup workspaces. */
public final class PopupSiblingOrder {
    private static int next;
    private PopupSiblingOrder() { }
    public static synchronized int front() { return ++next; }
}

