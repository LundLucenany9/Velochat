package de.lundlucenany9.velochat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.lundlucenany9.velochat.MessageUtil;
import de.lundlucenany9.velochat.MessagesUtil;
import de.lundlucenany9.velochat.Velochat;

import java.util.Map;

/**
 * Registers and executes the {@code /unblock} command.
 */
public class UnblockCommand {
    public static BrigadierCommand getCommand(ProxyServer proxy) {
        LiteralCommandNode<CommandSource> command = BrigadierCommand.literalArgumentBuilder("unblock")
                .requires(source -> source instanceof Player && source.hasPermission("velochat.block"))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            proxy.getAllPlayers().stream()
                                    .map(Player::getUsername)
                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Player sender = (Player) context.getSource();
                            String targetName = context.getArgument("player", String.class);
                            proxy.getPlayer(targetName).ifPresentOrElse(target -> {
                                Velochat.getBlockManager().unblock(sender.getUniqueId(), target.getUniqueId());
                                String template = MessagesUtil.template(
                                        Velochat.getMessages().block_removed,
                                        "<green>Unblocked <target>.</green>"
                                );
                                sender.sendMessage(MessageUtil.render(sender, template, Map.of(
                                        "target", target.getUsername()
                                )));
                            }, () -> {
                                String template = MessagesUtil.template(
                                        Velochat.getMessages().block_not_found,
                                        "<red>Player not found.</red>"
                                );
                                sender.sendMessage(MessageUtil.render(sender, template, Map.of(
                                        "player", targetName
                                )));
                            });
                            return Command.SINGLE_SUCCESS;
                        })
                ).build();
        return new BrigadierCommand(command);
    }
}
