package com.example.storageesp;

import java.util.Properties;

/** Persistent options. Every switch is independent of the module's master switch. */
public final class EspSettings {
    private EspSettings() {}

    public static boolean playerHealth = true;
    public static boolean playerTracer = false;
    public static boolean playerDistance = true;
    public static boolean playerArmor = true;

    public static boolean storageTracer = false;
    public static boolean storageEnder = true;
    public static boolean storageBarrels = true;
    public static boolean storageShulkers = true;

    public static boolean markOtherDeaths = true;
    public static boolean markGhosts = false; // Avoid mixing last-known positions with live player ESP.

    static void load(Properties p) {
        playerHealth = read(p, "esp.playerHealth", true);
        playerTracer = read(p, "esp.playerTracer", false);
        playerDistance = read(p, "esp.playerDistance", true);
        playerArmor = read(p, "esp.playerArmor", true);
        storageTracer = read(p, "esp.storageTracer", false);
        storageEnder = read(p, "esp.storageEnder", true);
        storageBarrels = read(p, "esp.storageBarrels", true);
        storageShulkers = read(p, "esp.storageShulkers", true);
        markOtherDeaths = read(p, "esp.markOtherDeaths", true);
        markGhosts = read(p, "esp.markGhosts", false);
    }

    private static boolean read(Properties p, String name, boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(name, Boolean.toString(fallback)));
    }

    static void save(Properties p) {
        p.setProperty("esp.playerHealth", Boolean.toString(playerHealth));
        p.setProperty("esp.playerTracer", Boolean.toString(playerTracer));
        p.setProperty("esp.playerDistance", Boolean.toString(playerDistance));
        p.setProperty("esp.playerArmor", Boolean.toString(playerArmor));
        p.setProperty("esp.storageTracer", Boolean.toString(storageTracer));
        p.setProperty("esp.storageEnder", Boolean.toString(storageEnder));
        p.setProperty("esp.storageBarrels", Boolean.toString(storageBarrels));
        p.setProperty("esp.storageShulkers", Boolean.toString(storageShulkers));
        p.setProperty("esp.markOtherDeaths", Boolean.toString(markOtherDeaths));
        p.setProperty("esp.markGhosts", Boolean.toString(markGhosts));
    }
}
