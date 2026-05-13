package de.lundlucenany9.velochat.discord;

import java.util.concurrent.CompletableFuture;

public interface DiscordBot {
    CompletableFuture<String> sendMessage(String group, String message);
}
