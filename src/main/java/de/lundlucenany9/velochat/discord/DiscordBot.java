package de.lundlucenany9.velochat.discord;

import net.dv8tion.jda.api.JDA;

import java.util.concurrent.CompletableFuture;

public interface DiscordBot {
    CompletableFuture<String> sendMessage(String group, String message, String senderName);
    JDA getJda();
}
