package me.wesley1808.fastrtp.util;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import me.lucko.fabric.api.permissions.v0.Permissions;
import me.wesley1808.fastrtp.config.Config;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CooldownManager {
    private static final Object2LongOpenHashMap<UUID> COOLDOWNS = new Object2LongOpenHashMap<>();

    public static long getCooldownInSeconds(UUID uuid) {
        return (COOLDOWNS.getLong(uuid) - System.currentTimeMillis()) / 1000;
    }

    public static boolean hasCooldown(UUID uuid) {
        long time = COOLDOWNS.getLong(uuid);
        if (time == 0L) return false;

        if (System.currentTimeMillis() > time - 1000) {
            COOLDOWNS.removeLong(uuid);
            return false;
        }

        return true;
    }

    public static void addCooldown(ServerPlayer player) {
        if (!Permissions.check(player, Permission.BYPASS_COOLDOWN, 2)) return;

        List<Integer> cooldowns = Config.instance().cooldowns;

        if (cooldowns.size() == 1 && cooldowns.getFirst() <= 0) return;

        int cooldown = Collections.max(cooldowns);

        if (cooldown <= 0) return;

        for (int value : cooldowns) {
            String permission = Permission.COOLDOWN + value;
            if (Permissions.check(player, permission)) {
                cooldown = Math.min(cooldown, value);
            }
        }

        COOLDOWNS.put(player.getUUID(), System.currentTimeMillis() + (cooldown * 1000L));
    }

    public static void removeCooldown(UUID uuid) {
        COOLDOWNS.removeLong(uuid);
    }
}
