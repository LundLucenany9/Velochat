package de.lundlucenany9.velochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves basic tags for non-player contexts (e.g. Discord-origin replies).
 */
public record ContextTagResolver(String username) implements TagResolver.WithoutArguments {
    @Override
    public Tag resolve(@NotNull String name) {
        switch (name) {
            case "username" -> {
                return (Inserting) () -> Component.text(username == null ? "" : username);
            }
            case "server", "ping", "message" -> {
                return (Inserting) Component::empty;
            }
            default -> {
                return null;
            }
        }
    }
}
