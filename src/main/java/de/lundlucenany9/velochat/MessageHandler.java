package de.lundlucenany9.velochat;

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles outgoing and incoming backend messages over plugin messaging, TCP, and Netty.
 */
public class MessageHandler {
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from("velochat:main");
    private static final String LOOPBACK = "127.0.0.1";
    private final List<MessageEventListener> listeners = new CopyOnWriteArrayList<>();
    private final EventLoopGroup nettyClientGroup = new NioEventLoopGroup(1);
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(8,r -> {
        Thread t = new Thread(r, "velochat-io");
        t.setDaemon(true);
        return t;
    });
    private final EventLoopGroup nettyBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup nettyWorkerGroup = new NioEventLoopGroup();


    public MessageHandler() throws IOException {
        if(Velochat.getConfig().isUseTcpSocket()) {
            startServer();
        }
        if (Velochat.getConfig().isUse_netty()) {
            startNettyServer();
        }
    }
    public void startServer() {
        Thread tcpAcceptThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(Velochat.getConfig().tcp_socket_port)) {

                while (true) {
                    Socket socket = serverSocket.accept();
                    ioExecutor.execute(() -> handleTcp(socket));
                }

            } catch (IOException e) {
                Velochat.getLogger().warn("IO exception occurred while accepting tcp socket: {}", e.getLocalizedMessage());
            }
        }, "velochat-tcp-accept");
        tcpAcceptThread.setDaemon(true);
        tcpAcceptThread.start();
    }

    private void handleTcp(Socket socket) {
        try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            String rawMessage = in.readUTF();
            OutboundMessage payload = fromJson(rawMessage);
            emitMessageEvent(new MessageEvent(
                    Transport.TCP,
                    payload,
                    response -> out.writeUTF(toJson(response))
            ));
        } catch (IOException e) {
            Velochat.getLogger().warn("IOException occurred while handling tcp: {}", e.getLocalizedMessage());
        }
    }

    public void handlePluginMessage(byte[] data, RegisteredServer sourceServer) {
        String rawMessage = new String(data, StandardCharsets.UTF_8);
        OutboundMessage payload = fromJson(rawMessage);
        emitMessageEvent(new MessageEvent(
                Transport.PLUGIN_MESSAGE,
                payload,
                response -> sourceServer.sendPluginMessage(IDENTIFIER, toJson(response).getBytes(StandardCharsets.UTF_8))
        ));
    }

    private void emitMessageEvent(MessageEvent event) {
        if (listeners.isEmpty()) {
            try {
                event.respond(new OutboundMessage(
                        "result",
                        "",
                        event.player(),
                        "received:" + event.message()
                ));
            } catch (IOException e) {
                Velochat.getLogger().warn("Error occurred while responding to message: {}", e.getLocalizedMessage(), e.fillInStackTrace());
            }
            return;
        }
        for (MessageEventListener listener : listeners) {
            try {
                listener.onMessage(event);
            } catch (IOException e) {
                Velochat.getLogger().warn("Error occurred while receiving message: {}", e.getLocalizedMessage(), e.fillInStackTrace());
            }
        }
    }

    public void addListener(MessageEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MessageEventListener listener) {
        listeners.remove(listener);
    }

    public void addDefaultResponder() {
        addListener(event -> event.respond(new OutboundMessage(
                "result",
                event.action(),
                event.player(),
                "received:" + event.message()
        )));
    }

    public void sendMessage(byte[] data, RegisteredServer server) {
        Config config = Velochat.getConfig();
        if (config.isUsePluginMessage()) {
            sendPluginMessage(data, server);
        }
        if (config.isUseTcpSocket()) {
            sendTcpMessage(data);
        }
        if (config.isUse_netty()) {
            sendNettyMessage(data);
        }
    }

    public void sendMessage(String type, String action, String player, RegisteredServer server) {
        sendMessage(type, action, player,"", server);
    }
    public void sendMessage(String type,String action, String player) {
        sendMessage(type, action, player, "");
    }

    public void sendMessage(String type, String action, String player, String message, RegisteredServer server) {
        String json = toJson(new OutboundMessage(type, action, player, message));
        sendMessage(json.getBytes(StandardCharsets.UTF_8), server);
    }
    public void sendMessage(String type, String action, String player, String message) {
        String pluginServer = Velochat.getConfig().getPluginMessageServer();
        RegisteredServer target = null;
        if (pluginServer != null && !pluginServer.isBlank()) {
            target = Velochat.getServer().getServer(pluginServer).orElse(null);
        }
        sendMessage(type, action, player, message, target);

    }

    private void sendPluginMessage(byte[] data, RegisteredServer server){
        if(server != null)
            server.sendPluginMessage(IDENTIFIER, data);
        else if (Velochat.getConfig().isUsePluginMessage())
            Velochat.getLogger().warn("plugin_message_server is empty or not recognised; skipping plugin message send.");
    }
    private void sendTcpMessage(byte[] data) {
        byte[] copy = data.clone();
        ioExecutor.execute(()-> {
            try (
                    Socket socket = new Socket(LOOPBACK, Velochat.getConfig().getTcpSocketPort());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream())
            ) {
                out.writeUTF(new String(copy, StandardCharsets.UTF_8));
                String response = in.readUTF();
                System.out.println("TCP response: " + response);
            } catch (IOException e) {
                Velochat.getLogger().warn("Error occurred while sending tcp message: {}", e.getLocalizedMessage(), e.fillInStackTrace());
            }
        });

    }

    private void sendNettyMessage(byte[] data) {
        ioExecutor.execute(()->{
            Config config = Velochat.getConfig();
            try {
                Bootstrap bootstrap = new Bootstrap()
                        .group(nettyClientGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                        String response = msg.toString(StandardCharsets.UTF_8);
                                        System.out.println("Netty response: " + response);
                                    }
                                });
                            }
                        })
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);

                ChannelFuture connectFuture = bootstrap.connect(LOOPBACK, config.getNetty_port()).sync();
                connectFuture.channel().writeAndFlush(Unpooled.wrappedBuffer(data)).sync();
                connectFuture.channel().close().sync();
            } catch (InterruptedException e) {
                Velochat.getLogger().warn("Error occurred while closing ChannelFuture: {}", e.getLocalizedMessage(), e.fillInStackTrace());
                Thread.currentThread().interrupt();
            }
        });

    }

    public void startNettyServer() {
        EventLoopGroup bossGroup = nettyBossGroup;
        EventLoopGroup workerGroup = nettyWorkerGroup;
        Thread acceptThread = new Thread(() -> {
            try {
                ServerBootstrap bootstrap = new ServerBootstrap()
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                        String raw = msg.toString(StandardCharsets.UTF_8);
                                        OutboundMessage payload = fromJson(raw);
                                        emitMessageEvent(new MessageEvent(
                                                Transport.NETTY,
                                                payload,
                                                response -> ctx.writeAndFlush(Unpooled.wrappedBuffer(toJson(response).getBytes(StandardCharsets.UTF_8)))
                                        ));
                                    }
                                });
                            }
                        });
                bootstrap.bind(Velochat.getConfig().getNetty_port()).sync().channel().closeFuture().sync();
            } catch (InterruptedException e) {
                Velochat.getLogger().warn("Error occurred while closing ChannelFutur: {}", e.getLocalizedMessage(), e.fillInStackTrace());
                Thread.currentThread().interrupt();

            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        },"velochat-tcp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }
    public void shutdownNetty() throws InterruptedException {
        nettyWorkerGroup.shutdownGracefully().sync();
        nettyBossGroup.shutdownGracefully().sync();
        nettyClientGroup.shutdownGracefully().sync();
    }

    private String toJson(OutboundMessage payload) {
        return "{\"type\":\"" + escapeJson(payload.type()) + "\","
                + "\"action\":\"" + escapeJson(payload.action()) + "\","
                + "\"player\":\"" + escapeJson(payload.player()) + "\","
                + "\"message\":\"" + escapeJson(payload.message()) + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private OutboundMessage fromJson(String json) {
        return new OutboundMessage(
                extractJsonField(json, "type"),
                extractJsonField(json, "action"),
                extractJsonField(json, "player"),
                extractJsonField(json, "message")
        );
    }

    private String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJson(matcher.group(1));
    }

    private String unescapeJson(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    /**
     * Payload format for cross-channel message transport.
     */
    public record OutboundMessage(String type, String action, String player, String message) {}

    /**
     * Transport type that delivered the inbound payload.
     */
    public enum Transport {
        PLUGIN_MESSAGE,
        TCP,
        NETTY
    }

    /**
     * Listener for normalized inbound message events.
     */
    @FunctionalInterface
    public interface MessageEventListener {
        void onMessage(MessageEvent event) throws IOException;
    }

    @FunctionalInterface
    private interface MessageResponder {
        void send(OutboundMessage response) throws IOException;
    }

    /**
     * Event object exposed to listeners for reading payload data and replying.
     */
    public static final class MessageEvent {
        private final Transport transport;
        private final OutboundMessage payload;
        private final MessageResponder responder;

        private MessageEvent(Transport transport, OutboundMessage payload, MessageResponder responder) {
            this.transport = transport;
            this.payload = payload;
            this.responder = responder;
        }

        public Transport transport() {
            return transport;
        }

        public String type() {
            return payload.type();
        }

        public String player() {
            return payload.player();
        }

        public String action() {
            return payload.action();
        }

        public String message() {
            return payload.message();
        }
        public UUID uuid() {
            return UUID.fromString(player());
        }

        public OutboundMessage payload() {
            return payload;
        }

        public void respond(OutboundMessage response) throws IOException {
            responder.send(response);
        }

        public void respond(String type, String player, String message) throws IOException {
            responder.send(new OutboundMessage(type, payload.action(), player, message));
        }

        public void respond(String type, String action, String player, String message) throws IOException {
            responder.send(new OutboundMessage(type, action, player, message));
        }
        public void respond(String message) throws IOException {
            respond("result",action(),player(),message);
        }
    }
}
