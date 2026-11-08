package com.juicyslew.moonstation14.ms14.ui.client.navigation;

import com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Bounded viewport plus independent vanilla search field. Register both this and searchBox() with the screen. */
public final class MachineSearchList extends AbstractButton {
    public static final int MAX_VISIBLE_ROWS = 24;
    public static final int ROW_HEIGHT = 14;
    public record RowPlacement(int iconY, float labelY) { }
    public static RowPlacement rowPlacement(int fontLineHeight, boolean hasIcon) {
        return new RowPlacement(1,(ROW_HEIGHT-fontLineHeight)/2f+1);
    }
    private final FilteredListModel model;
    private final EditBox search;
    private final net.minecraft.client.gui.Font font;
    private final Consumer<String> onActivate;
    private final LabelCache labels;
    private final java.util.function.Consumer<String> queryChanged;
    private final Map<String, Tooltip> tooltips = new HashMap<>();
    private String tooltipId;
    private final Map<String, Object> rowIcons = new HashMap<>();
    private int contentIndent;

    /** pageSize must equal the model's page size. Height applies to rows only; place searchBox() above. */
    public MachineSearchList(int x, int y, int width, int pageSize, Component title,
                             FilteredListModel model, Consumer<String> onActivate) {
        this(x,y,width,pageSize,title,model,onActivate,value -> { });
    }
    public MachineSearchList(int x, int y, int width, int pageSize, Component title,
                             FilteredListModel model, Consumer<String> onActivate, Consumer<String> queryChanged) {
        this(x,y,width,pageSize,title,model,onActivate,queryChanged,Minecraft.getInstance().font);
    }

    /** Testable font dependency; normal callers continue to use the active Minecraft font. */
    public MachineSearchList(int x,int y,int width,int pageSize,Component title,
                             FilteredListModel model,Consumer<String> onActivate,Consumer<String> queryChanged,
                             net.minecraft.client.gui.Font font) {
        super(x, y, width, viewportHeight(pageSize), title);
        if (width < 12) throw new IllegalArgumentException("invalid list size");
        this.model = java.util.Objects.requireNonNull(model);
        this.font=java.util.Objects.requireNonNull(font);
        if (model.pageSize() != pageSize) throw new IllegalArgumentException("model page size differs from list page size");
        labels = new LabelCache(model);
        this.onActivate = onActivate;
        this.queryChanged = java.util.Objects.requireNonNull(queryChanged);
        search = new EditBox(font, x, y - 22, width, 18, Component.literal("Search"));
        search.setMaxLength(128);
        search.setResponder(value -> {
            applyQuery(model,value,this.queryChanged);
            updateNarrationLabel();
        });
    }

    /** Validated row-only viewport extent, independent of the Minecraft client. */
    public static int viewportHeight(int pageSize) {
        if (pageSize < 1 || pageSize > MAX_VISIBLE_ROWS) throw new IllegalArgumentException("invalid page size");
        return pageSize * ROW_HEIGHT;
    }

    private int renderedCount() { return Math.min(model.visibleCount(), getHeight() / ROW_HEIGHT); }

    public EditBox searchBox() { return search; }
    public void focusSearch() { search.setFocused(true); }
    /** Pointer-overlay bridge: EditBox.onClick positions its cursor but does not focus it. */
    public boolean pointerSearchClick(double mouseX, double mouseY, int button) {
        if (!search.mouseClicked(mouseX, mouseY, button)) return false;
        focusSearch();
        return true;
    }
    public String selectedId() { return model.selectedId(); }
    public int visibleCount() { return model.visibleCount(); }
    public Object rowIconFor(String id) { return rowIcons.get(id); }
    public int contentIndent() { return contentIndent; }
    public void renderIconForRow(GuiGraphics graphics,String id,int x,int y) {
        Object icon=rowIcons.get(id);
        if(icon!=null&&iconRenderer!=null)iconRenderer.render(graphics,icon,x,y);
    }
    /** Resize the row viewport and keep model navigation bounds in sync. */
    public void resizeViewport(int width, int height) {
        if (width < 12 || height < ROW_HEIGHT) throw new IllegalArgumentException("invalid list viewport");
        setWidth(width); setHeight(height);
        model.setPageSize(Math.max(1, Math.min(MAX_VISIBLE_ROWS, height / ROW_HEIGHT)));
    }
    /** Shared production responder path, testable without creating an EditBox or client font. */
    public static void applyQuery(FilteredListModel model,String value,Consumer<String> queryChanged) {
        model.filter(value);
        queryChanged.accept(value);
    }

    /** Only authoritative snapshot changes rebuild cached labels; filtering is handled by the model. */
    public void snapshot(long revision, List<FilteredListModel.Entry> entries) {
        if (!labels.snapshot(revision, entries)) return;
        tooltips.clear();
        for (FilteredListModel.Entry entry : entries) {
            tooltips.put(entry.id(), Tooltip.create(labels.get(entry.id())));
        }
        tooltipId = null;
        setTooltip(null);
        updateNarrationLabel();
    }

