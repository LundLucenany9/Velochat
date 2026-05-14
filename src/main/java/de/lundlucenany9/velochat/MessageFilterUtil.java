package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;

import java.util.HashMap;
import java.util.Map;

public class MessageFilterUtil {
    public static FilteredMessage filter(String originalMessage, Player player) {
        Config config = Velochat.getConfig();
        if (!player.hasPermission("velochat.mute.bypass")
                && Velochat.getMuteManager().isMuted(player)) {
            Messages messages = Velochat.getMessages();
            String template = MessagesUtil.template(messages.muted, "<red>You are muted for <duration>.Reason: <italic><reason></italic></red>");
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("duration", Velochat.getMuteManager().remainingFormated(player));
            String reason = Velochat.getMuteManager().reason(player);
            placeholders.put("reason", reason != null ? reason : "");
            player.sendMessage(MessageUtil.render(player, template, placeholders));
            return new FilteredMessage(true, originalMessage);
        }

        if (!player.hasPermission("velochat.antispam.bypass")
                && config.isAntiSpamEnabled()
                && Velochat.getSpamManager().isSpam(
                player,
                config.getAntiSpamIntervalMillis(),
                originalMessage,
                config.getAntiSpamSimilarityThreshold()
        )) {
            if(Velochat.getConfig().isAntiSpamShouldMute()) {
                Velochat.getMuteManager().mute(
                        player.getUniqueId(),
                        "spam",
                        config.getAntiSpamMuteSeconds(),
                        null
                );
                Messages messages = Velochat.getMessages();
                String template = MessagesUtil.template(
                        messages.muted_spam,
                        "<red>You have been muted for spamming.</red>"
                );
                player.sendMessage(MessageUtil.render(player, template, null));
            }
            MessageHandler handler = Velochat.getMessageHandler();
            if (handler != null) {
                handler.sendMessage("flag", "spam", player.getUniqueId().toString(),originalMessage);
            }
            return new FilteredMessage(true, originalMessage);
        }

        String message = originalMessage;
        if (FontFilter.shouldBlock(player, message, config)) {
            Messages messages = Velochat.getMessages();
            String template = MessagesUtil.template(
                    messages.font_blocked,
                    "<red>Your message contains unsupported fonts.</red>"
            );
            player.sendMessage(MessageUtil.render(player, template, null));
            MessageHandler handler = Velochat.getMessageHandler();
            if (handler != null) {
                handler.sendMessage("flag", "font", player.getUniqueId().toString(),originalMessage);
            }
            return new FilteredMessage(true, message);
        }
        FilterUtil.FilterResult filterResult = FilterUtil.applyFilter(message, config);
        MessageHandler handler = Velochat.getMessageHandler();


        if (filterResult.blocked()) {
            Messages messages = Velochat.getMessages();
            String template = MessagesUtil.template(
                    messages.filter_blocked,
                    "<red>Your message was blocked.</red>"
            );
            player.sendMessage(MessageUtil.render(player, template, null));
            if (handler != null) {
                handler.sendMessage("flag", "filtered", player.getUniqueId().toString(),originalMessage);
            }
            return new FilteredMessage(true, message);
        }
        if (filterResult.modified()) {
            message = filterResult.message();
            if (handler != null) {
                handler.sendMessage("flag", "filtered", player.getUniqueId().toString(),originalMessage);
            }
        }
        return new FilteredMessage(false, message);
    }
    public record FilteredMessage(boolean shouldBlock, String finalMessage){}

    /**
     * Filters a Discord message through word/font filters only (no player context needed).
     */
    public static FilteredMessage filterDiscord(String originalMessage) {
        Config config = Velochat.getConfig();
        if (!config.isDiscordFilterEnabled()) {
            return new FilteredMessage(false, originalMessage);
        }
        if (FontFilter.shouldBlock(null, originalMessage, config)) {
            return new FilteredMessage(true, originalMessage);
        }
        FilterUtil.FilterResult filterResult = FilterUtil.applyFilter(originalMessage, config);
        if (filterResult.blocked()) {
            return new FilteredMessage(true, originalMessage);
        }
        return new FilteredMessage(false, filterResult.modified() ? filterResult.message() : originalMessage);
    }
}
