package com.example.storageesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Карта пустот: ищет под землёй ровные тоннели 1x2, которые сами по себе почти не образуются
 * (шахты, ходы игроков). Подозрительными считаются чанки, где найдено 2+ прямых участка длиной от 8 блоков.
 * Сканируется по одному чанку за тик, чтобы не просаживать FPS.
 */
public class VoidMapScanner {

    private static final int RADIUS = 5;
    private static final int MIN_RUN = 8;
    private static final int MIN_RUNS_PER_CHUNK = 2;
    private static final int MAX_BOXES_PER_CHUNK = 60;
    private static final int COLOR = 0xFFFF8800;

    private static final Map<Long, List<AABB>> results = new HashMap<>();
    private static int cursor;
    private static Object lastLevel;

    public static void tick(Minecraft mc, ClientLevel level) {
        if (lastLevel != level) {
            results.clear();
            lastLevel = level;
        }
        int side = RADIUS * 2 + 1;
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int py = mc.player.blockPosition().getY();

        // Один чанк за тик по кругу
        cursor = (cursor + 1) % (side * side);
        int cx = pcx - RADIUS + cursor % side;
        int cz = pcz - RADIUS + cursor / side;
        long key = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
        if (level.getChunkSource().getChunkNow(cx, cz) != null) {
            List<AABB> boxes = scanChunk(level, cx, cz, py - 48, py + 8);
            if (boxes.size() >= MIN_RUNS_PER_CHUNK) results.put(key, boxes);
            else results.remove(key);
        }

        // Чистим то, что далеко
        results.keySet().removeIf(k -> {
            int kx = (int) (k >> 32);
            int kz = (int) (long) k;
            return Math.abs(kx - pcx) > RADIUS + 1 || Math.abs(kz - pcz) > RADIUS + 1;
        });

        for (List<AABB> boxes : results.values()) {
            for (AABB b : boxes) {
                Gizmos.cuboid(b, GizmoStyle.stroke(COLOR, 2.0f)).setAlwaysOnTop();
            }
        }
    }

    private static List<AABB> scanChunk(ClientLevel level, int cx, int cz, int yMin, int yMax) {
        List<AABB> boxes = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int bx0 = cx << 4;
        int bz0 = cz << 4;
        for (int y = yMin; y <= yMax && boxes.size() < MAX_BOXES_PER_CHUNK; y++) {
            // Тоннели вдоль X
            for (int z = 0; z < 16; z++) {
                int run = 0;
                int start = 0;
                for (int x = 0; x < 16; x++) {
                    if (tunnelCell(level, p, bx0 + x, y, bz0 + z, true)) {
                        if (run == 0) start = bx0 + x;
                        run++;
                    } else {
                        if (run >= MIN_RUN) boxes.add(new AABB(start, y, bz0 + z, start + run, y + 2, bz0 + z + 1));
                        run = 0;
                    }
                }
                if (run >= MIN_RUN) boxes.add(new AABB(start, y, bz0 + z, start + run, y + 2, bz0 + z + 1));
            }
            // Тоннели вдоль Z
            for (int x = 0; x < 16; x++) {
                int run = 0;
                int start = 0;
                for (int z = 0; z < 16; z++) {
                    if (tunnelCell(level, p, bx0 + x, y, bz0 + z, false)) {
                        if (run == 0) start = bz0 + z;
                        run++;
                    } else {
                        if (run >= MIN_RUN) boxes.add(new AABB(bx0 + x, y, start, bx0 + x + 1, y + 2, start + run));
                        run = 0;
                    }
                }
                if (run >= MIN_RUN) boxes.add(new AABB(bx0 + x, y, start, bx0 + x + 1, y + 2, start + run));
            }
        }
        return boxes;
    }

    /** Клетка тоннеля 1x2: два воздуха, сверху/снизу/по бокам сплошные блоки. */
    private static boolean tunnelCell(ClientLevel level, BlockPos.MutableBlockPos p, int x, int y, int z, boolean alongX) {
        if (!air(level, p, x, y, z) || !air(level, p, x, y + 1, z)) return false;
        if (!solid(level, p, x, y - 1, z) || !solid(level, p, x, y + 2, z)) return false;
        if (alongX) {
            return solid(level, p, x, y, z - 1) && solid(level, p, x, y, z + 1)
                    && solid(level, p, x, y + 1, z - 1) && solid(level, p, x, y + 1, z + 1);
        }
        return solid(level, p, x - 1, y, z) && solid(level, p, x + 1, y, z)
                && solid(level, p, x - 1, y + 1, z) && solid(level, p, x + 1, y + 1, z);
    }

    private static boolean air(ClientLevel level, BlockPos.MutableBlockPos p, int x, int y, int z) {
        p.set(x, y, z);
        return level.getBlockState(p).isAir();
    }

    private static boolean solid(ClientLevel level, BlockPos.MutableBlockPos p, int x, int y, int z) {
        p.set(x, y, z);
        BlockState s = level.getBlockState(p);
        return !s.isAir() && s.isCollisionShapeFullBlock(level, p);
    }
}
