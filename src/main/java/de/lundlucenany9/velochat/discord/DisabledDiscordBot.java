package de.lundlucenany9.velochat.discord;

import java.util.concurrent.CompletableFuture;

public final class DisabledDiscordBot implements DiscordBot{
    DisabledDiscordBot(){}

    @Override
    public CompletableFuture<String> sendMessage(String group, String message) {
        return CompletableFuture.completedFuture(null);
    }

}
