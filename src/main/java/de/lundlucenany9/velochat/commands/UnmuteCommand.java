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
 * Registers and executes the {@code /unmute} command.
 */
public class UnmuteCommand {
    public static BrigadierCommand getCommand(ProxyServer proxy) {
        LiteralCommandNode<CommandSource> unmuteCommand = BrigadierCommand.literalArgumentBuilder("unmute")
                .requires(source -> !(source instanceof Player) || source.hasPermission("velochat.mute"))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            proxy.getAllPlayers().stream()
                                    .map(Player::getUsername)
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String targetName = context.getArgument("player", String.class);
                            proxy.getPlayer(targetName).ifPresentOrElse(target -> {
                                Velochat.getMuteManager().unmute(target.getUniqueId());
                                String template = MessagesUtil.template(
                                        Velochat.getMessages().mute_removed,
                                        "<green>Unmuted <target>.</green>"
                                );
                                context.getSource().sendMessage(MessageUtil.render(
                                        context.getSource() instanceof Player p ? p : null,
                                        template,
                                        Map.of("target", target.getUsername())
                                ));
                                target.sendMessage(MessageUtil.render(target, "<green>You have been unmuted.</green>", null));
                            }, () -> {
                                String template = MessagesUtil.template(
                                        Velochat.getMessages().player_not_online,
                                        "<red>Player not online.</red>"
                                );
                                context.getSource().sendMessage(MessageUtil.render(
                                        context.getSource() instanceof Player p ? p : null,
                                        template,
                                        Map.of("player", targetName)
                                ));
                            });
                            return Command.SINGLE_SUCCESS;
                        })
                ).build();
        return new BrigadierCommand(unmuteCommand);
    }
}
