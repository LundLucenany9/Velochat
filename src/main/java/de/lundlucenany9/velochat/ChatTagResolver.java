package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves chat template tags for a player message context.
 */
public record ChatTagResolver(Player player, String message, String replyId, String replySnippet)
        implements TagResolver.WithoutArguments {
    @Override
    public Tag resolve(@NotNull String name) {
        switch (name) {
            case "server" -> {
                return (Inserting) () -> Component.text(serverName());
            }
            case "username" -> {
                return (Inserting) () -> Component.text(player.getUsername());
            }
            case "ping" -> {
                return (Inserting) () -> Component.text(player.getPing());
            }
            case "message" -> {
                return (Inserting) () -> Component.text(message);
            }
            case "reply_id" -> {
                return (Inserting) () -> Component.text(replyId == null ? "" : replyId);
            }
            case "reply_snippet" -> {
                return (Inserting) () -> Component.text(replySnippet == null ? "" : replySnippet);
            }
            default -> {
                return null;
            }
        }
    }

    private String serverName() {
        return player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("");
    }
}
