package com.juicyslew.moonstation14.ms14.ui.client.navigation;

/** Pure hit testing shared by the navigation widgets. Coordinates use half-open bounds. */
public final class NavigationHit {
    private NavigationHit() {}

    public static int tab(int mouseX, int mouseY, int x, int y, int width, int height, int count) {
        if (count <= 0 || width <= 0 || height <= 0 || mouseX < x || mouseX >= x + width
                || mouseY < y || mouseY >= y + height) return -1;
        return (int) Math.min(count - 1L, ((long) (mouseX - x) * count) / width);
    }

    public static int row(int mouseX, int mouseY, int x, int y, int width, int rowHeight, int visibleCount) {
        if (rowHeight <= 0 || visibleCount <= 0 || mouseX < x || mouseX >= x + width
                || mouseY < y || (long) mouseY >= (long) y + (long) rowHeight * visibleCount) return -1;
        return (mouseY - y) / rowHeight;
    }

    public static boolean canSubmit(boolean valid, boolean pending, long revision,
                                    String draft, String authority) {
        return valid && !pending && revision >= 0 && !draft.equals(authority);
    }
}
