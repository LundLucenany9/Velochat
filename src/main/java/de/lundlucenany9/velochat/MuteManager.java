package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory mute state with expiry and optional server scoping.
 */
public final class MuteManager {
    private final Map<UUID, MuteEntry> mutes = new HashMap<>();

    public void registerMessageListener(MessageHandler messageHandler) {
        messageHandler.addListener(event -> {
            switch (event.type()) {
                case "get" -> {
                    switch (event.action()) {
                        case "mute_duration" -> {
                            Duration remaining = remaining(event.uuid());
                            event.respond(remaining == null ? "" : String.valueOf(remaining.toSeconds()));
                        }
                        case "mute_ismuted" -> event.respond(String.valueOf(isMuted(event.uuid())));
                    }
                }
                case "set" -> {
                    switch (event.action()) {
                        case "mute_mute" -> {
                            long duration;
                            String reason = "";
                            int sep = event.message().indexOf(';');
                            if (sep == -1) {
                                duration = Long.parseLong(event.message());
                            } else {
                                duration = Long.parseLong(event.message().substring(0, sep));
                                reason = event.message().substring(sep + 1);
                            }

                            mute(event.uuid(), reason, duration, null);
                        }
                        case "mute_unmute" -> unmute(event.uuid());
                    }
                }
            }

        });
    }

    public synchronized void mute(UUID uuid, String reason, long durationSeconds, Set<String> servers) {
        Instant expiresAt = durationSeconds < 0 ? null : Instant.now().plusSeconds(durationSeconds);
        Set<String> serverSet = servers == null ? Collections.emptySet() : new HashSet<>(servers);
        mutes.put(uuid, new MuteEntry(reason, expiresAt, serverSet));
        MessageHandler handler = Velochat.getMessageHandler();
        if (handler != null) {
            handler.sendMessage("flag","mute_muted",uuid.toString(),durationSeconds + ";" + reason);
        }
    }

    public synchronized void unmute(UUID uuid) {
        mutes.remove(uuid);
        MessageHandler handler = Velochat.getMessageHandler();
        if (handler != null) {
            handler.sendMessage("flag","mute_unmuted",uuid.toString());
        }
    }

    public synchronized boolean isMuted(Player player) {
        MuteEntry entry = mutes.get(player.getUniqueId());
        if (entry == null) {
            return false;
        }
        if (entry.expiresAt != null && Instant.now().isAfter(entry.expiresAt)) {
            mutes.remove(player.getUniqueId());
            return false;
        }
        if (entry.servers.isEmpty()) {
            return true;
        }
        String serverName = player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("");
        return entry.servers.contains(serverName);
    }

    public synchronized Duration remaining(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        if (entry == null || entry.expiresAt == null) {
            return null;
        }
        Duration duration = Duration.between(Instant.now(), entry.expiresAt);
        return duration.isNegative() ? Duration.ZERO : duration;
    }
    public synchronized Duration remaining(Player player) {
        return remaining(player.getUniqueId());
    }

    public synchronized String remainingFormated(Player player) {
        Duration duration = remaining(player);
        if(duration == null) return "permanent";
        long hours   = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (hours > 0)   return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        if (minutes > 0) return String.format("%dm %02ds", minutes, seconds);
        return String.format("%ds", seconds);
    }

    public synchronized String reason(Player player) {
        MuteEntry entry = mutes.get(player.getUniqueId());
        return entry == null ? null : entry.reason;
    }

    public synchronized boolean isMuted(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        if (entry == null) {
            return false;
        }
        if (entry.expiresAt != null && Instant.now().isAfter(entry.expiresAt)) {
            mutes.remove(uuid);
            return false;
        }
        return true;
    }

    private static final class MuteEntry {
        private final String reason;
        private final Instant expiresAt;
        private final Set<String> servers;

        private MuteEntry(String reason, Instant expiresAt, Set<String> servers) {
            this.reason = reason;
            this.expiresAt = expiresAt;
            this.servers = servers;
        }
    }
}
