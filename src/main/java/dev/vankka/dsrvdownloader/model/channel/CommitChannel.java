package dev.vankka.dsrvdownloader.model.channel;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;

public class CommitChannel extends AbstractVersionChannel {

    public CommitChannel(Downloader downloader, VersionChannelConfig config) {
        super(downloader, config);
    }

    @Override
    public int versionsBehind(String comparedTo) {
        return 0;
    }
}
