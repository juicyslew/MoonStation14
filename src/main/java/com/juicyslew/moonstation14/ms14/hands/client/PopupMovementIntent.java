package com.juicyslew.moonstation14.ms14.hands.client;

/** Physical-key sample for the committed character only, independent of Screen-suppressed player input. */
public final class PopupMovementIntent {
    public record State(double left, double forward, boolean jump, boolean shift, boolean sprint) {
        public static final State IDLE = new State(0, 0, false, false, false);
    }

    private State state = State.IDLE;

    public State sample(boolean focused, boolean forward, boolean backward, boolean left, boolean right,
                        boolean jump, boolean shift, boolean sprint) {
        state = focused ? new State((left ? 1 : 0) - (right ? 1 : 0),
                (forward ? 1 : 0) - (backward ? 1 : 0), jump, shift, sprint) : State.IDLE;
        return state;
    }

    public void clear() { state = State.IDLE; }

    public State state() { return state; }

    /** A binding with no modifier must not fire while an unrelated modifier is held. */
    public static boolean modifiersMatch(int required, int actual) {
        int mask = org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT | org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL
                | org.lwjgl.glfw.GLFW.GLFW_MOD_ALT | org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER;
        return (actual & mask) == required;
    }
}

