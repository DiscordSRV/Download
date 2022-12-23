package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.VersionCheck;
import dev.vankka.dsrvdownloader.model.VersionResponse;

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
    VersionResponse versionResponse(HttpServletRequest request, boolean preferIdentifier);
    VersionCheck checkVersion(String comparedTo);
    void receiveWebhook(String event, JsonNode node);
    void removeExpiredVersions();

    VersionChannelConfig getConfig();

}
