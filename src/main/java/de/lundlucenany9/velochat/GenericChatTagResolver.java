package de.lundlucenany9.velochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves chat template tags without requiring a Velocity player context.
 */
public record GenericChatTagResolver(String username,
                                     String server,
                                     String message,
                                     String replyId,
                                     String replySnippet) implements TagResolver.WithoutArguments {
    @Override
    public Tag resolve(@NotNull String name) {
        switch (name) {
            case "server" -> {
                return (Inserting) () -> Component.text(server == null ? "" : server);
            }
            case "username" -> {
                return (Inserting) () -> Component.text(username == null ? "" : username);
            }
            case "ping" -> {
                return (Inserting) () -> Component.text("");
            }
            case "message" -> {
                return (Inserting) () -> Component.text(message == null ? "" : message);
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
}
