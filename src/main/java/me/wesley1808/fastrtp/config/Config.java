package me.wesley1808.fastrtp.config;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import me.wesley1808.fastrtp.FastRTP;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.List;
import java.util.Set;

public class Config {
    protected static Config instance = new Config();
    public boolean requirePermission = false;
    public boolean rtpBackEnabled = true;
    public boolean useCurrentWorld = false;
    public boolean useStrictTeleportCheck = false;
    public String defaultDimension = "minecraft:overworld";
    public Object2ObjectOpenHashMap<String, String> dimensionRedirects = new Object2ObjectOpenHashMap<>();
    public int radius = -1;
    public int minRadius = 0;
    public int safetyCheckRadius = 1;
    private final List<Integer> defaultCooldown = List.of(30);
    public Object cooldown = defaultCooldown;

    public ReferenceOpenHashSet<ResourceKey<Biome>> blackListedBiomes = new ReferenceOpenHashSet<>(Set.of(
            Biomes.THE_END,
            Biomes.SMALL_END_ISLANDS,
            Biomes.THE_VOID
    ));

    public ReferenceArrayList<TagKey<Biome>> blackListedBiomeTags = new ReferenceArrayList<>(List.of(
            BiomeTags.IS_BEACH,
            BiomeTags.IS_OCEAN,
            BiomeTags.IS_DEEP_OCEAN,
            BiomeTags.IS_RIVER
    ));

    public Messages messages = new Messages();

    public List<Integer> getCooldowns() {
        if (cooldown instanceof Integer) {
            return List.of((Integer) cooldown);
        } else {
            try {
                return (List<Integer>) cooldown;
            } catch (ClassCastException e) {
                FastRTP.LOGGER.error("Invalid cooldown format in config! Using default cooldown.");
            }
        }

        return defaultCooldown;
    }

    public static Config instance() {
        return instance;
    }

    public static class Messages {
        public String rtpStartSearch = "<yellow>Searching for a safe location...";
        public String rtpLocFound = "<yellow>Found a safe location in ${seconds} seconds";
        public String rtpTeleportPlayer = "<dark_aqua>Teleported to <green>${x} ${y} ${z} <dark_aqua>in <green>${world}";
        public String rtpLocNotFound = "<red>[✖] Could not find a safe location!";
        public String rtpOnCooldown = "<red>[✖] <gold>Please wait <yellow>${seconds} <gold>seconds before using the RTP again!";
        public String preventedRtp = "<red>[✖] Could not start random teleport.\nReason: ${reason}";

        public String rtpBackSuccess = "<dark_aqua>Teleported back to your last random teleport!";
        public String rtpBackLocNotFound = "<red>[✖] You don't have any recent random teleports.";
        public String preventedRtpBack = "<red>[✖] Unable to teleport back to your last RTP location.\nReason: ${reason}";

        public String tpSecondsLeft = "<yellow>Teleporting in ${seconds} seconds...";
        public String tpCancelled = "<red>[✖] Teleportation was cancelled.";
    }
}
