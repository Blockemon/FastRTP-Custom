package me.wesley1808.fastrtp.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import me.lucko.fabric.api.permissions.v0.Permissions;
import me.wesley1808.fastrtp.config.Config;
import me.wesley1808.fastrtp.config.ConfigHandler;
import me.wesley1808.fastrtp.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.silkmc.silk.igui.Gui;
import net.silkmc.silk.igui.GuiKt;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.DimensionArgument.getDimension;
import static net.minecraft.commands.arguments.EntityArgument.getPlayer;
import static net.minecraft.commands.arguments.EntityArgument.player;

public final class RandomTeleportCommand {
    private static final TicketType<Integer> PRE_TELEPORT = TicketType.create("pre_teleport", Integer::compareTo, 70);
    private static final Object2ObjectOpenHashMap<UUID, Pair<ServerLevel, Vec3>> RTP_COORDS = new Object2ObjectOpenHashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("rtp")
            .requires(src -> !Config.instance().requirePermission || Permissions.check(src, Permission.COMMAND_RTP, 2))
            .executes(ctx -> {
                if (Config.instance().useRtpGui) {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Gui gui = new RtpGui().buildGui(player);
                    if (gui == null) return 0;

                    GuiKt.openGui(player, gui, 0);
                    return Command.SINGLE_SUCCESS;
                } else {
                    return execute(ctx.getSource());
                }
            })

            .then(argument("world", StringArgumentType.word())
                .suggests(new WorldSuggestionProvider())
                .executes(ctx -> {
                    String dimensionName = ctx.getArgument("world", String.class);
                    ServerLevel level = resolveDimension(ctx.getSource(), dimensionName);

                    if (level == null) {
                        ctx.getSource().sendFailure(Component.literal("Unknown or invalid world: " + dimensionName));
                        return 0;
                    }

                    return execute(ctx.getSource(), level);
                })

                .then(argument("player", player())
                    .requires(Permissions.require(Permission.COMMAND_RTP_ADVANCED, 2))
                    .executes(ctx -> execute(ctx.getSource(), getPlayer(ctx, "player")))

                    .then(argument("radius", integer(0))
                        .executes(ctx -> execute(ctx.getSource(), getPlayer(ctx, "player"), getDimension(ctx, "world"), getInteger(ctx, "radius")))

                        .then(argument("minRadius", integer(0))
                            .executes(ctx -> execute(ctx.getSource(), getPlayer(ctx, "player"), getDimension(ctx, "world"), getInteger(ctx, "radius"), getInteger(ctx, "minRadius")))
                        )
                    )
                )
            )

