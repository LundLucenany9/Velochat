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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Registers and executes the {@code /mute} command.
 */
public class MuteCommand {
    public static BrigadierCommand getCommand(ProxyServer proxy) {
        LiteralCommandNode<CommandSource> muteCommand = BrigadierCommand.literalArgumentBuilder("mute")
                .requires(source -> !(source instanceof Player) || source.hasPermission("velochat.mute"))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            proxy.getAllPlayers().stream()
                                    .map(Player::getUsername)
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("duration", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("60s");
                                    builder.suggest("5m");
                                    builder.suggest("1h");
                                    builder.suggest("1d");
                                    builder.suggest("-1");
                                    return builder.buildFuture();
                                })
                                .then(BrigadierCommand.requiredArgumentBuilder("servers", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            proxy.getAllServers().stream()
                                                    .map(server -> server.getServerInfo().getName())
                                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> executeMute(proxy, context.getSource(),
                                                context.getArgument("player", String.class),
                                                context.getArgument("duration", String.class),
                                                context.getArgument("servers", String.class)))
                                )
                                .executes(context -> executeMute(proxy, context.getSource(),
                                        context.getArgument("player", String.class),
                                        context.getArgument("duration", String.class),
                                        null))
                        )
                ).build();
        return new BrigadierCommand(muteCommand);
    }

    private static int executeMute(ProxyServer proxy,
                                   CommandSource source,
                                   String targetName,
                                   String durationText,
                                   String serversText) {
        Long durationSeconds = parseDurationSeconds(durationText);
        if (durationSeconds == null) {
            source.sendMessage(MessageUtil.render(
                    source instanceof Player p ? p : null,
                    "<red>Invalid duration. Use values like 60s, 5m, 1h, 1d or -1.</red>",
                    null
            ));
            return Command.SINGLE_SUCCESS;
        }
        Set<String> servers = parseServers(serversText);
        proxy.getPlayer(targetName).ifPresentOrElse(target -> {
            Velochat.getMuteManager().mute(target.getUniqueId(), "manual", durationSeconds, servers);
            String template = MessagesUtil.template(
                    Velochat.getMessages().mute_applied,
                    "<green>Muted <target> for <duration>.</green>"
            );
            source.sendMessage(MessageUtil.render(
                    source instanceof Player p ? p : null,
                    template,
                    Map.of(
                            "target", target.getUsername(),
                            "duration", durationText
                    )
            ));
            String mutedTemplate = MessagesUtil.template(Velochat.getMessages().muted, "<red>You are muted.</red>");
            target.sendMessage(MessageUtil.render(target, mutedTemplate, Map.of(
                    "duration", durationText,
                    "reason", "manual"
            )));
        }, () -> {
            String template = MessagesUtil.template(
                    Velochat.getMessages().player_not_online,
                    "<red>Player not online.</red>"
            );
            source.sendMessage(MessageUtil.render(
                    source instanceof Player p ? p : null,
                    template,
                    Map.of("player", targetName)
            ));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static Long parseDurationSeconds(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if ("-1".equals(text)) {
            return -1L;
        }
        try {
            long value = Long.parseLong(text);
            return value >= 0 ? value : null;
        } catch (NumberFormatException ignored) {
        }
        if (text.length() < 2) {
            return null;
        }
        String number = text.substring(0, text.length() - 1);
        String unit = text.substring(text.length() - 1).toLowerCase();
        long value;
        try {
            value = Long.parseLong(number);
        } catch (NumberFormatException e) {
            return null;
        }
        if (value < 0) {
            return null;
        }
        return switch (unit) {
            case "s" -> value;
            case "m" -> TimeUnit.MINUTES.toSeconds(value);
            case "h" -> TimeUnit.HOURS.toSeconds(value);
            case "d" -> TimeUnit.DAYS.toSeconds(value);
            default -> null;
        };
    }

    private static Set<String> parseServers(String serversText) {
        if (serversText == null || serversText.isBlank()) {
            return Set.of();
        }
        String cleaned = serversText.replace(",", " ");
        Set<String> servers = new HashSet<>();
        Arrays.stream(cleaned.split("\\s+"))
                .filter(s -> !s.isBlank())
                .forEach(servers::add);
        return servers;
    }
}
