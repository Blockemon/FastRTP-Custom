package me.wesley1808.fastrtp.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import me.wesley1808.fastrtp.config.Config;
import me.wesley1808.fastrtp.util.Permission;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

public class WorldSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();
        var server = source.getServer();

        // Filter and suggest valid worlds based on namespaces and permissions
        var suggestions = server.levelKeys().stream()
            .filter(levelKey -> {
                ResourceLocation worldResource = levelKey.location();
                String permission = Permission.COMMAND_RTP_DIMENSION + worldResource.toString().replace(":", ".");
                return Config.instance().dimensionNameSpaces.contains(worldResource.getNamespace()) &&
                    Permissions.check(source, permission);
            })
            .map(levelKey -> levelKey.location().getPath()) // Suggest world names without namespaces
            .toList();

        // Add filtered suggestions to the builder
        suggestions.forEach(builder::suggest);

        // Return the finalized suggestions
        return builder.buildFuture();
    }
}