    /** Pure snapshot-owned label cache: filtering and scrolling never rebuild display components. */
    static final class LabelCache {
        private final FilteredListModel model;
        private final Map<String, Component> labels = new HashMap<>();

        LabelCache(FilteredListModel model) { this.model = model; }

        boolean snapshot(long revision, List<FilteredListModel.Entry> entries) {
            long old = model.revision();
            model.snapshot(revision, entries);
            if (old == model.revision()) return false;
            labels.clear();
            for (FilteredListModel.Entry entry : entries)
                labels.put(entry.id(), Component.literal(entry.label()));
            return true;
        }

        Component get(String id) { return labels.get(id); }
    }

    private void updateNarrationLabel() {
        Component selected = labels.get(model.selectedId());
        setMessage(selected == null ? search.getMessage() : selected);
    }

    @Override public void onPress() {
        if (model.selectedId() != null) onActivate.accept(model.selectedId());
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = NavigationHit.row((int) mouseX, (int) mouseY, getX(), getY(), getWidth(), ROW_HEIGHT,
                    renderedCount());
            if (row >= 0) {
                model.select(model.visibleAt(row).id());
                updateNarrationLabel();
            }
            else return false; // blank rows cannot activate an earlier selection
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
        model.scroll(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isFocused() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            model.move(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
            updateNarrationLabel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderWidget(graphics, mouseX, mouseY, partialTick, getX(), getY(), getX()+getWidth(), getY()+getHeight());
    }

    /** Snapshot presentation extension; existing callers retain icon-free rows. */
    public void snapshot(long revision, List<FilteredListModel.Entry> entries,
                         Function<String, ResourceLocation> iconForId, int contentIndent) {
        snapshotWithIcons(revision,entries,iconForId,(graphics,icon,x,y)->{
            graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(.75f,.75f,1f);
            com.juicyslew.moonstation14.ms14.hands.client.ItemUiRenderer.render(graphics,icon.toString(),1,null,0,0);
            graphics.pose().popPose();
        },contentIndent);
    }

    @FunctionalInterface public interface RowIconRenderer {
        void render(GuiGraphics graphics,Object icon,int x,int y);
    }
    /** Typed feature callers can supply non-item thumbnails while legacy callers keep ResourceLocation items. */
    public void snapshotWithIcons(long revision,List<FilteredListModel.Entry> entries,
                                  Function<String,?> iconForId,RowIconRenderer renderer,int contentIndent) {
        this.contentIndent=Math.max(0,contentIndent);
        rowIcons.clear();
        if(iconForId!=null) for(var entry:entries) {
            Object icon=iconForId.apply(entry.id());
            if(icon!=null) rowIcons.put(entry.id(),icon);
        }
        iconRenderer=renderer;
        snapshot(revision,entries);
    }
    private RowIconRenderer iconRenderer;

    /** Paint with an explicitly screen-space clip; ordinary callers retain transformed widget clipping. */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int clipLeft, int clipTop, int clipRight, int clipBottom) {
        renderWidget(graphics, mouseX, mouseY, partialTick, clipLeft, clipTop, clipRight, clipBottom);
    }

    private void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                              int clipLeft, int clipTop, int clipRight, int clipBottom) {
        int x = getX(), y = getY(), width = getWidth();
        if (width <= 0) return;
        var font = this.font;
        graphics.fill(x, y, x + width, y + getHeight(), 0xff11191d);
        int count = renderedCount();
        int hovered = NavigationHit.row(mouseX, mouseY, x, y, width, ROW_HEIGHT, count);
        String hoveredId = null;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        try {
            for (int row = 0; row < count; row++) {
                var entry = model.visibleAt(row);
                int top = y + row * ROW_HEIGHT;
                boolean selected = entry.id().equals(model.selectedId());
                graphics.fill(x + 1, top + 1, x + width - 1, top + ROW_HEIGHT - 1,
                        selected ? 0xff496b75 : row == hovered ? 0xff35474e : 0xff202e35);
                Object icon=rowIcons.get(entry.id());
                int indent=icon==null?0:contentIndent;
                var placement=rowPlacement(font.lineHeight,icon!=null);
                if(icon!=null&&iconRenderer!=null)renderIconForRow(graphics,entry.id(),x+2,top+placement.iconY());
                Component label = labels.get(entry.id());
                if (label != null) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(x+5+indent,top+placement.labelY(),0);
                    float textScale=icon==null?1f:.8f;
                    graphics.pose().scale(textScale,textScale,1f);
                    graphics.drawString(font,label,0,0,selected ? 0xffe5ecee : 0xffb0bec2,true);
                    graphics.pose().popPose();
                    if (row == hovered && font.width(label)*textScale > width - 10-indent) hoveredId = entry.id();
                }
            }
        } finally { graphics.disableScissor(); }
        if (!java.util.Objects.equals(hoveredId, tooltipId)) {
            tooltipId = hoveredId;
            setTooltip(hoveredId == null ? null : tooltips.get(hoveredId));
        }
    }
}

