package me.wesley1808.fastrtp.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import eu.pb4.placeholders.api.TextParserUtils;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import me.wesley1808.fastrtp.config.Config;
import me.wesley1808.fastrtp.mixins.ServerChunkCacheAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Util {

    @Nullable
    public static LevelChunk getChunkIfLoaded(ServerLevel level, int chunkX, int chunkZ) {
        final ChunkHolder holder = getChunkHolder(level.getChunkSource(), chunkX, chunkZ);
        return holder != null ? holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).orElse(null) : null;
    }

    @Nullable
    private static ChunkHolder getChunkHolder(ServerChunkCache chunkCache, int chunkX, int chunkZ) {
        ServerChunkCacheAccessor accessor = (ServerChunkCacheAccessor) chunkCache;
        return accessor.getHolder(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static Component format(String string) {
        return TextParserUtils.formatTextSafe(string);
    }

    public static ServerLevel getLevel(ServerPlayer player) {
        ServerLevel currentLevel = player.serverLevel();

        ServerLevel redirect = parseLevel(player.server, Config.instance().dimensionRedirects.get(currentLevel.dimension().location().toString()));
        if (redirect != null) {
            return redirect;
        }

        if (Config.instance().useCurrentDimension) {
            return currentLevel;
        }

        ServerLevel defaultLevel = parseLevel(player.server, Config.instance().defaultDimension);
        return defaultLevel != null ? defaultLevel : currentLevel;
    }

    @Nullable
    public static ServerLevel parseLevel(MinecraftServer server, @Nullable String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return null;
        }

        ResourceLocation location = ResourceLocation.tryParse(dimension);
        if (location == null) {
            return null;
        }

        return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    public static int getRadius(ServerLevel level) {
        final int borderRadius = (int) (level.getWorldBorder().getSize() / 2) - 16;
        final int radius = Config.instance().radius;
        if (radius < 0) {
            return borderRadius;
        }

        return Math.min(borderRadius, radius);
    }

    @Nullable
    public static String mayTeleport(ServerPlayer player) {
        if (player.gameMode.isSurvival() && Config.instance().useStrictTeleportCheck) {
            if (player.hasEffect(MobEffects.LEVITATION)) {
                return "Levitation Effect";
            }

            if (player.hasEffect(MobEffects.DARKNESS)) {
                return "Darkness Effect";
            }

            List<Monster> monsters = player.level().getEntities(EntityTypeTest.forClass(Monster.class), player.getBoundingBox().inflate(64D), EntitySelector.NO_SPECTATORS);
            for (Monster monster : monsters) {
                if (isTargeted(player, monster)) {
                    if (monster instanceof Warden) {
                        return "Hunted by warden";
                    }

                    float distance = player.distanceTo(monster);
                    if (distance < 24 && monster.getSensing().hasLineOfSight(player)) {
                        return String.format("Hunted by %s (%.0f blocks away)", EntityType.getKey(monster.getType()).getPath(), distance);
                    }
                }
            }
        }

        return null;
    }

    public static boolean isTargeted(ServerPlayer target, Mob mob) {
        boolean isTargeted = mob.getTarget() == target;

        // Check the memory for mobs that don't use the target selector.
        if (!isTargeted) {
            Brain<?> brain = mob.getBrain();
            MemoryModuleType<?> module = MemoryModuleType.ATTACK_TARGET;
            isTargeted = brain.hasMemoryValue(module) && brain.getMemory(module).orElse(null) == target;
        }

        // Check if the mob is a warden that is sniffing out the player.
        if (!isTargeted && mob instanceof Warden warden) {
            isTargeted = warden.getAngerManagement().getActiveEntity().orElse(null) == target;
        }

        return isTargeted;
    }

    // Creates a Player Head ItemStack with the texture from the given textureUrl
    public static ItemStack createCustomHead(String textureUrl, String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);

        GameProfile profile = new GameProfile(UUID.randomUUID(), "");
        PropertyMap propertyMap = profile.getProperties();
        String base64Texture = Base64.getEncoder().encodeToString(
            ("{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}").getBytes()
        );
        propertyMap.put("textures", new Property("textures", base64Texture));

        head.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        head.set(DataComponents.ITEM_NAME, Component.literal(name));
        head.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Click to teleport"), Component.literal("somewhere random"))));

        return head;
    }

    public static BooleanList getItemDistribution(int nItems) {
        int nRows = (int) Math.ceil((double) nItems / 9); // Calculate rows needed
        int[] itemsPerRow = new int[nRows];

        // Calculate how many items should be in each row
        Arrays.fill(itemsPerRow, nItems / nRows);
        int remainder = nItems % nRows;
        for (int i = 0; i < remainder; i++) {
            itemsPerRow[i]++;
        }

        // Build a list of all row distributions
        BooleanList distribution = new BooleanArrayList();
        for (int itemsInRow : itemsPerRow) {
            distribution.addAll(getRowBooleans(itemsInRow));
        }

        return distribution;
    }

    private static @NotNull BooleanList getRowBooleans(int itemsInRow) {
        BooleanList row = new BooleanArrayList(new ArrayList<>(Collections.nCopies(9, false))); // 9 slots per row in an inventory GUI

        if (itemsInRow > 0) {
            double interval = (double) 9 / itemsInRow;
            for (int i = 0; i < itemsInRow; i++) {
                int index = (int) Math.round(i * interval + interval / 2 - 0.5); // Centered placement
                row.set(index, true);
            }
        }

        return row;
    }
}
