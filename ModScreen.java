package com.example.storageesp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Responsive StorageESP GUI for Minecraft 1.21.11.
 * It renders on a fixed virtual canvas, scaled to the available screen size;
 * mouse coordinates are transformed into the exact same virtual space.
 * No manual blur call: the game already blurs screen backgrounds.
 */
public final class ModScreen extends Screen {
    private static final int W = 710, H = 470;
    private static final int CONTENT_X = 183, CONTENT_W = 499;
    private static final int VIEW_Y = 133, VIEW_H = 270;
    private static final int ROW_H = 66, ROW_STEP = 80;

    private static final int INK = 0xFF3D3051;
    private static final int MUTED = 0xFF786C89;
    private static final int VIOLET = 0xFF916AE2;
    private static final int LILAC = 0xFFDDCEF8;
    private static final int PALE = 0xE8EAE3F4;
    private static final int LIGHT = 0xEFFFFFFF;

    private static final String[] TABS = {"ХРАНИЛИЩА", "ИГРОКИ", "МИР"};
    private static final Features[][] GROUPS = {
        {Features.STORAGE, Features.LOOT, Features.WARDEN, Features.PRICES},
        {Features.PLAYERS, Features.ANTI_INVIS, Features.RADAR, Features.MARKS},
        {Features.VOID_MAP, Features.TRACES, Features.FULLBRIGHT}
    };

    private int activeTab;
    private Features settingsFor;
    private float scroll;
    private float displayedScroll;
    private float openAnim;
    private long lastFrameTime;

    public ModScreen() {
        super(Component.literal("Storage ESP"));
    }

    private float uiScale() {
        return Math.min(1.0f, Math.min((width - 18.0f) / W, (height - 18.0f) / H));
    }

