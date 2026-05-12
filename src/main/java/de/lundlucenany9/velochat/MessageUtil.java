package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;

/**
 * Utility methods for rendering MiniMessage templates with placeholders.
 */
public final class MessageUtil {
    private MessageUtil() {
    }

    public static Component render(Player player, String template, Map<String, String> placeholders) {
        TagResolver resolver = resolver(placeholders);
        if (player != null) {
            return Velochat.parser.parseWithResolver(template, player, resolver).join();
        }
        return MiniMessage.miniMessage().deserialize(template, resolver);
    }

    private static TagResolver resolver(Map<String, String> placeholders) {
        TagResolver.Builder builder = TagResolver.builder();
        if (placeholders != null) {
            placeholders.forEach((key, value) -> builder.resolver(
                    TagResolver.resolver(key, Tag.inserting(Component.text(value)))
            ));
        }
        return builder.build();
    }
}
