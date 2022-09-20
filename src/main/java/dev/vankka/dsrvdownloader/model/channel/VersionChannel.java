package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Version;

import java.util.Map;

public interface VersionChannel {

    String LATEST_IDENTIFIER = "latest";

    VersionChannelConfig config();
    Map<String, Version> versions();
    byte[] versionResponse();
    int versionsBehind(String comparedTo) throws IllegalArgumentException;
    void receiveWebhook(String event, JsonNode node);

}
