package de.lundlucenany9.velochat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.lundlucenany9.velochat.Config;
import de.lundlucenany9.velochat.MessageUtil;
import de.lundlucenany9.velochat.ReplyRegistry;
import de.lundlucenany9.velochat.Velochat;

import java.util.Map;

/**
 * Registers and executes the {@code /r} command.
 */
public final class ReplyLastCommand {
    public static BrigadierCommand getCommand(ProxyServer proxy) {
        LiteralCommandNode<CommandSource> replyCommand = BrigadierCommand.literalArgumentBuilder("r")
                .requires(source -> source instanceof Player && source.hasPermission("velochat.replylast"))
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            Player sender = (Player) context.getSource();
                            String message = context.getArgument("message", String.class);
                            ReplyRegistry.LastPrivateContact lastContact =
                                    ReplyRegistry.getLastPrivateContact(sender.getUniqueId());
                            if (lastContact == null) {
                                String template = Velochat.getMessages().no_recent_message;
                                if (template == null || template.isBlank()) {
                                    template = "<red>You have no one to reply to.</red>";
                                }
                                sender.sendMessage(MessageUtil.render(sender, template, null));
                                return Command.SINGLE_SUCCESS;
                            }

                            Config config = Velochat.getConfig();
                            proxy.getPlayer(lastContact.senderId()).ifPresentOrElse(target -> MsgCommand.sendPrivateMessage(sender, target, message, config), () -> {
                                String template = Velochat.getMessages().player_not_online;
                                if (template == null || template.isBlank()) {
                                    template = "<red>Player not online.</red>";
                                }
                                sender.sendMessage(MessageUtil.render(sender, template, Map.of(
                                        "player", lastContact.senderName()
                                )));
                            });
                            return Command.SINGLE_SUCCESS;
                        })
                ).build();
        return new BrigadierCommand(replyCommand);
    }
}
