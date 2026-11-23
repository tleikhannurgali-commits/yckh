package com.example.storageesp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ценники в ауке. Добавляет в подсказку предмета:
 *  - "Ориентир": твоя цена за предмет из config/storageesp_prices.txt (строки вида minecraft:diamond=150);
 *  - "За 1 шт": цена из описания лота (строка со словом "Цена"), делённая на количество, с пометкой выгодно/дорого.
 */
public class PriceTags {

    private static final Pattern LORE_PRICE = Pattern.compile("(?i)цена\\D{0,20}?(\\d[\\d\\s.,]*)");
    private static final String SAMPLE = """
            # Ориентировочные цены за 1 шт. Формат: id_предмета=цена
            minecraft:diamond=100
            minecraft:netherite_ingot=1000
            minecraft:golden_apple=50
            minecraft:ender_pearl=20
            minecraft:totem_of_undying=300
            """;

    private static final Map<String, Double> prices = new HashMap<>();
    private static boolean loaded;

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = FMLPaths.CONFIGDIR.get().resolve("storageesp_prices.txt");
        try {
            if (!Files.exists(file)) Files.writeString(file, SAMPLE);
            for (String line : Files.readAllLines(file)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int i = line.indexOf('=');
                if (i < 0) continue;
                try {
                    prices.put(line.substring(0, i).trim(), Double.parseDouble(line.substring(i + 1).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static void onTooltip(ItemTooltipEvent event) {
        if (!Features.PRICES.on()) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        ensureLoaded();

        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Double ref = prices.get(key.toString());
        int count = stack.getCount();
        List<Component> tip = event.getToolTip();

        double lotPrice = -1;
        for (Component line : tip) {
            Matcher m = LORE_PRICE.matcher(line.getString());
            if (m.find()) {
                lotPrice = parse(m.group(1));
                break;
            }
        }

        if (ref != null) {
            tip.add(Component.literal("Ориентир: " + fmt(ref * count) + " (" + fmt(ref) + "/шт)")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (lotPrice > 0 && count > 0) {
            double unit = lotPrice / count;
            String text = "За 1 шт: " + fmt(unit);
            ChatFormatting color = ChatFormatting.WHITE;
            if (ref != null) {
                if (unit <= ref * 0.8) { text += " — выгодно"; color = ChatFormatting.GREEN; }
                else if (unit >= ref * 1.2) { text += " — дорого"; color = ChatFormatting.RED; }
            }
            tip.add(Component.literal(text).withStyle(color));
        }
    }

    private static double parse(String s) {
        String digits = s.replaceAll("[^0-9.]", "");
        if (digits.isEmpty()) return -1;
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.format(Locale.ROOT, "%,d", (long) v).replace(',', ' ');
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
