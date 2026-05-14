package de.lundlucenany9.velochat;

import de.lundlucenany9.velochat.discord.Bot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.IMemberContainer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.util.RGBLike;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record DiscordTagResolver(Member user, String message, String replyId, String replySnippet)
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
            case "role_highest" -> {
                Role role = user.getRoles().isEmpty() ? null : user.getRoles().getFirst();
                return (Inserting) () -> Component.text(role == null ? "" : role.getName());
            }
            case "role_first_color" -> {
                Optional<Role> role = user.getRoles().stream().filter(r -> !r.getColors().isDefault()).findFirst();
                return (Inserting) () -> Component.text(role.map(Role::getName).orElse("")).color(TextColor.color(role.map(value -> value.getColors().getPrimaryRaw()).orElse(0)));
            }
            default -> {
                return null;
            }
        }
    }

}
