package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.Map;

public final class GroupUtil {

    public static String findChatGroup(String serverName) {
        if (serverName == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : Velochat.getConfig().getChatGroups().entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(serverName)) {
                return entry.getKey();
            }
        }
        return null;
    }
    public static List<Player> getRecipients(Player sender) {
        if (Velochat.getConfig().global_chat) {
            String senderGroup = getGroup(sender);
            if (senderGroup != null) {
                return getRecipientsForGroup(senderGroup);
            } else if (Velochat.getConfig().broadcast_message) {
                List<Player> targets = new java.util.ArrayList<>();
                sender.getCurrentServer().ifPresent(server ->
                        targets.addAll(server.getServer().getPlayersConnected())
                );
                return targets;
            }
        } else if (Velochat.getConfig().broadcast_message) {
            List<Player> targets = new java.util.ArrayList<>();
            sender.getCurrentServer().ifPresent(server ->
                    targets.addAll(server.getServer().getPlayersConnected())
            );
            return targets;
        }
        return List.of();
    }
    public static String getGroup(Player sender) {
        String senderServer = sender.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse(null);
        return senderServer == null ? null : findChatGroup(senderServer);
    }
    public static String getGroup(String channelId) {
        return Velochat.getConfig().getDiscordGroupMappings().entrySet().stream().filter(e -> e.getValue().equals(channelId)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public static List<Player> getRecipients(String channelId) {
        if (Velochat.getConfig().global_chat) {
            String senderGroup = getGroup(channelId);
            if (senderGroup != null) {
                return getRecipientsForGroup(senderGroup);
            }
        }
        return List.of();
    }

    public static List<Player> getRecipientsByGroup(String group) {
        if (group == null || group.isBlank()) {
            return List.of();
        }
        return getRecipientsForGroup(group);
    }

    private static List<Player> getRecipientsForGroup(String group) {
        List<Player> targets = new java.util.ArrayList<>();
        Velochat.getServer().getAllPlayers().forEach(target -> target.getCurrentServer().ifPresent(server -> {
            String targetServer = server.getServerInfo().getName();
            if (!group.equals(findChatGroup(targetServer))) {
                return;
            }
            boolean listed = Velochat.getConfig().getServers().contains(targetServer);
            if (Velochat.getConfig().isBlacklist() != listed) {
                targets.add(target);
            }
        }));
        return targets;
    }
}
