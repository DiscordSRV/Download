package dev.vankka.dsrvdownloader.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.manager.ConfigManager;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.*;

@Service
public class DiscordWebhook {

    private final Queue<DiscordMessage> messages = new LinkedBlockingDeque<>();
    private final ScheduledExecutorService executorService;
    private final WebhookClient webhookClient;

    public DiscordWebhook(ConfigManager configManager) {
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.webhookClient = WebhookClient.withUrl(configManager.config().discordWebhookUrl);
        executorService.scheduleAtFixedRate(this::processQueue, 1, 1, TimeUnit.SECONDS);
    }

    public void processMessage(DiscordMessage message) {
        if (messages.contains(message)) {
            return;
        }

        messages.offer(message);
    }

    private void processQueue() {
        DiscordMessage message;
        while ((message = messages.poll()) != null) {
            Pair<String, String> content = message.consumeMessage();

            WebhookMessageBuilder webhookMessageBuilder = new WebhookMessageBuilder()
                    .setContent(content.getKey());

            String longerMessage = content.getValue();
            if (longerMessage != null) {
                int maxLength = 4096 - "```\n```".length();
                if (longerMessage.length() > maxLength) {
                    longerMessage = longerMessage.substring(0, maxLength);
                }

                webhookMessageBuilder.addEmbeds(new WebhookEmbedBuilder().setDescription("```\n" + longerMessage + "```").build());
            }

            WebhookMessage webhookMessage = webhookMessageBuilder.build();
            DiscordMessage discordMessage = message;
            CompletableFuture<ReadonlyMessage> future = message.getFuture();
            if (future != null) {
                future.whenComplete((msg, __) -> {
                    if (msg == null) {
                        return;
                    }

                    discordMessage.setFuture(webhookClient.edit(msg.getId(), webhookMessage).exceptionally(t -> {
                        Downloader.LOGGER.error("Failed to send webhook message", t);
                        return null;
                    }));
                });
            } else {
                discordMessage.setFuture(webhookClient.send(webhookMessage).exceptionally(t -> {
                    Downloader.LOGGER.error("Failed to send webhook message", t);
                    return null;
                }));
            }
        }
    }

    @Bean(destroyMethod = "shutdown")
    private ScheduledExecutorService discordExecutor() {
        return executorService;
    }

    @Bean(destroyMethod = "close")
    private WebhookClient webhookClient() {
        return webhookClient;
    }

}
