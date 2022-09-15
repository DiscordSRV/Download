package dev.vankka.dsrvdownloader.model.channel;

import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Version;

import java.util.Map;

public interface VersionChannel {

    VersionChannelConfig config();
    Map<String, Version> versions();
    String versionResponse();
    int versionsBehind(String comparedTo);

}
