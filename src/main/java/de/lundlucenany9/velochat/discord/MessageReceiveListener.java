package de.lundlucenany9.velochat.discord;

import de.lundlucenany9.velochat.MessageFilterUtil;
import de.lundlucenany9.velochat.GroupUtil;
import de.lundlucenany9.velochat.ReplyRegistry;
import de.lundlucenany9.velochat.ReplyService;
import de.lundlucenany9.velochat.Velochat;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public class MessageReceiveListener extends ListenerAdapter {


    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;
        if (!event.isFromGuild()) return;
        if (!event.getGuild().getId().equals(Velochat.getConfig().getDiscordGuild())) return;

        String group = GroupUtil.getGroup(event.getChannel().getId());
        if (group == null || group.isBlank()) return;

        if (!Velochat.getConfig().isGlobalChat()) {
            Velochat.getLogger().warn("Please enable globalChat to use discord");
            return;
        }

        String rawMessage = invalidate(event.getMessage().getContentStripped());
        MessageFilterUtil.FilteredMessage filtered = MessageFilterUtil.filterDiscord(rawMessage);
        if (filtered.shouldBlock()) return;
        String message = filtered.finalMessage();

        MessageReference reference = event.getMessage().getMessageReference();
        if (reference == null)
            Velochat.parser.sendDiscordChat(event.getMessage(), event.getMember());
        else {
            Optional<ReplyRegistry.ReplyContext> context = ReplyRegistry.getByMessageId(reference.getMessageId());
            if (context.isEmpty()) {
                Velochat.parser.sendDiscordChat(event.getMessage(), event.getMember());
                return;
            }
            ReplyService.sendReply(
                    ReplyService.ReplySource.discord(event.getMember(), group, event.getMessageId()),
                    null,
                    message,
                    context.get()
            );
        }
    }
    private String invalidate(String message) {
        return Velochat.getConfig().isDiscordInvalidateMinecraft() ? message.replace("§", Velochat.getConfig().getMinecraftFormatReplacement()) : message;
    }

}
