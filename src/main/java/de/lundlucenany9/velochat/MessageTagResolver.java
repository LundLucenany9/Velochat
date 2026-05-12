package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves tags for direct-message templates.
 */
public record MessageTagResolver(Player sender, Player receiver, String message) implements TagResolver.WithoutArguments {
    @Override
    public Tag resolve(@NotNull String name) {
        switch (name) {
            case "sender" -> {
                return (Inserting) () -> Component.text(sender.getUsername());
            }
            case "receiver" -> {
                return (Inserting) () -> Component.text(receiver.getUsername());
            }
            case "sender_server" -> {
                return (Inserting) () -> Component.text(serverName(sender));
            }
            case "receiver_server" -> {
                return (Inserting) () -> Component.text(serverName(receiver));
            }
            case "sender_ping" -> {
                return (Inserting) () -> Component.text(sender.getPing());
            }
            case "receiver_ping" -> {
                return (Inserting) () -> Component.text(receiver.getPing());
            }
            case "message" -> {
                return (Inserting) () -> Component.text(message);
            }
            default -> {
                return null;
            }
        }
    }

    private static String serverName(Player player) {
        return player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("");
    }
}
