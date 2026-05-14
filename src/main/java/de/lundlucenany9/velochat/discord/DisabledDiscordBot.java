package de.lundlucenany9.velochat.discord;

import net.dv8tion.jda.api.JDA;

import java.util.concurrent.CompletableFuture;

public final class DisabledDiscordBot implements DiscordBot{
    DisabledDiscordBot(){}

    @Override
    public CompletableFuture<String> sendMessage(String group, String message, String senderName) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public JDA getJda() {
        return null;
    }

    @Override
    public CompletableFuture<String> sendReply(String group, String messageId, String message, String senderName) {
        return CompletableFuture.completedFuture(null);
    }


}
