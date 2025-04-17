package me.wesley1808.fastrtp.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import kotlin.Unit;
import me.wesley1808.fastrtp.FastRTP;
import me.wesley1808.fastrtp.config.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.silkmc.silk.igui.Gui;
import net.silkmc.silk.igui.GuiBuilder;
import net.silkmc.silk.igui.GuiSlot;
import net.silkmc.silk.igui.GuiSlotCompound;
import net.silkmc.silk.igui.GuiType;
import net.silkmc.silk.igui.observable.GuiList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RtpGui {

    @Nullable
    public Gui buildGui(ServerPlayer player) {
        if (player.getServer() == null) return null;

        List<String> dimensions = player.getServer().levelKeys().stream().map(key -> key.location().getPath()).toList();
        int numDimensions = dimensions.size();
        if (numDimensions == 0) return null;

        Config config = Config.instance();
        Object2ObjectLinkedOpenHashMap<String, String> dimensionToTexture = config.worldHeadTextures;
        List<String> otherDimensions = new ArrayList<>(dimensions);
        otherDimensions.removeAll(dimensionToTexture.keySet());

        Object2ObjectLinkedOpenHashMap<String, String> iconList = getGuiIcons(
            Util.getItemDistribution(numDimensions),
            dimensionToTexture,
            otherDimensions,
            config.defaultWorldHeadTexture
        );

        GuiBuilder gui = getGuiBuilder(player, iconList);
        return gui.build();
    }

    private static GuiBuilder getGuiBuilder(ServerPlayer player, Object2ObjectLinkedOpenHashMap<String, String> iconList) {
        GuiBuilder gui = new GuiBuilder(GuiType.NINE_BY_ONE, Component.literal("Worlds"), 0);
        gui.page(0, 0, builder -> {
            builder.compound(
                new GuiSlotCompound.SlotRange.Rectangle(new GuiSlot(1, 1), new GuiSlot(1, 9)),
                new GuiList<>(iconList.keySet().stream().toList()),
                (dimension, _unused) -> (dimension == null) ? ItemStack.EMPTY : Util.createCustomHead(iconList.get(dimension)),
                (clickEvent, dimension, _unused) -> {
                    try {
                        Objects.requireNonNull(player.getServer()).getCommands().getDispatcher().execute("rtp " + dimension, player.createCommandSourceStack());
                    } catch (CommandSyntaxException e) {
                        FastRTP.LOGGER.warn("RTP command via GUI failed: {}", e.getMessage());
                    }
                    return Unit.INSTANCE;
                }
            );

            return Unit.INSTANCE;
        });
        return gui;
    }

    private Object2ObjectLinkedOpenHashMap<String, String> getGuiIcons(
        BooleanList distribution,
        Object2ObjectLinkedOpenHashMap<String, String> dimensionToTexture,
        List<String> otherDimensions,
        String defaultTexture
    ) {
        Object2ObjectLinkedOpenHashMap<String, String> slotMappings = new Object2ObjectLinkedOpenHashMap<>();

        for (Boolean slotFlag : distribution) {
            if (!slotFlag) {
                // Slot is empty.
                slotMappings.put(null, null);
            } else {
                if (!dimensionToTexture.isEmpty()) {
                    // Get and remove the first mapping from the config-defined map.
                    String dimension = dimensionToTexture.keySet().getFirst();
                    slotMappings.put(dimension, dimensionToTexture.remove(dimension));
                } else if (!otherDimensions.isEmpty()) {
                    // Non-configured world, use the configured default texture.
                    slotMappings.put(otherDimensions.removeFirst(), defaultTexture);
                } else {
                    // No dimensions left to map. Really shouldn't end up here so, to avoid masking a problem, use a default value instead of null.
                    slotMappings.put("unknown", defaultTexture);
                }
            }
        }

        return slotMappings;
    }
}
