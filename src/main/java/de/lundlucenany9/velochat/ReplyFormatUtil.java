package de.lundlucenany9.velochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Utilities for replacing reply-context placeholders in text templates.
 */
public final class ReplyFormatUtil {
    private ReplyFormatUtil() {
    }

    public static String applyTokens(String format, ReplyRegistry.ReplyContext context) {
        if (format == null || context == null) {
            return format;
        }
        MiniMessage miniMessage = MiniMessage.miniMessage();
        String replyId = context.id() == null ? "" : context.id();
        String snippet = context.snippet() == null ? "" : context.snippet();
        Component fullMessage = context.fullMessage();

        String replyIdText = miniMessage.serialize(Component.text(replyId));
        String snippetText = miniMessage.serialize(Component.text(snippet));
        String fullText = fullMessage == null ? "" : miniMessage.serialize(fullMessage);

        return format.replace("<reply_id>", replyIdText)
                .replace("<reply_snippet>", snippetText)
                .replace("<reply_full>", fullText);
    }
}
