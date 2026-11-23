package com.example.storageesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.DeltaTracker;

/** HUD-радар в правом верхнем углу: игроки (красные/зелёные), призраки (голубые), метки (жёлтые), следы (розовые). */
public class RadarHud {

    private static final int SIZE = 100;
    private static final double RANGE = 64.0;

    public static void onRender(GuiGraphics g, DeltaTracker deltaTracker) {
        if (!Features.RADAR.on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        int half = SIZE / 2;
        int cx = g.guiWidth() - half - 10;
        int cy = half + 10;

        // Фон, рамка, перекрестие
        g.fill(cx - half, cy - half, cx + half, cy + half, 0x88000000);
        g.fill(cx - half, cy - half, cx + half, cy - half + 1, 0xFFFFFFFF);
        g.fill(cx - half, cy + half - 1, cx + half, cy + half, 0xFFFFFFFF);
        g.fill(cx - half, cy - half, cx - half + 1, cy + half, 0xFFFFFFFF);
        g.fill(cx + half - 1, cy - half, cx + half, cy + half, 0xFFFFFFFF);
        g.fill(cx, cy - half, cx + 1, cy + half, 0x44FFFFFF);
        g.fill(cx - half, cy, cx + half, cy + 1, 0x44FFFFFF);

        Vec3 me = mc.player.position();
        double yaw = Math.toRadians(mc.player.getYRot());

        for (var p : mc.level.players()) {
            if (p == mc.player || p.isRemoved() || !p.isAlive() || mc.level.getEntity(p.getId()) != p) continue;
            int color = p.isAlliedTo(mc.player) ? 0xFF55FF55 : 0xFFFF3333;
            plot(g, cx, cy, half, p.position().subtract(me), yaw, color);
        }
        for (var ghost : MarksTracker.ghosts()) {
            plot(g, cx, cy, half, ghost.pos().subtract(me), yaw, 0xFF33DDFF);
        }
        for (var mark : MarksTracker.marks()) {
            plot(g, cx, cy, half, mark.pos().subtract(me), yaw, mark.color());
        }
        if (Features.TRACES.on()) {
            for (Vec3 t : TracesTracker.positions()) {
                plot(g, cx, cy, half, t.subtract(me), yaw, 0xFFFF44FF);
            }
        }

        // Ты — белая точка в центре
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
    }

    /** Рисует точку: вверх на радаре = направление взгляда. Дальние точки прижимаются к краю. */
    private static void plot(GuiGraphics g, int cx, int cy, int half, Vec3 d, double yaw, int color) {
        // forward = (-sin, cos), right = (-cos, -sin) в координатах (x, z)
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double right = d.x * rx + d.z * rz;
        double forward = d.x * fx + d.z * fz;

        double sx = right * half / RANGE;
        double sy = -forward * half / RANGE;
        double len = Math.max(Math.abs(sx), Math.abs(sy));
        double limit = half - 3;
        if (len > limit) {
            sx *= limit / len;
            sy *= limit / len;
        }
        int x = cx + (int) Math.round(sx);
        int y = cy + (int) Math.round(sy);
        g.fill(x - 1, y - 1, x + 2, y + 2, color);
    }
}
