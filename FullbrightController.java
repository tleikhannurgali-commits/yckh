package com.example.storageesp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Client-only stable night-vision lighting (long duration prevents pulsing at <200 ticks). No commands, server effects, or permanent gamma edits. */
public final class FullbrightController {
    private FullbrightController() {}

    private static boolean appliedByUs;
    private static Object lastPlayer;

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            appliedByUs = false;
            lastPlayer = null;
            return;
        }
        if (lastPlayer != mc.player) {
            appliedByUs = false;
            lastPlayer = mc.player;
        }
        MobEffectInstance current = mc.player.getEffect(MobEffects.NIGHT_VISION);
        if (Features.FULLBRIGHT.on()) {
            // Leave genuine night-vision effects intact; only refresh ours when necessary.
            if (current == null || (appliedByUs && current.getDuration() < 100)) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 30000, 0, false, false, false));
                appliedByUs = true;
            }
        } else if (appliedByUs) {
            // We remove only an effect that this client controller put on the player.
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            appliedByUs = false;
        }
    }
}
