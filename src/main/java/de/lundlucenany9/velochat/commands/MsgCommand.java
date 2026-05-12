package de.lundlucenany9.velochat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.lundlucenany9.velochat.Config;
import de.lundlucenany9.velochat.FontFilter;
import de.lundlucenany9.velochat.MessageUtil;
import de.lundlucenany9.velochat.MessageTagResolver;
import de.lundlucenany9.velochat.MessagesUtil;
import de.lundlucenany9.velochat.ReplyRegistry;
import de.lundlucenany9.velochat.Velochat;

import java.util.Map;

/**
 * Registers and executes the {@code /msg} command.
 */
public class MsgCommand {
    private static final String DEFAULT_SENDER_FORMAT =
            "<dark_gray>[<gray>you</gray> -> <yellow><receiver></yellow>]</dark_gray> <gray><message></gray>";
    private static final String DEFAULT_RECEIVER_FORMAT =
            "<dark_gray>[<yellow><sender></yellow> -> <gray>you</gray>]</dark_gray> <gray><message></gray>";

    public static BrigadierCommand getCommand(ProxyServer proxy) {
        LiteralCommandNode<CommandSource> msgCommand = BrigadierCommand.literalArgumentBuilder("msg")
                .requires(source -> source instanceof Player && source.hasPermission("velochat.msg"))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            proxy.getAllPlayers().stream()
                                    .map(Player::getUsername)
                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    Player sender = (Player) context.getSource();
                                    String targetName = context.getArgument("player", String.class);
                                    String message = context.getArgument("message", String.class);
                                    Config config = Velochat.getConfig();
                                    proxy.getPlayer(targetName).ifPresentOrElse(target -> sendPrivateMessage(sender, target, message, config),
                                            () -> {
                                                String template = MessagesUtil.template(
                                                        Velochat.getMessages().player_not_online,
                                                        "<red>Player not online.</red>"
                                                );
                                                sender.sendMessage(MessageUtil.render(sender, template, Map.of(
                                                        "player", targetName
                                                )));
                                            }
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                ).build();
        return new BrigadierCommand(msgCommand);
    }

    static void sendPrivateMessage(Player sender, Player target, String message, Config config) {
        if (Velochat.getBlockManager().hasBlocked(sender.getUniqueId(), target.getUniqueId())) {
            String template = MessagesUtil.template(Velochat.getMessages().blocked_self, "<red>You blocked <target>.</red>");
            sender.sendMessage(MessageUtil.render(sender, template, Map.of(
                    "target", target.getUsername()
            )));
            return;
        }
        if (Velochat.getBlockManager().hasBlocked(target.getUniqueId(), sender.getUniqueId())) {
            String template = MessagesUtil.template(Velochat.getMessages().blocked_other, "<red>You are blocked by <target>.</red>");
            sender.sendMessage(MessageUtil.render(sender, template, Map.of(
                    "target", target.getUsername()
            )));
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            String template = MessagesUtil.template(
                    Velochat.getMessages().cannot_message_self,
                    "<red>You cannot message yourself.</red>"
            );
            sender.sendMessage(MessageUtil.render(sender, template, Map.of(
                    "player", target.getUsername()
            )));
            return;
        }
        if (FontFilter.shouldBlock(sender, message, config)) {
            String template = MessagesUtil.template(
                    Velochat.getMessages().font_blocked,
                    "<red>Your message contains unsupported fonts.</red>"
            );
            sender.sendMessage(MessageUtil.render(sender, template, null));
            return;
        }
        MessageTagResolver resolver = new MessageTagResolver(sender, target, message);
        String senderFormat = config.getMsgFormatSender();
        String receiverFormat = config.getMsgFormatReceiver();
        if (senderFormat == null || senderFormat.isBlank()) {
            senderFormat = DEFAULT_SENDER_FORMAT;
        }
        if (receiverFormat == null || receiverFormat.isBlank()) {
            receiverFormat = DEFAULT_RECEIVER_FORMAT;
        }
        Velochat.parser.parseWithResolver(senderFormat, sender, resolver)
                .thenAccept(sender::sendMessage);
        Velochat.parser.parseWithResolver(receiverFormat, target, resolver)
                .thenAccept(target::sendMessage);
        ReplyRegistry.registerPrivateMessage(sender, target);
    }
}
