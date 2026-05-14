package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;
import de.lundlucenany9.velochat.discord.Bot;
import net.dv8tion.jda.api.entities.Member;
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
                              Player player,
                              Member discordMember) {
        public static ReplySource minecraft(Player player) {
            return new ReplySource(
                    player.getUniqueId().toString(),
                    player.getUsername(),
                    ReplyRegistry.Origin.MINECRAFT,
                    GroupUtil.getGroup(player),
                    player,
                    null
            );
        }
        public static ReplySource discord(Member member, String group) {
            return new ReplySource(
                    member.getId(),
                    member.getEffectiveName(),
                    ReplyRegistry.Origin.DISCORD,
                    group,
                    null,
                    member
            );
        }
    }

    private ReplyService() {}

    public static void sendReply(Player source, Player target, String message, ReplyRegistry.ReplyContext context) {
        sendReply(ReplySource.minecraft(source), target, message, context);
    }

    public static void sendReply(ReplySource source, Player target, String message, ReplyRegistry.ReplyContext context) {
        Config config = Velochat.getConfig();
        if (source.player() != null && FontFilter.shouldBlock(source.player(), message, config)) {
            String template = MessagesUtil.template(Velochat.getMessages().font_blocked,
                    "<red>Your message contains unsupported fonts.</red>");
            source.player().sendMessage(MessageUtil.render(source.player(), template, null));
            return;
        }

        List<Player> recipients = computeRecipients(source, target, context);
        Set<UUID> recipientIds = new java.util.HashSet<>();
        for (Player recipient : recipients) recipientIds.add(recipient.getUniqueId());

        String replyGroup = context == null ? source.group() : context.group();
        String newReplyId = ReplyRegistry.register(source.senderId(), source.senderName(), message,
                recipientIds, source.origin(), replyGroup);
        ReplyRegistry.ReplyContext newReplyContext = ReplyRegistry.get(newReplyId);

        String snippet = context == null ? "" : context.snippet();
        String replyId = context == null ? "" : context.id();
        Component fullMessage = context == null ? Component.empty() : context.fullMessage();

        // Discord context: retrieve member via REST (no GUILD_MEMBERS intent = empty cache)
        if (context != null && context.origin() == ReplyRegistry.Origin.DISCORD) {
            de.lundlucenany9.velochat.discord.DiscordBot bot = Bot.getInstance();
            if (bot instanceof de.lundlucenany9.velochat.discord.EnabledDiscordBot enabledBot) {
                net.dv8tion.jda.api.entities.Guild guild = enabledBot.getJda()
                        .getGuildById(Velochat.getConfig().getDiscordGuild());
                if (guild != null) {
                    final ReplyRegistry.ReplyContext fc = context;
                    final String fRid = replyId, fSnip = snippet;
                    final Component fFull = fullMessage;
                    guild.retrieveMemberById(context.senderId()).queue(
                            member -> finishSendReply(source, target, message, fc,
                                    TagResolver.resolver(
                                            new DiscordTagResolver(member, fSnip, fRid, fSnip),
                                            new ReplyTagResolver(fRid, fSnip, fFull)),
                                    recipients, newReplyId, newReplyContext),
                            err -> finishSendReply(source, target, message, fc,
                                    TagResolver.resolver(
                                            new ContextTagResolver(fc.senderName()),
                                            new ReplyTagResolver(fRid, fSnip, fFull)),
                                    recipients, newReplyId, newReplyContext)
                    );
                    return;
                }
            }
        }

        // MC context or Discord unavailable
        TagResolver headerResolver;
        if (target != null) {
            headerResolver = TagResolver.resolver(
                    new SingleTagResolver(target, ""),
                    new ReplyTagResolver(replyId, snippet, fullMessage));
        } else {
            headerResolver = TagResolver.resolver(
                    new ContextTagResolver(context == null ? "" : context.senderName()),
                    new ReplyTagResolver(replyId, snippet, fullMessage));
        }
        finishSendReply(source, target, message, context, headerResolver, recipients, newReplyId, newReplyContext);
    }

    private static void finishSendReply(
            ReplySource source, Player target, String message,
            ReplyRegistry.ReplyContext context, TagResolver headerResolver,
            List<Player> recipients, String newReplyId, ReplyRegistry.ReplyContext newReplyContext) {

        Config config = Velochat.getConfig();

        // PAPI context: use original MC sender when Discord replies to MC message
        Player papiContextPlayer = source.player();
        if (papiContextPlayer == null && context != null && context.origin() == ReplyRegistry.Origin.MINECRAFT) {
            try {
                UUID id = UUID.fromString(context.senderId());
                papiContextPlayer = Velochat.getServer().getPlayer(id).orElse(null);
            } catch (IllegalArgumentException ignored) {}
        }
        final Player resolvedPapiPlayer = papiContextPlayer;

        boolean discordSource = source.origin() == ReplyRegistry.Origin.DISCORD;
        boolean mcContext = context != null && context.origin() == ReplyRegistry.Origin.MINECRAFT;

        // Header: style of original message
        String replyFormat = ReplyFormatUtil.applyTokens(
                mcContext ? config.getReplyFormat() : config.getDiscordReplyFormat(), context);
        CompletableFuture<Component> header = target != null
                ? Velochat.parser.parseWithResolver(replyFormat, target, headerResolver)
                : resolvedPapiPlayer != null
                        ? Velochat.parser.parseWithResolver(replyFormat, resolvedPapiPlayer, headerResolver)
                        : Velochat.parser.parseWithResolver(replyFormat, headerResolver);

        // Body: style of the reply sender
        String normalFormat = ReplyFormatUtil.applyTokens(
                discordSource ? config.getDiscordMessageFormat() : config.getFormat(), newReplyContext);
        String blockedFormat = ReplyFormatUtil.applyTokens(
                config.getBlockedReplyFormat() == null ? config.getFormat() : config.getBlockedReplyFormat(),
                newReplyContext);

        TagResolver bodyResolver = source.player() != null
                ? TagResolver.resolver(new ChatTagResolver(source.player(), message, newReplyId, message))
                : source.discordMember() != null
                        ? TagResolver.resolver(new DiscordTagResolver(source.discordMember(), message, newReplyId, message))
                        : TagResolver.resolver(new GenericChatTagResolver(
                                source.senderName(), source.group(), message, newReplyId, message));

        CompletableFuture<String> discordFuture = source.origin() == ReplyRegistry.Origin.MINECRAFT
                ? Bot.getInstance().sendMessage(source.group(), message, source.senderName())
                        .exceptionally(ex -> {
                            Velochat.getLogger().warn("Failed to mirror reply to Discord: {}", ex.getMessage());
                            return null;
                        })
                : CompletableFuture.completedFuture(null);

        CompletableFuture<Component> normalBody =
                (resolvedPapiPlayer != null
                        ? Velochat.parser.parseWithResolver(normalFormat, resolvedPapiPlayer, bodyResolver)
                        : Velochat.parser.parseWithResolver(normalFormat, bodyResolver))
                        .thenApply(c -> {
                            discordFuture.thenAccept(id ->
                                    ReplyRegistry.updateFullMessage(newReplyId, c, source.origin(), id));
                            return c;
                        });

        CompletableFuture<Component> blockedBody = blockedFormat.equals(normalFormat) ? normalBody
                : (resolvedPapiPlayer != null
                        ? Velochat.parser.parseWithResolver(blockedFormat, resolvedPapiPlayer, bodyResolver)
                        : Velochat.parser.parseWithResolver(blockedFormat, bodyResolver));

        CompletableFuture<Component> prefixFuture = CompletableFuture.completedFuture(
                MiniMessage.miniMessage().deserialize(config.getReplayMessagePrefix()));

        header.thenCombine(normalBody, (h, b) -> h.appendNewline())
                .thenCombine(prefixFuture, Component::append)
                .thenCombine(normalBody, Component::append)
                .thenAccept(normalResult -> {
                    Component blockedResult = header.join().appendNewline()
                            .append(prefixFuture.join()).append(blockedBody.join());
                    for (Player recipient : recipients) {
                        boolean blocked = source.player() != null && Velochat.getBlockManager()
                                .isBlockedEither(source.player().getUniqueId(), recipient.getUniqueId());
                        recipient.sendMessage(blocked ? blockedResult : normalResult);
                    }
                    if (target != null)
                        target.playSound(Sound.sound(Key.key("minecraft", "bell"), Sound.Source.MASTER, 1, 1));
                }).exceptionally(ex -> {
                    if (source.player() != null)
                        source.player().sendMessage(Component.text("Formatting error").color(NamedTextColor.RED));
                    Velochat.getLogger().warn("Formatting error in sendReply: {}", ex.getLocalizedMessage());
                    return null;
                });
    }

    private static List<Player> computeRecipients(ReplySource source, Player target, ReplyRegistry.ReplyContext context) {
        List<Player> computed = context != null && context.origin() == ReplyRegistry.Origin.DISCORD
                ? resolveRecipientsFromContext(context)
                : GroupUtil.getRecipientsByGroup(source.group());
        if (computed.isEmpty() && target != null) return List.of(target);
        if (computed.isEmpty() && source.player() != null) return List.of(source.player());
        return computed;
    }

    public static List<Player> resolveRecipientsFromContext(ReplyRegistry.ReplyContext context) {
        if (context == null || context.recipients() == null || context.recipients().isEmpty()) return List.of();
        List<Player> recipients = new ArrayList<>();
        for (UUID id : context.recipients()) Velochat.getServer().getPlayer(id).ifPresent(recipients::add);
        return recipients;
    }
}
