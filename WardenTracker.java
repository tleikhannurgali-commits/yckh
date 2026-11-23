package com.example.storageesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers timers the client has actually observed near a dungeon chest.
 * NEVER assumes "no timer loaded" means a chest is open. Client-side expiry
 * is approximate and is corrected whenever another server timer is observed.
 */
public final class WardenTracker {
    private static final Pattern TIMER = Pattern.compile("(?<!\\d)(\\d{1,2}):([0-5]\\d)(?!\\d)");
    private static final int RANGE_CHUNKS = 4;
    private static final double RANGE_SQ = 64.0 * 64.0;
    private static final int UNKNOWN_COLOR = 0xFF9C94AD;
    private static final int TIMER_COLOR = 0xFFFF525C;
    private static final int READY_COLOR = 0xFF43DF86;

    private record Timer(long endsAtMs, long observedAtMs) {}
    private static final Map<Long, Timer> timers = new HashMap<>();
    private static Object lastLevel;
    private static int tick;
    private static List<BlockPos> loadedChests = List.of();

    public static void draw(Minecraft mc, ClientLevel level) {
        if (lastLevel != level) {
            timers.clear();
            loadedChests = List.of();
            lastLevel = level;
            tick = 0;
        }
        if (mc.player == null) return;
        long now = System.currentTimeMillis();

        // Scanning every 5 ticks is enough for a visible MM:SS hologram.
        if (++tick % 5 == 1) {
            loadedChests = scanLoadedChests(mc, level);
            observeHolograms(level, loadedChests, now);
            // Bound cache size across exploration; old timers are not reliable indefinitely.
            timers.entrySet().removeIf(e -> now - e.getValue().observedAtMs() > 60L * 60L * 1000L);
        }
        for (BlockPos pos : loadedChests) {
            Timer timer = timers.get(pos.asLong());
            int color;
            String label;
            float thickness;
            if (timer == null) {
                color = UNKNOWN_COLOR;
                label = null; // no status guess when a timer hasn't been received
                thickness = 1.3f;
            } else {
                long ms = Math.max(0, timer.endsAtMs() - now);
                if (ms > 0) {
                    color = TIMER_COLOR;
                    long seconds = (ms + 999) / 1000;
                    label = "Ожидание " + String.format("%02d:%02d", seconds / 60, seconds % 60);
                } else {
                    color = READY_COLOR;
                    label = "Таймер истёк (проверь)";
                }
                thickness = 3.0f;
            }
            Gizmos.cuboid(new AABB(pos).inflate(0.015), GizmoStyle.stroke(color, thickness)).setAlwaysOnTop();
            if (label != null) Gizmos.billboardTextOverBlock(label, pos, 0, color, 0.85f);
        }
    }

    private static List<BlockPos> scanLoadedChests(Minecraft mc, ClientLevel level) {
        List<BlockPos> result = new ArrayList<>();
        int cx0 = mc.player.blockPosition().getX() >> 4;
        int cz0 = mc.player.blockPosition().getZ() >> 4;
        Vec3 me = mc.player.position();
        for (int cx = cx0 - RANGE_CHUNKS; cx <= cx0 + RANGE_CHUNKS; cx++) {
            for (int cz = cz0 - RANGE_CHUNKS; cz <= cz0 + RANGE_CHUNKS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ChestBlockEntity &&
                            be.getBlockPos().distToCenterSqr(me.x, me.y, me.z) <= RANGE_SQ) {
                        result.add(be.getBlockPos().immutable());
                    }
                }
            }
        }
        return result;
    }

    private static void observeHolograms(ClientLevel level, List<BlockPos> chests, long now) {
        for (Entity e : level.entitiesForRendering()) {
            if (e.isRemoved()) continue;
            String text = "";
            Component custom = e.getCustomName();
            if (custom != null) text = custom.getString();
            if (e instanceof Display.TextDisplay display) {
                Display.TextDisplay.TextRenderState state = display.textRenderState();
                if (state != null && state.text() != null) {
                    text = text + " " + state.text().getString();
                }
            }
            Matcher match = TIMER.matcher(text);
            if (!match.find()) continue;
            int seconds = Integer.parseInt(match.group(1)) * 60 + Integer.parseInt(match.group(2));
            Vec3 holo = e.position();
            BlockPos closest = null;
            double best = Double.MAX_VALUE;
            for (BlockPos c : chests) {
                double dx = holo.x - (c.getX() + 0.5);
                double dz = holo.z - (c.getZ() + 0.5);
                double dy = holo.y - c.getY();
                double flat = dx * dx + dz * dz;
                if (dy >= -0.5 && dy <= 4.0 && flat <= 2.0 * 2.0 && flat < best) {
                    closest = c;
                    best = flat;
                }
            }
            if (closest == null) continue;
            long deadline = now + seconds * 1000L;
            Timer prior = timers.get(closest.asLong());
            // Ignore harmless timer tick drift; react to actual server resets.
            if (prior == null || Math.abs(prior.endsAtMs() - deadline) > 1800L) {
                timers.put(closest.asLong(), new Timer(deadline, now));
            } else {
                timers.put(closest.asLong(), new Timer(prior.endsAtMs(), now));
            }
        }
    }
}
