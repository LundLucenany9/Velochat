package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves tags for single-player message formatting.
 */
public record SingleTagResolver(Player player, String message) implements TagResolver.WithoutArguments {
    @Override
    public Tag resolve(@NotNull String name) {
        switch (name) {
            case "server" -> {
                return (Inserting) () -> {
                    if (player.getCurrentServer().isPresent())
                        return Component.text(player.getCurrentServer().get().getServerInfo().getName());
                    else return Component.text("");
                };
            }
            case "username" -> {
                return (Inserting) () -> Component.text(player.getUsername());
            }
            case "ping" -> {
                return (Inserting) () -> Component.text(player.getPing());
            }
            case "message" ->{
                return (Inserting) () -> Component.text(message);
            }
        }
        return null;
    }
}
