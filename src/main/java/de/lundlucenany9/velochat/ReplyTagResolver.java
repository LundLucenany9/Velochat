package de.lundlucenany9.velochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves tags for reply header/footer templates.
 */
public record ReplyTagResolver(String replyId, String replySnippet, Component fullMessage)
        implements TagResolver.WithoutArguments {
    @Override
    public Tag resolve(@NotNull String name) {
        switch (name) {
            case "reply_id" -> {
                return (Inserting) () -> Component.text(replyId == null ? "" : replyId);
            }
            case "reply_snippet" -> {
                return (Inserting) () -> Component.text(replySnippet == null ? "" : replySnippet);
            }
            case "reply_full" -> {
                return (Inserting) () -> fullMessage == null ? Component.empty() : fullMessage;
            }
            default -> {
                return null;
            }
        }
    }
}
