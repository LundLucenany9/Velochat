package de.lundlucenany9.velochat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import de.lundlucenany9.velochat.MessageUtil;
import de.lundlucenany9.velochat.MessagesUtil;
import de.lundlucenany9.velochat.Velochat;

/**
 * Registers and executes the {@code /velochatreload} command.
 */
public class ReloadCommand {
    public static BrigadierCommand getCommand(Velochat plugin) {
        LiteralCommandNode<CommandSource> reloadCommand = BrigadierCommand.literalArgumentBuilder("velochatreload")
                .requires(source -> !(source instanceof Player) || source.hasPermission("velochat.reload"))
                .executes(context -> {
                    plugin.reload();
                    String template = MessagesUtil.template(
                            Velochat.getMessages().reload_done,
                            "<green>Velochat config reloaded.</green>"
                    );
                    context.getSource().sendMessage(MessageUtil.render(
                            context.getSource() instanceof Player p ? p : null,
                            template,
                            null
                    ));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
        return new BrigadierCommand(reloadCommand);
    }
}
