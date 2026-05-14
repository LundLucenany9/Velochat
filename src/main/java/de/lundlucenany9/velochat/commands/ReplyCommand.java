package de.lundlucenany9.velochat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.lundlucenany9.velochat.GroupUtil;
import de.lundlucenany9.velochat.*;

import java.util.Map;
import java.util.UUID;
import java.util.Optional;

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

                                    ReplyRegistry.ReplyContext contextById = ReplyRegistry.get(targetName);
                                    if (contextById != null) {
                                        String playerGroup = GroupUtil.getGroup(source);
                                        String contextGroup = contextById.group();
                                        boolean sameGroup = playerGroup != null && playerGroup.equals(contextGroup);
                                        if (!sameGroup) {
                                            String template = MessagesUtil.template(
                                                    Velochat.getMessages().reply_context_invalid,
                                                    "<red>You cannot reply to that message.</red>"
                                            );
                                            source.sendMessage(MessageUtil.render(source, template, Map.of(
                                                    "player", contextById.senderName()
                                            )));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        if (contextById.origin() == ReplyRegistry.Origin.MINECRAFT) {
                                            resolveMinecraftPlayer(proxy, contextById).ifPresentOrElse(target ->
                                                    ReplyService.sendReply(source, target, message, contextById), () -> {
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
                                        ReplyService.sendReply(source, null, message, contextById);
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    proxy.getPlayer(targetName).ifPresentOrElse(target -> ReplyService.sendReply(source, target, message, null), () -> {
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

    private static Optional<Player> resolveMinecraftPlayer(ProxyServer proxy, ReplyRegistry.ReplyContext context) {
        try {
            UUID uuid = UUID.fromString(context.senderId());
            return proxy.getPlayer(uuid);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
