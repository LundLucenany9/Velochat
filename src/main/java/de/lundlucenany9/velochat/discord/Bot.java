package de.lundlucenany9.velochat.discord;

import de.lundlucenany9.velochat.Velochat;

public final class Bot {
    private static volatile DiscordBot instance;
    private Bot(){}

    public static synchronized void initializeBot() {
        if(instance != null) throw new IllegalStateException("Bot already initialised");
        instance = Velochat.getConfig().isDiscordEnabled()
                ? createEnabled(Velochat.getConfig().getDiscordToken())
                : new DisabledDiscordBot();
    }

    private static DiscordBot createEnabled(String token) {
        try{
            return new EnabledDiscordBot(token);
        } catch (IllegalArgumentException e) {
            Velochat.getLogger().warn("Discord bot disabled: invalid token");
            return new DisabledDiscordBot();
        }
    }

    public static DiscordBot getInstance() {
        DiscordBot result = instance;
        if (result == null) {
            throw new IllegalStateException("Bot was not initialized");
        }
        return result;
    }

}
