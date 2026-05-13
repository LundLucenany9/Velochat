package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;
import de.lundlucenany9.velochat.discord.Bot;
import net.dv8tion.jda.api.entities.User;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ReplyService {
    public record ReplySource(String senderId,
                              String senderName,
                              ReplyRegistry.Origin origin,
                              String group,
                              Player player) {
        public static ReplySource minecraft(Player player) {
            return new ReplySource(
                    player.getUniqueId().toString(),
                    player.getUsername(),
                    ReplyRegistry.Origin.MINECRAFT,
                    GroupUtil.getGroup(player),
                    player
            );
        }
        public static ReplySource discord(User user, String group) {
            return new ReplySource(
                    user.getId(),
                    user.getEffectiveName(),
                    ReplyRegistry.Origin.DISCORD,
                    group,
                    null
            );
        }
    }

    private ReplyService() {
    }

    public static void sendReply(
                                 Player source,
                                 Player target,
                                 String message,
                                 ReplyRegistry.ReplyContext context) {
        sendReply(ReplySource.minecraft(source), target, message, context);
    }

    public static void sendReply(
                                 ReplySource source,
                                 Player target,
                                 String message,
                                 ReplyRegistry.ReplyContext context) {
        Config config = Velochat.getConfig();
        if (source.player() != null && FontFilter.shouldBlock(source.player(), message, config)) {
            String template = MessagesUtil.template(
                    Velochat.getMessages().font_blocked,
                    "<red>Your message contains unsupported fonts.</red>"
            );
            source.player().sendMessage(MessageUtil.render(source.player(), template, null));
            return;
        }

        List<Player> recipients = computeRecipients(source, target, context);
        Set<UUID> recipientIds = new java.util.HashSet<>();
        for (Player recipient : recipients) {
            recipientIds.add(recipient.getUniqueId());
        }

        String replyGroup = context == null ? source.group() : context.group();
        String newReplyId = ReplyRegistry.register(
                source.senderId(),
                source.senderName(),
                message,
                recipientIds,
                source.origin(),
                replyGroup
        );
        ReplyRegistry.ReplyContext newReplyContext = ReplyRegistry.get(newReplyId);

        String snippet = context == null ? "" : context.snippet();
        String replyId = context == null ? "" : context.id();
        Component fullMessage = context == null ? Component.empty() : context.fullMessage();
        TagResolver headerResolver = target != null
                ? TagResolver.resolver(new SingleTagResolver(target, ""),
                new ReplyTagResolver(replyId, snippet, fullMessage))
                : TagResolver.resolver(new ContextTagResolver(context == null ? "" : context.senderName()),
                new ReplyTagResolver(replyId, snippet, fullMessage));

        // For Discord replies on MC-origin messages, use the original MC sender as PAPI context
        Player papiContextPlayer = source.player();
        if (papiContextPlayer == null && context != null && context.origin() == ReplyRegistry.Origin.MINECRAFT) {
            try {
                java.util.UUID originalSenderId = java.util.UUID.fromString(context.senderId());
                papiContextPlayer = Velochat.getServer().getPlayer(originalSenderId).orElse(null);
            } catch (IllegalArgumentException ignored) {}
        }
        final Player resolvedPapiPlayer = papiContextPlayer;

        String replyFormat = ReplyFormatUtil.applyTokens(config.getReplyFormat(), context);
        CompletableFuture<Component> header = target != null
                ? Velochat.parser.parseWithResolver(replyFormat, target, headerResolver)
                : resolvedPapiPlayer != null
                        ? Velochat.parser.parseWithResolver(replyFormat, resolvedPapiPlayer, headerResolver)
                        : Velochat.parser.parseWithResolver(replyFormat, headerResolver);

        String normalFormat = ReplyFormatUtil.applyTokens(config.getFormat(), newReplyContext);
        String blockedFormatTemplate = config.getBlockedReplyFormat() == null
                ? config.getFormat()
                : config.getBlockedReplyFormat();
        String blockedFormat = ReplyFormatUtil.applyTokens(blockedFormatTemplate, newReplyContext);
        TagResolver bodyResolver = source.player() != null
                ? TagResolver.resolver(new ChatTagResolver(source.player(), message, newReplyId, message))
                : TagResolver.resolver(new GenericChatTagResolver(
                        source.senderName(),
                        source.group(),
                        message,
                        newReplyId,
                        message
                ));

        CompletableFuture<String> discordMessageIdFuture = source.origin() == ReplyRegistry.Origin.MINECRAFT
                ? Bot.getInstance().sendMessage(source.group(), message)
                        .exceptionally(ex -> {
                            Velochat.getLogger().warn("Failed to mirror chat message to Discord: {}", ex.getMessage());
                            return null;
                        })
                : CompletableFuture.completedFuture(null);

        CompletableFuture<Component> normalBody =
                (resolvedPapiPlayer != null
                        ? Velochat.parser.parseWithResolver(normalFormat, resolvedPapiPlayer, bodyResolver)
                        : Velochat.parser.parseWithResolver(normalFormat, bodyResolver))
                        .thenApply(component -> {
                            discordMessageIdFuture.thenAccept(id -> ReplyRegistry.updateFullMessage(
                                    newReplyId,
                                    component,
                                    source.origin(),
                                    id
                            ));
                            return component;
                        });
        CompletableFuture<Component> blockedBody =
                blockedFormat.equals(normalFormat)
                        ? normalBody
                        : (resolvedPapiPlayer != null
                        ? Velochat.parser.parseWithResolver(blockedFormat, resolvedPapiPlayer, bodyResolver)
                        : Velochat.parser.parseWithResolver(blockedFormat, bodyResolver));

        CompletableFuture<Component> prefixFuture =
                CompletableFuture.completedFuture(MiniMessage.miniMessage().deserialize(config.getReplayMessagePrefix()));

        header.thenCombine(normalBody, (h, b) -> h.appendNewline())
                .thenCombine(prefixFuture, Component::append)
                .thenCombine(normalBody, Component::append)
                .thenAccept(normalResult -> {
                    Component blockedResult = header.join()
                            .appendNewline()
                            .append(prefixFuture.join())
                            .append(blockedBody.join());
                    for (Player recipient : recipients) {
                        boolean blocked = source.player() != null && Velochat.getBlockManager().isBlockedEither(
                                source.player().getUniqueId(), recipient.getUniqueId());
                        recipient.sendMessage(blocked ? blockedResult : normalResult);
                    }
                    if (target != null) {
                        target.playSound(Sound.sound(Key.key("minecraft", "bell"), Sound.Source.MASTER, 1, 1));
                    }
                }).exceptionally(ex -> {
                    if (source.player() != null) {
                        source.player().sendMessage(Component.text("Formatting error").color(NamedTextColor.RED));
                    }
                    Velochat.getLogger().warn("Formating Error: {}", ex.getLocalizedMessage());
                    return null;
                });
    }

    private static List<Player> computeRecipients(ReplySource source,
                                                  Player target,
                                                  ReplyRegistry.ReplyContext context) {
        List<Player> computedRecipients = context != null && context.origin() == ReplyRegistry.Origin.DISCORD
                ? resolveRecipientsFromContext(context)
                : GroupUtil.getRecipientsByGroup(source.group());
        if (computedRecipients.isEmpty() && target != null) {
            return List.of(target);
        }
        if (computedRecipients.isEmpty() && source.player() != null) {
            return List.of(source.player());
        }
        return computedRecipients;
    }

    public static List<Player> resolveRecipientsFromContext(ReplyRegistry.ReplyContext context) {
        if (context == null || context.recipients() == null || context.recipients().isEmpty()) {
            return List.of();
        }
        List<Player> recipients = new ArrayList<>();
        for (UUID recipientId : context.recipients()) {
            Velochat.getServer().getPlayer(recipientId).ifPresent(recipients::add);
        }
        return recipients;
    }
}
