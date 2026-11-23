package com.example.storageesp;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Следы активности: помечает места, где ты слышал "строительные" звуки —
 * поршни, раздатчики, сундуки, бочки, шалкеры, двери, воронки. Метка живёт 30 секунд.
 */
public class TracesTracker {

    private record Trace(Vec3 pos, long createdMs) {}

    private static final long TTL_MS = 30_000;
    private static final String[] KEYWORDS = {
            "piston", "dispenser", "chest", "barrel", "shulker_box", "door", "hopper", "anvil"
    };
    private static final List<Trace> traces = new CopyOnWriteArrayList<>();

    public static void onSound(PlaySoundEvent event) {
        if (!Features.TRACES.on()) return;
        var sound = event.getSound();
        String name = event.getName();
        if (sound == null || name == null) return;

        String lower = name.toLowerCase(Locale.ROOT);
        boolean match = false;
        for (String k : KEYWORDS) {
            if (lower.contains(k)) { match = true; break; }
        }
        if (!match) return;

        Vec3 pos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        Minecraft mc = Minecraft.getInstance();
        // Свои звуки не считаем
        if (mc.player != null && mc.player.position().distanceToSqr(pos) < 6.25) return;
        traces.add(new Trace(pos, System.currentTimeMillis()));
    }

    public static List<Vec3> positions() {
        return traces.stream().map(Trace::pos).toList();
    }

    public static void draw() {
        long now = System.currentTimeMillis();
        traces.removeIf(t -> now - t.createdMs() > TTL_MS);
        for (Trace t : traces) {
            Vec3 p = t.pos();
            AABB box = new AABB(p.x - 0.3, p.y - 0.3, p.z - 0.3, p.x + 0.3, p.y + 0.3, p.z + 0.3);
            Gizmos.cuboid(box, GizmoStyle.stroke(0xFFFF44FF, 3.0f)).setAlwaysOnTop();
        }
    }
}
