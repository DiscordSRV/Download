package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Version;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

public interface VersionChannel {

    String LATEST_IDENTIFIER = "latest";

    Map<String, Version> versionsByIdentifier();
    String getUrl(HttpServletRequest request);
    String versionResponse(HttpServletRequest request, boolean preferIdentifier);
    int versionsBehind(String comparedTo) throws IllegalArgumentException;
    void receiveWebhook(String event, JsonNode node);

    VersionChannelConfig getConfig();

}
