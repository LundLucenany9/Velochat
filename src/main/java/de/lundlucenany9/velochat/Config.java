package de.lundlucenany9.velochat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds configuration values loaded from {@code config.toml}.
 */
public class Config {
    public List<String> getServers() {
        return servers == null ? List.of() : servers;
    }
    public Map<String, List<String>> getChatGroups() {
        return chat_groups == null ? Collections.emptyMap() : chat_groups;
    }

    public boolean isGlobal_chat() {
        return global_chat;
    }

    public boolean isUse_minimessage() {
        return use_minimessage;
    }

    public boolean isUse_papi_socket_bridge() {
        return use_papi_socket_bridge || use_papi_proxy_bridge;
    }

    public boolean isUse_papi_proxy_bridge() {
        return use_papi_proxy_bridge;
    }

    public String getFormat() {
        return format != null ? format : "<message>";
    }
    public boolean isBlacklist() {
        return blacklist;
    }
    public int getForward_mode() {
        return forward_mode;
    }

    public boolean isBroadcast_message() {
        return broadcast_message;
    }

    public String getReplyFormat() {
        return replyFormat;
    }
    public String getReplayMessagePrefix() {
        return replayMessagePrefix;
    }
    public String getMsgFormatSender() {
        return msgFormatSender;
    }

    public String getMsgFormatReceiver() {
        return msgFormatReceiver;
    }
    public String getBroadcastPrefix() {
        return broadcastPrefix;
    }
    public String getBlockedFormat() {
        return blocked_format;
    }
    public String getBlockedReplyFormat() {
        return blocked_reply_format;
    }
    public boolean isAntiSpamEnabled() {
        return anti_spam_enabled;
    }
    public int getAntiSpamIntervalMillis() {
        return anti_spam_interval_millis;
    }
    public int getAntiSpamMuteSeconds() {
        return anti_spam_mute_seconds;
    }
    public boolean isAntiSpamShouldMute() {
        return anti_spam_should_mute;
    }
    public double getAntiSpamSimilarityThreshold() {
        return anti_spam_similarity_threshold;
    }
    public boolean isFilterEnabled() {
        return filter_enabled;
    }
    public String getFilterMode() {
        return filter_mode;
    }
    public List<String> getFilterWords() {
        return filter_words == null ? List.of() : filter_words;
    }
    public String getFilterChar() {
        return filter_char;
    }
    public boolean isFilterLeetspeak() {
        return filter_leetspeak;
    }
    public boolean isBan_alternate_fonts() {
        return ban_alternate_fonts;
    }
    public String getBan_alternate_fonts_permission() {
        return ban_alternate_fonts_permission;
    }
    public int getTcpSocketPort() {
        return tcp_socket_port;
    }
    public boolean isUseTcpSocket() {
        return use_tcp_socket;
    }
    public boolean isUsePluginMessage() {
        return use_plugin_message;
    }
    public String getPluginMessageServer() {
        return plugin_message_server;
    }
    public boolean isUse_netty() {
        return use_netty;
    }
    public int getNetty_port() {
        return netty_port;
    }
    public String getDiscordGuild() {
        return discord_guild;
    }
    public String getDiscordToken() {
        return discord_token;
    }
    public Map<String, String> getDiscordGroupMappings() {
        return discord_group_mappings;
    }
    public String getDiscordServerName() {
        return discord_server_name;
    }
    public boolean isDiscordEnabled() {
        return discord_enable;
    }
    public String getDiscordMessageFormat() {
        return discord_message_format != null ? discord_message_format : "<message>";
    }
    public String getDiscordReplyFormat() {
        return discord_reply_format;
    }
    public String getDiscordFormat() {
        return discord_format != null ? discord_format : "<<username>> <message>";
    }

    List<String> servers;
    Map<String, List<String>> chat_groups;
    boolean global_chat;
    boolean use_minimessage;
    boolean use_papi_socket_bridge;
    boolean use_papi_proxy_bridge;
    String format;
    String replyFormat;
    String replayMessagePrefix;
    String msgFormatSender;
    String msgFormatReceiver;
    String broadcastPrefix;
    String blocked_format;
    String blocked_reply_format;
    boolean anti_spam_enabled;
    int anti_spam_interval_millis;
    int anti_spam_mute_seconds;
    boolean anti_spam_should_mute;
    double anti_spam_similarity_threshold;
    boolean filter_enabled;
    String filter_mode;
    List<String> filter_words;
    String filter_char;
    boolean filter_leetspeak;
    boolean ban_alternate_fonts;
    String ban_alternate_fonts_permission;
    boolean blacklist;
    int forward_mode;
    boolean broadcast_message;
    boolean use_plugin_message;
    String plugin_message_server;
    boolean use_tcp_socket;
    int tcp_socket_port;
    boolean use_netty;
    int netty_port;
    String discord_token;
    String discord_guild;
    String discord_server_name;
    Map<String, String > discord_group_mappings;
    boolean discord_enable;
    String discord_message_format;
    String discord_reply_format;
    String discord_format;

}
