package de.lundlucenany9.velochat.discord;

import de.lundlucenany9.velochat.Velochat;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

public final class EnabledDiscordBot implements DiscordBot{
    private final JDA jda;
    EnabledDiscordBot(String token) {
        try {
            jda = JDABuilder.create(
                            token,
                            EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
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
        if(group == null || group.isBlank()) return CompletableFuture.completedFuture(null);
        MessageChannel channel = resolveChannel(group);
        return channel.sendMessage(PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(Velochat.getConfig().getDiscordFormat(), Placeholder.parsed("message", message), Placeholder.parsed("username", senderName)))).submit()
                .thenApply(ISnowflake::getId);
    }

    @Override
    public JDA getJda() {
        return jda;
    }

    @Override
    public CompletableFuture<String> sendReply(String group, String messageId, String message, String senderName) {
        if(group == null || group.isBlank()) return CompletableFuture.completedFuture(null);
        if(messageId == null || messageId.isBlank()) return CompletableFuture.completedFuture(null);
        MessageChannel channel = resolveChannel(group);
        return channel.retrieveMessageById(messageId).submit()
                .thenCompose(m -> m.reply(PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(Velochat.getConfig().getDiscordFormat(), Placeholder.parsed("message", message), Placeholder.parsed("username", senderName)))).submit())
                .thenApply(ISnowflake::getId);
    }


    private MessageChannel resolveChannel(String group) {
        String channelId = Velochat.getConfig()
                .getDiscordGroupMappings()
                .get(group);

        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException(
                    "No channel mapping for group " + group
            );
        }

        MessageChannel channel = jda.getChannelById(MessageChannel.class,channelId);

        if (channel == null) {
            throw new IllegalArgumentException(
                    "Channel not found: " + channelId
            );
        }

        return channel;
    }

}
