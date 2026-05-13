package de.lundlucenany9.velochat;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import de.lundlucenany9.velochat.commands.ReplyCommand;
import de.lundlucenany9.velochat.commands.ReplyLastCommand;
import de.lundlucenany9.velochat.commands.MsgCommand;
import de.lundlucenany9.velochat.commands.BroadcastCommand;
import de.lundlucenany9.velochat.commands.ReloadCommand;
import de.lundlucenany9.velochat.commands.MuteCommand;
import de.lundlucenany9.velochat.commands.UnmuteCommand;
import de.lundlucenany9.velochat.commands.BlockCommand;
import de.lundlucenany9.velochat.commands.UnblockCommand;
import de.lundlucenany9.velochat.listeners.ChatListener;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main Velocity plugin entrypoint.
 */
@Plugin(id = "velochat", name = "Velochat", version = BuildConstants.VERSION, authors = {"LundLucenanny9"})
public final class Velochat {

    public static Logger getLogger() {
        return logger;
    }

    private static Logger logger;

    public static ProxyServer getServer() {
        return server;
    }

    private static ProxyServer server;
    private final File dataDirectory;
    private static File configFile;
    public static FormatParser parser;
    private static Config config;
    private static Messages messages;
    private static MessageHandler messageHandler;
    private static final MuteManager MUTE_MANAGER = new MuteManager();
    private static final SpamManager SPAM_MANAGER = new SpamManager();
    private static final BlockManager BLOCK_MANAGER = new BlockManager();

    @Inject
    public Velochat(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        Velochat.logger = logger;
        Velochat.server = server;
        this.dataDirectory = dataDirectory.toFile();
        configFile = new File(dataDirectory.toFile(), "config.toml");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        if(!dataDirectory.exists()) dataDirectory.mkdirs();
        saveDefaultConfig();
        saveDefaultMessages();
        config = loadConfig();
        messages = loadMessages();
        parser = new FormatParser(config, server, this, logger);
        try {
            messageHandler = new MessageHandler();
            //messageHandler.addDefaultResponder();
            MUTE_MANAGER.registerMessageListener(messageHandler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        server.getChannelRegistrar().register(MessageHandler.IDENTIFIER);

        server.getEventManager().register(this, new ChatListener());
        CommandManager commandManager = server.getCommandManager();
        registerCommand(commandManager, "reply", ReplyCommand.getCommand(server));
        registerCommand(commandManager, "r", ReplyLastCommand.getCommand(server));
        registerCommand(commandManager, "msg", MsgCommand.getCommand(server));
        registerCommand(commandManager, "broadcast", BroadcastCommand.getCommand(server));
        registerCommand(commandManager, "velochatreload", ReloadCommand.getCommand(this));
        registerCommand(commandManager, "mute", MuteCommand.getCommand(server));
        registerCommand(commandManager, "unmute", UnmuteCommand.getCommand(server));
        registerCommand(commandManager, "block", BlockCommand.getCommand(server));
        registerCommand(commandManager, "unblock", UnblockCommand.getCommand(server));
    }
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent e) {
        try {
            messageHandler.shutdownNetty();
        } catch (InterruptedException ex) {
            logger.error("MessagHandler shutdown interrupted: {}", ex.getLocalizedMessage(), ex.fillInStackTrace());
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(MessageHandler.IDENTIFIER)) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }
        if (messageHandler == null) {
            return;
        }
        messageHandler.handlePluginMessage(event.getData(), connection.getServer());
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    private void registerCommand(CommandManager manager, String name, Command command) {
        CommandMeta meta = manager.metaBuilder(name).plugin(this).build();
        manager.register(meta, command);
    }
    private void saveDefaultConfig() {
        if(configFile.exists()) return;

        try {
            try (InputStream in = getClass().getResourceAsStream("/config.toml")) {
                assert in != null;
                Files.copy(in, configFile.toPath());
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static Config getConfig(){
        if (config == null) {
            config = loadConfig();
        }
        return config;
    }

    public static Config reloadConfig() {
        config = loadConfig();
        return config;
    }

    public static Messages getMessages() {
        if (messages == null) {
            messages = loadMessages();
        }
        return messages;
    }

    private static Config loadConfig() {
        return new Toml().read(configFile).to(Config.class);
    }

    public void reload() {
        config = loadConfig();
        messages = loadMessages();
        parser = new FormatParser(config, server, this, logger);
    }

    public static MuteManager getMuteManager() {
        return MUTE_MANAGER;
    }

    public static SpamManager getSpamManager() {
        return SPAM_MANAGER;
    }

    public static BlockManager getBlockManager() {
        return BLOCK_MANAGER;
    }

    public static MessageHandler getMessageHandler() {
        return messageHandler;
    }

    private static void saveDefaultMessages() {
        File messagesFile = new File(configFile.getParentFile(), "messages.toml");
        if (messagesFile.exists()) {
            return;
        }
        try (InputStream in = Velochat.class.getResourceAsStream("/messages.toml")) {
            assert in != null;
            Files.copy(in, messagesFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Messages loadMessages() {
        File messagesFile = new File(configFile.getParentFile(), "messages.toml");
        return new Toml().read(messagesFile).to(Messages.class);
    }
}
