package me.wesley1808.fastrtp.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import kotlin.Unit;
import me.lucko.fabric.api.permissions.v0.Permissions;
import me.wesley1808.fastrtp.FastRTP;
import me.wesley1808.fastrtp.config.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.silkmc.silk.igui.Gui;
import net.silkmc.silk.igui.GuiBuilderKt;
import net.silkmc.silk.igui.GuiIcon;
import net.silkmc.silk.igui.GuiSlot;
import net.silkmc.silk.igui.GuiType;
import org.apache.commons.lang3.text.WordUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RtpGui {

    @Nullable
    public Gui buildGui(ServerPlayer player) {
        if (player.getServer() == null) return null;

        List<String> dimensions = player.getServer().levelKeys().stream().map(key -> key.location().toString())
            .filter(dimension -> !Config.instance().blackListedGuiDimensions.contains(dimension))
            .filter(dimension -> Permissions.check(player, Permission.COMMAND_RTP_DIMENSION + dimension.replace(":", "."), 2))
            .limit(54).toList();

        if (dimensions.isEmpty()) return null;

        Config config = Config.instance();
        List<String> unspecifiedDimensions = new ArrayList<>(dimensions);
        unspecifiedDimensions.removeAll(config.dimensionHeadTextures.keySet());

        ObjectArrayList<ObjectObjectImmutablePair<String, String>> dimensionIconList = generateDimensionIcons(
            Util.getItemDistribution(dimensions.size()),
            new Object2ObjectLinkedOpenHashMap<>(config.dimensionHeadTextures),
            unspecifiedDimensions,
            config.defaultDimensionHeadTexture
        );

        int numRows = Math.min(dimensionIconList.size() / 9, 6);
        GuiType guiType = GuiType.getEntries().get(numRows - 1);

        return GuiBuilderKt.igui(guiType, Component.literal("Available Worlds"), 0, guiBuilder -> {
            guiBuilder.page(0, 0, builder -> {
                int slotCounter = 1;
                for (ObjectObjectImmutablePair<String, String> dimensionIconPair : dimensionIconList) {
                    String dimension = dimensionIconPair.left();
                    GuiSlot slot = new GuiSlot(1, slotCounter++);
                    if (dimension == null) {
                        builder.placeholder(slot, new GuiIcon.StaticIcon(ItemStack.EMPTY));
                    } else {
                        String texture = dimensionIconPair.right();
                        String dimensionName = dimension.split(":")[1];
                        String name = WordUtils.capitalize(dimensionName.replace("_", " "));
                        builder.button(slot, new GuiIcon.StaticIcon(Util.createCustomHead(texture, name)), (clickEvent, _unused) -> {
                            try {
                                player.getServer().getCommands().getDispatcher().execute("rtp " + dimensionName, player.createCommandSourceStack());
                                clickEvent.getGui().closeForViewers();
                            } catch (CommandSyntaxException e) {
                                FastRTP.LOGGER.warn("RTP command via GUI failed: {}", e.getMessage());
                            }
                            return Unit.INSTANCE;
                        });
                    }
                }
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }

    private ObjectArrayList<ObjectObjectImmutablePair<String, String>> generateDimensionIcons(
        BooleanList distribution,
        Object2ObjectLinkedOpenHashMap<String, String> configuredDimensionTextures,
        List<String> unspecifiedDimensions,
        String defaultTexture
    ) {
        ObjectArrayList<ObjectObjectImmutablePair<String, String>> slotIcons = new ObjectArrayList<>();

        for (Boolean slotFlag : distribution) {
            ObjectObjectImmutablePair<String, String> iconPair;
            if (!slotFlag) {
                iconPair = ObjectObjectImmutablePair.of(null, null);
            } else {
                if (!configuredDimensionTextures.isEmpty()) {
                    // Get and remove the first mapping from the config-defined map.
                    String dimension = configuredDimensionTextures.keySet().getFirst();
                    iconPair = ObjectObjectImmutablePair.of(dimension, configuredDimensionTextures.remove(dimension));
                } else if (!unspecifiedDimensions.isEmpty()) {
                    // Non-configured dimension, use the configured default texture.
                    iconPair = ObjectObjectImmutablePair.of(unspecifiedDimensions.removeFirst(), defaultTexture);
                } else {
                    // No dimensions left to map. Really shouldn't end up here so, to avoid masking a problem, use a default value instead of null.
                    iconPair = ObjectObjectImmutablePair.of("unknown", defaultTexture);
                }
            }
            slotIcons.add(iconPair);
        }

        return slotIcons;
    }
}
