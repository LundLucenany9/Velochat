package de.lundlucenany9.velochat.listeners;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import de.lundlucenany9.velochat.*;

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
                MessageFilterUtil.FilteredMessage filteredMessage = MessageFilterUtil.filter(e.getMessage(), e.getPlayer());
                if(filteredMessage.shouldBlock()) {
                    e.setResult(PlayerChatEvent.ChatResult.denied());
                    return null;
                }
                String finalMessage = filteredMessage.finalMessage();
                e.setResult(config.getForwardMode() == -1 ? PlayerChatEvent.ChatResult.denied() : config.getForwardMode() == 0 ? PlayerChatEvent.ChatResult.allowed() : PlayerChatEvent.ChatResult.message(finalMessage));
                if(config.getForwardMode() == 1) return null;

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
