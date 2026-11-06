package com.juicyslew.moonstation14.ms14.shuttle.sable;

import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks only active EntityRenderer packed-light calls, preserving nested renderer ownership. */
public final class PackedLightAccountingScope {
    private static final ThreadLocal<Deque<Object>> RENDERERS = new ThreadLocal<>();

    private PackedLightAccountingScope() { }

    public static boolean activeForRenderer(Object renderer) {
        Deque<Object> stack = RENDERERS.get();
        return stack != null && stack.peek() == renderer;
    }

    public static void begin(Object renderer) {
        Deque<Object> stack = RENDERERS.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            RENDERERS.set(stack);
        }
        stack.push(renderer);
    }

    public static void end() {
        Deque<Object> stack = RENDERERS.get();
        if (stack == null || stack.isEmpty()) throw new IllegalStateException("Packed-light scope underflow");
        stack.pop();
        if (stack.isEmpty()) RENDERERS.remove();
    }
}
