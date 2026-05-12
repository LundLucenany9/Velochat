package de.lundlucenany9.velochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves sender and message tags for broadcast templates.
 */
public record BroadcastTagResolver(String sender, Component message) implements TagResolver.WithoutArguments {
    @Override
    public Tag resolve(@NotNull String name) {
        switch (name) {
            case "sender" -> {
                return (Inserting) () -> Component.text(sender);
            }
            case "message" -> {
                return (Inserting) () -> message;
            }
            default -> {
                return null;
            }
        }
    }
}
