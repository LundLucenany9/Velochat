package de.lundlucenany9.velochat.discord;

import de.lundlucenany9.velochat.Velochat;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

public final class EnabledDiscordBot implements DiscordBot{
    private final JDA jda;
    EnabledDiscordBot(String token) {
        try {
            jda = JDABuilder.createLight(
                            token,
                            EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    )
                    .addEventListeners(new MessageReceiveListener())
                    .build().awaitReady();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bot initialization was interrupted", e);
        } catch (InvalidTokenException e) {
            throw new IllegalArgumentException("Invalid Discord bot token", e);
        }
    }


    public CompletableFuture<String> sendMessage(String group, String message) {
        return resolveChannel(group)
                .thenCompose(channel ->
                        channel.sendMessage(message)
                                .submit()
                )
                .thenApply(ISnowflake::getId);
    }


    private CompletableFuture<TextChannel> resolveChannel(String group) {
        String channelId = Velochat.getConfig()
                .getDiscordGroupMappings()
                .get(group);

        if (channelId == null || channelId.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No channel mapping for group " + group)
            );
        }

        TextChannel channel = jda.getTextChannelById(channelId);

        if (channel == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Channel not found: " + channelId)
            );
        }

        return CompletableFuture.completedFuture(channel);
    }

}