            .then(literal("reload")
                .requires(Permissions.require(Permission.COMMAND_RELOAD, 2))
                .executes(ctx -> reloadConfig(ctx.getSource()))
            )
        );

        if (Config.instance().rtpBackEnabled) {
            dispatcher.register(literal("rtpback")
                .requires(Permissions.require(Permission.COMMAND_RTP_BACK, true))
                .executes(ctx -> executeBack(ctx.getSource().getPlayerOrException()))
            );
        }
    }

    @Nullable
    private static ServerLevel resolveDimension(CommandSourceStack source, String dimensionName) {
        var server = source.getServer();

        Optional<ResourceKey<Level>> levelMatch = server.levelKeys().stream()
            .filter(levelKey -> Config.instance().dimensionNameSpaces.contains(levelKey.location().getNamespace())) // Only dimensions with namespaces in config
            .filter(levelKey -> levelKey.location().getPath().equalsIgnoreCase(dimensionName)) // Only dimensions with names that match the argument
            .filter(levelKey -> Permissions.check(
                source, Permission.COMMAND_RTP_DIMENSION + levelKey.location().toString().replace(":", "."), 2) // Only dimensions the player has permission for
            ).findFirst();

        return levelMatch.map(server::getLevel).orElse(null);
    }

    private static int execute(CommandSourceStack source) throws CommandSyntaxException {
        return execute(source, source.getPlayerOrException());
    }

    private static int execute(CommandSourceStack source, ServerLevel level) throws CommandSyntaxException {
        return execute(source, source.getPlayerOrException(), level);
    }

    private static int execute(CommandSourceStack source, ServerPlayer player) {
        return execute(source, player, Util.getLevel(player));
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level) {
        return execute(source, player, level, Util.getRadius(level));
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level, int radius) {
        return execute(source, player, level, radius, Config.instance().minRadius);
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level, int radius, int minRadius) {
        return execute(source, player, level, radius, minRadius, source.getPlayer() != player);
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, ServerLevel level, int radius, int minRadius, boolean force) {
        if (minRadius > radius) {
            source.sendFailure(Component.literal("The minimum radius cannot be larger than the maximum radius!"));
            return 0;
        }

        if (PositionLocator.isLocating(player) || !Scheduler.canSchedule(player.getUUID())) {
            // If the player is already locating a random position, don't start a new one.
            return 0;
        }

        Config.Messages messages = Config.instance().messages;
        if (!force && CooldownManager.hasCooldown(player.getUUID())) {
            String seconds = String.valueOf(CooldownManager.getCooldownInSeconds(player.getUUID()));
            player.displayClientMessage(Util.format(messages.rtpOnCooldown.replace("${seconds}", seconds)), false);
            return 0;
        }

        String error = force ? null : Util.mayTeleport(player);
        if (error != null) {
            player.sendSystemMessage(Util.format(messages.preventedRtp.replace("${reason}", error)));
            return 0;
        }

        player.displayClientMessage(Util.format(messages.rtpStartSearch), true);
        CooldownManager.addCooldown(player);

        long startTime = System.currentTimeMillis();
        PositionLocator locator = new PositionLocator(level, player.getUUID(), radius, minRadius);

        locator.findPosition((pos) -> {
            if (pos == null) {
                player.sendSystemMessage(Util.format(messages.rtpLocNotFound));
                CooldownManager.removeCooldown(player.getUUID());
            } else {
                long elapsedTime = System.currentTimeMillis() - startTime;
                player.displayClientMessage(Util.format(messages.rtpLocFound.replace("${seconds}", String.format("%.1f", elapsedTime / 1000F))), true);

                if (force || !Config.instance().useStrictTeleportCheck) {
                    teleportPlayer(player, level, pos);
                } else {
                    Scheduler.scheduleTeleport(player,
                            () -> teleportPlayer(player, level, pos),
                            () -> player.displayClientMessage(Util.format(messages.tpCancelled), false)
                    );
                }
            }
        });

        return 1;
    }

    private static void teleportPlayer(ServerPlayer player, ServerLevel level, Vec3 pos) {
        if (player.isAlive()) {
            player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
            player.connection.resetPosition();
            RTP_COORDS.put(player.getUUID(), new ObjectObjectImmutablePair<>(level, pos));

            player.sendSystemMessage(Util.format(Config.instance().messages.rtpTeleportPlayer
                    .replace("${x}", String.format("%.0f", pos.x))
                    .replace("${y}", String.format("%.0f", pos.y))
                    .replace("${z}", String.format("%.0f", pos.z))
                    .replace("${world}", level.dimension().location().getPath())
            ));
        }
    }

    private static int executeBack(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!Scheduler.canSchedule(uuid)) {
            return 0;
        }

        Config.Messages messages = Config.instance().messages;
        Pair<ServerLevel, Vec3> pair = RTP_COORDS.get(uuid);
        if (pair == null) {
            player.sendSystemMessage(Util.format(messages.rtpBackLocNotFound));
            return 0;
        }

        String error = Util.mayTeleport(player);
        if (error != null) {
            player.sendSystemMessage(Util.format(messages.preventedRtpBack.replace("${reason}", error)));
            return 0;
        }

        Vec3 pos = pair.right();
        ServerLevel level = pair.left();

        level.getChunkSource().addRegionTicket(PRE_TELEPORT, new ChunkPos(BlockPos.containing(pos)), 1, player.getId());
        Scheduler.scheduleTeleport(player, () -> {
            player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
            player.connection.resetPosition();
            player.sendSystemMessage(Util.format(messages.rtpBackSuccess));
        }, () -> {
            player.displayClientMessage(Util.format(messages.tpCancelled), false);
        });

        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        ConfigHandler.load();
        source.sendSuccess(() -> Component.literal("Config reloaded!").withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }
}