package com.example.storageesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashSet;
import java.util.Set;

/** Лут-трекер: запоминает сундуки, которые ты уже открывал, чтобы подсвечивать их серым. */
public class LootTracker {

    private static final Set<Long> looted = new HashSet<>();
    private static boolean wasOpen;
    private static Object lastLevel;

    public static boolean isLooted(BlockPos pos) {
        return looted.contains(pos.asLong());
    }

    public static void clear() {
        looted.clear();
    }

    public static void tick(Minecraft mc, ClientLevel level) {
        if (lastLevel != level) {
            looted.clear();
            lastLevel = level;
            wasOpen = false;
        }
        Screen s = mc.screen;
        boolean open = s instanceof ContainerScreen || s instanceof ShulkerBoxScreen;
        if (open && !wasOpen && mc.hitResult instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            looted.add(pos.asLong());
            // Двойной сундук: помечаем и вторую половину
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = pos.relative(d);
                    if (level.getBlockEntity(n) instanceof ChestBlockEntity) looted.add(n.asLong());
                }
            }
        }
        wasOpen = open;
    }
}
