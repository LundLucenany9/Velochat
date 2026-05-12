package de.lundlucenany9.velochat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.lundlucenany9.velochat.BroadcastTagResolver;
import de.lundlucenany9.velochat.Config;
import de.lundlucenany9.velochat.FontFilter;
import de.lundlucenany9.velochat.MessageUtil;
import de.lundlucenany9.velochat.Velochat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Registers and executes the {@code /broadcast} command.
 */
public class BroadcastCommand {
    private static final String DEFAULT_PREFIX = "<red>[Broadcast]</red> ";

    public static BrigadierCommand getCommand(ProxyServer proxy) {
        LiteralCommandNode<CommandSource> broadcastCommand = BrigadierCommand.literalArgumentBuilder("broadcast")
                .requires(source -> !(source instanceof Player) || source.hasPermission("velochat.broadcast"))
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            String message = context.getArgument("message", String.class);
                            Config config = Velochat.getConfig();
                            if (source instanceof Player player
                                    && FontFilter.shouldBlock(player, message, config)) {
                                String template = Velochat.getMessages().font_blocked;
                                if (template == null || template.isBlank()) {
                                    template = "<red>Your message contains unsupported fonts.</red>";
                                }
                                player.sendMessage(MessageUtil.render(player, template, null));
                                return Command.SINGLE_SUCCESS;
                            }
                            String prefix = config.getBroadcastPrefix();
                            if (prefix == null || prefix.isBlank()) {
                                prefix = DEFAULT_PREFIX;
                            }
                            String sender = source instanceof Player player ? player.getUsername() : "Console";
                            Component messageComponent = MiniMessage.miniMessage().deserialize(message);
                            String format = prefix.contains("<message>") ? prefix : prefix + "<message>";
                            Component component = MiniMessage.miniMessage().deserialize(
                                    format,
                                    new BroadcastTagResolver(sender, messageComponent)
                            );
                            proxy.getAllPlayers().forEach(target -> target.sendMessage(component));
                            return Command.SINGLE_SUCCESS;
                        })
                ).build();
        return new BrigadierCommand(broadcastCommand);
    }
}
