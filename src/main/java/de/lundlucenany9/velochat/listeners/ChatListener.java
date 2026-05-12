package de.lundlucenany9.velochat.listeners;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import de.lundlucenany9.velochat.Config;
import de.lundlucenany9.velochat.FontFilter;
import de.lundlucenany9.velochat.FilterUtil;
import de.lundlucenany9.velochat.MessageUtil;
import de.lundlucenany9.velochat.MessagesUtil;
import de.lundlucenany9.velochat.Messages;
import de.lundlucenany9.velochat.MessageHandler;
import de.lundlucenany9.velochat.Velochat;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles player chat events and applies mute/spam/filter/format logic.
 */
public class ChatListener {
    @Subscribe(priority = 1)
    public EventTask onPlayerChat(PlayerChatEvent e){
        try {
            Config config = Velochat.getConfig();
            String currentServer = e.getPlayer().getCurrentServer()
                    .map(connection -> connection.getServerInfo().getName())
                    .orElse(null);
            if (currentServer == null) {
                return null;
            }
            boolean listed = config.getServers().contains(currentServer);
            if(config.isBlacklist() != listed) {
                if (!e.getPlayer().hasPermission("velochat.mute.bypass")
                        && Velochat.getMuteManager().isMuted(e.getPlayer())) {
                    Messages messages = Velochat.getMessages();
                    String template = MessagesUtil.template(messages.muted, "<red>You are muted for <duration>.Reason: <italic><reason></italic></red>");
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("duration", Velochat.getMuteManager().remainingFormated(e.getPlayer()));
                    placeholders.put("reason", String.valueOf(Velochat.getMuteManager().reason(e.getPlayer())));
                    e.getPlayer().sendMessage(MessageUtil.render(e.getPlayer(), template, placeholders));
                    e.setResult(PlayerChatEvent.ChatResult.denied());
                    return null;
                }

                if (!e.getPlayer().hasPermission("velochat.antispam.bypass")
                        && config.isAntiSpamEnabled()
                        && Velochat.getSpamManager().isSpam(
                                e.getPlayer(),
                                config.getAntiSpamIntervalMillis(),
                                e.getMessage(),
                                config.getAntiSpamSimilarityThreshold()
                        )) {
                    if(Velochat.getConfig().isAntiSpamShouldMute()) {
                        Velochat.getMuteManager().mute(
                                e.getPlayer().getUniqueId(),
                                "spam",
                                config.getAntiSpamMuteSeconds(),
                                null
                        );
                        Messages messages = Velochat.getMessages();
                        String template = MessagesUtil.template(
                                messages.muted_spam,
                                "<red>You have been muted for spamming.</red>"
                        );
                        e.getPlayer().sendMessage(MessageUtil.render(e.getPlayer(), template, null));
                    }
                    MessageHandler handler = Velochat.getMessageHandler();
                    if (handler != null) {
                        handler.sendMessage("flag", "spam", e.getPlayer().getUniqueId().toString(),e.getMessage());
                    }
                    e.setResult(PlayerChatEvent.ChatResult.denied());
                    return null;
                }

                String message = e.getMessage();
                if (FontFilter.shouldBlock(e.getPlayer(), message, config)) {
                    Messages messages = Velochat.getMessages();
                    String template = MessagesUtil.template(
                            messages.font_blocked,
                            "<red>Your message contains unsupported fonts.</red>"
                    );
                    e.getPlayer().sendMessage(MessageUtil.render(e.getPlayer(), template, null));
                    MessageHandler handler = Velochat.getMessageHandler();
                    if (handler != null) {
                        handler.sendMessage("flag", "font", e.getPlayer().getUniqueId().toString(),e.getMessage());
                    }
                    e.setResult(PlayerChatEvent.ChatResult.denied());
                    return null;
                }
                FilterUtil.FilterResult filterResult = FilterUtil.applyFilter(message, config);
                MessageHandler handler = Velochat.getMessageHandler();
                if (handler != null) {
                    handler.sendMessage("flag", "filtered", e.getPlayer().getUniqueId().toString(),e.getMessage());
                }

                if (filterResult.blocked()) {
                    Messages messages = Velochat.getMessages();
                    String template = MessagesUtil.template(
                            messages.filter_blocked,
                            "<red>Your message was blocked.</red>"
                    );
                    e.getPlayer().sendMessage(MessageUtil.render(e.getPlayer(), template, null));
                    e.setResult(PlayerChatEvent.ChatResult.denied());
                    return null;
                }
                if (filterResult.modified()) {
                    message = filterResult.message();
                }

                e.setResult(config.getForward_mode() == -1 ? PlayerChatEvent.ChatResult.denied() : config.getForward_mode() == 0 ? PlayerChatEvent.ChatResult.allowed() : PlayerChatEvent.ChatResult.message(message));
                if(config.getForward_mode() == 1) return null;
                String finalMessage = message;
                return EventTask.async(() -> Velochat.parser.sendChat(finalMessage, e.getPlayer()));
            }
            e.setResult(PlayerChatEvent.ChatResult.allowed());
            return null;
        } catch (Exception ex) {
            Velochat.getLogger().error("Error while handling player chat for {}", e.getPlayer().getUsername(), ex);
            e.setResult(PlayerChatEvent.ChatResult.allowed());
            return null;
        }
    }
}
