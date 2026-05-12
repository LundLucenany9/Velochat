package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects repeated or highly similar messages in a short interval.
 */
public final class SpamManager {
    private final Map<UUID, Entry> lastMessage = new HashMap<>();

    public synchronized boolean isSpam(Player player, long intervalMillis, String message, double threshold) {
        if (intervalMillis <= 0) {
            return false;
        }
        String normalized = normalize(message);
        Instant now = Instant.now();
        Entry last = lastMessage.get(player.getUniqueId());
        lastMessage.put(player.getUniqueId(), new Entry(normalized, now));
        if (last == null) {
            return false;
        }
        long delta = now.toEpochMilli() - last.at.toEpochMilli();
        if (delta >= intervalMillis) {
            return false;
        }
        if (normalized.isEmpty() || last.message.isEmpty()) {
            return false;
        }
        if (normalized.equals(last.message)) {
            return true;
        }
        int distance = levenshtein(normalized, last.message);
        int maxLen = Math.max(normalized.length(), last.message.length());
        double similarity = 1.0 - (double) distance / (double) maxLen;
        double effectiveThreshold = threshold <= 0 ? 0.85 : threshold;
        return similarity >= effectiveThreshold;
    }

    private static String normalize(String message) {
        if (message == null) {
            return "";
        }
        String lower = message.toLowerCase();
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            switch (ch) {
                case '0':
                    sb.append('o');
                    break;
                case '1':
                case '!':
                    sb.append('i');
                    break;
                case '3':
                    sb.append('e');
                    break;
                case '4':
                case '@':
                    sb.append('a');
                    break;
                case '5':
                case '$':
                    sb.append('s');
                    break;
                case '7':
                    sb.append('t');
                    break;
                default:
                    if (Character.isLetterOrDigit(ch)) {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    private static final class Entry {
        private final String message;
        private final Instant at;

        private Entry(String message, Instant at) {
            this.message = message;
            this.at = at;
        }
    }
}
