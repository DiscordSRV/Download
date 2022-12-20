package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.VersionCheck;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public interface VersionChannel {

    long EXPIRE_AFTER = TimeUnit.HOURS.toMillis(2);
    String LATEST_IDENTIFIER = "latest";

    void cleanupDirectory(boolean ignoreVersions);
    void refresh();
    Map<String, Version> versionsByIdentifier();
    String getUrl(HttpServletRequest request);
    ObjectNode versionResponse(HttpServletRequest request, boolean preferIdentifier);
    VersionCheck checkVersion(String comparedTo);
    void receiveWebhook(String event, JsonNode node);
    void removeExpiredVersions();

    VersionChannelConfig getConfig();

}
