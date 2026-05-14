package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.lundlucenanny9.papisocketbridge.api.PlaceholderBridgeApi;
import de.lundlucenanny9.papisocketbridge.api.PlaceholderBridgeProvider;
import de.lundlucenany9.velochat.discord.Bot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses chat and private-message templates into Adventure components.
 */
public class FormatParser {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[^%]+%");
    private final Config config;
    private final ProxyServer proxy;
    private volatile PlaceholderBridgeApi placeholderApi;
    private final Object proxyBridgeApi;
    private final Object plugin;
    private final Logger logger;
    public FormatParser(Config config, ProxyServer proxy, Object plugin, Logger logger) {
        this.config = config;
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = logger;
        PlaceholderBridgeApi socketApi = null;
        Object proxyApi = null;
        if (config.isUse_papi_socket_bridge()) {
            socketApi = PlaceholderBridgeProvider.get(proxy).orElse(null);
            if (socketApi == null) {
                logger.warn("PAPISocketBridge enabled but API not available; falling back to PAPIProxyBridge.");
            }
        }
        if (socketApi == null && config.isUse_papi_proxy_bridge()) {
            proxyApi = createProxyBridgeApi(logger);
            if (proxyApi != null) {
                logger.info("Using PAPIProxyBridge placeholder resolution fallback.");
            }
        }
        this.placeholderApi = socketApi;
        this.proxyBridgeApi = proxyApi;
    }

    public CompletableFuture<Component> parse(String input, String format, Player player) {
        return parseWithResolver(format, player, new SingleTagResolver(player, input));
    }

    public CompletableFuture<Component> parseWithResolver(String format, Player player, TagResolver resolver) {
        PlaceholderBridgeApi socketApi = getPlaceholderApi();
        if (config.isUse_papi_socket_bridge() && socketApi != null) {
            return resolvePlaceholders(format, player).thenApply(resolved ->
                    MiniMessage.miniMessage().deserialize(
                            resolved,
                            resolver
                    )
            );
        }
        if (proxyBridgeApi != null) {
            return resolveProxyBridge(format, player)
                    .thenApply(this::sanitizePlaceholderValue)
                    .thenApply(resolved ->
                            MiniMessage.miniMessage().deserialize(
                                    resolved,
                                    resolver
                            )
                    );
        }

        return CompletableFuture.completedFuture(
                MiniMessage.miniMessage().deserialize(
                        format,
                        resolver
                )
        );
    }
    public CompletableFuture<Component> parseWithResolver(String format, TagResolver resolver) {
        return CompletableFuture.completedFuture(
                MiniMessage.miniMessage().deserialize(
                        format,
                        resolver
                )
        );
    }

