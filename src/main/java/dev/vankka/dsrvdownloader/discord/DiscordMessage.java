package dev.vankka.dsrvdownloader.discord;

import club.minnced.discord.webhook.receive.ReadonlyMessage;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.CompletableFuture;

public class DiscordMessage {

    private String message;
    private String longerMessage;
    private boolean updated = true;
    private CompletableFuture<ReadonlyMessage> future;

    public DiscordMessage() {}

    public DiscordMessage setMessage(String message, String longerMessage) {
        this.message = message;
        this.longerMessage = longerMessage;
        updated = false;
        return this;
    }

    protected Pair<String, String> consumeMessage() {
        if (updated) {
            return null;
        }

        updated = true;
        return Pair.of(message, longerMessage);
    }

    protected void setFuture(CompletableFuture<ReadonlyMessage> future) {
        this.future = future;
        this.updated = true;
    }

    protected CompletableFuture<ReadonlyMessage> getFuture() {
        return future;
    }
}