    private float offsetX() { return (width - W * uiScale()) * 0.5f; }
    private float offsetY() { return (height - H * uiScale()) * 0.5f; }
    private double vx(double x) { return (x - offsetX()) / uiScale(); }
    private double vy(double y) { return (y - offsetY()) / uiScale(); }
    private static boolean in(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    private int rowCount() {
        return settingsFor == Features.PLAYERS ? 4
                : settingsFor == Features.STORAGE ? 4
                : settingsFor == Features.MARKS ? 2
                : GROUPS[activeTab].length;
    }

    private float maxScroll() { return Math.max(0, rowCount() * ROW_STEP - VIEW_H + 4); }

    private void resetScroll() { scroll = 0; displayedScroll = 0; }

    private void updateAnimations() {
        long now = System.currentTimeMillis();
        float dt = lastFrameTime == 0 ? 0.016f : Math.min(0.06f, (now - lastFrameTime) / 1000.0f);
        lastFrameTime = now;
        float blend = 1.0f - (float) Math.exp(-12.0f * dt);
        openAnim += (1.0f - openAnim) * blend;
        displayedScroll += (scroll - displayedScroll) * blend;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateAnimations();
        // Never call renderBlurredBackground here; it crashes 1.21.11 on repeated blur.
        g.fill(0, 0, width, height, 0x440E0920);
        float s = uiScale();
        float ox = offsetX(), oy = offsetY();
        double mx = vx(mouseX), my = vy(mouseY);
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(s, s);

        rect(g, 0, 0, W, H, 21, 0xD5FFFFFF);
        rect(g, 3, 3, W - 6, H - 6, 19, PALE);
        rect(g, 4, 4, 157, H - 8, 18, 0xD5DED2F1);
        rect(g, 153, 4, 11, H - 8, 0, 0xD5DED2F1);
        rect(g, 20, 23, 9, 30, 5, VIOLET);
        rect(g, 33, 17, 9, 36, 5, 0xFFCEBBF6);
        rect(g, 46, 29, 9, 24, 5, 0xFFA58BDC);
        g.drawString(font, "STORAGE ESP", 68, 23, INK, false);
        g.drawString(font, "FORGE 1.21.11    /    v1.2", 68, 40, MUTED, false);
        rect(g, 547, 22, 139, 31, 14, 0xD7F7F2FF);
        g.drawCenteredString(font, "VISUAL CONTROL", 616, 33, 0xFF7657A9);
        g.fill(19, 68, W - 19, 69, 0x88FFFFFF);

        drawSidebar(g, mx, my);
        drawContent(g, mx, my);
        drawFooter(g, mx, my);
        g.pose().popMatrix();
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawSidebar(GuiGraphics g, double mx, double my) {
        g.drawString(font, "РАЗДЕЛЫ", 20, 93, MUTED, false);
        for (int t = 0; t < TABS.length; t++) {
            int y = 119 + t * 56;
            boolean active = t == activeTab;
            boolean hover = in(mx, my, 13, y, 139, 44);
            if (active || hover) rect(g, 12, y, 139, 44, 13, active ? 0xAFCAB9F0 : 0x81FFFFFF);
            if (active) rect(g, 15, y + 8, 4, 28, 2, VIOLET);
            g.drawString(font, t == 0 ? "ST" : t == 1 ? "PL" : "WR", 27, y + 18, active ? 0xFF7352C0 : MUTED, false);
            g.drawString(font, TABS[t], 58, y + 18, active ? INK : MUTED, false);
        }
        g.fill(20, 305, 143, 306, 0x88FFFFFF);
        g.drawString(font, "МОДУЛЕЙ", 23, 324, MUTED, false);
        rect(g, 21, 346, 41, 26, 9, LILAC);
        g.drawCenteredString(font, String.valueOf(Features.values().length), 41, 355, 0xFF6E55B4);
        g.drawString(font, "в сборке", 68, 356, MUTED, false);
        g.drawString(font, "ЛКМ - вкл/выкл", 19, H - 72, MUTED, false);
        g.drawString(font, "ПКМ - настройки", 19, H - 55, MUTED, false);
    }

    private void drawContent(GuiGraphics g, double mx, double my) {
        if (settingsFor == null) {
            g.drawString(font, TABS[activeTab], CONTENT_X, 89, INK, false);
            g.drawString(font, "Колесо мыши / СКМ - прокрутка", CONTENT_X, 107, MUTED, false);
            int active = 0;
            for (Features f : GROUPS[activeTab]) if (f.on()) active++;
            String state = active + " / " + GROUPS[activeTab].length + " ON";
            g.drawString(font, state, CONTENT_X + CONTENT_W - font.width(state) - 13, 91, 0xFF835CC2, false);
        } else {
            String title = settingsFor == Features.PLAYERS ? "PLAYER ESP"
                    : settingsFor == Features.STORAGE ? "STORAGE ESP" : "MARKS";
            g.drawString(font, "НАСТРОЙКИ  /  " + title, CONTENT_X, 89, INK, false);
            g.drawString(font, "Переключатели сохраняются автоматически", CONTENT_X, 107, MUTED, false);
        }

        g.enableScissor(CONTENT_X, VIEW_Y, CONTENT_X + CONTENT_W, VIEW_Y + VIEW_H);
        int count = rowCount();
        for (int i = 0; i < count; i++) {
            int y = Math.round(VIEW_Y + i * ROW_STEP - displayedScroll);
            if (y + ROW_H < VIEW_Y || y > VIEW_Y + VIEW_H) continue;
            if (settingsFor == null) drawFeature(g, GROUPS[activeTab][i], y, mx, my);
            else drawSetting(g, i, y, mx, my);
        }
        g.disableScissor();

        if (maxScroll() > 0) {
            int railX = CONTENT_X + CONTENT_W - 5;
            rect(g, railX, VIEW_Y, 3, VIEW_H, 2, 0x72B9ACD5);
            int thumb = Math.max(26, Math.round(VIEW_H * VIEW_H / (VIEW_H + maxScroll())));
            int thumbY = VIEW_Y + Math.round((VIEW_H - thumb) * displayedScroll / maxScroll());
            rect(g, railX - 1, thumbY, 5, thumb, 3, VIOLET);
        }
    }

    private void drawFeature(GuiGraphics g, Features f, int y, double mx, double my) {
        boolean hover = in(mx, my, CONTENT_X, y, CONTENT_W - 14, ROW_H);
        rect(g, CONTENT_X, y, CONTENT_W - 14, ROW_H, 14, hover ? 0xF6FFFFFF : LIGHT);
        rect(g, CONTENT_X + 12, y + 13, 42, 40, 13, f.on() ? 0xC9D7C3F7 : 0xA8E6E0EC);
        g.drawCenteredString(font, icon(f), CONTENT_X + 33, y + 29, f.on() ? 0xFF7652C2 : MUTED);
        g.drawString(font, name(f), CONTENT_X + 65, y + 16, INK, false);
        g.drawString(font, subtitle(f), CONTENT_X + 65, y + 36, MUTED, false);
        boolean hasOptions = f == Features.STORAGE || f == Features.PLAYERS || f == Features.MARKS;
        if (hasOptions) {
            rect(g, CONTENT_X + CONTENT_W - 179, y + 36, 91, 19, 8, 0xE6E5D9F8);
            g.drawCenteredString(font, "НАСТРОИТЬ", CONTENT_X + CONTENT_W - 134, y + 42, 0xFF7354AC);
        }
        drawSwitch(g, CONTENT_X + CONTENT_W - 76, y + 12, f.on());
    }

    private void drawSetting(GuiGraphics g, int index, int y, double mx, double my) {
        String label;
        String subtitle;
        String icon;
        if (settingsFor == Features.PLAYERS) {
            label = switch (index) {
                case 0 -> "HP и шкала здоровья";
                case 1 -> "Контрастные линии";
                case 2 -> "Дистанция + ник";
                default -> "Броня H/C/L/B";
            };
            subtitle = switch (index) {
                case 0 -> "Сердце, число и полоска над игроком";
                case 1 -> "От камеры до центра игрока";
                case 2 -> "Расстояние в блоках";
                default -> "Шлем, нагрудник, штаны, ботинки";
            };
            icon = switch (index) { case 0 -> "HP"; case 1 -> "LN"; case 2 -> "M"; default -> "AR"; };
        } else if (settingsFor == Features.STORAGE) {
            label = switch (index) {
                case 0 -> "Линии к контейнерам";
                case 1 -> "Эндер-сундуки";
                case 2 -> "Бочки";
                default -> "Шалкеры";
            };
            subtitle = switch (index) {
                case 0 -> "До 12 ближайших контейнеров";
                case 1 -> "Фиолетовая подсветка";
                case 2 -> "Коричневая подсветка";
                default -> "Цвет под тип шалкера";
            };
            icon = switch (index) { case 0 -> "LN"; case 1 -> "EN"; case 2 -> "BR"; default -> "SH"; };
        } else {
            label = index == 0 ? "Смерти других игроков" : "Последние позиции (призраки)";
            subtitle = index == 0 ? "Только увиденная клиентом смерть" : "Отдельные голубые метки на 10 секунд";
            icon = index == 0 ? "DE" : "GH";
        }
        boolean value = settingValue(index);
        boolean hover = in(mx, my, CONTENT_X, y, CONTENT_W - 14, ROW_H);
        rect(g, CONTENT_X, y, CONTENT_W - 14, ROW_H, 14, hover ? 0xF6FFFFFF : LIGHT);
        rect(g, CONTENT_X + 13, y + 13, 41, 40, 12, 0xC6DDD0F7);
        g.drawCenteredString(font, icon, CONTENT_X + 33, y + 29, 0xFF7458B4);
        g.drawString(font, label, CONTENT_X + 65, y + 16, INK, false);
        g.drawString(font, subtitle, CONTENT_X + 65, y + 36, MUTED, false);
        drawSwitch(g, CONTENT_X + CONTENT_W - 76, y + 22, value);
    }

    private void drawFooter(GuiGraphics g, double mx, double my) {
        g.fill(178, 417, 687, 418, 0x9FFFFFFF);
        rect(g, CONTENT_X, 428, 145, 30, 12, 0xD2E2D5F5);
        g.drawCenteredString(font, settingsFor == null ? "СБРОС МЕТОК" : "НАЗАД", CONTENT_X + 72, 439, 0xFF7155AD);
        rect(g, CONTENT_X + CONTENT_W - 117, 428, 116, 30, 12, 0xC4C8B4F2);
        g.drawCenteredString(font, "ГОТОВО", CONTENT_X + CONTENT_W - 59, 439, 0xFF55338A);
        g.drawString(font, "RIGHT CTRL - MENU", CONTENT_X + 159, 440, MUTED, false);
    }

    private static void drawSwitch(GuiGraphics g, int x, int y, boolean value) {
        rect(g, x, y, 47, 24, 12, value ? VIOLET : 0xFFC8C1D0);
        rect(g, x + (value ? 25 : 3), y + 3, 18, 18, 9, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double x = vx(mouseX), y = vy(mouseY);
        if (in(x, y, CONTENT_X, VIEW_Y, CONTENT_W, VIEW_H)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (float) verticalAmount * 46f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = vx(event.x()), y = vy(event.y());
        int button = event.button();
        // Middle mouse button: move down one page; at the bottom return to the top.
        if (button == 2 && in(x, y, CONTENT_X, VIEW_Y, CONTENT_W, VIEW_H)) {
            scroll = scroll >= maxScroll() - 1 ? 0 : Math.min(maxScroll(), scroll + VIEW_H * 0.72f);
            return true;
        }
        if (button != 0 && button != 1) return super.mouseClicked(event, doubleClick);

        for (int t = 0; t < TABS.length; t++) {
            if (button == 0 && in(x, y, 13, 119 + t * 56, 139, 44)) {
                activeTab = t;
                settingsFor = null;
                resetScroll();
                return true;
            }
        }
        if (button == 0 && in(x, y, CONTENT_X, 428, 145, 30)) {
            if (settingsFor != null) settingsFor = null;
            else { MarksTracker.clear(); LootTracker.clear(); }
            resetScroll();
            return true;
        }
        if (button == 0 && in(x, y, CONTENT_X + CONTENT_W - 117, 428, 116, 30)) {
            onClose();
            return true;
        }
        if (in(x, y, CONTENT_X, VIEW_Y, CONTENT_W - 14, VIEW_H)) {
            int i = (int) ((y - VIEW_Y + displayedScroll) / ROW_STEP);
            if (i < 0 || i >= rowCount()) return true;
            int rowY = Math.round(VIEW_Y + i * ROW_STEP - displayedScroll);
            if (!in(x, y, CONTENT_X, rowY, CONTENT_W - 14, ROW_H)) return true;
            if (settingsFor != null) {
                if (button == 0) { toggleSetting(i); Features.save(); }
                return true;
            }
            Features f = GROUPS[activeTab][i];
            if ((button == 1 || x >= CONTENT_X + CONTENT_W - 185 && x < CONTENT_X + CONTENT_W - 83 && y > rowY + 32)
                    && (f == Features.PLAYERS || f == Features.STORAGE || f == Features.MARKS)) {
                settingsFor = f;
                resetScroll();
                return true;
            }
            if (button == 0) { f.toggle(); Features.save(); }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean settingValue(int i) {
        if (settingsFor == Features.STORAGE) return switch (i) {
            case 0 -> EspSettings.storageTracer;
            case 1 -> EspSettings.storageEnder;
            case 2 -> EspSettings.storageBarrels;
            default -> EspSettings.storageShulkers;
        };
        if (settingsFor == Features.MARKS) return i == 0 ? EspSettings.markOtherDeaths : EspSettings.markGhosts;
        return switch (i) {
            case 0 -> EspSettings.playerHealth;
            case 1 -> EspSettings.playerTracer;
            case 2 -> EspSettings.playerDistance;
            default -> EspSettings.playerArmor;
        };
    }

    private void toggleSetting(int i) {
        if (settingsFor == Features.STORAGE) {
            switch (i) {
                case 0 -> EspSettings.storageTracer = !EspSettings.storageTracer;
                case 1 -> EspSettings.storageEnder = !EspSettings.storageEnder;
                case 2 -> EspSettings.storageBarrels = !EspSettings.storageBarrels;
                default -> EspSettings.storageShulkers = !EspSettings.storageShulkers;
            }
        } else if (settingsFor == Features.MARKS) {
            if (i == 0) EspSettings.markOtherDeaths = !EspSettings.markOtherDeaths;
            else EspSettings.markGhosts = !EspSettings.markGhosts;
        } else {
            switch (i) {
                case 0 -> EspSettings.playerHealth = !EspSettings.playerHealth;
                case 1 -> EspSettings.playerTracer = !EspSettings.playerTracer;
                case 2 -> EspSettings.playerDistance = !EspSettings.playerDistance;
                default -> EspSettings.playerArmor = !EspSettings.playerArmor;
            }
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Features.save(); super.onClose(); }

    private static String icon(Features f) {
        return switch (f) {
            case STORAGE -> "ST";
            case LOOT -> "LT";
            case WARDEN -> "WD";
            case PRICES -> "$";
            case PLAYERS -> "PL";
            case ANTI_INVIS -> "AI";
            case RADAR -> "RD";
            case MARKS -> "MK";
            case VOID_MAP -> "VM";
            case TRACES -> "TR";
            case FULLBRIGHT -> "FB";
        };
    }
    private static String name(Features f) {
        return switch (f) {
            case STORAGE -> "Storage ESP";
            case LOOT -> "Loot Tracker";
            case WARDEN -> "Warden Tracker";
            case PRICES -> "Price Tags";
            case PLAYERS -> "Player ESP";
            case ANTI_INVIS -> "Anti-Invis";
            case RADAR -> "Player Radar";
            case MARKS -> "Marks & Ghosts";
            case VOID_MAP -> "Void Map";
            case TRACES -> "Activity Traces";
            case FULLBRIGHT -> "Fullbright";
        };
    }
    private static String subtitle(Features f) {
        return switch (f) {
            case STORAGE -> "Контейнеры через стены";
            case LOOT -> "Уже открытые сундуки";
            case WARDEN -> "Память таймера / без ложной зелени";
            case PRICES -> "Цены предметов на аукционе";
            case PLAYERS -> "HP-бар, броня, ник, линии, метры";
            case ANTI_INVIS -> "Видимость невидимых";
            case RADAR -> "Игроки на мини-карте";
            case MARKS -> "Метки и подтверждённые смерти";
            case VOID_MAP -> "Поиск прямых тоннелей";
            case TRACES -> "Отметки по звукам";
            case FULLBRIGHT -> "Ночное зрение без команд";
        };
    }

    /** Rounded rectangle without textures or shader dependencies. */
    private static void rect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w < 1 || h < 1) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }
}
