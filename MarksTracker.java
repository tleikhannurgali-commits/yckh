package com.example.storageesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-observed locations. A disappearing player is NOT assumed dead:
 * teleport/unload and death are indistinguishable without a death event.
 */
public final class MarksTracker {
    public record Mark(String label, Vec3 pos, int color) {}
    public record Ghost(String name, Vec3 pos, long lastSeenMs) {}

    private static final long GHOST_TTL_MS = 10_000L;
    private static final int MAX_MARKS = 96;
    private static final List<Mark> marks = new ArrayList<>();
    private static final Map<UUID, Ghost> seen = new HashMap<>();
    private static final List<Ghost> activeGhosts = new ArrayList<>();
    private static final Set<UUID> deathRecorded = new HashSet<>();
    private static boolean localDeathRecorded;
    private static Object lastLevel;

    public static List<Mark> marks() { return marks; }
    public static List<Ghost> ghosts() { return activeGhosts; }

    public static void clear() {
        marks.clear();
        seen.clear();
        activeGhosts.clear();
        deathRecorded.clear();
        localDeathRecorded = false;
    }

    public static void addMark(Minecraft mc) {
        if (mc.player == null) return;
        Vec3 pos = mc.player.position();
        add("Метка " + (marks.size() + 1), pos, 0xFFFFDB56);
        mc.player.displayClientMessage(Component.literal("Метка: " + coords(pos)), true);
    }

    private static void add(String label, Vec3 pos, int color) {
        if (marks.size() >= MAX_MARKS) marks.remove(0);
        marks.add(new Mark(label, pos, color));
    }

    private static String coords(Vec3 v) {
        return (int) Math.floor(v.x) + " " + (int) Math.floor(v.y) + " " + (int) Math.floor(v.z);
    }

    public static void tick(Minecraft mc, ClientLevel level) {
        if (lastLevel != level) {
            clear();
            lastLevel = level;
        }

        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        boolean localDead = mc.player.isDeadOrDying();
        if (localDead && !localDeathRecorded) {
            add("Моя смерть", mc.player.position(), 0xFFFF5F75);
        }
        localDeathRecorded = localDead;

        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayer p : level.players()) {
            if (p == mc.player || p.isRemoved() || level.getEntity(p.getId()) != p) continue;
            UUID id = p.getUUID();
            present.add(id);
            if (p.isDeadOrDying()) {
                if (EspSettings.markOtherDeaths && deathRecorded.add(id)) {
                    add("Смерть: " + p.getName().getString(), p.position(), 0xFFFF5F75);
                }
                seen.remove(id);
                continue;
            }
            deathRecorded.remove(id);
            seen.put(id, new Ghost(p.getName().getString(), p.position(), now));
        }

        seen.entrySet().removeIf(e -> now - e.getValue().lastSeenMs() > GHOST_TTL_MS);
        activeGhosts.clear();
        if (EspSettings.markGhosts) {
            for (Map.Entry<UUID, Ghost> entry : seen.entrySet()) {
                if (!present.contains(entry.getKey())) activeGhosts.add(entry.getValue());
            }
        }

        for (Mark m : marks) {
            drawBeam(m.pos(), m.color(), m.label() + "  [" + coords(m.pos()) + "]");
        }
        for (Ghost g : activeGhosts) {
            // Distinct ghost rendering: cyan, short, labeled LAST SEEN, never a player-sized ESP box.
            drawBeam(g.pos(), 0xFF45C8F2, "Последний сигнал: " + g.name());
        }
    }

    private static void drawBeam(Vec3 pos, int color, String caption) {
        AABB beam = new AABB(pos.x - 0.12, pos.y, pos.z - 0.12,
                pos.x + 0.12, pos.y + 8.0, pos.z + 0.12);
        Gizmos.cuboid(beam, GizmoStyle.stroke(color, 1.6f)).setAlwaysOnTop();
        Gizmos.billboardTextOverBlock(caption, BlockPos.containing(pos), 0, color, 0.8f);
    }
}
