package de.lundlucenany9.velochat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.lundlucenany9.velochat.Config;
import de.lundlucenany9.velochat.ChatTagResolver;
import de.lundlucenany9.velochat.FontFilter;
import de.lundlucenany9.velochat.ReplyFormatUtil;
import de.lundlucenany9.velochat.ReplyRegistry;
import de.lundlucenany9.velochat.ReplyTagResolver;
import de.lundlucenany9.velochat.SingleTagResolver;
import de.lundlucenany9.velochat.Velochat;
import de.lundlucenany9.velochat.MessageUtil;
import de.lundlucenany9.velochat.MessagesUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;
import java.util.UUID;

import java.util.concurrent.CompletableFuture;

/**
 * Registers and executes the {@code /reply} command.
 */
public final class ReplyCommand {
    public static BrigadierCommand getCommand(final ProxyServer proxy) {
        LiteralCommandNode<CommandSource> replyCommand = BrigadierCommand.literalArgumentBuilder("reply")
                .requires(source -> source instanceof Player && source.hasPermission("velochat.reply"))
                .then(BrigadierCommand.requiredArgumentBuilder("target", StringArgumentType.word())
                        .suggests((ctx, builder)->{
                            proxy.getAllPlayers().stream().map(Player::getUsername)
                                    .filter(n->n.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    Player source = (Player) context.getSource();
                                    String targetName = context.getArgument("target", String.class);
                                    String message = context.getArgument("message", String.class);
                                    Config config = Velochat.getConfig();

                                    ReplyRegistry.ReplyContext contextById = ReplyRegistry.get(targetName);
                                    if (contextById != null) {
                                        if (contextById.recipients() != null
                                                && !contextById.recipients().contains(source.getUniqueId())) {
                                            String template = MessagesUtil.template(
                                                    Velochat.getMessages().reply_context_invalid,
                                                    "<red>You cannot reply to that message.</red>"
                                            );
                                            source.sendMessage(MessageUtil.render(source, template, Map.of(
                                                    "player", contextById.senderName()
                                            )));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        proxy.getPlayer(contextById.senderId()).ifPresentOrElse(target ->
                                                sendReply(config, source, target, message, contextById),
                                                () -> {
                                                    String template = MessagesUtil.template(
                                                            Velochat.getMessages().player_not_online,
                                                            "<red>Player not online.</red>"
                                                    );
                                                    source.sendMessage(MessageUtil.render(source, template, Map.of(
                                                            "player", contextById.senderName()
                                                    )));
                                                });
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    proxy.getPlayer(targetName).ifPresentOrElse(target -> sendReply(config, source, target, message, null), () -> {
                                                String template = MessagesUtil.template(
                                                        Velochat.getMessages().player_not_online,
                                                        "<red>Player not online.</red>"
                                                );
                                                source.sendMessage(MessageUtil.render(source, template, Map.of(
                                                        "player", targetName
                                                )));
                                            }
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                ).build();
        return new BrigadierCommand(replyCommand);
    }

    private static void sendReply(Config config,
                                  Player source,
                                  Player target,
                                  String message,
                                  ReplyRegistry.ReplyContext context) {
        if (FontFilter.shouldBlock(source, message, config)) {
            String template = MessagesUtil.template(
                    Velochat.getMessages().font_blocked,
                    "<red>Your message contains unsupported fonts.</red>"
            );
            source.sendMessage(MessageUtil.render(source, template, null));
            return;
        }
        java.util.List<Player> computedRecipients = Velochat.parser.getRecipients(source);
        if (computedRecipients.isEmpty()) {
            computedRecipients = java.util.List.of(target);
        }
        final java.util.List<Player> recipients = computedRecipients;
        java.util.Set<UUID> recipientIds = new java.util.HashSet<>();
        for (Player recipient : recipients) {
            recipientIds.add(recipient.getUniqueId());
        }
        String newReplyId = ReplyRegistry.register(source, message, recipientIds);
        ReplyRegistry.ReplyContext newReplyContext = ReplyRegistry.get(newReplyId);
        String snippet = context == null ? "" : context.snippet();
        String replyId = context == null ? "" : context.id();
        Component fullMessage = context == null ? Component.empty() : context.fullMessage();
        TagResolver headerResolver = TagResolver.resolver(
                new SingleTagResolver(target, ""),
                new ReplyTagResolver(replyId, snippet, fullMessage)
        );

        String replyFormat = ReplyFormatUtil.applyTokens(config.getReplyFormat(), context);
        CompletableFuture<Component> header =
                Velochat.parser.parseWithResolver(replyFormat, target, headerResolver);

        String normalFormat = ReplyFormatUtil.applyTokens(config.getFormat(), newReplyContext);
        String blockedFormatTemplate = config.getBlockedReplyFormat() == null
                ? config.getFormat()
                : config.getBlockedReplyFormat();
        String blockedFormat = ReplyFormatUtil.applyTokens(blockedFormatTemplate, newReplyContext);
        TagResolver bodyResolver = TagResolver.resolver(new ChatTagResolver(source, message, newReplyId, message));
        CompletableFuture<Component> normalBody =
                Velochat.parser.parseWithResolver(normalFormat, source, bodyResolver)
                        .thenApply(component -> {
                            ReplyRegistry.updateFullMessage(newReplyId, component);
                            return component;
                        });
        CompletableFuture<Component> blockedBody =
                blockedFormat.equals(normalFormat)
                        ? normalBody
                        : Velochat.parser.parseWithResolver(blockedFormat, source, bodyResolver);

        CompletableFuture<Component> prefixFuture =
                CompletableFuture.completedFuture(MiniMessage.miniMessage().deserialize(config.getReplayMessagePrefix()));

        header.thenCombine(normalBody, (h, b) -> h.appendNewline()).thenCombine(prefixFuture, Component::append)
                .thenCombine(normalBody, Component::append)
                .thenAccept(normalResult -> {
                    Component blockedResult = header.join()
                            .appendNewline()
                            .append(prefixFuture.join())
                            .append(blockedBody.join());
                    for (Player recipient : recipients) {
                        boolean blocked = Velochat.getBlockManager().isBlockedEither(
                                source.getUniqueId(), recipient.getUniqueId());
                        recipient.sendMessage(blocked ? blockedResult : normalResult);
                    }
                    target.playSound(Sound.sound(Key.key("minecraft", "bell"), Sound.Source.MASTER, 1, 1));
                }).exceptionally(ex -> {
            source.sendMessage(Component.text("Formatting error").color(NamedTextColor.RED));
            Velochat.getLogger().warn("Formating Error: {}", ex.getLocalizedMessage());
            return null;
        });
    }

}