    private CompletableFuture<String> resolvePlaceholders(String format, Player player) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(format);
        Map<String, CompletableFuture<String>> futures = new LinkedHashMap<>();
        while (matcher.find()) {
            String placeholder = matcher.group();
            if (!futures.containsKey(placeholder)) {
                PlaceholderBridgeApi socketApi = getPlaceholderApi();
                if (socketApi == null) {
                    futures.put(placeholder, CompletableFuture.completedFuture(placeholder));
                } else {
                    futures.put(placeholder, socketApi.resolvePlaceholder(player.getUniqueId(), placeholder));
                }
            }
        }
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(format);
        }
        List<CompletableFuture<String>> futureList = new ArrayList<>(futures.values());
        return CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    String resolved = format;
                    for (Map.Entry<String, CompletableFuture<String>> entry : futures.entrySet()) {
                    String value = entry.getValue().join();
                    resolved = resolved.replace(entry.getKey(), sanitizePlaceholderValue(value));
                    }
                    return resolved;
                });
    }

    private static Object createProxyBridgeApi(Logger logger) {
        try {
            Class<?> apiClass = Class.forName("net.william278.papiproxybridge.api.PlaceholderAPI");
            Method createInstance = apiClass.getMethod("createInstance");
            return createInstance.invoke(null);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            logger.warn("Failed to initialize PAPIProxyBridge API: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<String> resolveProxyBridge(String format, Player player) {
        try {
            Method method = proxyBridgeApi.getClass().getMethod("formatPlaceholders", String.class, UUID.class);
            Object result = method.invoke(proxyBridgeApi, format, player.getUniqueId());
            if (result instanceof CompletableFuture) {
                return (CompletableFuture<String>) result;
            }
        } catch (ReflectiveOperationException e) {
            logger.warn("PAPIProxyBridge placeholder resolution failed: {}", e.getMessage());
        }
        return CompletableFuture.completedFuture(format);
    }

    private String sanitizePlaceholderValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.stripTrailing();
        if (trimmed.indexOf('\u00A7') < 0) {
            return trimmed;
        }
        Component legacy = LegacyComponentSerializer.legacySection().deserialize(trimmed);
        return MiniMessage.miniMessage().serialize(legacy);
    }

    private PlaceholderBridgeApi getPlaceholderApi() {
        if (placeholderApi != null || !config.isUse_papi_socket_bridge()) {
            return placeholderApi;
        }
        placeholderApi = PlaceholderBridgeProvider.get(proxy).orElse(null);
        if (placeholderApi == null) {
            logger.warn("PAPISocketBridge API not available; placeholders will not be resolved.");
        } else {
            logger.info("PAPISocketBridge API detected.");
        }
        return placeholderApi;
    }

    public void sendChat(String msg, Player player) {
        List<Player> recipients = GroupUtil.getRecipients(player);
        String group = GroupUtil.getGroup(player);
        boolean hasDiscordTarget = group != null
                && !group.isBlank()
                && config.isDiscordEnabled()
                && config.getDiscordGroupMappings().containsKey(group);
        if (recipients.isEmpty() && !hasDiscordTarget) {
            return;
        }
        Set<UUID> recipientIds = new java.util.HashSet<>();
        for (Player target : recipients) {
            recipientIds.add(target.getUniqueId());
        }
        String replyId = ReplyRegistry.register(player.getUniqueId().toString(), player.getUsername(), msg, recipientIds, ReplyRegistry.Origin.MINECRAFT, group);
        ReplyRegistry.ReplyContext replyContext = ReplyRegistry.get(replyId);
        String snippet = replyContext == null ? "" : replyContext.snippet();
        TagResolver resolver = TagResolver.resolver(new ChatTagResolver(player, msg, replyId, snippet));
        String format = ReplyFormatUtil.applyTokens(config.format, replyContext);
        String blockedFormat = ReplyFormatUtil.applyTokens(config.getBlockedFormat(), replyContext);
        CompletableFuture<Component> normalFuture = parseWithResolver(format, player, resolver)
                .exceptionally(ex -> {
                    logger.warn("Failed to render chat message: {}", ex.getMessage());
                    return Component.text(msg);
                });
        CompletableFuture<Component> blockedFuture =
                (blockedFormat != null && !blockedFormat.isBlank())
                        ? parseWithResolver(blockedFormat, player, resolver)
                        .exceptionally(ex -> {
                            logger.warn("Failed to render blocked chat message: {}", ex.getMessage());
                            return null;
                        })
                        : CompletableFuture.completedFuture(null);
        CompletableFuture<String> discordMessageIdFuture = Bot.getInstance()
                .sendMessage(group, msg, player.getUsername())
                .exceptionally(ex -> {
                    logger.warn("Failed to mirror chat message to Discord: {}", ex.getMessage());
                    return null;
                });
        normalFuture.thenCombine(blockedFuture, (component, blockedComponent) -> {
            discordMessageIdFuture
                    .thenAccept(discordMessageId ->
                            ReplyRegistry.updateFullMessage(replyId, component, ReplyRegistry.Origin.MINECRAFT, discordMessageId))
                    .exceptionally(ex -> {
                        logger.warn("Failed to update reply context with Discord message id: {}", ex.getMessage());
                        return null;
                    });
            proxy.getScheduler().buildTask(plugin, () -> {
                if (config.isGlobal_chat()) {
                    sendToRecipients(player, recipients, component, blockedComponent);
                } else if (config.isBroadcast_message()) {
                    sendToRecipients(player, recipients, component, blockedComponent);
                }
            }).schedule();
            return null;
        }).exceptionally(ex -> {
            logger.warn("Failed to deliver chat message: {}", ex.getMessage());
            return null;
        });
    }

    public void sendDiscordChat(Message message, Member author) {
        List<Player> recipients = GroupUtil.getRecipients(message.getChannelId());
        Set<UUID> recipientIds = new java.util.HashSet<>();
        for (Player target : recipients) {
            recipientIds.add(target.getUniqueId());
        }
        String replyId = ReplyRegistry.register(author.getId(), author.getEffectiveName(),message.getContentRaw(), recipientIds, ReplyRegistry.Origin.DISCORD, GroupUtil.getGroup(message.getChannelId()));
        ReplyRegistry.ReplyContext replyContext = ReplyRegistry.get(replyId);
        String snippet = replyContext == null ? "" : replyContext.snippet();
        TagResolver resolver = TagResolver.resolver(new DiscordTagResolver(author, message.getContentStripped(), replyId, snippet));
        String format = ReplyFormatUtil.applyTokens(config.getDiscordMessageFormat(), replyContext);
        CompletableFuture<Component> normalFuture = parseWithResolver(format, resolver)
                .exceptionally(ex -> {
                    logger.warn("Failed to render chat message: {}", ex.getMessage());
                    return Component.text(message.getContentStripped());
                });
        normalFuture.thenAccept((component) -> {
            ReplyRegistry.updateFullMessage(replyId, component, ReplyRegistry.Origin.DISCORD, message.getId());
            proxy.getScheduler().buildTask(plugin, () -> {
                if (config.isGlobal_chat()) {
                    sendToRecipients(recipients, component);
                }
            }).schedule();

        });
    }


    private void sendToRecipients(Player sender,
                                  List<Player> recipients,
                                  Component normalMessage,
                                  Component blockedMessage) {
        for (Player target : recipients) {
            if (Velochat.getBlockManager().isBlockedEither(
                    sender.getUniqueId(), target.getUniqueId())) {
                if (blockedMessage != null) {
                    target.sendMessage(blockedMessage);
                } else {
                    target.sendMessage(normalMessage);
                }
            } else {
                target.sendMessage(normalMessage);
            }
        }
    }
    private void sendToRecipients(
                                  List<Player> recipients,
                                  Component normalMessage) {
        for (Player target : recipients) {
            target.sendMessage(normalMessage);
        }
    }


}
