package me.shiny.matesignal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class MateSignalConfigScreen extends Screen {
    enum FilterMode { ALL, ACTIVE, INACTIVE }
    enum View { MAIN, MOBS }

    private final Screen parent;

    private EditBox radiusBox;
    private Button minusBtn;
    private Button plusBtn;
    private Button saveBtn;
    private Button cancelBtn;

    private CycleButton<Boolean> dayBtn;
    private CycleButton<Boolean> nightBtn;
    private CycleButton<Boolean> lowHealthBtn;
    private CycleButton<Boolean> lowHungerBtn;

    private CycleButton<Boolean> deathBtn;
    private CycleButton<Boolean> rainBtn;
    private CycleButton<Boolean> drownBtn;
    private CycleButton<Boolean> sleepBtn;
    private CycleButton<Boolean> craftBtn;

    private Button configureMobsBtn;

    private Button showAllBtn;
    private Button showActiveBtn;
    private Button showUnactiveBtn;
    private Button backBtn;

    private final List<Entry> entries = new ArrayList<>();
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int rowH = 18;
    private double scroll;
    private boolean draggingScrollbar;

    private final int outerPad = 20;
    private final int scrollbarW = 10;
    private final int scrollbarGap = 6;

    private int contentLeft;
    private int contentRight;
    private int contentWidth;

    private FilterMode filterMode = FilterMode.ALL;
    private View view = View.MAIN;

    private boolean leftWasDown;

    public MateSignalConfigScreen(Screen parent) {
        super(Component.literal("MateSignal Config"));
        this.parent = parent;
    }

    protected void init() {
        for (Entry e : entries) removeWidget(e.toggle);
        entries.clear();

        radiusBox = new EditBox(this.font, 0, 0, 40, 20, Component.literal("radius"));
        radiusBox.setValue(Integer.toString(Config.RADIUS.get()));
        radiusBox.setEditable(false);

        minusBtn = Button.builder(Component.literal("-"), b -> step(-1)).bounds(0, 0, 20, 20).build();
        plusBtn  = Button.builder(Component.literal("+"), b -> step(+1)).bounds(0, 0, 20, 20).build();

        addRenderableWidget(minusBtn);
        addRenderableWidget(radiusBox);
        addRenderableWidget(plusBtn);

        dayBtn = CycleButton.onOffBuilder(Config.DAY_MESSAGE.get()).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b,v)->{});
        nightBtn = CycleButton.onOffBuilder(Config.NIGHT_MESSAGE.get()).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b,v)->{});
        lowHealthBtn = CycleButton.onOffBuilder(Config.LOW_HEALTH_MESSAGE.get()).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b,v)->{});
        lowHungerBtn = CycleButton.onOffBuilder(Config.LOW_HUNGER_MESSAGE.get()).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b,v)->{});
        addRenderableWidget(dayBtn);
        addRenderableWidget(nightBtn);
        addRenderableWidget(lowHealthBtn);
        addRenderableWidget(lowHungerBtn);

        deathBtn = CycleButton.onOffBuilder(Config.DEATH_MESSAGE.get()).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b,v)->{});
        rainBtn = CycleButton.onOffBuilder(Config.RAIN_START_MESSAGE.get()).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b,v)->{});
        drownBtn = CycleButton.onOffBuilder(Config.DROWNING_HALF_MESSAGE.get()).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b,v)->{});
        sleepBtn = CycleButton.onOffBuilder(Config.SLEEP_MESSAGE.get()).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b,v)->{});
        craftBtn = CycleButton.onOffBuilder(Config.CRAFTING_MESSAGE.get()).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b,v)->{});
        addRenderableWidget(deathBtn);
        addRenderableWidget(rainBtn);
        addRenderableWidget(drownBtn);
        addRenderableWidget(sleepBtn);
        addRenderableWidget(craftBtn);

        configureMobsBtn = Button.builder(Component.literal("Conf."), b -> { view = View.MOBS; clampScroll(); layoutForCurrentView(); updateVisibility(); })
                .bounds(0, 0, 64, 18).build();
        addRenderableWidget(configureMobsBtn);

        int wAll = 100, wAct = 120, wInact = 130, h = 20;
        showAllBtn = Button.builder(Component.literal("Show All"), b -> { filterMode = FilterMode.ALL; scroll = 0; clampScroll(); }).bounds(0, 0, wAll, h).build();
        showActiveBtn = Button.builder(Component.literal("Show Active"), b -> { filterMode = FilterMode.ACTIVE; scroll = 0; clampScroll(); }).bounds(0, 0, wAct, h).build();
        showUnactiveBtn = Button.builder(Component.literal("Show Unactive"), b -> { filterMode = FilterMode.INACTIVE; scroll = 0; clampScroll(); }).bounds(0, 0, wInact, h).build();
        backBtn = Button.builder(Component.literal("Back"), b -> { view = View.MAIN; clampScroll(); layoutForCurrentView(); updateVisibility(); }).bounds(0, 0, 70, 20).build();
        addRenderableWidget(showAllBtn);
        addRenderableWidget(showActiveBtn);
        addRenderableWidget(showUnactiveBtn);
        addRenderableWidget(backBtn);

        List<EntityType<?>> all = new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getValues());
        all.removeIf(t -> t.getCategory() != MobCategory.MONSTER);
        all.sort(Comparator.comparing(t -> {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(t);
            return id == null ? "" : id.toString();
        }));

        Set<String> enabled = new HashSet<>();
        var cfg = Config.MOBS.get();
        if (cfg != null) for (String s : cfg) if (s != null && !s.isBlank()) enabled.add(s.toLowerCase(Locale.ROOT));

        for (EntityType<?> t : all) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(t);
            if (id == null) continue;
            String full = id.toString();
            String name = id.getPath();
            boolean sel = enabled.isEmpty() ? ("minecraft:creeper".equals(full) || "minecraft:zombie".equals(full)) : enabled.contains(full) || enabled.contains(name);
            CycleButton<Boolean> toggle = CycleButton.onOffBuilder(sel).displayOnlyValue().create(0, 0, 64, 18, Component.literal(""), (b, val) -> {});
            addRenderableWidget(toggle);
            entries.add(new Entry(full, name, toggle));
        }

        saveBtn = Button.builder(Component.literal("Save"), b -> save()).bounds(0, 0, 100, 20).build();
        cancelBtn = Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(0, 0, 100, 20).build();
        addRenderableWidget(saveBtn);
        addRenderableWidget(cancelBtn);

        layoutForCurrentView();
        updateVisibility();
        clampScroll();
    }

    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        layoutForCurrentView();
        clampScroll();
    }

    private void layoutForCurrentView() {
        int cw = Math.min(700, this.width - 2 * outerPad);
        if (cw < 300) cw = this.width - 2 * outerPad;
        contentWidth = cw;
        contentLeft = (this.width - contentWidth) / 2;
        contentRight = contentLeft + contentWidth;
        if (view == View.MAIN) {
            listLeft = contentLeft;
            listRight = contentRight - scrollbarW - scrollbarGap;
            listTop = 88;
            listBottom = this.height - 60;
        } else {
            layoutMobControls();
        }
    }

    private boolean passesFilter(Entry e) {
        boolean on = e.toggle.getValue();
        if (filterMode == FilterMode.ALL) return true;
        if (filterMode == FilterMode.ACTIVE) return on;
        return !on;
    }

    private int rowHeight() {
        return view == View.MOBS ? rowH : 22;
    }

    private int mainContentHeight() {
        return 11 * 22;
    }

    private int filteredCount() {
        int c = 0;
        for (Entry e : entries) if (passesFilter(e)) c++;
        return c;
    }

    private int contentHeight() {
        if (view == View.MOBS) return filteredCount() * rowH;
        return mainContentHeight();
    }

    private int viewHeight() {
        return Math.max(0, listBottom - listTop);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - viewHeight());
    }

    private int thumbH() {
        int vh = viewHeight();
        int ch = contentHeight();
        if (ch <= 0) return vh;
        return Math.max(16, vh * vh / ch);
    }

    private int thumbY() {
        int ms = maxScroll();
        int vh = viewHeight();
        int th = thumbH();
        if (ms == 0) return listTop;
        return listTop + (int)((vh - th) * (scroll / (double)ms));
    }

    private void setScrollFromThumbY(int mouseY) {
        int vh = viewHeight();
        int th = thumbH();
        int ms = maxScroll();
        if (ms == 0) { scroll = 0; return; }
        double rel = (mouseY - listTop - th * 0.5) / Math.max(1, (vh - th));
        scroll = rel * ms;
        clampScroll();
    }

    private void clampScroll() {
        if (scroll < 0) scroll = 0;
        int ms = maxScroll();
        if (scroll > ms) scroll = ms;
    }

    private void step(int d) {
        int v;
        try { v = Integer.parseInt(radiusBox.getValue().trim()); } catch (Exception e) { v = Config.RADIUS.get(); }
        v = Math.max(3, Math.min(64, v + d));
        radiusBox.setValue(Integer.toString(v));
    }

    private void save() {
        int r;
        try { r = Integer.parseInt(radiusBox.getValue().trim()); } catch (Exception e) { r = 10; }
        r = Math.max(3, Math.min(64, r));
        Config.RADIUS.set(r);
        List<String> list = new ArrayList<>();
        for (Entry e : entries) if (e.toggle.getValue()) list.add(e.id);
        Config.MOBS.set(list);
        Config.DAY_MESSAGE.set(dayBtn.getValue());
        Config.NIGHT_MESSAGE.set(nightBtn.getValue());
        Config.LOW_HEALTH_MESSAGE.set(lowHealthBtn.getValue());
        Config.LOW_HUNGER_MESSAGE.set(lowHungerBtn.getValue());
        Config.DEATH_MESSAGE.set(deathBtn.getValue());
        Config.RAIN_START_MESSAGE.set(rainBtn.getValue());
        Config.DROWNING_HALF_MESSAGE.set(drownBtn.getValue());
        Config.SLEEP_MESSAGE.set(sleepBtn.getValue());
        Config.CRAFTING_MESSAGE.set(craftBtn.getValue());
        Minecraft.getInstance().setScreen(parent);
    }

    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= dy * rowHeight();
        clampScroll();
        return true;
    }

    private void updateVisibility() {
        boolean main = view == View.MAIN;
        minusBtn.visible = main;
        plusBtn.visible = main;
        radiusBox.visible = main;
        dayBtn.visible = main;
        nightBtn.visible = main;
        lowHealthBtn.visible = main;
        lowHungerBtn.visible = main;
        deathBtn.visible = main;
        rainBtn.visible = main;
        drownBtn.visible = main;
        sleepBtn.visible = main;
        craftBtn.visible = main;
        configureMobsBtn.visible = main;
        saveBtn.visible = main;
        cancelBtn.visible = main;

        showAllBtn.visible = !main;
        showActiveBtn.visible = !main;
        showUnactiveBtn.visible = !main;
        backBtn.visible = !main;

        for (Entry e : entries) e.toggle.visible = !main;
    }

    private void layoutMainControls(int offY) {
        int controlW = 64;
        int controlRight = listRight;
        int controlX = controlRight - controlW;

        int yBlock = 88 + offY;
        radiusBox.setX(controlX - 44);
        radiusBox.setY(yBlock + 1);
        minusBtn.setPosition(controlX, yBlock);
        plusBtn.setPosition(controlX + controlW - 20, yBlock);

        int y0 = 110 + offY;
        dayBtn.setPosition(controlX, y0);
        nightBtn.setPosition(controlX, y0 + 22);
        lowHealthBtn.setPosition(controlX, y0 + 44);
        lowHungerBtn.setPosition(controlX, y0 + 66);
        deathBtn.setPosition(controlX, y0 + 88);
        rainBtn.setPosition(controlX, y0 + 110);
        drownBtn.setPosition(controlX, y0 + 132);
        sleepBtn.setPosition(controlX, y0 + 154);
        craftBtn.setPosition(controlX, y0 + 176);
        configureMobsBtn.setPosition(controlX, y0 + 198);
    }

    private void applyViewportVisibilityForMain() {
        setVisibleWithin(minusBtn);
        setVisibleWithin(plusBtn);
        setVisibleWithin(radiusBox);
        setVisibleWithin(dayBtn);
        setVisibleWithin(nightBtn);
        setVisibleWithin(lowHealthBtn);
        setVisibleWithin(lowHungerBtn);
        setVisibleWithin(deathBtn);
        setVisibleWithin(rainBtn);
        setVisibleWithin(drownBtn);
        setVisibleWithin(sleepBtn);
        setVisibleWithin(craftBtn);
        setVisibleWithin(configureMobsBtn);
    }

    private void setVisibleWithin(Button b) {
        int y = b.getY();
        int h = b.getHeight();
        b.visible = y + h >= listTop && y <= listBottom;
    }

    private void setVisibleWithin(EditBox e) {
        int y = e.getY();
        int h = e.getHeight();
        e.visible = y + h >= listTop && y <= listBottom;
    }

    private void setVisibleWithin(CycleButton<?> c) {
        int y = c.getY();
        int h = c.getHeight();
        c.visible = y + h >= listTop && y <= listBottom;
    }

    private void layoutMobControls() {
        int gap = 10, wAll = 100, wAct = 120, wInact = 130, h = 20;
        int totalW = wAll + gap + wAct + gap + wInact;
        int sx = contentLeft + (contentWidth - totalW) / 2;
        int fy = 80;

        showAllBtn.setPosition(sx, fy);
        showActiveBtn.setPosition(sx + wAll + gap, fy);
        showUnactiveBtn.setPosition(sx + wAll + gap + wAct + gap, fy);
        backBtn.setPosition(contentLeft, this.height - 40);

        listLeft = contentLeft;
        listRight = contentRight - scrollbarW - scrollbarGap;
        listTop = fy + 28;
        listBottom = this.height - 60;

        clampScroll();
    }

    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xFF101010);
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        boolean leftDown = Minecraft.getInstance().mouseHandler.isLeftPressed();
        int bx = contentRight - scrollbarW;
        int by0 = listTop;
        int by1 = listBottom;
        int th = thumbH();
        int ty = thumbY();
        if (leftDown && !leftWasDown) {
            if (mx >= bx && mx <= bx + scrollbarW && my >= by0 && my <= by1) {
                draggingScrollbar = true;
                if (my < ty || my > ty + th) setScrollFromThumbY((int) my);
            }
        }
        if (draggingScrollbar && leftDown) setScrollFromThumbY((int) my);
        if (draggingScrollbar && !leftDown) draggingScrollbar = false;
        leftWasDown = leftDown;

        this.renderBackground(g, mx, my, pt);

        if (view == View.MAIN) {
            layoutForCurrentView();
            int offY = -(int)scroll;
            layoutMainControls(offY);

            g.drawCenteredString(this.font, "MateSignal", this.width / 2, 20, 0xFFFFFFFF);
            int labelX = contentLeft;

            int yBlockLbl = 92 + offY;
            if (yBlockLbl >= listTop - 16 && yBlockLbl <= listBottom) g.drawString(this.font, "Block Radius", labelX, yBlockLbl, 0xFFFFFFFF);

            int y0 = 110 + offY;
            if (y0 + 4 >= listTop - 16 && y0 + 4 <= listBottom) g.drawString(this.font, "Day Time Message", labelX, y0 + 4, 0xFFFFFFFF);
            if (y0 + 26 >= listTop - 16 && y0 + 26 <= listBottom) g.drawString(this.font, "Night Time Message", labelX, y0 + 26, 0xFFFFFFFF);
            if (y0 + 48 >= listTop - 16 && y0 + 48 <= listBottom) g.drawString(this.font, "Low Health Message", labelX, y0 + 48, 0xFFFFFFFF);
            if (y0 + 70 >= listTop - 16 && y0 + 70 <= listBottom) g.drawString(this.font, "Low Hunger Message", labelX, y0 + 70, 0xFFFFFFFF);
            if (y0 + 92 >= listTop - 16 && y0 + 92 <= listBottom) g.drawString(this.font, "Death Message", labelX, y0 + 92, 0xFFFFFFFF);
            if (y0 + 114 >= listTop - 16 && y0 + 114 <= listBottom) g.drawString(this.font, "Rain Start Message", labelX, y0 + 114, 0xFFFFFFFF);
            if (y0 + 136 >= listTop - 16 && y0 + 136 <= listBottom) g.drawString(this.font, "Drowning 50% Air", labelX, y0 + 136, 0xFFFFFFFF);
            if (y0 + 158 >= listTop - 16 && y0 + 158 <= listBottom) g.drawString(this.font, "Sleep Message", labelX, y0 + 158, 0xFFFFFFFF);
            if (y0 + 180 >= listTop - 16 && y0 + 180 <= listBottom) g.drawString(this.font, "Crafting Message (30%)", labelX, y0 + 180, 0xFFFFFFFF);
            if (y0 + 202 >= listTop - 16 && y0 + 202 <= listBottom) g.drawString(this.font, "Configure Mob Messages", labelX, y0 + 202, 0xFFFFFFFF);

            applyViewportVisibilityForMain();

            saveBtn.setPosition(labelX, this.height - 40);
            cancelBtn.setPosition(contentRight - 100, this.height - 40);

            super.render(g, mx, my, pt);

            g.fill(bx, listTop, bx + scrollbarW, listBottom, 0xAA303030);
            if (maxScroll() > 0) {
                int tth = thumbH();
                int tty = thumbY();
                g.fill(bx + 1, tty, bx + scrollbarW - 1, tty + tth, draggingScrollbar ? 0xFFFFFFFF : 0xFFE0E0E0);
            }
            return;
        }

        layoutMobControls();
        g.drawCenteredString(this.font, "Mob Messages", this.width / 2, 20, 0xFFFFFFFF);

        int startIndex = (int)Math.floor(scroll / rowH);
        int yStart = listTop - (int)(scroll % rowH);

        int skipped = startIndex;
        int y = yStart;
        for (Entry e : entries) {
            if (!passesFilter(e)) { e.toggle.visible = false; continue; }
            if (skipped > 0) { skipped--; e.toggle.visible = false; continue; }
            if (y > listBottom) { e.toggle.visible = false; continue; }
            e.toggle.visible = true;
            e.toggle.setX(listRight - 70);
            e.toggle.setY(y + 1);
            e.toggle.setWidth(64);
            g.drawString(this.font, e.name + "  (" + e.id + ")", listLeft, y + 5, 0xFFFFFFFF);
            y += rowH;
        }

        super.render(g, mx, my, pt);

        g.fill(bx, listTop, bx + scrollbarW, listBottom, 0xAA303030);
        if (maxScroll() > 0) {
            int tth = thumbH();
            int tty = thumbY();
            g.fill(bx + 1, tty, bx + scrollbarW - 1, tty + tth, draggingScrollbar ? 0xFFFFFFFF : 0xFFE0E0E0);
        }
    }

    private static class Entry {
        final String id;
        final String name;
        final CycleButton<Boolean> toggle;
        Entry(String id, String name, CycleButton<Boolean> toggle) {
            this.id = id;
            this.name = name;
            this.toggle = toggle;
        }
    }
}
