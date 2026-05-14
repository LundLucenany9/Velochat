package de.lundlucenany9.velochat.discord;

import de.lundlucenany9.velochat.Velochat;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

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
            Velochat.getLogger().info("Discord bot connected as {}", jda.getSelfUser().getEffectiveName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bot initialization was interrupted", e);
        } catch (InvalidTokenException e) {
            throw new IllegalArgumentException("Invalid Discord bot token", e);
        }
    }


    public CompletableFuture<String> sendMessage(String group, String message, String senderName) {
        return resolveChannel(group)
                .thenCompose(channel ->
                        channel.sendMessage(PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(Velochat.getConfig().getDiscordFormat(), Placeholder.parsed("message", message), Placeholder.parsed("username", senderName))))
                                .submit()
                )
                .thenApply(ISnowflake::getId);
    }

    @Override
    public JDA getJda() {
        return jda;
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
