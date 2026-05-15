package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;

import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stores reply contexts and recent private-message contacts.
 */
public final class ReplyRegistry {
    private static final int MAX_ENTRIES = 1000;
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Map<String, ReplyContext> ENTRIES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ReplyContext> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private static final Map<String, String> LAST_BY_SENDER = new HashMap<>();
    private static final Map<UUID, LastPrivateContact> LAST_PRIVATE_BY_RECIPIENT = new HashMap<>();

    private ReplyRegistry() {
    }

    public static synchronized String register(String senderId, String sender, String message, Set<UUID> recipients, Origin origin, String group) {
        String id = Integer.toString(COUNTER.incrementAndGet(), 36);
        String snippet = buildSnippet(message);
        Set<UUID> recipientSet = recipients == null ? Collections.emptySet() : new HashSet<>(recipients);
        ReplyContext context = new ReplyContext(id, senderId, sender, snippet, null, recipientSet, origin, Optional.empty(), group);
        ENTRIES.put(id, context);
        LAST_BY_SENDER.put(senderId, id);
        return id;
    }

    public static synchronized ReplyContext get(String id) {
        return ENTRIES.get(id);
    }
    public static synchronized Optional<ReplyContext> getByMessageId(String id) {
        return ENTRIES.values().stream().filter(e -> e.discordMessageId().filter(id::equals).isPresent()).findFirst();
    }

    /** Updates the rendered Component after dispatch. Chat critical path. */
    public static synchronized void updateRendered(String id, Component fullMessage) {
        ReplyContext existing = ENTRIES.get(id);
        if (existing == null) return;
        ENTRIES.put(id, new ReplyContext(
                existing.id(), existing.senderId(), existing.senderName(),
                existing.snippet(), fullMessage, existing.recipients(),
                existing.origin(), existing.discordMessageId(), existing.group()
        ));
    }

    /** Updates the Discord message ID after Discord send. Fire-and-forget observer. */
    public static synchronized void updateDiscordId(String id, String dcMessageId) {
        ReplyContext existing = ENTRIES.get(id);
        if (existing == null) return;
        ENTRIES.put(id, new ReplyContext(
                existing.id(), existing.senderId(), existing.senderName(),
                existing.snippet(), existing.fullMessage(), existing.recipients(),
                existing.origin(), Optional.ofNullable(dcMessageId), existing.group()
        ));
    }



    public static synchronized ReplyContext getLastBySender(UUID sender) {
        String id = sender == null ? null : LAST_BY_SENDER.get(sender.toString());
        return id == null ? null : ENTRIES.get(id);
    }

    public static synchronized void registerPrivateMessage(Player sender, Player recipient) {
        if (sender == null || recipient == null) {
            return;
        }
        LAST_PRIVATE_BY_RECIPIENT.put(recipient.getUniqueId(),
                new LastPrivateContact(sender.getUniqueId(), sender.getUsername()));
    }

    public static synchronized LastPrivateContact getLastPrivateContact(UUID recipient) {
        return LAST_PRIVATE_BY_RECIPIENT.get(recipient);
    }

    private static String buildSnippet(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.strip();
        int max = 32;
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max - 3) + "...";
    }

    /**
     * Immutable reply context used by reply commands.
     */
    public record ReplyContext(String id,
                               String senderId,
                               String senderName,
                               String snippet,
                               Component fullMessage,
                               Set<UUID> recipients,
                               Origin origin,
                               Optional<String> discordMessageId,
                               String group) {
    }

    /**
     * Stores the latest private-message sender for a recipient.
     */
    public record LastPrivateContact(UUID senderId, String senderName) {
    }
    public enum Origin {
        MINECRAFT,
        DISCORD
    }
}
