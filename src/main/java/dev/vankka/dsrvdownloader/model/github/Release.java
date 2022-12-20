package dev.vankka.dsrvdownloader.model.github;

import java.util.List;

public record Release(String html_url, String tag_name, String name, List<Asset> assets) {

    public record Asset(String name, String content_type, String browser_download_url) {}
}
