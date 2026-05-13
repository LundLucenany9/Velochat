package de.lundlucenany9.velochat;

import net.dv8tion.jda.api.entities.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

public record DiscordTagResolver(User user, String message, String replyId, String replySnippet)
        implements TagResolver.WithoutArguments {
    @Override
    public Tag resolve(@NotNull String name) {
        switch (name) {
            case "server" -> {
                return (Inserting) () -> MiniMessage.miniMessage().deserialize(Velochat.getConfig().getDiscordServerName());
            }
            case "username" -> {
                return (Inserting) () -> Component.text(user.getEffectiveName());
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

}