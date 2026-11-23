package com.example.storageesp;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import org.lwjgl.glfw.GLFW;

/**
 * Главный клиентский класс: клавиши, тик, подписки на события.
 * Рамки рисуются через Gizmos с setAlwaysOnTop() — видны сквозь стены.
 * Gizmos отправляются раз в тик (20 раз в секунду).
 *
 * Клавиши: Right Control — меню; H/J/K/O — быстрые переключатели; M — поставить метку.
 */
public class StorageEspClient {

    /** Радиус поиска хранилищ в чанках (6 чанков = 96 блоков). */
    private static final int RANGE_CHUNKS = 6;
    private static final float LINE_WIDTH = 2.5f;
    private static Object storageWorld;
    private static List<net.minecraft.core.BlockPos> nearbyStorage = List.of();
    private static int storageTick;
    private static int scanCx = Integer.MIN_VALUE, scanCz = Integer.MIN_VALUE;

    private static final KeyMapping KEY_MENU = key("menu", GLFW.GLFW_KEY_RIGHT_CONTROL);
    private static final KeyMapping KEY_STORAGE = key("storage", GLFW.GLFW_KEY_H);
    private static final KeyMapping KEY_PLAYERS = key("players", GLFW.GLFW_KEY_J);
    private static final KeyMapping KEY_INVIS = key("invis", GLFW.GLFW_KEY_K);
    private static final KeyMapping KEY_WARDEN = key("warden", GLFW.GLFW_KEY_O);
    private static final KeyMapping KEY_MARK = key("mark", GLFW.GLFW_KEY_M);

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.storageesp." + name, InputConstants.Type.KEYSYM, code, KeyMapping.Category.MISC);
    }

    public static void init(BusGroup modBus) {
        Features.load();
        RegisterKeyMappingsEvent.getBus(modBus).addListener(StorageEspClient::onRegisterKeys);
        TickEvent.ClientTickEvent.Post.BUS.addListener(StorageEspClient::onClientTick);
        AddGuiOverlayLayersEvent.BUS.addListener(StorageEspClient::onRegisterHudLayer);
        ItemTooltipEvent.BUS.addListener(PriceTags::onTooltip);
        PlaySoundEvent.BUS.addListener(TracesTracker::onSound);
    }

    private static void onRegisterHudLayer(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
            Identifier.fromNamespaceAndPath("storageesp", "radar"),
            RadarHud::onRender
        );
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_MENU);
        event.register(KEY_STORAGE);
        event.register(KEY_PLAYERS);
        event.register(KEY_INVIS);
        event.register(KEY_WARDEN);
        event.register(KEY_MARK);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        while (KEY_MENU.consumeClick()) { if (mc.screen == null) mc.setScreen(new ModScreen()); }
        while (KEY_STORAGE.consumeClick()) toggle(mc, Features.STORAGE);
        while (KEY_PLAYERS.consumeClick()) toggle(mc, Features.PLAYERS);
        while (KEY_INVIS.consumeClick())   toggle(mc, Features.ANTI_INVIS);
        while (KEY_WARDEN.consumeClick())  toggle(mc, Features.WARDEN);
        while (KEY_MARK.consumeClick())    MarksTracker.addMark(mc);

        FullbrightController.tick(mc);

        if (Features.ANTI_INVIS.on()) revealInvisiblePlayers(level);
        if (Features.PLAYERS.on()) drawPlayers(mc, level);
        if (Features.LOOT.on()) LootTracker.tick(mc, level);
        if (Features.STORAGE.on()) drawStorage(mc, level);
        if (Features.WARDEN.on()) WardenTracker.draw(mc, level);
        if (Features.MARKS.on()) MarksTracker.tick(mc, level);
        if (Features.VOID_MAP.on()) VoidMapScanner.tick(mc, level);
        if (Features.TRACES.on()) TracesTracker.draw();
    }

    private static void toggle(Minecraft mc, Features f) {
        f.toggle();
        Features.save();
        mc.player.displayClientMessage(Component.literal(f.label + ": " + (f.on() ? "ON" : "OFF")), true);
    }

    /** Снимает флаг невидимости у игроков на клиенте — они снова рисуются. */
    private static void revealInvisiblePlayers(ClientLevel level) {
        for (AbstractClientPlayer p : level.players()) {
            if (p.isInvisible()) p.setInvisible(false);
        }
    }

    /** Refresh live entities each tick; do not draw a player who is removed, dead, or absent from the level. */
    private static void drawPlayers(Minecraft mc, ClientLevel level) {
        Vec3 camera = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(1.15));
        for (AbstractClientPlayer p : level.players()) {
            if (p == mc.player || p.isRemoved() || !p.isAlive() || p.isSpectator()) continue;
            if (level.getEntity(p.getId()) != p) continue;
            if (mc.player.distanceToSqr(p) > 128.0 * 128.0) continue;

            int color = p.isAlliedTo(mc.player) ? 0xFF60E5A2 : 0xFFFF6782;
            Gizmos.cuboid(p.getBoundingBox().inflate(0.025), GizmoStyle.stroke(color, 2.2f))
                    .setAlwaysOnTop();

            Vec3 target = p.position().add(0.0, p.getBbHeight() * 0.52, 0.0);
            if (EspSettings.playerTracer && camera.distanceToSqr(target) > 1.0) {
                drawTracer(camera, target, color, 3.0f);
            }

            int row = 0;
            if (EspSettings.playerHealth) {
                float hp = Math.max(0.0f, p.getHealth());
                float maxHp = Math.max(1.0f, p.getMaxHealth());
                int filled = Math.max(0, Math.min(10, Math.round(10.0f * hp / maxHp)));
                int hpColor = hp / maxHp > 0.60f ? 0xFF61ED8E : hp / maxHp > 0.28f ? 0xFFFFCE63 : 0xFFFF677F;
                String bar = "■".repeat(filled) + "□".repeat(10 - filled);
                Gizmos.billboardTextOverMob(p, row++, "❤ " + Math.round(hp) + "/" + Math.round(maxHp) + " " + bar,
                        hpColor, 0.85f);
            }
            if (EspSettings.playerArmor) {
                boolean head = equipped(p, EquipmentSlot.HEAD);
                boolean chest = equipped(p, EquipmentSlot.CHEST);
                boolean legs = equipped(p, EquipmentSlot.LEGS);
                boolean feet = equipped(p, EquipmentSlot.FEET);
                int pieces = (head ? 1 : 0) + (chest ? 1 : 0) + (legs ? 1 : 0) + (feet ? 1 : 0);
                String armor = "⛨ " + pieces + "/4  H:" + armorType(p, EquipmentSlot.HEAD)
                        + " C:" + armorType(p, EquipmentSlot.CHEST)
                        + " L:" + armorType(p, EquipmentSlot.LEGS)
                        + " B:" + armorType(p, EquipmentSlot.FEET);
                Gizmos.billboardTextOverMob(p, row++, armor, 0xFF9CCBFF, 0.78f);
            }
            if (EspSettings.playerDistance) {
                int distance = (int) Math.round(mc.player.distanceTo(p));
                Gizmos.billboardTextOverMob(p, row, "⌁ " + distance + " м  •  " + p.getName().getString(),
                        0xFFFFFFFF, 0.75f);
            }
        }
    }

    private static boolean equipped(AbstractClientPlayer p, EquipmentSlot slot) {
        return !p.getItemBySlot(slot).isEmpty();
    }

    private static String armorType(AbstractClientPlayer p, EquipmentSlot slot) {
        ItemStack item = p.getItemBySlot(slot);
        if (item.isEmpty()) return "-";
        String id = BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
        if (id.contains("netherite")) return "N";
        if (id.contains("diamond")) return "D";
        if (id.contains("iron")) return "I";
        if (id.contains("chainmail")) return "C";
        if (id.contains("gold")) return "G";
        if (id.contains("leather")) return "L";
        if (id.contains("turtle")) return "T";
        return "*";
    }

    /** Contrast outline + colored core; start in front of the eye so the near-plane doesn't clip it. */
    private static void drawTracer(Vec3 start, Vec3 end, int color, float width) {
        Gizmos.line(start, end, 0xB02A2238, width + 2.4f).setAlwaysOnTop();
        Gizmos.line(start, end, color, width).setAlwaysOnTop();
        Gizmos.point(end, color, 5.0f).setAlwaysOnTop();
    }

    private static void drawStorage(Minecraft mc, ClientLevel level) {
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        Vec3 eye = mc.player.getEyePosition();
        if (storageWorld != level) {
            storageWorld = level;
            storageTick = 0;
            scanCx = Integer.MIN_VALUE;
            scanCz = Integer.MIN_VALUE;
            nearbyStorage = List.of();
        }
        // A cheap position scan every 8 ticks, or instantly on chunk movement.
        if (storageTick++ % 8 == 0 || scanCx != pcx || scanCz != pcz) {
            scanCx = pcx;
            scanCz = pcz;
            List<net.minecraft.core.BlockPos> posList = new ArrayList<>();
            for (int cx = pcx - RANGE_CHUNKS; cx <= pcx + RANGE_CHUNKS; cx++) {
                for (int cz = pcz - RANGE_CHUNKS; cz <= pcz + RANGE_CHUNKS; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (colorFor(be) != 0) posList.add(be.getBlockPos().immutable());
                    }
                }
            }
            posList.sort(Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos))));
            nearbyStorage = posList.size() > 260 ? new ArrayList<>(posList.subList(0, 260)) : posList;
        }

        boolean lootOn = Features.LOOT.on();
        Vec3 lineStart = eye.add(mc.player.getLookAngle().scale(1.15));
        int tracers = 0;
        for (net.minecraft.core.BlockPos pos : nearbyStorage) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue; // never draw a cached box for an unloaded chunk
            BlockEntity be = chunk.getBlockEntity(pos);
            if (be == null) continue;
            int color = colorFor(be);
            if (color == 0) continue;
            if (lootOn && LootTracker.isLooted(pos)) color = 0xFF888392;
            Gizmos.cuboid(new AABB(pos).inflate(0.008), GizmoStyle.stroke(color, 2.2f))
                    .setAlwaysOnTop();
            Vec3 target = Vec3.atCenterOf(pos);
            if (EspSettings.storageTracer && tracers < 12 && eye.distanceToSqr(target) <= 64.0 * 64.0
                    && lineStart.distanceToSqr(target) > 1.0) {
                drawTracer(lineStart, target, color, 2.3f);
                tracers++;
            }
        }
    }

    /** ARGB-цвет рамки для типа блока, либо 0 — не подсвечивать. */
    private static int colorFor(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity)    return 0xFFFF2626; // красный
        if (be instanceof ChestBlockEntity)           return 0xFFFFA619; // оранжевый
        if (be instanceof EnderChestBlockEntity)      return EspSettings.storageEnder ? 0xFFB333FF : 0; // фиолетовый
        if (be instanceof BarrelBlockEntity)          return EspSettings.storageBarrels ? 0xFFBF8C50 : 0; // коричневый
        if (be instanceof ShulkerBoxBlockEntity shulker) {
            if (!EspSettings.storageShulkers) return 0;
            DyeColor dye = shulker.getColor();
            return dye != null ? (0xFF000000 | dye.getTextureDiffuseColor()) : 0xFF9966B3;
        }
        if (be instanceof HopperBlockEntity)          return 0xFF999999; // серый
        if (be instanceof DispenserBlockEntity)       return 0xFF4DCCFF; // голубой (и дроппер)
        if (be instanceof AbstractFurnaceBlockEntity) return 0xFFE6E64D; // жёлтый
        return 0;
    }
}
