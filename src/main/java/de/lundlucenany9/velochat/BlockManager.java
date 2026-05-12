package de.lundlucenany9.velochat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks per-player block relationships.
 */
public final class BlockManager {
    private final Map<UUID, Set<UUID>> blocked = new HashMap<>();

    public synchronized void block(UUID owner, UUID target) {
        blocked.computeIfAbsent(owner, ignored -> new HashSet<>()).add(target);
    }

    public synchronized void unblock(UUID owner, UUID target) {
        Set<UUID> set = blocked.get(owner);
        if (set == null) {
            return;
        }
        set.remove(target);
        if (set.isEmpty()) {
            blocked.remove(owner);
        }
    }

    public synchronized boolean hasBlocked(UUID owner, UUID target) {
        Set<UUID> set = blocked.get(owner);
        return set != null && set.contains(target);
    }

    public synchronized boolean isBlockedEither(UUID a, UUID b) {
        return hasBlocked(a, b) || hasBlocked(b, a);
    }
}
