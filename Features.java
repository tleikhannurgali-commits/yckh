package com.example.storageesp;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Список функций мода: состояние вкл/выкл хранится в config/storageesp.properties. */
public enum Features {
    STORAGE("Подсветка хранилищ", true),
    PLAYERS("Подсветка игроков", true),
    ANTI_INVIS("Анти-невидимость", true),
    WARDEN("Warden: запомненные таймеры", true),
    LOOT("Лут-трекер", true),
    RADAR("Радар игроков", true),
    MARKS("Метки (призраки, смерть)", true),
    VOID_MAP("Карта пустот", false),
    TRACES("Следы активности", true),
    PRICES("Ценники в ауке", true),
    FULLBRIGHT("Fullbright (ночное зрение)", false);

    public final String label;
    private boolean on;

    Features(String label, boolean defaultOn) {
        this.label = label;
        this.on = defaultOn;
    }

    public boolean on() { return on; }

    public void toggle() { on = !on; }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("storageesp.properties");
    }

    public static void load() {
        Path p = file();
        if (!Files.exists(p)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(p)) {
            props.load(in);
        } catch (IOException e) {
            return;
        }
        EspSettings.load(props);
        for (Features f : values()) {
            String v = props.getProperty(f.name());
            if (v != null) f.on = Boolean.parseBoolean(v);
        }
    }

    public static void save() {
        Properties props = new Properties();
        for (Features f : values()) props.setProperty(f.name(), Boolean.toString(f.on));
        EspSettings.save(props);
        try (OutputStream out = Files.newOutputStream(file())) {
            props.store(out, "Storage ESP");
        } catch (IOException ignored) {
        }
    }
}
