package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

import java.util.List;

/** Pure geometry shared by the alarm screen and its viewport tests. Rectangles use exclusive right/bottom edges. */
public record AirAlarmLayout(int viewportWidth, int viewportHeight, int x, int y, int width, int height,
                             boolean tooSmall) {
    public static final int WIDTH = 304, HEIGHT = 224;
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean inside(int x, int y, int w, int h) { return this.x >= x && this.y >= y && right() <= x+w && bottom() <= y+h; }
        public boolean overlaps(Rect r) { return x < r.right() && right() > r.x && y < r.bottom() && bottom() > r.y; }
    }
    public static AirAlarmLayout forViewport(int w, int h) {
        boolean small = w - 16 < WIDTH || h - 16 < HEIGHT;
        int ww = small ? Math.max(1, Math.min(WIDTH, w - 8)) : WIDTH;
        int wh = small ? Math.max(1, Math.min(HEIGHT, h - 8)) : HEIGHT;
        return new AirAlarmLayout(w, h, (w-ww)/2, (h-wh)/2, ww, wh, small);
    }
    public Rect rect(int ox, int oy, int w, int h) { return new Rect(x+ox,y+oy,w,h); }
    public Rect detailBack() { return rect(8,28,80,20); }
    public Rect detailTabs() { return rect(8,58,288,20); }
    public Rect close() { return rect(width - 23,5,18,18); }
    public Rect refresh() { return rect(8,180,88,20); }
    public Rect configure() { return rect(200,180,96,20); }
    public Rect search() { return rect(8,60,288,18); }
    public Rect deviceRows() { return rect(8,82,288,72); }
    public Rect enabled() { return rect(8,82,288,20); }
    public Rect mode() { return rect(8,106,288,20); }
    public Rect numericEdit() { return rect(8,150,202,20); }
    // MachineDraftField places its submit button 4px after the edit field.
    public Rect numericSubmit() { return rect(214,150,76,20); }
    public Rect gas(int index) { return rect(8+(index%2)*146,82+(index/2)*22,140,19); }
    public Rect inset() { return rect(7,52,width-14,height-80); }
    public Rect footer() { return rect(8,height-18,width-16,14); }
    public Rect fallback() { return rect(8,height-34,width-16,14); }
    public List<Rect> filters() { return java.util.stream.IntStream.range(0,9).mapToObj(this::gas).toList(); }
}

