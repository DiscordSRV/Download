package dev.vankka.dsrvdownloader.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Release {

    public String html_url;
    public String tag_name;
    public String name;
    public List<Asset> assets;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Asset {

        public String name;
        public String content_type;
        public String browser_download_url;

    }
}
